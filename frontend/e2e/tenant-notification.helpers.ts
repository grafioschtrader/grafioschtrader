import { Page } from '@playwright/test';

/** Holds one controlled response until released; never writes to the shared database. */
export async function holdTenantResponse(page: Page, url: string, body: unknown) {
  let release!: () => void;
  let arrive!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const arrived = new Promise<void>((resolve) => (arrive = resolve));
  await page.route(
    url,
    async (route) => {
      arrive();
      await gate;
      await route.fulfill({ json: body });
    },
    { times: 1 }
  );
  return { arrived, release };
}

export async function openTenantTab(page: Page, name: RegExp) {
  await page.goto('/mainview/tenantTabMenu');
  await page.getByRole('tab', { name }).click();
}

export function dividendReport(year = 2025) {
  return {
    mainCurrency: 'CHF',
    tenantCountry: 'DE',
    numberOfSecurityAccounts: 1,
    numberOfCashAccounts: 1,
    portfolioList: [
      {
        idPortfolio: 71000,
        name: 'Notification portfolio',
        securityaccountList: [{ idSecuritycashAccount: 71001, name: 'Notification securities' }],
        cashaccountList: [{ idSecuritycashAccount: 71002, name: 'Notification cash', currency: 'CHF' }]
      }
    ],
    availableTaxYears: [],
    hasMarginData: false,
    securityDividendsYearGroup: [
      {
        year,
        yearInterestMC: 123,
        securityCostGroup: {},
        securityDividendsPositions: [
          {
            security: {
              idSecuritycurrency: 71003,
              name: 'Notification equity',
              currency: 'CHF',
              assetClass: { categoryType: 'EQUITIES', specialInvestmentInstrument: 'DIRECT_INVESTMENT' }
            },
            taxableAmountMC: 321,
            realReceivedDivInterestMC: 300
          }
        ],
        cashAccountPositions: [
          {
            cashaccount: { idSecuritycashAccount: 71002, name: 'Notification cash', currency: 'CHF' },
            cashBalanceMC: 456
          }
        ]
      }
    ]
  };
}
