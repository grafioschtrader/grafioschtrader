import { expect, Locator, Page, Request, Response, test } from '@playwright/test';
import { holdCalendarResponse } from './calendar.helpers';
import { loginAsFixtureUser } from './helpers';

// The fresh E2E database starts with an empty global trading calendar, while every security transaction is validated
// against trading_days_plus. TradingDaysPlusResourceTest (ResourceTestSuite_50) creates the years from 2000 through
// next year before Playwright resumes at 050, so a failure here cannot cascade into the specs that book, import or
// replay dated data. This spec verifies those years and enters only the later years through the UI.

const ADMIN = 'admin'; // hg@hugograf.com in users.json
const FIRST_YEAR = 2000;
/** First year entered through the UI; the years before it belong to TradingDaysPlusResourceTest. */
const FIRST_UI_YEAR = new Date().getFullYear() + 2;
/** GlobalConstants.YOUNGEST_TRADING_CALENDAR_DAY limits the calendar to this year. */
const LAST_YEAR = 2030;
const CALENDAR_NODE_RX = /^(Global trading calendar|Handelskalender global)$/i;
const MARK_YEAR_RX = /^(Marking of all trading days|Markierung aller Handelstage)$/i;

interface AddRemoveDayPayload {
  date: string;
  add: boolean;
}

interface SaveTradingDaysPayload {
  year: number;
  addRemoveDays: AddRemoveDayPayload[];
}

interface TradingDaysResponse {
  dates: string[];
}

interface RenderedDay {
  disabled: boolean;
  backgroundColor: string;
}

async function authHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return { 'x-auth-token': token!, Accept: 'application/json' };
}

async function loadTradingDays(page: Page, headers: Record<string, string>, year: number): Promise<string[]> {
  const response = await page.request.get(`/api/tradingdaysplus/${year}`, { headers });
  expect(response.ok(), `loading trading calendar ${year}: ${await response.text()}`).toBeTruthy();
  return ((await response.json()) as TradingDaysResponse).dates;
}

function isCompleteYear(dates: string[], year: number): boolean {
  return JSON.stringify([...dates].sort()) === JSON.stringify(expectedTradingDays(year));
}

/**
 * Verifies the years created by TradingDaysPlusResourceTest, then clears a partial calendar of the UI years.
 *
 * @returns false when the UI years are already complete, so that nothing is left to enter
 */
async function prepareFreshCalendar(page: Page): Promise<boolean> {
  const headers = await authHeaders(page);
  for (let year = FIRST_YEAR; year < FIRST_UI_YEAR; year++) {
    expect(
      isCompleteYear(await loadTradingDays(page, headers, year), year),
      `trading calendar ${year} must be complete; it is created by TradingDaysPlusResourceTest in ResourceTestSuite_50`
    ).toBeTruthy();
  }

  const existingByYear = new Map<number, string[]>();
  let complete = true;
  for (let year = FIRST_UI_YEAR; year <= LAST_YEAR; year++) {
    const dates = await loadTradingDays(page, headers, year);
    existingByYear.set(year, dates);
    complete &&= isCompleteYear(dates, year);
  }

  if (complete) {
    return false;
  }

  for (const [year, dates] of existingByYear) {
    if (dates.length > 0) {
      const response = await page.request.put('/api/tradingdaysplus', {
        headers,
        data: { year, addRemoveDays: dates.map((date) => ({ date, add: false })) }
      });
      expect(response.ok(), `clearing partial trading calendar ${year}: ${await response.text()}`).toBeTruthy();
    }
  }
  return true;
}

function formatDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function expectedTradingDays(year: number): string[] {
  const tradingDays: string[] = [];
  const date = new Date(year, 0, 1);
  const end = new Date(year, 11, 31);

  while (date <= end) {
    const isWeekend = date.getDay() === 0 || date.getDay() === 6;
    const isExcludedHoliday =
      (date.getMonth() === 0 && date.getDate() === 1) || (date.getMonth() === 11 && date.getDate() === 25);
    if (!isWeekend && !isExcludedHoliday) {
      tradingDays.push(formatDate(date));
    }
    date.setDate(date.getDate() + 1);
  }
  return tradingDays;
}

function weekdayCount(year: number): number {
  const date = new Date(year, 0, 1);
  const end = new Date(year, 11, 31);
  let count = 0;
  while (date <= end) {
    if (date.getDay() !== 0 && date.getDay() !== 6) {
      count++;
    }
    date.setDate(date.getDate() + 1);
  }
  return count;
}

function isTradingDaysRequest(request: Request, method: 'GET' | 'PUT', year?: number): boolean {
  const pathname = new URL(request.url()).pathname;
  const expectedSuffix = year == null ? '/api/tradingdaysplus' : `/api/tradingdaysplus/${year}`;
  return request.method() === method && pathname.endsWith(expectedSuffix);
}

