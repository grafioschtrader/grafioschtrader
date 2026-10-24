import { expect, Locator, Page, test } from '@playwright/test';

import { loginAsFixtureUser } from './helpers';

/**
 * The language-model keys of a tenant: `TenantAIProviderDialogComponent` with its CRUD table and the edit dialog
 * `TenantAIProviderEditComponent`, reached through Settings → Language-model keys.
 *
 * The three components run with the OnPush default, so every assertion below waits for something the screen only
 * shows after an asynchronous answer: the provider options, the model placeholder of the chosen provider, the submit
 * button re-enabled after a rejected save, the table and its selection buttons after a save or a delete. A view that
 * misses a notification fails here as a timeout rather than silently.
 *
 * The e2e database allows the providers of `gt.ai.tenant.providers` (`anthropic,openai,gemini`), all of which need
 * a key. The spec owns the Anthropic and OpenAI rows of `e2euser`'s tenant and removes them at the start. It never
 * presses "Test key", which would send the fake key to the provider.
 */

const NICKNAME = 'e2euser';
const OWNED_PROVIDERS = ['anthropic', 'openai'];
const FAKE_KEY = 'sk-e2e-not-a-real-key-0001';

const RX = {
  settings: /^(Settings|Einstellungen|SETTINGS)$/,
  aiProviders: /^(Language-model keys|Sprachmodell-Schlüssel|AI_PROVIDERS)/,
  create: /^(Add provider|Anbieter hinzufügen|AI_PROVIDER_CREATE)$/,
  edit: /^(Edit|Bearbeiten|EDIT)$/,
  delete: /^(Delete|Löschen|DELETE)$/,
  confirmYes: /^\s*(yes|ja)\s*$/i,
  editHeader: /(Language-model provider|Sprachmodell-Anbieter|TENANT_AI_PROVIDER)/,
  keyHint: /(Leave the key empty when editing|Lassen Sie den Schlüssel beim Bearbeiten leer|AI_KEY_KEEP_HINT)/,
  anthropic: /^\s*Anthropic\s*$/
};

interface ProviderRow {
  idTenantAIProvider: number;
  provider: string;
}

interface ProviderOptions {
  defaultModels: Record<string, string>;
}

async function authHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return { 'x-auth-token': token ?? '', Accept: 'application/json' };
}

/** Removes the rows this spec owns, so a run that stopped halfway does not break the retry. */
async function deleteOwnedRows(page: Page): Promise<void> {
  const headers = await authHeaders(page);
  const response = await page.request.get('/api/tenantaiprovider', { headers });
  expect(response.ok(), `${response.status()} ${await response.text()}`).toBeTruthy();
  for (const row of (await response.json()) as ProviderRow[]) {
    if (OWNED_PROVIDERS.includes(row.provider)) {
      const deleted = await page.request.delete(`/api/tenantaiprovider/${row.idTenantAIProvider}`, { headers });
      expect(deleted.ok(), `${deleted.status()} ${await deleted.text()}`).toBeTruthy();
    }
  }
}

async function readDefaultModels(page: Page): Promise<Record<string, string>> {
  const response = await page.request.get('/api/tenantaiprovider/providers', { headers: await authHeaders(page) });
  expect(response.ok(), `${response.status()} ${await response.text()}`).toBeTruthy();
  return ((await response.json()) as ProviderOptions).defaultModels;
}

/** Opens Settings → Language-model keys and returns the dialog holding the table. */
async function openProviderDialog(page: Page): Promise<Locator> {
  await page.getByRole('menuitem', { name: RX.settings }).click({ timeout: 10_000 });
  await page.getByRole('menuitem', { name: RX.aiProviders }).click({ timeout: 10_000 });
  const dialog = page.locator('.p-dialog:visible', { has: page.locator('tenant-ai-provider-table') });
  await dialog.waitFor({ state: 'visible', timeout: 10_000 });
  // The create button is enabled only once the provider options arrived.
  await expect(dialog.getByRole('button', { name: RX.create })).toBeEnabled({ timeout: 10_000 });
  return dialog;
}

/** Opens the edit dialog through one of the buttons above the table. */
async function openEditDialog(page: Page, tableDialog: Locator, button: RegExp): Promise<Locator> {
  await tableDialog.getByRole('button', { name: button }).click();
  // The edit dialog is appended to the body, so it is found by its key hint rather than inside its component.
  const edit = page.locator('.p-dialog:visible').filter({ hasText: RX.keyHint });
  await edit.waitFor({ state: 'visible', timeout: 10_000 });
  await expect(edit).toContainText(RX.editHeader);
  return edit;
}

function tableRows(tableDialog: Locator): Locator {
  return tableDialog.locator('configurable-table tbody tr');
}

/** The table row of the Anthropic provider, matched on its provider cell. */
function anthropicRow(tableDialog: Locator): Locator {
  return tableRows(tableDialog).filter({ has: tableDialog.page().locator('td', { hasText: RX.anthropic }) });
}

