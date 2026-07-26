import {expect, Locator, Page, Response, test} from '@playwright/test';
import {loginAsCsvUser} from './helpers';

const ADMIN = 'admin'; // hg@hugograf.com in users.csv
const FIRST_YEAR = 2000;
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
    const isExcludedHoliday = (date.getMonth() === 0 && date.getDate() === 1)
      || (date.getMonth() === 11 && date.getDate() === 25);
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

function isTradingDaysResponse(response: Response, method: 'GET' | 'PUT', year?: number): boolean {
  const pathname = new URL(response.url()).pathname;
  const expectedSuffix = year == null ? '/api/tradingdaysplus' : `/api/tradingdaysplus/${year}`;
  return response.request().method() === method && pathname.endsWith(expectedSuffix);
}

async function renderedDays(calendar: Locator): Promise<RenderedDay[]> {
  return calendar.locator('.day').evaluateAll(elements => elements.map(element => {
    const day = element as HTMLElement;
    return {
      disabled: day.parentElement?.classList.contains('daydisabled') ?? false,
      backgroundColor: day.style.backgroundColor,
    };
  }));
}

function calendarDay(calendar: Locator, monthIndex: number, day: number): Locator {
  return calendar.locator('month-calendar').nth(monthIndex).locator('.day')
    .filter({hasText: new RegExp(`^\\s*${day}\\s*$`)}).first();
}

async function removeHolidayWhenMarked(calendar: Locator, year: number, monthIndex: number,
    dayOfMonth: number): Promise<void> {
  const date = new Date(year, monthIndex, dayOfMonth);
  const holiday = calendarDay(calendar, monthIndex, dayOfMonth);
  await expect(holiday).toBeVisible();

  if (date.getDay() === 0 || date.getDay() === 6) {
    await expect(holiday.locator('..')).toHaveClass(/daydisabled/);
    await expect.poll(() => holiday.evaluate(element => (element as HTMLElement).style.backgroundColor))
      .toBe('transparent');
  } else {
    await expect.poll(() => holiday.evaluate(element => (element as HTMLElement).style.backgroundColor))
      .toBe('yellow');
    await holiday.click();
    await expect.poll(() => holiday.evaluate(element => (element as HTMLElement).style.backgroundColor))
      .toBe('transparent');
  }
}

async function selectYear(page: Page, year: number): Promise<Response> {
  const responsePromise = page.waitForResponse(response => isTradingDaysResponse(response, 'GET', year));
  await page.locator('p-select#idYearSelect').click();
  await page.getByRole('option', {name: String(year), exact: true}).click();
  return responsePromise;
}

async function navigateToNextYear(page: Page, contentArea: Locator, year: number): Promise<Response> {
  const nextYear = year + 1;
  const responsePromise = page.waitForResponse(response => isTradingDaysResponse(response, 'GET', nextYear));
  const nextButton = contentArea.locator('p-footer button')
    .filter({hasText: new RegExp(`^\\s*${nextYear}\\s*$`)});
  await nextButton.click();
  return responsePromise;
}

async function assertFreshYear(response: Response, year: number): Promise<void> {
  expect(response.ok(), `GET trading calendar ${year}`).toBeTruthy();
  const body = await response.json() as TradingDaysResponse;
  expect(body.dates, `Year ${year} must be empty before this fresh-state workflow runs`).toEqual([]);
}

test.describe.serial('38: create the global trading calendar as admin', () => {
  test('marks and saves weekdays excluding New Year and Christmas for 2000-2030', async ({page}) => {
    test.setTimeout(600_000);
    await loginAsCsvUser(page, ADMIN);

    const calendarNode = page.getByRole('treeitem', {name: CALENDAR_NODE_RX}).first();
    await calendarNode.waitFor({state: 'visible', timeout: 15_000});
    await calendarNode.click();
    await page.waitForURL(/\/mainview\/tradingcalendarglobal(?:$|[/?#])/, {timeout: 15_000});

    const contentArea = page.locator('.data-container', {has: page.locator('ng-fullyearcalendar-lib')}).first();
    const calendar = contentArea.locator('ng-fullyearcalendar-lib');
    await expect(calendar.locator('month-calendar')).toHaveCount(12, {timeout: 15_000});

    await assertFreshYear(await selectYear(page, FIRST_YEAR), FIRST_YEAR);

    for (let year = FIRST_YEAR; year <= LAST_YEAR; year++) {
      await expect(page.locator('p-select#idYearSelect')).toContainText(String(year));

      await contentArea.locator('h4').click({button: 'right'});
      const menu = page.locator('[role="menu"]:visible');
      await menu.waitFor({state: 'visible', timeout: 5_000});
      await menu.getByText(MARK_YEAR_RX, {exact: true}).click();

      await expect.poll(async () => (await renderedDays(calendar))
        .filter(day => day.backgroundColor === 'yellow').length).toBe(weekdayCount(year));
      const markedYear = await renderedDays(calendar);
      expect(markedYear).toHaveLength(new Date(year, 1, 29).getMonth() === 1 ? 366 : 365);
      expect(markedYear.filter(day => !day.disabled)).toHaveLength(weekdayCount(year));
      expect(markedYear.filter(day => !day.disabled).every(day => day.backgroundColor === 'yellow')).toBeTruthy();
      expect(markedYear.filter(day => day.disabled).every(day => day.backgroundColor === 'transparent')).toBeTruthy();

      await removeHolidayWhenMarked(calendar, year, 0, 1);
      await removeHolidayWhenMarked(calendar, year, 11, 25);

      const expectedDates = expectedTradingDays(year);
      const finalDraft = await renderedDays(calendar);
      expect(finalDraft.filter(day => day.backgroundColor === 'yellow')).toHaveLength(expectedDates.length);

      const saveResponsePromise = page.waitForResponse(response => isTradingDaysResponse(response, 'PUT'));
      // The projected PrimeNG icon is part of the accessible name (for example "\uE95B Save"), so
      // locate the calendar footer button by its visible label instead of an exact role name.
      const saveButton = contentArea.locator('p-footer button').filter({hasText: /Save|Speichern/i});
      await expect(saveButton).toBeEnabled();
      await saveButton.click();

      const saveResponse = await saveResponsePromise;
      expect(saveResponse.ok(), `PUT trading calendar ${year}`).toBeTruthy();
      const payload = saveResponse.request().postDataJSON() as SaveTradingDaysPayload;
      expect(payload.year).toBe(year);
      expect(payload.addRemoveDays.every(day => day.add)).toBeTruthy();
      expect(payload.addRemoveDays.map(day => day.date)).toEqual(expectedDates);

      const saved = await saveResponse.json() as TradingDaysResponse;
      expect([...saved.dates].sort()).toEqual([...expectedDates].sort());
      await expect.poll(async () => (await renderedDays(calendar))
        .filter(day => day.backgroundColor === 'lime').length).toBe(expectedDates.length);

      if (year < LAST_YEAR) {
        await assertFreshYear(await navigateToNextYear(page, contentArea, year), year + 1);
      }
    }

    const beyondLastYear = contentArea.locator('p-footer button')
      .filter({hasText: new RegExp(`^\\s*${LAST_YEAR + 1}\\s*$`)});
    await expect(beyondLastYear).toHaveCount(0);
  });
});
