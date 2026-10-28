import { expect, Locator, Page, test } from '@playwright/test';

import { libHelpers } from './helpers';

/**
 * Change notification of the library's table and form building blocks, driven on the batch processing monitor of
 * `grafiosch-host`: `TaskDataChangeTableComponent` (a `TableCrudSupportMenu` on `ConfigurableTableComponent`), its
 * column visibility dialog, the active panel of `ActivePanelService` and the create dialog
 * `TaskDataChangeEditComponent` on `dynamic-form`.
 *
 * Every assertion waits for a change that reaches the screen without a template event of the changed view itself:
 * a column toggled in a dialog that lives in its own DialogService root, the border of a panel when another panel is
 * clicked, a submit button that becomes enabled once the form turns valid, a required message after the field lost
 * focus. A component that misses a notification fails here as a timeout instead of showing a stale view.
 *
 * Nothing is saved. The column choice the table persists is restored before the test ends and repaired at the start
 * when an earlier run stopped with the column hidden.
 */

const ADMIN = 'admin';

const RX = {
  adminData: /(Administrative data|Administrative Daten|ADMIN_DATA)/i,
  monitor: /(Batch processing monitor|Stapelverarbeitungs Monitor|TASK_DATA_MONITOR)/i,
  globalSettings: /(Global Settings|Globale Einstellungen|GLOBAL_SETTINGS)/i,
  showMenu: /^(View|Ansicht|SHOW)$/,
  editMenu: /^(Edit|Bearbeiten|EDIT)$/,
  columns: /^(Turn on\/off columns|Spalten anzeigen|ON_OFF_COLUMNS)/,
  create: /^(Create|Erstellen)/,
  // The header may carry the badge of its sort position after the text.
  creationTime: /^\s*(Creation time|Erstellungszeit|CREATION_TIME)\b/,
  required: /(Input required!|Eingabe verlangt!|required)/
};

const TOGGLED_COLUMN = 'creationTime';

/** Opens the batch processing monitor under the administrative root and returns the table container. */
async function openTaskMonitor(page: Page): Promise<Locator> {
  const node = page.locator('.p-tree-node-content', { hasText: RX.monitor }).first();
  if (!(await node.isVisible())) {
    const root = page.locator('.p-tree-node-content', { hasText: RX.adminData }).first();
    await root.waitFor({ state: 'visible', timeout: 15_000 });
    await root.dblclick();
  }
  await node.waitFor({ state: 'visible', timeout: 15_000 });
  await node.click();
  // The routed table component has no selector of its own; the container of its configurable-table carries the
  // active border. It is located without its sizing class (data-container-full), like the watchlist table.
  const container = page.locator('configurable-table > div', { has: page.locator('thead') }).first();
  await container.waitFor({ state: 'visible', timeout: 15_000 });
  return container;
}

/** Clicks the caption of the table, which activates its panel without selecting a row. */
async function activateTable(container: Locator): Promise<void> {
  await container.locator('h4').first().click();
  await expect(container).toHaveClass(/active-border/);
}

function columnHeader(container: Locator): Locator {
  return container.locator('thead th', { hasText: RX.creationTime });
}

/** Opens View → Turn on/off columns and returns the dialog. */
async function openColumnDialog(page: Page): Promise<Locator> {
  await page.getByRole('menuitem', { name: RX.showMenu }).click();
  await page.getByRole('menuitem', { name: RX.columns }).click();
  const dialog = page.locator('.p-dialog:visible', { has: page.locator('column-visibility-dialog') });
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  return dialog;
}

function columnCheckbox(dialog: Locator): Locator {
  return dialog.locator(`#col-${TOGGLED_COLUMN}`);
}

async function closeDialog(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toBeHidden();
}

