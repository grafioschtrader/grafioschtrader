import { expect, Locator, Page, test } from '@playwright/test';
import { getUser, loginAsFixtureUser } from './helpers';
import { toShortDate } from './portfolio.helpers';
import { holdTenantResponse as holdResponse } from './tenant-notification.helpers';
import { scenario } from './cash-security-transaction.helpers';
import { watchlistTable } from './watchlist.helpers';

test.use({ viewport: { width: 1800, height: 1100 }, actionTimeout: 15_000 });

const cashaccount = { idSecuritycashAccount: 71002, name: 'Notification cash', currency: 'CHF' };
const transaction = {
  idTransaction: 71010,
  transactionType: 'DEPOSIT',
  transactionTime: '2020-01-02T12:00:00',
  cashaccount,
  cashaccountAmount: 123.45,
  transactionCost: 0,
  taxCost: 0,
  note: 'Notification transaction',
  connectedIdTransaction: null,
  security: null
};

async function contextAction(page: Page, row: Locator, action: RegExp) {
  for (let attempt = 0; attempt < 3; attempt++) {
    await row.click();
    await row.click({ button: 'right' });
    const item = page.locator('[role=menu]:visible').getByText(action);
    if (await item.isVisible()) {
      await item.click();
      return;
    }
    await page.keyboard.press('Escape');
  }
  throw new Error(`Transaction menu ${action} unavailable`);
}

async function tenantTable(page: Page) {
  await page.route('**/api/transaction', (route) => route.fulfill({ json: [transaction] }));
  await page.route('**/api/currencypair/tenant', (route) => route.fulfill({ json: [] }));
  await openTransactions(page);
  return page.locator('configurable-table tbody tr').filter({ hasText: cashaccount.name });
}

async function openTransactions(page: Page) {
  await page.goto('/mainview/tenantTabMenu');
  await page.getByRole('tab', { name: /^(Transactions|Transaktionen)$/ }).click();
}

test('tenant transactions arrive late and CSV export recovers from a failed download', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  await page.route('**/api/currencypair/tenant', (route) => route.fulfill({ json: [] }));
  const rows = await holdResponse(page, '**/api/transaction', [transaction]);
  try {
    await openTransactions(page);
    await rows.arrived;
    const table = page.locator('configurable-table');
    await expect(table.getByRole('columnheader', { name: /^(Date|Datum)$/ })).toBeVisible();
    await expect(table.locator('tbody')).not.toContainText(cashaccount.name);
    rows.release();
    const row = table.locator('tbody tr').filter({ hasText: cashaccount.name });
    await expect(row).toBeVisible();
    await contextAction(page, row, /^(Export transactions as CSV|Transaktionen als CSV exportieren)/);
    const dialog = page.locator('.p-dialog:visible');
    const download = dialog.locator('button[type=submit]');
    await expect(download).toBeEnabled();
    let release!: () => void;
    const gate = new Promise<void>((resolve) => (release = resolve));
    let requested = false;
    await page.route(
      '**/api/transaction/exportcsv',
      async (route) => {
        requested = true;
        expect(route.request().postDataJSON()).toEqual({ idPortfolio: null, dateFrom: null, dateTo: null });
        await gate;
        await route.fulfill({ status: 500, body: 'notification export failure' });
      },
      { times: 1 }
    );
    try {
      await download.click();
      await expect.poll(() => requested).toBe(true);
      await expect(download).toBeDisabled();
      release();
      await expect(download).toBeEnabled();
    } finally {
      release();
    }
    await page.route('**/api/transaction/exportcsv', (route) =>
      route.fulfill({
        contentType: 'text/csv',
        headers: { 'Content-Disposition': 'attachment; filename="notification.csv"' },
        body: 'date|amount\n2020-01-02|123.45'
      })
    );
    const downloaded = page.waitForEvent('download');
    await download.click();
    expect((await downloaded).suggestedFilename()).toBe('notification.csv');
    await expect(dialog).toHaveCount(0);
  } finally {
    rows.release();
  }
});

test('a closed-period warning follows delayed portfolios when the cash dialog is reopened', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  const row = await tenantTable(page);
  for (let opening = 0; opening < 2; opening++) {
    const portfolios = await holdResponse(page, '**/api/portfolio/tenant', [
      {
        idPortfolio: 71001,
        name: 'Notification portfolio',
        currency: 'CHF',
        closedUntil: '2021-12-31',
        cashaccountList: [cashaccount],
        securityaccountList: []
      }
    ]);
    try {
      await contextAction(page, row, /^(Edit Transaction|Bearbeiten Transaktion)$/i);
      await portfolios.arrived;
      const dialog = page.locator('.p-dialog:visible');
      await expect(dialog.locator('select#transactionType')).toBeVisible();
      await expect(dialog.locator('.alert-warning')).toHaveCount(0);
      portfolios.release();
      await expect(dialog.locator('select#idCashaccount option:checked')).toContainText(cashaccount.name);
      await expect(dialog.locator('.alert-warning')).toContainText(
        toShortDate('2021-12-31', getUser('alledit').localeStr)
      );
      await expect(dialog.locator('button[type=submit]')).toBeHidden();
      await dialog.locator('.p-dialog-close-button').click();
      await expect(dialog).toHaveCount(0);
    } finally {
      portfolios.release();
    }
  }
});

