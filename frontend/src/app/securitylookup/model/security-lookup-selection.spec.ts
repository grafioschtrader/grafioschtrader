import { describe, expect, it } from 'vitest';

import { ConnectorAlternative } from './security-lookup';
import { chooseDistributionFrequency, isUsableAlternative, preselectAlternative } from './security-lookup-selection';

function alternative(idConnector: string, probeStatus: string): ConnectorAlternative {
  return { idConnector, probeStatus } as ConnectorAlternative;
}

describe('security lookup connector selection', () => {
  it('accepts only a successful probe for price feeds', () => {
    expect(isUsableAlternative('history', alternative('a', 'OK'))).toBe(true);
    expect(isUsableAlternative('history', alternative('a', 'NO_DATA'))).toBe(false);
    expect(isUsableAlternative('intra', alternative('a', 'REQUEST_FAILED'))).toBe(false);
  });

  it('accepts no data for dividends and splits', () => {
    expect(isUsableAlternative('dividend', alternative('a', 'NO_DATA'))).toBe(true);
    expect(isUsableAlternative('split', alternative('a', 'NO_DATA'))).toBe(true);
    expect(isUsableAlternative('split', alternative('a', 'CHECK_FAILED'))).toBe(false);
  });

  it('preselects the only usable connector even when others failed', () => {
    const xetra = alternative('xetra', 'OK');
    expect(preselectAlternative('history', [alternative('yahoo', 'NO_DATA'), xetra, alternative('x', 'TIMEOUT')])).toBe(
      xetra
    );
  });

  it('takes the frequency of the chosen dividend connector, else the one the others agree on', () => {
    const yahoo = { ...alternative('yahoo', 'OK'), distributionFrequency: null } as ConnectorAlternative;
    const divvy = { ...alternative('divvydiary', 'OK'), distributionFrequency: 'DF_YEAR' } as ConnectorAlternative;
    const eodhd = { ...alternative('eodhd', 'OK'), distributionFrequency: 'DF_QUARTERLY' } as ConnectorAlternative;
    const failed = {
      ...alternative('six', 'REQUEST_FAILED'),
      distributionFrequency: 'DF_MONTHLY'
    } as ConnectorAlternative;
    expect(chooseDistributionFrequency(eodhd, [yahoo, divvy, eodhd])).toBe('DF_QUARTERLY');
    expect(chooseDistributionFrequency(yahoo, [yahoo, divvy, failed])).toBe('DF_YEAR');
    expect(chooseDistributionFrequency(null, [yahoo, divvy])).toBe('DF_YEAR');
    expect(chooseDistributionFrequency(yahoo, [yahoo, divvy, eodhd])).toBeUndefined();
    expect(chooseDistributionFrequency(yahoo, [yahoo])).toBeUndefined();
  });

  it('leaves the choice to the user when several or none are usable', () => {
    expect(preselectAlternative('history', [alternative('a', 'OK'), alternative('b', 'OK')])).toBeNull();
    expect(preselectAlternative('history', [alternative('a', 'REGEX_FAILED')])).toBeNull();
    expect(preselectAlternative('split', [])).toBeNull();
  });
});
