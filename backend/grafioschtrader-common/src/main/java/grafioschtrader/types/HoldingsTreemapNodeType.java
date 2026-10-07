package grafioschtrader.types;

/**
 * Level of a node in the holdings treemap of the report "security asset classes with cash". Serialized by name; the
 * client only maps it to a label and never builds select options from it.
 */
public enum HoldingsTreemapNodeType {
  /** The single top node, representing the whole wealth of the report. */
  ROOT,
  /** An asset class type, including the two cash classes CURRENCY_CASH and CURRENCY_FOREIGN. */
  ASSETCLASS,
  /** One held security, a leaf. Margin instruments never become such a node. */
  SECURITY,
  /** All cash accounts of one currency summed, plus the unrealized gain/loss of margin positions settling into it. */
  CASH_CURRENCY
}
