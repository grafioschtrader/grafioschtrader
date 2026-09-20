import { describe, expect, it } from 'vitest';
import { Assetclass } from '../../entities/assetclass';
import { Security } from '../../entities/security';
import { AssetclassType } from '../../shared/types/assetclass.type';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';
import { defaultCouponDayCount, extractCouponRateFromSecurityName } from './security-bond-terms.component';
import { isManualIssuerCountryChange } from './security-edit.component';

describe('extractCouponRateFromSecurityName', () => {
  it.each([
    ['1.20 AMCOM 26-32 /Z', 1.2],
    [' 0,375 Stadler Rail AG 19-26', 0.375],
    ['0% Sonova Holding AG 19-29', 0]
  ])('extracts the numeric name prefix from %s', (name, expected) => {
    expect(extractCouponRateFromSecurityName(name)).toBe(expected);
  });

  it('does not infer a coupon from a number later in the name', () => {
    expect(extractCouponRateFromSecurityName('Royal Bank 0.20 21-31')).toBeNull();
  });
});

describe('defaultCouponDayCount', () => {
  const defaults = { fallback: 'ACT_ACT_ICMA', byCurrency: { CHF: 'THIRTY_E_360' } };

  it.each([
    ['CHF', 'THIRTY_E_360'],
    [' chf ', 'THIRTY_E_360'],
    ['EUR', 'ACT_ACT_ICMA'],
    ['USD', 'ACT_ACT_ICMA']
  ])('proposes the convention served for %s', (currency, expected) => {
    expect(defaultCouponDayCount(defaults, currency)).toBe(expected);
  });

  it('proposes nothing before the currency or the defaults are known', () => {
    expect(defaultCouponDayCount(defaults, '')).toBeNull();
    expect(defaultCouponDayCount(defaults, null)).toBeNull();
    expect(defaultCouponDayCount(undefined, 'CHF')).toBeNull();
  });
});

describe('Security simulation field eligibility', () => {
  it('allows bond terms only for direct fixed-income and convertible-bond investments', () => {
    expect(Security.isBondDirectInvestment(assetclass(AssetclassType.FIXED_INCOME))).toBe(true);
    expect(Security.isBondDirectInvestment(assetclass(AssetclassType.CONVERTIBLE_BOND))).toBe(true);
    expect(Security.isBondDirectInvestment(assetclass(AssetclassType.EQUITIES))).toBe(false);
    expect(
      Security.isBondDirectInvestment(assetclass(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.ETF))
    ).toBe(false);
  });

  it('excludes issuer country only for instruments without an issuer', () => {
    expect(Security.canHaveIssuerCountry(assetclass(AssetclassType.EQUITIES))).toBe(true);
    expect(Security.canHaveIssuerCountry(assetclass(AssetclassType.EQUITIES, SpecialInvestmentInstruments.CFD))).toBe(
      false
    );
    expect(
      Security.canHaveIssuerCountry(assetclass(AssetclassType.CURRENCY_PAIR, SpecialInvestmentInstruments.FOREX))
    ).toBe(false);
    expect(
      Security.canHaveIssuerCountry(
        assetclass(AssetclassType.EQUITIES, SpecialInvestmentInstruments.NON_INVESTABLE_INDICES)
      )
    ).toBe(false);
  });
});

describe('Security.issuerCountryFromIsin', () => {
  it.each([
    ['CH', 'CH'],
    ['ch0012005267', 'CH'],
    ['  at0000A2VB47', 'AT']
  ])('extracts the country as soon as the ISIN prefix of %s is available', (isin, expected) => {
    expect(Security.issuerCountryFromIsin(isin)).toBe(expected);
  });

  it.each(['', 'C', '1H0012005267'])('does not infer a country without a two-letter prefix from %s', (isin) => {
    expect(Security.issuerCountryFromIsin(isin)).toBeNull();
  });
});

describe('isManualIssuerCountryChange', () => {
  it('does not treat the pristine control event emitted while showing the field as a manual override', () => {
    expect(isManualIssuerCountryChange(true, false, false, '', null)).toBe(false);
  });

  it('recognizes a country selected by the user on a dirty control', () => {
    expect(isManualIssuerCountryChange(true, false, true, 'DE', 'CH')).toBe(true);
  });

  it('does not treat the automatic prefill itself as a manual override', () => {
    expect(isManualIssuerCountryChange(true, true, true, 'CH', null)).toBe(false);
  });
});

function assetclass(
  categoryType: AssetclassType,
  specialInvestmentInstrument = SpecialInvestmentInstruments.DIRECT_INVESTMENT
): Assetclass {
  const value = new Assetclass();
  value.categoryType = AssetclassType[categoryType];
  value.specialInvestmentInstrument = SpecialInvestmentInstruments[specialInvestmentInstrument];
  return value;
}