function submitButton(edit: Locator): Locator {
  return edit.locator('button[type="submit"]');
}

test.describe.serial('language-model keys of a tenant', () => {
  test('creates a provider after a rejected save and offers it no second time', async ({ page }) => {
    await loginAsFixtureUser(page, NICKNAME);
    await deleteOwnedRows(page);
    const defaultModels = await readDefaultModels(page);
    const tableDialog = await openProviderDialog(page);

    let edit = await openEditDialog(page, tableDialog, RX.create);
    const provider = edit.locator('select#provider');
    await expect(provider.locator('option[value="anthropic"]')).toHaveCount(1);

    // Choosing a provider shows its default model as placeholder and requires a key for a new row.
    await provider.selectOption('anthropic');
    await expect(edit.locator('#model')).toHaveAttribute('placeholder', defaultModels['anthropic']);
    await provider.selectOption('openai');
    await expect(edit.locator('#model')).toHaveAttribute('placeholder', defaultModels['openai']);
    await provider.selectOption('anthropic');
    const apiKey = edit.locator('#apiKey');
    await expect(apiKey).toBeVisible();
    await expect(apiKey).toHaveClass(/required-input/);
    await expect(submitButton(edit)).toBeDisabled();

    await apiKey.fill(FAKE_KEY);
    await expect(submitButton(edit)).toBeEnabled();

    // The server rejects the model name; the dialog stays open and the submit button becomes usable again.
    await edit.locator('#model').fill('not a model');
    const rejected = page.waitForResponse(
      (response) => response.url().endsWith('/api/tenantaiprovider') && response.request().method() === 'POST'
    );
    await submitButton(edit).click();
    expect((await rejected).ok()).toBeFalsy();
    await expect(edit).toBeVisible();
    await expect(submitButton(edit)).toBeEnabled({ timeout: 10_000 });

    await edit.locator('#model').fill('');
    const saved = page.waitForResponse(
      (response) => response.url().endsWith('/api/tenantaiprovider') && response.request().method() === 'POST'
    );
    await submitButton(edit).click();
    const savedResponse = await saved;
    expect(savedResponse.ok(), `${savedResponse.status()} ${await savedResponse.text()}`).toBeTruthy();
    await expect(edit).toBeHidden({ timeout: 10_000 });
    await expect(anthropicRow(tableDialog)).toHaveCount(1, { timeout: 10_000 });

    // A provider that already has a row is not offered for a new one.
    edit = await openEditDialog(page, tableDialog, RX.create);
    await expect(edit.locator('select#provider option[value="anthropic"]')).toHaveCount(0);
    await expect(edit.locator('select#provider option[value="openai"]')).toHaveCount(1);
    await edit.locator('.p-dialog-close-button').click();
    await expect(edit).toBeHidden();
  });

  test('edits the provider without requiring the stored key again, then deletes it', async ({ page }) => {
    await loginAsFixtureUser(page, NICKNAME);
    const tableDialog = await openProviderDialog(page);
    const row = anthropicRow(tableDialog);
    await expect(row).toHaveCount(1, { timeout: 10_000 });

    // The selection buttons appear only for a selected row.
    await expect(tableDialog.getByRole('button', { name: RX.edit })).toHaveCount(0);
    await row.click();
    await expect(tableDialog.getByRole('button', { name: RX.edit })).toBeVisible();

    const edit = await openEditDialog(page, tableDialog, RX.edit);
    await expect(edit.locator('select#provider')).toBeDisabled();
    const apiKey = edit.locator('#apiKey');
    await expect(apiKey).toHaveValue('');
    await expect(apiKey).not.toHaveClass(/required-input/);
    // The stored key is never returned; its ending is shown as placeholder instead.
    await expect(apiKey).toHaveAttribute('placeholder', /0001$/);
    await expect(submitButton(edit)).toBeEnabled();

    await edit.locator('#priority').fill('3');
    const saved = page.waitForResponse(
      (response) => response.url().endsWith('/api/tenantaiprovider') && response.request().method() === 'PUT'
    );
    await submitButton(edit).click();
    const savedResponse = await saved;
    expect(savedResponse.ok(), `${savedResponse.status()} ${await savedResponse.text()}`).toBeTruthy();
    await expect(edit).toBeHidden({ timeout: 10_000 });
    await expect(row).toContainText('3');

    // Reloading after the save keeps the row selected, so its buttons stay without another click.
    await expect(tableDialog.getByRole('button', { name: RX.delete })).toBeVisible();
    await tableDialog.getByRole('button', { name: RX.delete }).click();
    const confirm = page.locator('[role="alertdialog"]:visible').first();
    await confirm.getByRole('button', { name: RX.confirmYes }).first().click();
    await expect(anthropicRow(tableDialog)).toHaveCount(0, { timeout: 10_000 });
    await expect(tableDialog.getByRole('button', { name: RX.edit })).toHaveCount(0);
  });
});
