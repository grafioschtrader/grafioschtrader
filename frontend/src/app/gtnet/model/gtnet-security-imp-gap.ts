import {GapCodeType} from './gap-code.type';

/**
 * Model representing a gap (mismatch) identified during GTNet security import.
 * When a security from a GTNet peer cannot be fully matched to local configuration,
 * gap records document what specifically didn't match.
 */
export class GTNetSecurityImpGap {
  /**
   * Unique identifier for the gap record.
   */
  idGtNetSecurityImpGap: number;

  /**
   * Reference to the parent import position.
   */
  idGtNetSecurityImpPos: number;

  /**
   * Reference to the GTNet peer from which the security lookup result came.
   */
  idGtNet: number;

  /**
   * Code indicating what type of gap/mismatch occurred.
   */
  gapCode: GapCodeType;

  /**
   * Human-readable description of the expected configuration from the remote peer.
   * For asset class: 'categoryType / subCategory / specialInvestmentInstrument'.
   * For connectors: the connector family name (e.g., 'yahoo', 'finnhub').
   * Always in English regardless of user's language preference.
   */
  gapMessage: string;
}
