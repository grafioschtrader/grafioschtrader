/**
 * Common tree node type constants for library-level nodes.
 * These constants represent node types that have generic behavior applicable across applications.
 *
 * Applications should define their own tree node types (e.g., TreeNodeType) for
 * application-specific nodes like Portfolio, Watchlist, Security, etc.
 */
export const LibTreeNodeType = {
  /**
   * Node type indicating that no context menu should be displayed for this node.
   * Used for nodes that are informational or administrative and don't support user actions.
   */
  NO_MENU: 'NO_MENU'
} as const;

/**
 * Type representing all possible library tree node type values.
 */
export type LibTreeNodeTypeValue = typeof LibTreeNodeType[keyof typeof LibTreeNodeType];
