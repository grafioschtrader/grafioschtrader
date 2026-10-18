import { ConnectorAlternative, SecurityLookupCandidate } from './security-lookup';

/** The four feeds of a security, each with its own connector table in the lookup dialog. */
export type LookupFeed = 'history' | 'intra' | 'dividend' | 'split';

/**
 * Tells whether a connector can be taken over for a feed: its probe succeeded. "No data" counts as success only for
 * dividends and splits, where an instrument without distributions or splits is the normal case; a price feed without
 * data is useless.
 *
 * @param feed - The feed of the connector table
 * @param alternative - The connector with the outcome of its probe
 * @returns True when the user may choose the connector
 */
export function isUsableAlternative(feed: LookupFeed, alternative: ConnectorAlternative): boolean {
  if (alternative?.probeStatus === 'OK') {
    return true;
  }
  return alternative?.probeStatus === 'NO_DATA' && (feed === 'dividend' || feed === 'split');
}

/**
 * Chooses the connector to preselect for a feed: the only usable one, no matter how many others failed their probe.
 *
 * @param feed - The feed of the connector table
 * @param alternatives - All connectors offered for the feed
 * @returns The connector to preselect, or null when none or several are usable
 */
export function preselectAlternative(feed: LookupFeed, alternatives: ConnectorAlternative[]): ConnectorAlternative {
  const usable = (alternatives ?? []).filter((a) => isUsableAlternative(feed, a));
  return usable.length === 1 ? usable[0] : null;
}

/**
 * Chooses the distribution frequency taken over with the dividend connector. The frequency of the chosen connector
 * comes first. When it has none - a first dividend in the current year tells Yahoo nothing about the rhythm - the
 * frequency is taken that all other usable dividend connectors agree on, e.g. DivvyDiary's own statement "annually".
 * Contradicting connectors leave the frequency open.
 *
 * @param chosen - The dividend connector the user chose, may be null
 * @param alternatives - All dividend connectors offered for the listing
 * @returns The frequency, or undefined when it stays open
 */
export function chooseDistributionFrequency(
  chosen: ConnectorAlternative,
  alternatives: ConnectorAlternative[]
): string | undefined {
  if (chosen?.distributionFrequency) {
    return chosen.distributionFrequency;
  }
  const frequencies = new Set(
    (alternatives ?? [])
      .filter((a) => isUsableAlternative('dividend', a) && a.distributionFrequency)
      .map((a) => a.distributionFrequency)
  );
  return frequencies.size === 1 ? frequencies.values().next().value : undefined;
}

/**
 * Tells whether a name is written entirely in capitals, as exchanges and some providers deliver it ("FIVERR INTL").
 *
 * @param name - The name, may be null
 * @returns True when the name has letters and none of them is lower case
 */
export function isAllUpperCase(name: string): boolean {
  return !!name && name === name.toUpperCase() && name !== name.toLowerCase();
}

/**
 * Chooses the listing whose name is taken over by default. Every listing names the same instrument, but the providers
 * write it differently; a name that is not written entirely in capitals ("Fiverr International") reads better than
 * the exchange spelling ("FIVERR INTL").
 *
 * @param candidates - The listings found by the lookup
 * @returns The first listing whose name is not written entirely in capitals, otherwise the first listing; null when
 *   there is none
 */
export function preselectNameCandidate(candidates: SecurityLookupCandidate[]): SecurityLookupCandidate {
  const list = candidates ?? [];
  return list.find((c) => c.name && !isAllUpperCase(c.name)) ?? list[0] ?? null;
}
