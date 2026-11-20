import { expect, Page, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { selectDynamicFormOptionByText } from './dynamic-form.helpers';
import { watchlistTable } from './watchlist.helpers';
import type { ConnectorAlternative, SecurityLookupCandidate } from '../src/app/securitylookup/model/security-lookup';

const WATCHLIST = 'Lookup OnPush e2e';
test.use({ viewport: { width: 1600, height: 1200 }, actionTimeout: 15_000 });

/** Only this spec's empty watchlist is persisted; provider responses and the security form stay local. */
async function cleanup(page: Page, headers: Record<string, string>): Promise<void> {
  const response = await page.request.get('/api/watchlist/tenant', { headers });
  expect(response.ok()).toBeTruthy();
  for (const list of await response.json()) {
    if (list.name === WATCHLIST) {
      const deleted = await page.request.delete(`/api/watchlist/${list.idWatchlist}`, { headers });
      expect(deleted.ok(), await deleted.text()).toBeTruthy();
    }
  }
}

function connector(name: string, status = 'OK'): ConnectorAlternative {
  return {
    idConnector: name,
    readableName: name,
    urlExtension: name,
    probeStatus: status,
    sampleDate: '2026-01-15',
    sampleValue: 25,
    sampleCount: 3,
    distributionFrequency: 'ANNUAL',
    probeDetail: status === 'OK' ? '' : 'Provider unavailable'
  };
}

function candidate(name: string, distributesNothing: boolean): SecurityLookupCandidate {
  return {
    providerId: 'fixture',
    providerInstrumentId: name,
    providerInstrumentType: 'stock',
    isin: 'ES0173516115',
    currency: 'EUR',
    mic: 'XMAD',
    name,
    tickerSymbol: name,
    issuerCountry: 'ES',
    activeFromDate: '2000-01-01',
    activeToDate: null,
    distributionFrequency: null,
    denomination: null,
    productLink: null,
    stockexchangeLink: null,
    categoryType: 'EQUITIES',
    specialInvestmentInstrument: 'DIRECT_INVESTMENT',
    matchedAssetClassId: null,
    instrumentTypeMismatch: false,
    baseDataStatus: 'OK',
    baseDataDetail: null,
    providerAttributes: { source: 'Recorded lookup fixture' },
    historyConnectors: [connector(`${name} history`), connector(`${name} failed`, 'TIMEOUT')],
    intraConnectors: [],
    dividendConnectors: [connector(`${name} dividend`)],
    splitConnectors: [],
    distributesNothing,
    providerIds: ['fixture'],
    fieldSources: {},
    hints: [{ severity: 'WARNING', field: 'activeFromDate', messageKey: `${name} listing hint`, args: {} }]
  };
}

test('lookup child tables refresh hints, selection and connectors when the listing changes', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token).toBeTruthy();
  const headers = { 'x-auth-token': token! };
  await cleanup(page, headers);
  const created = await page.request.post('/api/watchlist', { headers, data: { name: WATCHLIST } });
  expect(created.ok(), await created.text()).toBeTruthy();
  try {
    await page.route('**/api/securitylookup/lookup?*', (route) =>
      route.fulfill({
        json: {
          candidates: [candidate('Alpha', false), candidate('Beta', true)],
          providerStatus: [],
          dividendConnectorApplicable: true,
          splitConnectorApplicable: true,
          hints: [{ severity: 'INFO', field: null, messageKey: 'Lookup query hint', args: {} }]
        }
      })
    );
    await page.reload();
    await page.getByRole('treeitem', { name: WATCHLIST, exact: true }).click();
    const table = watchlistTable(page);
    await table.click();
    await table.click({ button: 'right' });
    await page
      .locator('[role="menu"]:visible')
      .getByText(/^(Create\s*and\s*add\s*security|Hinzuf.*neues\s*Wertpapier)\b/i)
      .first()
      .click();
    const form = page.locator('security-edit .p-dialog').first();
    await expect(form.locator('select#currency option')).not.toHaveCount(0);
    await form.locator('#name').fill('Lookup draft');
    await selectDynamicFormOptionByText(
      page,
      form,
      'assetClass',
      /\/ Aktien Spanien \/ (Direct investment|Direktanlage)\s*$/
    );
    await selectDynamicFormOptionByText(page, form, 'stockexchange', /Madrid/);
    await form.locator('#isin').fill('ES0173516115');
    await form.locator('select#currency').selectOption('EUR');
    await form.getByRole('button', { name: /^(Search|Suchen)$/ }).click();
    const lookup = page.locator('security-lookup-dialog');
    await expect(lookup).toContainText('Lookup query hint');
    const listings = lookup.locator('security-lookup-base-data-table');
    const alpha = listings
      .locator('tbody tr')
      .filter({ has: page.getByRole('cell', { name: 'Alpha', exact: true }) })
      .first();
    const beta = listings
      .locator('tbody tr')
      .filter({ has: page.getByRole('cell', { name: 'Beta', exact: true }) })
      .first();
    await alpha.getByRole('cell', { name: 'Alpha', exact: true }).first().click();
    await expect(lookup).toContainText('Alpha listing hint');
    const history = lookup.locator('security-lookup-connector-table').first();
    await expect(history).toContainText('Alpha history');
    const failed = history.locator('tbody tr').filter({ hasText: 'Alpha failed' });
    await expect(failed).toHaveClass(/lookup-row-unusable/);
    await failed.click();
    await expect(failed).not.toHaveClass(/p-datatable-row-selected/);

    await beta.getByRole('cell', { name: 'Beta', exact: true }).first().click();
    await expect(lookup).toContainText('Beta listing hint');
    await expect(lookup).not.toContainText('Alpha listing hint');
    await expect(history).toContainText('Beta history');
    await expect(history).not.toContainText('Alpha history');
    await expect(
      lookup.locator('security-lookup-connector-table').nth(2).locator('.lookup-table-disabled')
    ).toBeVisible();

    // The name source is independent of the selected listing and must also refresh under OnPush.
    await beta.locator('p-checkbox').click();
    await lookup.getByRole('button', { name: /(Apply|Übernehmen)$/ }).click();
    await expect(lookup).toHaveCount(0);
    await expect(form.locator('#name')).toHaveValue('Beta');
    await expect(form.locator('#tickerSymbol')).toHaveValue('Beta');
    // Discard the draft: no security, connector or price download is persisted.
    await page.keyboard.press('Escape');
  } finally {
    await cleanup(page, headers);
  }
});