/**
 * Performs a UI action and returns the response of the trading-calendar request it triggers. A request that fails
 * without a response (for example a refused connection to the dev server) fails the test at once instead of letting
 * it wait for a response until the test timeout.
 */
async function tradingDaysResponseOf(
  page: Page,
  method: 'GET' | 'PUT',
  year: number | undefined,
  action: () => Promise<void>
): Promise<Response> {
  const requestPromise = page.waitForRequest((request) => isTradingDaysRequest(request, method, year));
  await action();
  const request = await requestPromise;
  const response = await request.response();
  expect(response, `${method} ${request.url()} failed: ${request.failure()?.errorText}`).not.toBeNull();
  return response!;
}

async function renderedDays(calendar: Locator): Promise<RenderedDay[]> {
  return calendar.locator('.day').evaluateAll((elements) =>
    elements.map((element) => {
      const day = element as HTMLElement;
      return {
        disabled: day.parentElement?.classList.contains('daydisabled') ?? false,
        backgroundColor: day.style.backgroundColor
      };
    })
  );
}

function calendarDay(calendar: Locator, monthIndex: number, day: number): Locator {
  return calendar
    .locator('month-calendar')
    .nth(monthIndex)
    .locator('.day')
    .filter({ hasText: new RegExp(`^\\s*${day}\\s*$`) })
    .first();
}

async function removeHolidayWhenMarked(
  calendar: Locator,
  year: number,
  monthIndex: number,
  dayOfMonth: number
): Promise<void> {
  const date = new Date(year, monthIndex, dayOfMonth);
  const holiday = calendarDay(calendar, monthIndex, dayOfMonth);
  await expect(holiday).toBeVisible();

  if (date.getDay() === 0 || date.getDay() === 6) {
    await expect(holiday.locator('..')).toHaveClass(/daydisabled/);
    await expect
      .poll(() => holiday.evaluate((element) => (element as HTMLElement).style.backgroundColor))
      .toBe('transparent');
  } else {
    await expect
      .poll(() => holiday.evaluate((element) => (element as HTMLElement).style.backgroundColor))
      .toBe('yellow');
    await holiday.click();
    await expect
      .poll(() => holiday.evaluate((element) => (element as HTMLElement).style.backgroundColor))
      .toBe('transparent');
  }
}

async function selectYear(page: Page, year: number): Promise<Response> {
  return tradingDaysResponseOf(page, 'GET', year, async () => {
    await page.locator('p-select#idYearSelect').click();
    await page.getByRole('option', { name: String(year), exact: true }).click();
  });
}

async function navigateToNextYear(page: Page, contentArea: Locator, year: number): Promise<Response> {
  const nextYear = year + 1;
  const nextButton = contentArea.locator('p-footer button').filter({ hasText: new RegExp(`^\\s*${nextYear}\\s*$`) });
  return tradingDaysResponseOf(page, 'GET', nextYear, () => nextButton.click());
}

async function assertFreshYear(response: Response, year: number): Promise<void> {
  expect(response.ok(), `GET trading calendar ${year}`).toBeTruthy();
  const body = (await response.json()) as TradingDaysResponse;
  expect(body.dates, `Year ${year} must be empty before this fresh-state workflow runs`).toEqual([]);
}

