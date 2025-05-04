package grafioschtrader.dto;

//@formatter:off
/**
 * Projection interface for a flattened view of historical quote quality metrics,
 * aggregated by connector, stock exchange, asset class category, and investment instrument.
 * <p>
 * Used by queries that summarize quality data for connectors or exchanges, providing:
 * <ul>
 *   <li>Connector identifier and stock exchange context</li>
 *   <li>Asset class category and special investment instrument type</li>
 *   <li>Counts of total and active securities</li>
 *   <li>Counts of quotes by creation type</li>
 *   <li>Overall quality percentage</li>
 * </ul>
 */
// @formatter:on
public interface IHistoryquoteQualityFlat {

  /**
   * The identifier of the historyquote data connector.
   *
   * @return the connector ID
   */
  String getIdConnectorHistory();

  /**
   * The name of the stock exchange associated with these metrics.
   *
   * @return the stock exchange name
   */
  String getStockexchangeName();

  /**
   * The identifier of the stock exchange.
   *
   * @return the stock exchange ID
   */
  int getIdStockexchange();

  /**
   * The category type of the asset class (e.g., equity, bond).
   *
   * @return the asset class category type code
   */
  byte getCategoryType();

  /**
   * The special investment instrument code (e.g., DIRECT_INVESTMENT, CFD).
   *
   * @return the special investment instrument code
   */
  byte getSpecialInvestmentInstrument();

  /**
   * The total number of securities included in this aggregation.
   *
   * @return count of securities
   */
  int getNumberOfSecurities();

  /**
   * The number of securities that are currently active (not expired).
   *
   * @return count of active securities
   */
  int getActiveNowSecurities();

  /**
   * Count of quotes created by the connector (create_type = CONNECTOR_CREATED).
   *
   * @return number of connector-created quotes
   */
  int getConnectorCreated();

  /**
   * Count of quotes manually imported (create_type = MANUAL_IMPORTED).
   *
   * @return number of manually imported quotes
   */
  int getManualImported();

  /**
   * Count of quotes filled via linear interpolation (create_type =
   * FILLED_LINEAR).
   *
   * @return number of linearly interpolated quotes
   */
  int getFilledLinear();

  /**
   * The overall quality percentage of available quotes.
   *
   * @return the quality percentage (0.0–100.0)
   */
  double getQualityPercentage();
}
