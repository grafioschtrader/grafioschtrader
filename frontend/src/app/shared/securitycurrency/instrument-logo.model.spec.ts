import { describe, expect, it } from 'vitest';
import { SpecialInvestmentInstruments } from '../types/special.investment.instruments';
import {
  computeLogoDraftPrefill,
  InstrumentLogoRefDto,
  isIssuerScopeInstrument,
  isSvgFile,
  normalizeIsinInput,
  toSpecialInvestmentInstrumentName,
  utf8ByteLength
} from './instrument-logo.model';

describe('instrument-logo.model helpers', () => {
  const sampleCompanyLogo: InstrumentLogoRefDto = {
    idInstrumentLogo: 10,
    name: 'Apple Inc.',
    isin: 'US0378331005',
    logoScope: 'COMPANY',
    assetPath: 'US/US0378331005.svg',
    contentSha256: 'abc',
    explicit: false
  };

  const sampleIssuerLogo: InstrumentLogoRefDto = {
    idInstrumentLogo: 20,
    name: 'iShares',
    isin: null,
    logoScope: 'ISSUER',
    assetPath: 'issuers/ishares.svg',
    contentSha256: 'def',
    explicit: false
  };

  it('recognizes issuer-scope investment instruments', () => {
    expect(isIssuerScopeInstrument('ETF')).toBe(true);
    expect(isIssuerScopeInstrument('MUTUAL_FUND')).toBe(true);
    expect(isIssuerScopeInstrument('ISSUER_RISK_PRODUCT')).toBe(true);
    expect(isIssuerScopeInstrument('DIRECT_INVESTMENT')).toBe(false);
    expect(isIssuerScopeInstrument('CFD')).toBe(false);
    expect(isIssuerScopeInstrument(null)).toBe(false);
    expect(toSpecialInvestmentInstrumentName(SpecialInvestmentInstruments.ETF)).toBe('ETF');
    expect(toSpecialInvestmentInstrumentName('MUTUAL_FUND')).toBe('MUTUAL_FUND');
    expect(toSpecialInvestmentInstrumentName(null)).toBeNull();
  });

  it('normalizes ISIN inputs and measures UTF-8 byte lengths', () => {
    expect(normalizeIsinInput(' us0378331005 ')).toBe('US0378331005');
    expect(normalizeIsinInput('   ')).toBeNull();
    expect(normalizeIsinInput(undefined)).toBeNull();
    expect(utf8ByteLength('äöü')).toBe(6);
    expect(isSvgFile('logo.SVG', '')).toBe(true);
    expect(isSvgFile('logo', 'image/svg+xml')).toBe(true);
    expect(isSvgFile('logo.png', 'image/png')).toBe(false);
  });

  it('prefills ISSUER scope with empty ISIN and resolved issuer name for ETFs and funds', () => {
    const prefill = computeLogoDraftPrefill({
      securityName: 'iShares Core MSCI World UCITS ETF',
      searchName: 'iShares',
      securityIsin: 'IE00B4L5Y983',
      searchIsin: 'IE00B4L5Y983',
      specialInvestmentInstrument: 'ETF',
      isTenantPrivate: false,
      resolvedLogo: sampleIssuerLogo,
      catalogResults: [sampleIssuerLogo]
    });

    expect(prefill).toEqual({
      name: 'iShares',
      isin: null,
      logoScope: 'ISSUER',
      isinInUseHint: false
    });
  });

  it('prefills COMPANY scope with ISIN when free in catalog', () => {
    const prefill = computeLogoDraftPrefill({
      securityName: 'Custom AG',
      searchName: 'Custom',
      securityIsin: 'ch0000000001',
      searchIsin: null,
      specialInvestmentInstrument: 'DIRECT_INVESTMENT',
      isTenantPrivate: false,
      resolvedLogo: null,
      catalogResults: [sampleCompanyLogo]
    });

    expect(prefill).toEqual({
      name: 'Custom AG',
      isin: 'CH0000000001',
      logoScope: 'COMPANY',
      isinInUseHint: false
    });
  });

  it('clears ISIN and sets hint when ISIN is already occupied in catalog', () => {
    const prefill = computeLogoDraftPrefill({
      securityName: 'Apple Inc.',
      searchName: 'Apple',
      securityIsin: 'US0378331005',
      searchIsin: 'US0378331005',
      specialInvestmentInstrument: 'DIRECT_INVESTMENT',
      isTenantPrivate: false,
      resolvedLogo: sampleCompanyLogo,
      catalogResults: [sampleCompanyLogo]
    });

    expect(prefill).toEqual({
      name: 'Apple Inc.',
      isin: null,
      logoScope: 'COMPANY',
      isinInUseHint: true
    });
  });

  it('omits ISIN without conflict hint for tenant-private securities', () => {
    const prefill = computeLogoDraftPrefill({
      securityName: 'My Private Bond',
      searchName: '',
      securityIsin: 'US0378331005',
      searchIsin: 'US0378331005',
      specialInvestmentInstrument: 'DIRECT_INVESTMENT',
      isTenantPrivate: true,
      resolvedLogo: null,
      catalogResults: []
    });

    expect(prefill).toEqual({
      name: 'My Private Bond',
      isin: null,
      logoScope: 'COMPANY',
      isinInUseHint: false
    });
  });
});
