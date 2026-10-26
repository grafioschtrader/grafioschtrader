import { expect, Locator, test } from '@playwright/test';
import { libHelpers } from './helpers';

// Exercises the general metadata table and edit form in the zoneless consumer without saving a definition.
test('general UDF metadata loads asynchronously and repeated edit sessions update their dependent fields', async ({
  page
}) => {
  await libHelpers.loginAsFixtureUser(page, 'admin');
  let release!: () => void;
  let arrived!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const received = new Promise<void>((resolve) => (arrived = resolve));
  let rowCount = 0;
  await page.route('**/api/udfmetadatageneral', async (route) => {
    const response = await route.fetch();
    expect(response.ok()).toBeTruthy();
    rowCount = (await response.json()).length;
    arrived();
    await gate;
    await route.fulfill({ response });
  });
  try {
    await page.goto(`${page.url().split('/mainview')[0]}/mainview/udfmetadatageneral`);
    const table = page.locator('udf-metadata-general-table configurable-table');
    await expect(table).toBeVisible();
    await received;
    release();
    await expect(table.locator('tr[data-p-selectable-row]')).toHaveCount(rowCount);
    for (let opening = 0; opening < 2; opening++) {
      await table.locator('h4').click();
      await table.locator('h4').click({ button: 'right' });
      await page
        .locator('[role="menu"]:visible')
        .getByRole('menuitem', { name: /^(Create|Erstellen)\b/ })
        .click();
      const dialog = page.locator('udf-metadata-general-edit .p-dialog');
      await expect(dialog.locator('#description')).toHaveValue('');
      const type = dialog.locator('select#udfDataType');
      await expect(type.locator('option[value="UDF_String"]')).toHaveCount(1);
      await type.selectOption('UDF_String');
      await expect(dialog.locator('#fieldSize')).toBeEnabled();
      await dialog.locator('#fieldSize').fill('32');
      await type.selectOption('UDF_Boolean');
      await expect(dialog.locator('#fieldSize')).toBeDisabled();
      await expect(dialog.locator('#fieldSize')).toHaveValue('');
      await type.selectOption('UDF_String');
      await expect(dialog.locator('#fieldSize')).toBeEnabled();
      await dialog.locator('#description').fill('Unsaved library UDF draft');
      await close(dialog);
    }
  } finally {
    release();
  }
});

async function close(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}
