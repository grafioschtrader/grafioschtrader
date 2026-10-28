import { expect, test } from '@playwright/test';
import { libHelpers } from './helpers';

test.use({ actionTimeout: 15_000 });

/** Exercises the library table's own rows through its context menu; no forwarding setting is saved. */
test('a new inline row starts editing and cancelling removes it on every opening', async ({ page }) => {
  await libHelpers.loginAsFixtureUser(page, 'admin');
  await page
    .getByRole('treeitem', { name: /^(Messages for\/from|Nachrichten für\/von)$/ })
    .first()
    .click();
  const loaded = page.waitForResponse((response) => response.url().endsWith('/api/mailsettingforward/user'));
  await page.getByRole('tab', { name: /^(Setting messages|Einstellung Nachrichten)$/ }).click();
  await loaded;
  const table = page.locator('editable-table');
  await expect(table).toBeVisible();
  await expect(table.locator('thead th').first()).not.toHaveText('');
  const rows = table.locator('tbody tr[data-p-selectable-row]');
  const count = await rows.count();
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));

  for (let opening = 0; opening < 2; opening++) {
    await table.locator('thead').click();
    await table.locator('thead').click({ button: 'right' });
    await page
      .locator('[role="menu"]:visible')
      .getByRole('menuitem', { name: /^(Create new record|Neuen Datensatz erstellen|CREATE_NEW_RECORD)$/ })
      .click();
    const editingRow = table.locator('tbody tr', { has: page.locator('button[pCancelEditableRow]') });
    await expect(editingRow).toHaveCount(1);
    const type = editingRow.locator('select[data-field="messageComType"]');
    await expect(type).toBeVisible();
    await expect(type).toBeFocused();
    await expect(type.locator('option')).not.toHaveCount(0);
    await editingRow.locator('button[pCancelEditableRow]').click();
    await expect(editingRow).toHaveCount(0);
    await expect(rows).toHaveCount(count);
  }
  expect(errors).toEqual([]);
});