test('a connected transfer opens after its delayed counterpart and keeps its accounts and amounts', async ({
  page
}) => {
  await loginAsFixtureUser(page, 'alledit');
  const row = await tenantTable(page);
  const credit = { ...cashaccount, idSecuritycashAccount: 71003, name: 'Notification credit' };
  const withdrawal = {
    ...transaction,
    transactionType: 'WITHDRAWAL',
    cashaccountAmount: -123.45,
    connectedIdTransaction: 71011
  };
  const deposit = {
    ...transaction,
    idTransaction: 71011,
    cashaccount: credit,
    connectedIdTransaction: transaction.idTransaction
  };
  await page.route('**/api/transaction', (route) => route.fulfill({ json: [withdrawal] }));
  await page.route('**/api/transaction/71011', (route) => route.fulfill({ json: deposit }));
  await page.route('**/api/portfolio/tenant', (route) =>
    route.fulfill({
      json: [
        {
          idPortfolio: 71001,
          name: 'Notification portfolio',
          currency: 'CHF',
          cashaccountList: [cashaccount, credit],
          securityaccountList: []
        }
      ]
    })
  );
  // Re-enter the tab so the table owns the connected withdrawal rather than the initial deposit.
  await page.getByRole('tab', { name: /^(Portfolios)$/ }).click();
  await page.getByRole('tab', { name: /^(Transactions|Transaktionen)$/ }).click();
  const counterpart = await holdResponse(page, '**/api/transaction/71011', deposit);
  try {
    await contextAction(page, row, /^(Edit Transaction|Bearbeiten Transaktion)$/i);
    await counterpart.arrived;
    await expect(page.locator('transaction-cashaccount-editdouble')).toHaveCount(0);
    counterpart.release();
    const dialog = page.locator('.p-dialog:visible');
    await expect(dialog.locator('select#idDebitCashaccount')).toHaveValue(String(cashaccount.idSecuritycashAccount));
    await expect(dialog.locator('select#idCreditCashaccount')).toHaveValue(String(credit.idSecuritycashAccount));
    await expect(dialog.locator('#currencyExRate input')).toBeDisabled();
    await expect(dialog.locator('#creditAmount input')).toHaveValue(/123[.,]45/);
    await expect(dialog.locator('#debitAmount input')).toHaveValue(/123[.,]45/);
    await dialog.locator('.p-dialog-close-button').click();
    await expect(dialog).toHaveCount(0);
  } finally {
    counterpart.release();
  }
});

test('receipt rows and download state follow late responses for cash and margin instruments', async ({ page }) => {
  await loginAsFixtureUser(page, scenario.loginNickname);
  await page.getByRole('treeitem', { name: scenario.watchlistName, exact: true }).first().click();
  const row = watchlistTable(page)
    .locator('p-table tbody > tr')
    .filter({
      has: page.getByRole('cell', { name: scenario.securityName, exact: true })
    });
  for (const marginBased of [false, true]) {
    const trades = [
      { ...transaction, transactionType: 'ACCUMULATE', units: 10, quotation: 12 },
      {
        ...transaction,
        idTransaction: 71011,
        transactionType: 'REDUCE',
        units: 4,
        quotation: 13,
        connectedIdTransaction: marginBased ? 71010 : null
      }
    ];
    const summary = await holdResponse(page, '**/api/security/tenantsecurity/*?*', {
      transactionPositionList: trades.map((transaction) => ({ transaction })),
      securityPositionSummary: {
        security: { assetClass: { specialInvestmentInstrument: marginBased ? 'CFD' : 'DIRECT_INVESTMENT' } }
      }
    });
    try {
      await contextAction(page, row, /^(Transaction receipts|Transaktionsbelege)/);
      await summary.arrived;
      const dialog = page.locator('.p-dialog:visible');
      const download = dialog.getByRole('button', { name: /(Download|Herunterladen)$/ });
      await expect(download).toBeDisabled();
      summary.release();
      const table = dialog.locator('transaction-receipt-table');
      await expect(table.locator('tbody tr')).toHaveCount(2);
      await expect(table.locator('tbody')).toContainText(cashaccount.name);
      await table
        .locator('tbody tr')
        .first()
        .locator(marginBased ? 'p-treetablecheckbox' : 'p-tablecheckbox')
        .click();
      await expect(download).toBeEnabled();
      let fail!: () => void;
      const gate = new Promise<void>((resolve) => (fail = resolve));
      let requested = false;
      await page.route(
        '**/api/transaction/receipts',
        async (route) => {
          requested = true;
          const { idTransactions } = route.request().postDataJSON();
          expect(idTransactions).toEqual(marginBased ? [71010, 71011] : [71010]);
          await gate;
          await route.fulfill({ status: 500, body: 'notification receipt failure' });
        },
        { times: 1 }
      );
      try {
        await download.click();
        await expect.poll(() => requested).toBe(true);
        await expect(download).toBeDisabled();
        fail();
        await expect(download).toBeEnabled();
      } finally {
        fail();
      }
      await page.route(
        '**/api/transaction/receipts',
        (route) =>
          route.fulfill({
            contentType: 'application/pdf',
            headers: { 'Content-Disposition': 'attachment; filename="notification.pdf"' },
            body: '%PDF-1.4\n%%EOF'
          }),
        { times: 1 }
      );
      const downloaded = page.waitForEvent('download');
      await download.click();
      expect((await downloaded).suggestedFilename()).toBe('notification.pdf');
      await expect(dialog).toHaveCount(0);
    } finally {
      summary.release();
    }
  }
});
