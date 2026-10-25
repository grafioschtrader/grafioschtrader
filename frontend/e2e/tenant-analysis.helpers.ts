import { Page } from '@playwright/test';
import type { FeeModelComparisonDetail, FeeModelComparisonResponse } from '../src/app/entities/fee.model.comparison';
import type { FxObservationGroup } from '../src/app/entities/fx.observation';

/** UI-only report data: 020 runs before portfolio creation, and must not depend on real trades or FX quotes. */
function feeDetail(
  name: string,
  transactionDate: string,
  transactionType: string,
  actualCost: number
): FeeModelComparisonDetail {
  return {
    transactionDate,
    transactionType,
    securityName: name,
    categoryType: 'EQUITIES',
    specInvestInstrument: 'DIRECT_INVESTMENT',
    mic: 'XSWX',
    currency: 'CHF',
    quotation: 100,
    units: 2,
    tradeValue: 200,
    actualCost,
    estimatedCost: actualCost,
    relativeError: 0,
    matchedRuleName: 'UI comparison rule',
    error: null
  };
}

function fxGroup(
  currencyPair: string,
  kind: FxObservationGroup['kind'],
  count: number,
  mean: number
): FxObservationGroup {
  return {
    idCurrencypair: 1,
    currencyPair,
    kind,
    periodFrom: '2026-01-01',
    periodTo: '2026-01-31',
    count,
    skippedNoClose: 0,
    skippedNoRate: 0,
    skippedOutlier: 0,
    mean,
    median: mean,
    stdDev: 0,
    volumeWeightedMean: mean,
    modelledCount: count,
    modelledMean: mean,
    uncoveredCount: 0,
    outcomes: { MATCHED: count },
    error: null
  };
}

/** Intercepts the report inputs only. Authentication and translation still use the verified E2E backend. */
export async function mockTenantAnalysis(page: Page): Promise<void> {
  await page.route('**/api/portfolio/tenant', (route) =>
    route.fulfill({
      json: [
        {
          idPortfolio: 71000,
          name: 'UI analysis',
          currency: 'CHF',
          cashaccountList: [],
          securityaccountList: [
            { idSecuritycashAccount: 71001, name: 'First account' },
            { idSecuritycashAccount: 71002, name: 'Second account' }
          ]
        }
      ]
    })
  );
  await page.route('**/api/securityaccount/*/feemodelcomparison?*', (route) => {
    const url = new URL(route.request().url());
    const details = url.pathname.includes('/71001/')
      ? [feeDetail('Later sell', '2026-01-03', 'REDUCE', 2), feeDetail('Earlier buy', '2026-01-02', 'ACCUMULATE', 10)]
      : [feeDetail('Replacement income', '2026-01-04', 'DIVIDEND', 3)];
    if (url.searchParams.get('excludeZeroCost') === 'false') {
      details.push(feeDetail('Zero-cost buy', '2026-01-05', 'ACCUMULATE', 0));
    }
    const response: FeeModelComparisonResponse = {
      planName: 'UI comparison plan',
      totalTransactions: details.length,
      skippedCount: 0,
      errorCount: 0,
      comparedCount: details.length,
      meanActualCost: 0,
      meanEstimatedCost: 0,
      meanAbsoluteError: 0,
      meanRelativeError: 0,
      rmse: 0,
      details
    };
    return route.fulfill({ json: response });
  });
  await page.route('**/api/securityaccount/*/fxobservations', (route) =>
    route.fulfill({
      json: {
        groups: route.request().url().includes('/71001/')
          ? [fxGroup('USD/CHF', 'TRADE', 3, 0.25), fxGroup('EUR/CHF', 'INCOME', 8, 0.75)]
          : [fxGroup('GBP/CHF', 'TRADE', 12, 0.5)]
      }
    })
  );
  await page.route('**/api/tenant/fxobservations', (route) =>
    route.fulfill({
      json: { groups: [fxGroup('CAD/CHF', 'TRANSFER', 6, 0.1)] }
    })
  );
}
