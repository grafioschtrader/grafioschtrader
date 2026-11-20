import { expect, Locator, Page, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';

test.use({ actionTimeout: 10_000 });
import { openSecurityAccount, openTransactionImport } from './import-transaction.helpers';

async function contextMenu(page: Page): Promise<Locator> {
  const table = page.locator('securityaccount-import-transaction-table');
  await table.dispatchEvent('click');
  await table.locator('xpath=ancestor::div[contains(@class,"data-container")]').first().click({ button: 'right' });
  return page.locator('[role="menu"]:visible');
}

test('notifies cash assignment, retains row selection and cancels an obsolete head load', async ({ page }) => {
  await loginAsFixtureUser(page, 'limit1');
  const accountId = await openSecurityAccount(page, { portfolioName: 'Migros', securityAccountName: 'Migros' });
  const heads = [910001, 910002].map((idTransactionHead) => ({
    idTransactionHead,
    name: `Notification import ${idTransactionHead}`,
    securityaccount: { idSecuritycashAccount: accountId }
  }));
  const position = {
    idTransactionPos: 920001,
    transactionTime: '2020-01-01T00:00:00',
    transactionType: 'ACCUMULATE',
    isin: 'CH0038863350',
    security: null,
    cashaccount: null as any,
    currencyAccount: 'CHF',
    units: 7,
    quotation: 10,
    cashaccountAmount: 70,
    calcCashaccountAmount: 70,
    readyForTransaction: false,
    fileNameOriginal: 'notification.pdf',
    fileType: 'P',
    idTransaction: null,
    idTransactionMaybe: null
  };
  const rows = [
    {
      importTransactionPos: position,
      importTransactionTemplate: { templatePurpose: 'Notification template', validSince: '2020-01-01' },
      fullPath: true
    }
  ];
  await page.route(`**/api/importtransactionhead/securityaccount/${accountId}`, (route) =>
    route.fulfill({ json: heads })
  );
  await page.route('**/api/importtransactionpos/importtransactionhead/*', (route) => route.fulfill({ json: rows }));
  await openTransactionImport(page);
  await page.locator('select#idTransactionHead').selectOption('910001');
  const table = page.locator('securityaccount-import-transaction-table');
  await expect(table.locator('tbody tr').first()).toContainText('CH0038863350');
  await table.locator('tbody tr').first().locator('a').first().click();
  await expect(
    page.locator('securityaccount-import-extended-info').getByText('Notification template', { exact: true })
  ).toBeVisible();
  await table.locator('tbody p-tablecheckbox').first().click();
  let releaseOptions!: () => void;
  const options = new Promise<void>((resolve) => (releaseOptions = resolve));
  let requested = false;
  await page.route(`**/api/portfolio/account/${accountId}`, async (route) => {
    requested = true;
    await options;
    await route
      .fulfill({ json: { cashaccountList: [{ idSecuritycashAccount: 930001, name: 'Notification cash' }] } })
      .catch(() => {});
  });
  await page.route('**/api/importtransactionpos/setcashaccount', async (route) => {
    expect(route.request().postDataJSON()).toEqual({ idSecuritycashAccount: '930001', idTransactionPosList: [920001] });
    position.cashaccount = { idSecuritycashAccount: 930001, name: 'Notification cash' };
    await route.fulfill({ json: [position] });
  });
  const menu = await contextMenu(page);
  await menu.getByText(/Assign cash account|Bankkonto zuweisen|SET_CASHACCOUNT/i).click();
  const dialog = page.locator('securityaccount-import-set-cashaccount .p-dialog');
  await expect.poll(() => requested).toBe(true);
  releaseOptions();
  await dialog.locator('select#idCashaccount').selectOption('930001');
  await dialog.locator('button[type="submit"]').click();
  await expect(dialog).toBeHidden();
  await expect(table.locator('tbody tr').first()).toContainText('Notification cash');
  await expect(table.locator('tbody p-tablecheckbox input:checked')).toHaveCount(1);
  let releaseHeads!: () => void;
  const headsLoaded = new Promise<void>((resolve) => (releaseHeads = resolve));
  let headsRequested = false;
  await page.route('**/api/gtnetsecurityimphead', async (route) => {
    headsRequested = true;
    await headsLoaded;
    await route
      .fulfill({ json: [{ idGtNetSecurityImpHead: 940001, name: 'Existing notification head' }] })
      .catch(() => {});
  });
  let creates = 0;
  await page.route('**/api/gtnetsecurityimppos/fromimport/**', async (route) => {
    creates++;
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'Notification create rejected' })
    });
  });
  const gtMenu = await contextMenu(page);
  await gtMenu.getByText(/GTNet.*(missing|fehlende)/i).click();
  const gtDialog = page.locator('gtnet-import-head-select-dialog .p-dialog');
  await expect.poll(() => headsRequested).toBe(true);
  await gtDialog.locator('input#headName').fill('Draft before options');
  releaseHeads();
  await expect(gtDialog.locator('select#idGtNetSecurityImpHead option[value="940001"]')).toHaveCount(1);
  await expect(gtDialog.locator('input#headName')).toHaveValue('Draft before options');
  await gtDialog.locator('select#mode').selectOption('existing');
  await expect(gtDialog.locator('input#headName')).toBeHidden();
  await gtDialog.locator('select#idGtNetSecurityImpHead').selectOption('940001');
  const submit = gtDialog.locator('button[type="submit"]');
  await submit.click();
  await expect.poll(() => creates).toBe(1);
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect.poll(() => creates).toBe(2);
  await expect(submit).toBeEnabled();
  await gtDialog.locator('button.p-dialog-close-button').click();
  await expect(page.locator('gtnet-import-head-select-dialog')).toHaveCount(0);

  let releaseOld!: () => void;
  const old = new Promise<void>((resolve) => (releaseOld = resolve));
  let oldRequested = false;
  await page.route('**/api/importtransactionpos/importtransactionhead/*', async (route) => {
    if (route.request().url().endsWith('/910002')) {
      oldRequested = true;
      await old;
      await route
        .fulfill({ json: [{ ...rows[0], importTransactionPos: { ...position, isin: 'OBSOLETE' } }] })
        .catch(() => {});
    } else await route.fulfill({ json: rows });
  });
  await page.locator('select#idTransactionHead').selectOption('910002');
  await expect.poll(() => oldRequested).toBe(true);
  await page.locator('select#idTransactionHead').selectOption('910001');
  await expect(table.locator('tbody p-tablecheckbox input:checked')).toHaveCount(0);
  await expect(table.locator('tbody tr').first()).toContainText('CH0038863350');
  releaseOld();
  await expect(table.getByText('OBSOLETE', { exact: true })).toHaveCount(0);
  await page.locator('select#idTransactionHead').selectOption('');
  await expect(table.locator('tbody p-tablecheckbox')).toHaveCount(0);
});