test.describe.serial('table and form notification on the batch processing monitor', () => {
  test('column visibility follows the dialog while it is open', async ({ page }) => {
    await libHelpers.loginAsFixtureUser(page, ADMIN);
    const container = await openTaskMonitor(page);
    await activateTable(container);

    let dialog = await openColumnDialog(page);
    // Repair a hidden column left behind by an earlier run before asserting anything.
    if (!(await columnCheckbox(dialog).isChecked())) {
      await dialog.locator(`label[for="col-${TOGGLED_COLUMN}"]`).click();
    }
    await expect(columnHeader(container)).toHaveCount(1);

    await dialog.locator(`label[for="col-${TOGGLED_COLUMN}"]`).click();
    await expect(columnCheckbox(dialog)).not.toBeChecked();
    await expect(columnHeader(container)).toHaveCount(0);
    await dialog.locator(`label[for="col-${TOGGLED_COLUMN}"]`).click();
    await expect(columnHeader(container)).toHaveCount(1);
    await closeDialog(dialog);

    // The choice is kept when the view is left and opened again, which recreates the table.
    await page.locator('.p-tree-node-content', { hasText: RX.globalSettings }).first().click();
    await expect(page.locator('thead th', { hasText: RX.creationTime })).toHaveCount(0);
    const reloaded = await openTaskMonitor(page);
    await expect(columnHeader(reloaded)).toHaveCount(1);
    await activateTable(reloaded);
    dialog = await openColumnDialog(page);
    await expect(columnCheckbox(dialog)).toBeChecked();
    await closeDialog(dialog);
  });

  test('the active border moves to the panel the user clicked', async ({ page }) => {
    await libHelpers.loginAsFixtureUser(page, ADMIN);
    const container = await openTaskMonitor(page);
    await activateTable(container);

    // The main tree is a panel of its own; clicking it deactivates the table in another part of the page.
    await page.locator('.p-tree-node-content', { hasText: RX.monitor }).first().click();
    await expect(container).toHaveClass(/passiv-border/);
    await expect(container).not.toHaveClass(/active-border/);

    await activateTable(container);
    await expect(container).not.toHaveClass(/passiv-border/);
  });

  test('the create dialog enables submit only when its required fields are valid', async ({ page }) => {
    await libHelpers.loginAsFixtureUser(page, ADMIN);
    const container = await openTaskMonitor(page);
    await activateTable(container);

    await page.getByRole('menuitem', { name: RX.editMenu }).click();
    await page.getByRole('menuitem', { name: RX.create }).click();
    const dialog = page.locator('.p-dialog:visible', { has: page.locator('#idTask') });
    await dialog.waitFor({ state: 'visible', timeout: 10_000 });
    const submit = dialog.locator('button[type="submit"]');
    await expect(submit).toBeDisabled();

    const taskRow = dialog.locator('.dynamic-form-row', { has: page.locator('#idTask') });
    const startTimeRow = dialog.locator('.dynamic-form-row', { has: page.locator('#earliestStartTime') });
    const startTime = dialog.locator('#earliestStartTime input');
    // The start time is prefilled, so only the task is missing.
    await expect(startTime).not.toHaveValue('');

    // Leaving the empty task shows its required message.
    await dialog.locator('p-select#idTask').click();
    const overlay = page.locator('.p-select-overlay:visible').first();
    await overlay.waitFor({ state: 'visible', timeout: 10_000 });
    await page.keyboard.press('Escape');
    await expect(overlay).toBeHidden();
    await dialog.locator('.p-dialog-header').click();
    await expect(taskRow.locator('.alert-danger:visible')).toHaveText(RX.required);
    await expect(submit).toBeDisabled();

    // Choosing a task removes the message and makes the form valid.
    await dialog.locator('p-select#idTask').click();
    await overlay.locator('[role="option"]').first().click();
    await expect(overlay).toBeHidden();
    await expect(taskRow.locator('.alert-danger:visible')).toHaveCount(0);
    await expect(submit).toBeEnabled();

    // Clearing the start time invalidates the form again and shows the message below that field.
    await startTime.click();
    await page.keyboard.press('Control+A');
    await page.keyboard.press('Backspace');
    await dialog.locator('.p-dialog-header').click();
    await expect(startTime).toHaveValue('');
    await expect(startTimeRow.locator('.alert-danger:visible')).toHaveText(RX.required);
    await expect(submit).toBeDisabled();

    // A start time in the next month, picked in the calendar, makes it valid once more.
    await dialog.locator('#earliestStartTime button').click();
    const picker = page.locator('.p-datepicker-panel:visible').first();
    await picker.waitFor({ state: 'visible', timeout: 10_000 });
    await picker.locator('.p-datepicker-next-button').click();
    await picker.locator('td:not(.p-datepicker-other-month) span', { hasText: /^15$/ }).first().click();
    await page.keyboard.press('Escape');
    await expect(startTime).not.toHaveValue('');
    await expect(startTimeRow.locator('.alert-danger:visible')).toHaveCount(0);
    await expect(submit).toBeEnabled();

    await closeDialog(dialog);
  });
});
