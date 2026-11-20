import { expect, Locator, Page, Route, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';

test.use({ actionTimeout: 10_000 });
import { openContextMenu } from './generic-connector.helpers';

const CREATE =
  /^\s*((Create|Erstellen)\s+(Import template|Importvorlage)|CREATE\|IMPORT_TRANSACTION_TEMPLATE)\s*(\.\.\.)?\s*$/i;
const CHECK = /Check template form|Pr.fe Importvorlagen|CHECK_TEMPLATE_FORM/i;
const TRANSFORM = /Transform PDF|PDF.*Text|TRANSFORM_PDF_TO_TXT/i;

const releases: (() => void)[] = [];
test.afterEach(() => {
  releases.splice(0).forEach((release) => release());
});

function gate() {
  let release!: () => void;
  const promise = new Promise<void>((resolve) => (release = resolve));
  releases.push(release);
  return { promise, release };
}

async function openTemplates(page: Page): Promise<void> {
  await loginAsFixtureUser(page, 'alledit');
  await page
    .locator('.p-tree-node-content', { hasText: /Base Data|Basisdaten/i })
    .first()
    .dblclick();
  await page
    .locator('.p-tree-node-content', { hasText: /Import template set|Import Vorlagengruppe/i })
    .first()
    .click();
  const select = page.locator('select#idTransactionImportPlatform');
  await expect(select.locator('option', { hasText: 'Grafioschtrader' })).toHaveCount(1);
  const value = await select.locator('option', { hasText: 'Grafioschtrader' }).getAttribute('value');
  await select.selectOption(value!);
  await expect(page.locator('import-transaction-template-table tbody tr').first()).toBeVisible();
}

async function openAction(page: Page, label: RegExp): Promise<Locator> {
  const menu = await openContextMenu(page);
  await menu.getByText(label).first().click();
  const dialog = page.locator('.p-dialog').last();
  await expect(dialog).toBeVisible();
  return dialog;
}

async function close(dialog: Locator): Promise<void> {
  await dialog.locator('button.p-dialog-close-button').click();
  await expect(dialog.page().locator('.p-dialog')).toHaveCount(0);
}

test.describe('import template notification', () => {
  test.use({ viewport: { width: 1400, height: 1800 } });

  test('keeps a draft through delayed options and cancels options when the editor closes', async ({ page }) => {
    await openTemplates(page);
    const pending = gate();
    let requested = false;
    await page.route('**/api/importtransactiontemplate/languages', async (route) => {
      requested = true;
      const response = await route.fetch();
      await pending.promise;
      await route.fulfill({ response }).catch(() => {});
    });
    const dialog = await openAction(page, CREATE);
    await expect.poll(() => requested).toBe(true);
    await dialog.locator('#templatePurpose').fill('Draft survives delayed options');
    pending.release();
    await expect(dialog.locator('select#templateLanguage option[value="de"]')).toHaveCount(1);
    await expect(dialog.locator('#templatePurpose')).toHaveValue('Draft survives delayed options');
    await close(dialog);
    await page.unroute('**/api/importtransactiontemplate/languages');

    const abandoned = gate();
    let abandonedRequested = false;
    await page.route('**/api/importtransactiontemplate/languages', async (route) => {
      abandonedRequested = true;
      const response = await route.fetch();
      await abandoned.promise;
      await route.fulfill({ response }).catch(() => {});
    });
    const editor = await openAction(page, CREATE);
    await expect.poll(() => abandonedRequested).toBe(true);
    await editor.locator('#templatePurpose').fill('Discarded pending draft');
    await close(editor);
    abandoned.release();
    await page.unroute('**/api/importtransactiontemplate/languages');
    const reopened = await openAction(page, CREATE);
    await expect(reopened.locator('select#templateLanguage option[value="de"]')).toHaveCount(1);
    await expect(reopened.locator('#templatePurpose')).toHaveValue('');
    await reopened.locator('#templatePurpose').fill('Discarded reopened draft');
    await close(reopened);
  });

  test('renders failed and successful checks, recovers after an error and ignores an obsolete result', async ({
    page
  }) => {
    await openTemplates(page);
    const old = gate();
    let calls = 0;
    await page.route('**/api/importtransactiontemplate/checkformagainsttemplate', async (route) => {
      const call = ++calls;
      if (call === 1) {
        await old.promise;
        await route
          .fulfill({ json: { failedParsedTemplateStateList: [{ templatePurpose: 'Obsolete result' }] } })
          .catch(() => {});
      } else if (call === 2) {
        await route.fulfill({
          json: {
            failedParsedTemplateStateList: [
              {
                templatePurpose: 'Current failed result',
                validSince: '2020-01-01',
                localeStr: 'de',
                lastMatchingProperty: 'isin',
                errorMessage: 'Expected instrument'
              }
            ]
          }
        });
      } else if (call === 3) {
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Notification check failed' })
        });
      } else {
        await route.fulfill({
          json: {
            importTransactionPos: {
              transactionType: 'ACCUMULATE',
              isin: 'CH0038863350',
              units: 7,
              quotation: 10,
              currencyAccount: 'CHF',
              cashaccountAmount: 70
            },
            successParsedTemplateState: { templatePurpose: 'Current successful result', validSince: '2020-01-01' }
          }
        });
      }
    });
    const dialog = await openAction(page, CHECK);
    await dialog.locator('textarea#templateAsTxt').fill('A local notification scenario');
    const check = dialog.locator('button').filter({ hasText: CHECK });
    await check.click();
    await expect.poll(() => calls).toBe(1);
    await check.click();
    await expect(dialog.getByText('Current failed result', { exact: true })).toBeVisible();
    old.release();
    await expect(dialog.getByText('Obsolete result', { exact: true })).toHaveCount(0);
    await check.click();
    await expect(page.getByRole('alert').first()).toBeVisible();
    await check.click();
    await expect(dialog.getByText('Current successful result', { exact: true })).toBeVisible();
    await expect(dialog.getByText('CH0038863350', { exact: true })).toBeVisible();
    await close(dialog);
    const reopened = await openAction(page, CHECK);
    await expect(reopened.getByText('Current successful result', { exact: true })).toHaveCount(0);
    await reopened.locator('textarea#templateAsTxt').fill('Discarded reopened document');
    await close(reopened);
  });

  test('creates, edits and deletes a template with table reloads after each save', async ({ page }) => {
    await openTemplates(page);
    let rows: any[] = [];
    await page.route('**/api/importtransactiontemplate/importtransactionplatform/*', (route) =>
      route.fulfill({ json: rows })
    );
    await page.route('**/api/importtransactiontemplate', async (route) => {
      const saved = { ...route.request().postDataJSON(), idTransactionImportTemplate: 960001 };
      rows = [saved];
      await route.fulfill({ json: saved });
    });
    await page.route('**/api/importtransactiontemplate/960001', async (route) => {
      if (route.request().method() === 'DELETE') {
        rows = [];
        await route.fulfill({ status: 204 });
      } else {
        const saved = { ...route.request().postDataJSON(), idTransactionImportTemplate: 960001 };
        rows = [saved];
        await route.fulfill({ json: saved });
      }
    });
    const group = page.locator('select#idTransactionImportPlatform');
    const selected = await group.inputValue();
    await group.selectOption('');
    await group.selectOption(selected);
    const table = page.locator('import-transaction-template-table');
    await expect(table.locator('tbody tr').filter({ hasText: 'Grafioschtrader' })).toHaveCount(0);
    const editor = await openAction(page, CREATE);
    await expect(editor.locator('select#templateLanguage option[value="de"]')).toHaveCount(1);
    await editor.locator('input#templatePurpose').fill('Notification template draft');
    await editor.locator('input#templatePurpose').blur();
    await editor.locator('select#templateCategory').selectOption('BUY_SELL_INSTRUMENT');
    await editor.locator('select#templateFormatType').selectOption('PDF');
    const date = editor.locator('#validSince input');
    await date.click();
    await expect(date).toBeFocused();
    await date.pressSequentially('01.01.20', { delay: 20 });
    await date.blur();
    await editor.locator('select#templateLanguage').selectOption('de');
    await editor.locator('textarea#templateAsTxt').fill('Notification test template');
    await editor.locator('button[type="submit"]').click();
    await expect(page.locator('import-transaction-edit-template')).toHaveCount(0);
    const row = table.locator('tbody tr').filter({ hasText: 'Notification template draft' });
    await expect(row).toBeVisible();
    await row.click();
    const edit = await openAction(page, /^\s*(Edit|Bearbeiten)\s+(Import template|Importvorlage)\s*(\.\.\.)?\s*$/i);
    await expect(edit.locator('input#templatePurpose')).toHaveValue('Notification template draft');
    await edit.locator('input#templatePurpose').fill('Notification template saved');
    await edit.locator('button[type="submit"]').click();
    await expect(table.getByText('Notification template saved', { exact: true })).toBeVisible();
    const menu = await openContextMenu(page);
    await menu.getByText(/^\s*(Delete|Löschen)\s+(Import template|Importvorlage)\s*$/i).click();
    await page
      .locator('.p-confirmdialog')
      .getByRole('button', { name: /^(Yes|Ja)$/ })
      .click();
    await expect(table.getByText('Notification template saved', { exact: true })).toHaveCount(0);
  });

  test('shows transformed text after upload and cancels the previous file and a closed session', async ({ page }) => {
    await openTemplates(page);
    const first = gate();
    let calls = 0;
    await page.route('**/api/importtransactionplatform/transformpdftotxt', async (route: Route) => {
      const call = ++calls;
      if (call === 1) await first.promise;
      await route
        .fulfill({ contentType: 'text/plain', body: call === 1 ? 'Obsolete PDF text' : 'Current PDF text' })
        .catch(() => {});
    });
    const dialog = await openAction(page, TRANSFORM);
    const upload = dialog.locator('input[type="file"]');
    await upload.setInputFiles({
      name: 'first.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.4 first')
    });
    await expect.poll(() => calls).toBe(1);
    await upload.setInputFiles({
      name: 'second.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.4 second')
    });
    await expect(dialog.locator('textarea#templateAsTxt')).toHaveValue('Current PDF text');
    first.release();
    await expect(dialog.locator('textarea#templateAsTxt')).toHaveValue('Current PDF text');
    await close(dialog);
    const reopened = await openAction(page, TRANSFORM);
    await expect(reopened.locator('textarea#templateAsTxt')).toHaveValue('');
    await close(reopened);
  });
});
