import type { InstrumentLogoRefDto } from '../../shared/securitycurrency/instrument-logo.model';

/**
 * Market map of the stocks of a watchlist. Built by the backend as a flat node list; the client only maps it to a
 * Plotly treemap trace.
 */
export interface WatchlistTreemap {
  /** Main currency of the tenant, the currency of every area. */
  mainCurrency: string;
  /** Number of instruments of the watchlist that are no active direct stocks. */
  notApplicableCount: number;
  /** Nodes with the root first; empty when no stock can be drawn. */
  nodes: WatchlistTreemapNode[];
  /** Stocks that cannot be drawn. */
  excluded: WatchlistTreemapExcluded[];
}

/** Level of a node of the market map. */
export type WatchlistTreemapNodeType = 'ROOT' | 'GROUP' | 'SECURITY';

/**
 * One node of the market map.
 */
export interface WatchlistTreemapNode {
  /** Stable node id: 'root', 'grp/<idAssetClass>' or 'sec/<idSecuritycurrency>'. */
  id: string;
  /** Id of the parent node, null for the root. */
  parentId: string | null;
  nodeType: WatchlistTreemapNodeType;
  /** Name of the stock or sub category in the language of the user; null for the root. */
  label: string | null;
  /** Area in main currency: the market capitalisation of a stock, 0 for other nodes. */
  valueMC: number;
  /** Market capitalisation in main currency, for other nodes the sum of their stocks. */
  totalValueMC: number;
  /** Price change in percent; for other nodes the change of their stocks weighted by market capitalisation. */
  changePercentage: number;
  /** Stock nodes only. */
  idSecuritycurrency: number | null;
  /** Stock nodes only: currency of the listing. */
  currency: string | null;
  /** Stock nodes only: time of the price. */
  timestamp: string | null;
  /** Stock nodes only: resolved company or issuer logo, null when none applies. */
  resolvedLogo: InstrumentLogoRefDto | null;
  /** Stock nodes only: whether the tenant ever held the stock; false disables the security position chart. */
  hadPosition: boolean | null;
}

/**
 * A stock of the watchlist that the market map cannot draw.
 */
export interface WatchlistTreemapExcluded {
  /** Name of the stock. */
  label: string;
  /** Translation key of the reason. */
  reasonKey: string;
}
