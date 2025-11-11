/**
 * Tree node types for the Grafioschtrader application.
 * Using const object instead of enum for better compatibility with library layer.
 */
export const TreeNodeType = {
  AdminDataRoot: 'AdminDataRoot',
  PortfolioRoot: 'PortfolioRoot',
  Portfolio: 'Portfolio',
  SecurityaccountRoot: 'SecurityaccountRoot',
  SecurityAccount: 'SecurityAccount',
  WatchlistRoot: 'WatchlistRoot',
  Watchlist: 'Watchlist',
  AlgoRoot: 'AlgoRoot',
  Strategy: 'Strategy',
  TradingCalendarGlobal: 'TradingCalendarGlobal',
  BaseDataRoot: 'BaseDataRoot',
  AssetClass: 'AssetClass',
  Stockexchange: 'Stockexchange',
  TradingPlatformPlan: 'TradingPlatformPlan',
  ImpTransTemplate: 'ImpTransTemplate',
  UDFMetadataSecurity: 'UDFMetadataSecurity',
  NO_MENU: 'NO_MENU'
} as const;

/**
 * Type representing all possible tree node type values.
 */
export type TreeNodeType = typeof TreeNodeType[keyof typeof TreeNodeType];

