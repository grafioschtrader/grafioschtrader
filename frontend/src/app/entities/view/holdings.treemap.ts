/**
 * Holdings treemap of the report "security asset classes with cash": the value of every holding on a hypothetical sale
 * at the report date. Built by the backend as a flat node list; the client only maps it to a Plotly treemap trace.
 */
export interface HoldingsTreemap {
  /** Flat node list, root first, asset classes in the order of AssetclassType. */
  nodes: HoldingsTreemapNode[];
  /** Values that cannot be drawn, for example negative ones; listed in a note below the chart. */
  excluded: HoldingsTreemapExcluded[];
}

/** Level of a treemap node. */
export type HoldingsTreemapNodeType = 'ROOT' | 'ASSETCLASS' | 'SECURITY' | 'CASH_CURRENCY';

/**
 * One node of the holdings treemap.
 */
export interface HoldingsTreemapNode {
  /** Stable node id, for example 'root', 'ac/EQUITIES', 'sec/12' or 'cash/CURRENCY_FOREIGN/USD'. */
  id: string;
  /** Id of the parent node, null for the root. */
  parentId: string;
  nodeType: HoldingsTreemapNodeType;
  /** Asset class type name (to be translated), security name or ISO currency; null for the root. */
  label: string;
  /** Area in main currency: the value of a leaf, 0 for root and asset class nodes. */
  valueMC: number;
  /** Value to display in main currency; for root and asset class nodes the sum of their leaves. */
  totalValueMC: number;
  /** Share in the report total in percentage points; null when the total is not positive. */
  shareOfTotalPercentage: number;
  /** Security nodes only. */
  idSecuritycurrency: number;
  /** Security nodes only: gain/loss of the position in percent. */
  positionGainLossPercentage: number;
  /** Cash nodes only: part of the value that comes from open CFD/Forex positions settling into this currency. */
  marginGainLossMC: number;
  /** Cash nodes only: names of the margin securities contributing to marginGainLossMC. */
  marginPositionNames: string[];
}

/**
 * A value the treemap cannot draw.
 */
export interface HoldingsTreemapExcluded {
  /** Security name or ISO currency code. */
  label: string;
  valueMC: number;
  /** Translation key of the reason, TREEMAP_NEGATIVE_VALUE or TREEMAP_PRICE_MISSING. */
  reasonKey: string;
}
