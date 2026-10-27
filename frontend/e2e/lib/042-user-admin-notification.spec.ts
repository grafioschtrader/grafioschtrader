import { expect, Locator, Page, test } from '@playwright/test';
import { libHelpers } from './helpers';

// Exercises the user administration and the own change requests in the zoneless consumer without saving anything.
// Both tables receive their rows after a held response, so a missing notification leaves them empty.

interface User {
  nickname: string;
  mostPrivilegedRole: string;
  entityLimitList: unknown[];
  userChangeLimitProposeList: unknown[];
}

const OWNER_DIALOG = 'user-change-owner-entities .p-dialog';
const EDIT_DIALOG = 'user-edit .p-dialog';

test('user administration renders delayed rows, nested limits and reopens its dialogs', async ({ page }) => {
  await libHelpers.loginAsFixtureUser(page, 'admin');
  const held = await holdOnce(page, '**/api/useradmin');
  try {
    await page.goto(`${page.url().split('/mainview')[0]}/mainview/useradmin`);
    const table = page.locator('configurable-table').first();
    await expect(table).toBeVisible();
    const users = (await held.arrived) as User[];
    held.release();
    await expect(table.locator('tr[data-p-selectable-row]')).toHaveCount(users.length);

    const withLimits = users.find(
      (user) => user.entityLimitList.length > 0 && user.userChangeLimitProposeList.length === 0
    );
    if (withLimits) {
      await userRow(page, table, withLimits.nickname).locator('a[href="#"]').first().click();
      await expect(page.locator('entity-limit-user-table tr[data-p-selectable-row]')).toHaveCount(
        withLimits.entityLimitList.length
      );
    }

    const limited = users.find((user) => user.mostPrivilegedRole === 'ROLE_LIMITEDIT')!;
    expect(limited).toBeTruthy();
    const target = userRow(page, table, limited.nickname);
    for (let opening = 0; opening < 2; opening++) {
      await menuItem(page, target, /^(Change entity owner|Besitzer von Entitäten ändern)/);
      const dialog = page.locator(OWNER_DIALOG);
      await expect(dialog.locator('#fromIdUser')).toHaveValue(limited.nickname);
      await expect(dialog.locator('select#toIdUser option').first()).toBeAttached();
      await dialog.locator('.p-dialog-close-button').click();
      await expect(dialog).toHaveCount(0);
    }
    for (let opening = 0; opening < 2; opening++) {
      await menuItem(page, target, /^(Edit|Bearbeiten)\b/);
      const dialog = page.locator(EDIT_DIALOG);
      await expect(dialog.locator('#nickname')).toHaveValue(limited.nickname);
      await expect(dialog.locator('select#mostPrivilegedRole')).toHaveValue('ROLE_LIMITEDIT');
      await dialog.locator('.p-dialog-close-button').click();
      await expect(dialog).toHaveCount(0);
    }
  } finally {
    held.release();
  }
});

test('own change requests render after a delayed response', async ({ page }) => {
  await libHelpers.loginAsFixtureUser(page, 'admin');
  await page.goto(`${page.url().split('/mainview')[0]}/mainview/proposeChangeTabMenu`);
  const held = await holdOnce(page, '**/api/proposechangeentity');
  try {
    await page.getByRole('tab', { name: /^(Your data change requests|Ihre Datenänderungswünsche)$/ }).click();
    const table = page.locator('configurable-table').first();
    await expect(table.locator('h4')).toHaveText(/^(Your data change requests|Ihre Datenänderungswünsche)$/);
    const proposals = (await held.arrived) as unknown[];
    held.release();
    await expect(table.locator('tr[data-p-selectable-row]')).toHaveCount(proposals.length);
  } finally {
    held.release();
  }
});

function userRow(page: Page, table: Locator, nickname: string): Locator {
  return table
    .locator('tbody > tr[data-p-selectable-row]')
    .filter({ has: page.getByRole('cell', { name: nickname, exact: true }) });
}

async function menuItem(page: Page, row: Locator, name: RegExp): Promise<void> {
  if (!(await row.getAttribute('class'))?.split(' ').includes('p-datatable-row-selected')) await row.click();
  await row.click({ button: 'right' });
  await page.locator('[role="menu"]:visible').getByRole('menuitem', { name }).click();
}

/** Holds the first GET answer of the URL until released and hands its JSON body to the test. */
async function holdOnce(page: Page, url: string): Promise<{ arrived: Promise<unknown>; release: () => void }> {
  let release!: () => void;
  let arrive!: (body: unknown) => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const arrived = new Promise<unknown>((resolve) => (arrive = resolve));
  let holding = false;
  await page.route(url, async (route) => {
    if (holding || route.request().method() !== 'GET') return route.fallback();
    holding = true;
    const response = await route.fetch();
    expect(response.ok()).toBeTruthy();
    arrive(await response.json());
    await gate;
    await route.fulfill({ response });
  });
  return { arrived, release };
}
