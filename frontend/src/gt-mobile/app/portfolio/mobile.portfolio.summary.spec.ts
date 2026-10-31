import { describe, expect, it } from 'vitest';
import {
  findMobilePortfolioGroup,
  mobileAccountSections,
  MobileAccountGrandSummary,
  mobileGrandSection,
  mobileMissingExchangeRates,
  mobilePortfolioGroupCards
} from './mobile.portfolio.summary';

const summary: MobileAccountGrandSummary = {
  mainCurrency: 'CHF',
  grandValueMC: 200000,
  grandValueSecuritiesMC: 150000,
  grandCashBalanceMC: 50000,
  grandGainLossSecuritiesMC: 12000,
  grandAccountFeesMC: -350,
  grandAccountInterestMC: 0,
  missingExchangeRates: [{ fromCurrency: 'BTC', toCurrency: 'CHF' }],
  accountPositionGroupSummaryList: [
    {
      groupName: 'Hauptdepot',
      groupValueMC: 150000,
      groupValueSecuritiesMC: 120000,
      groupGainLossSecuritiesMC: 9000,
      accountPositionSummaryList: [
        {
          cashaccount: { idSecuritycashAccount: 1, name: 'UBS CHF', currency: 'CHF' },
          cashBalance: 20000,
          cashBalanceMC: 20000,
          valueSecuritiesMC: 100000,
          valueMC: 120000,
          accountFeesMC: 0
        },
        {
          cashaccount: { idSecuritycashAccount: 2, name: 'UBS USD', currency: 'USD' },
          cashBalance: 11000,
          cashBalanceMC: 10000,
          closePrice: 0.9091,
          valueSecuritiesMC: 20000,
          valueMC: 30000
        }
      ]
    },
    { groupName: 'Säule 3a', groupValueMC: 50000, accountPositionSummaryList: [] }
  ]
};

describe('mobilePortfolioGroupCards', () => {
  it('gives each group its share of the total value', () => {
    expect(mobilePortfolioGroupCards(summary).map((c) => [c.groupName, c.sharePercentage])).toEqual([
      ['Hauptdepot', 75],
      ['Säule 3a', 25]
    ]);
  });

  it('gives no share when the total is not positive', () => {
    expect(mobilePortfolioGroupCards({ ...summary, grandValueMC: 0 })[0].sharePercentage).toBeNull();
  });
});

describe('mobileGrandSection', () => {
  it('lists the amounts other than zero, the balance first', () => {
    expect(mobileGrandSection(summary).entries.map((e) => e.labelKey)).toEqual([
      'CASH_BALANCE',
      'SECURITY',
      'GAIN_LOSS_SECURITIES',
      'FEE'
    ]);
  });
});

describe('mobileAccountSections', () => {
  const group = findMobilePortfolioGroup(summary, 'Hauptdepot');

  it('shows an account in the main currency without exchange rate and a foreign one with it', () => {
    if (!group) {
      throw new Error('group missing');
    }
    const [chf, usd] = mobileAccountSections(group, 'CHF');
    expect(chf.title).toBe('UBS CHF');
    expect(chf.entries.map((e) => e.labelKey)).toEqual(['CASH_BALANCE', 'VALUE', 'SECURITY']);
    expect(usd.entries.map((e) => e.labelKey)).toEqual(['CASH_BALANCE', 'CURRENCY_RATE', 'VALUE', 'SECURITY']);
    expect(usd.entries[0].currency).toBe('USD');
  });

  it('finds no group the report no longer contains', () => {
    expect(findMobilePortfolioGroup(summary, 'Altes Depot')).toBeNull();
  });
});

describe('mobileMissingExchangeRates', () => {
  it('names the pairs without a rate', () => {
    expect(mobileMissingExchangeRates(summary)).toBe('BTC/CHF');
    expect(mobileMissingExchangeRates({ ...summary, missingExchangeRates: [] })).toBeNull();
  });
});