test.describe.serial('create the global trading calendar as admin', () => {
  test('marks and saves weekdays excluding New Year and Christmas for the years after next year', async ({ page }) => {
    test.skip(
      FIRST_UI_YEAR > LAST_YEAR,
      'No calendar year is left for the UI; raise GlobalConstants.YOUNGEST_TRADING_CALENDAR_DAY and LAST_YEAR'
    );
    test.setTimeout(120_000);
    await loginAsFixtureUser(page, ADMIN);
    if (!(await prepareFreshCalendar(page))) {
      return;
    }

    const calendarNode = page.getByRole('treeitem', { name: CALENDAR_NODE_RX }).first();
    await calendarNode.waitFor({ state: 'visible', timeout: 15_000 });
    await calendarNode.click();
    await page.waitForURL(/\/mainview\/tradingcalendarglobal(?:$|[/?#])/, { timeout: 15_000 });

    const contentArea = page.locator('.data-container', { has: page.locator('ng-fullyearcalendar-lib') }).first();
    const calendar = contentArea.locator('ng-fullyearcalendar-lib');
    await expect(calendar.locator('month-calendar')).toHaveCount(12, { timeout: 15_000 });

    await assertFreshYear(await selectYear(page, FIRST_UI_YEAR), FIRST_UI_YEAR);

    for (let year = FIRST_UI_YEAR; year <= LAST_YEAR; year++) {
      await expect(page.locator('p-select#idYearSelect')).toContainText(String(year));

      await contentArea.locator('h4').click({ button: 'right' });
      const menu = page.locator('[role="menu"]:visible');
      await menu.waitFor({ state: 'visible', timeout: 5_000 });
      await menu.getByText(MARK_YEAR_RX, { exact: true }).click();

      await expect
        .poll(async () => (await renderedDays(calendar)).filter((day) => day.backgroundColor === 'yellow').length)
        .toBe(weekdayCount(year));
      const markedYear = await renderedDays(calendar);
      expect(markedYear).toHaveLength(new Date(year, 1, 29).getMonth() === 1 ? 366 : 365);
      expect(markedYear.filter((day) => !day.disabled)).toHaveLength(weekdayCount(year));
      expect(markedYear.filter((day) => !day.disabled).every((day) => day.backgroundColor === 'yellow')).toBeTruthy();
      expect(
        markedYear.filter((day) => day.disabled).every((day) => day.backgroundColor === 'transparent')
      ).toBeTruthy();

      await removeHolidayWhenMarked(calendar, year, 0, 1);
      await removeHolidayWhenMarked(calendar, year, 11, 25);

      const expectedDates = expectedTradingDays(year);
      const finalDraft = await renderedDays(calendar);
      expect(finalDraft.filter((day) => day.backgroundColor === 'yellow')).toHaveLength(expectedDates.length);

      // The projected Optimus UI icon is part of the accessible name (for example "\uE95B Save"), so
      // locate the calendar footer button by its visible label instead of an exact role name.
      const saveButton = contentArea.locator('p-footer button').filter({ hasText: /Save|Speichern/i });
      await expect(saveButton).toBeEnabled();
      const saveResponse = await tradingDaysResponseOf(page, 'PUT', undefined, () => saveButton.click());
      expect(saveResponse.ok(), `PUT trading calendar ${year}`).toBeTruthy();
      const payload = saveResponse.request().postDataJSON() as SaveTradingDaysPayload;
      expect(payload.year).toBe(year);
      expect(payload.addRemoveDays.every((day) => day.add)).toBeTruthy();
      expect(payload.addRemoveDays.map((day) => day.date)).toEqual(expectedDates);

      const saved = (await saveResponse.json()) as TradingDaysResponse;
      expect([...saved.dates].sort()).toEqual([...expectedDates].sort());
      await expect
        .poll(async () => (await renderedDays(calendar)).filter((day) => day.backgroundColor === 'lime').length)
        .toBe(expectedDates.length);

      if (year < LAST_YEAR) {
        await assertFreshYear(await navigateToNextYear(page, contentArea, year), year + 1);
      }
    }

    const beyondLastYear = contentArea
      .locator('p-footer button')
      .filter({ hasText: new RegExp(`^\\s*${LAST_YEAR + 1}\\s*$`) });
    await expect(beyondLastYear).toHaveCount(0);
  });
});

test('global calendar publishes delayed data and keeps the newest selected year', async ({ page }) => {
  await loginAsFixtureUser(page, ADMIN);
  const initial = await holdCalendarResponse(page, `**/api/tradingdaysplus/${new Date().getFullYear()}`);
  const calendar = page.locator('ng-fullyearcalendar-lib');
  try {
    await page.getByRole('treeitem', { name: CALENDAR_NODE_RX }).first().click();
    await initial.arrived;
    initial.release();
    await expect(calendar.locator('.day[style*="lime"]').first()).toBeVisible();
  } finally {
    initial.release();
  }
  const obsolete = await holdCalendarResponse(page, '**/api/tradingdaysplus/2024');
  try {
    await page.locator('p-select#idYearSelect').click();
    await page.getByRole('option', { name: '2024', exact: true }).click();
    await obsolete.arrived;
    const response = await selectYear(page, 2023);
    const dates = ((await response.json()) as TradingDaysResponse).dates;
    expect(dates.length).toBeGreaterThan(0);
    await expect(calendar.locator('.day')).toHaveCount(365);
    await expect(calendar.locator('.day[style*="lime"]')).toHaveCount(dates.length);
    obsolete.release();
    await obsolete.done;
    await expect(page.locator('p-select#idYearSelect')).toContainText('2023');
    await expect(calendar.locator('.day[style*="lime"]')).toHaveCount(dates.length);
    const firstDay = new Date(`${dates[0]}T00:00:00`);
    const day = calendarDay(calendar, firstDay.getMonth(), firstDay.getDate());
    const save = page.locator('p-footer button').filter({ hasText: /Save|Speichern/i });
    await day.click();
    await expect(save).toBeEnabled();
    await expect(page.locator('p-select#idYearSelect')).toHaveClass(/p-disabled/);
    await day.click();
    await expect(save).toBeDisabled();
    await expect(calendar.locator('.day[style*="lime"]')).toHaveCount(dates.length);
  } finally {
    obsolete.release();
  }
});
