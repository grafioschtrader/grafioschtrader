package grafioschtrader.priceupdate.historyquote;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.context.MessageSource;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityGrouped;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityHead;
import grafioschtrader.repository.ISecuritycurrencyService;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityServiceAsyncExectuion;
import grafioschtrader.repository.SecuritycurrencyService;

/**
 * Interface for loading and managing historical price data for securities and currency pairs.
 * 
 * <p>Historical quote data can be obtained in different ways:</p>
 * <ul>
 *   <li>Downloaded from external data sources (e.g., financial APIs, stock exchanges)</li>
 *   <li>Calculated/derived from existing instruments (e.g., spreads, synthetic instruments)</li>
 * </ul>
 * 
 * <p>This interface provides methods for:</p>
 * <ul>
 *   <li>Updating historical quotes to the most recent date</li>
 *   <li>Loading historical data for specific time periods</li>
 *   <li>Filling gaps in historical data</li>
 *   <li>Reloading complete historical datasets (e.g., after stock splits)</li>
 *   <li>Generating download links for historical data</li>
 *   <li>Quality assessment of historical quote data</li>
 * </ul>
 *
 * @param <S> the type of security or currency, must extend {@link Securitycurrency}
 */
public interface IHistoryquoteLoad<S extends Securitycurrency<S>> {

  /**
   * Updates historical quotes for all securities or currency pairs until yesterday's date.
   * 
   * <p>Catches up missing historical data for instruments from specified stock exchanges,
   * ensuring all historical quotes are current up to the previous trading day.</p>
   *
   * @param idsStockexchange list of stock exchange IDs to process
   * @return list of updated securities or currency pairs
   */
  List<S> catchAllUpSecuritycurrencyHistoryquote(List<Integer> idsStockexchange);

  /**
   * Retrieves and persists historical quotes for a security or currency pair within a specified time period.
   * 
   * <p><strong>Important:</strong> This operation may take considerable time. The security or currency pair
   * should not be modified by other threads during execution. All retrieved data is persisted to the database.</p>
   *
   * @param securitycurrencyService service for managing the security or currency pair
   * @param securitycurrency the security or currency pair to update
   * @param fromDate start date for historical data (null = use global parameter default)
   * @param toDate end date for historical data (null = use current date)
   * @return the updated security or currency pair with persisted historical quotes
   */
  S createHistoryQuotesAndSave(final ISecuritycurrencyService<S> securitycurrencyService, final S securitycurrency,
      final Date fromDate, final Date toDate);

  /**
   * Updates historical quotes for a list of currency pairs and securities.
   * 
   * <p>Fills missing historical data without checking for retry attempts.
   * Any previous retry indicators are ignored.</p>
   *
   * @param historySecurityCurrencyList list of securities/currencies with their maximum historical quote dates
   * @param currentCalendar current calendar for determining the update range
   * @return list of updated securities or currency pairs
   */
  List<S> fillHistoryquoteForSecuritiesCurrencies(
      List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList, final Calendar currentCalendar);

  /**
   * Asynchronously reloads the complete historical price data for a security.
   * 
   * <p>Used when historical prices must be completely reloaded, such as after:</p>
   * <ul>
   *   <li>Stock splits or reverse splits</li>
   *   <li>Corporate actions affecting historical prices</li>
   *   <li>Data corruption or quality issues</li>
   * </ul>
   *
   * @param <U> the type of security position summary
   * @param securityServiceAsyncExectuion async execution service for the reload operation
   * @param securitycurrencyService service for managing the security or currency pair
   * @param securitycurrency the security to reload
   */
  <U extends SecuritycurrencyPositionSummary<S>> void reloadAsyncFullHistoryquote(
      final SecurityServiceAsyncExectuion<S, U> securityServiceAsyncExectuion,
      final SecuritycurrencyService<S, U> securitycurrencyService, final S securitycurrency);

  /**
   * Generates a download link URL for historical data of a security or currency pair.
   *
   * @param securitycurrency the security or currency pair
   * @return URL string for downloading historical data
   */
  String getSecuritycurrencyHistoricalDownloadLinkAsUrlStr(S securitycurrency);

  /**
   * Creates a download link for a security using a specific feed connector.
   *
   * @param securitycurrency the security or currency pair
   * @param feedConnector the feed connector to use for generating the link
   * @return download link as string
   */
  String createDownloadLink(S securitycurrency, IFeedConnector feedConnector);

  /**
   * Retrieves quality metrics for historical quote data grouped by specified criteria.
   *
   * @param groupedBy the grouping criteria for quality assessment
   * @param securityJpaRepository repository for accessing security data
   * @param messages message source for internationalization
   * @return quality assessment header with aggregated metrics
   */
  HistoryquoteQualityHead getHistoryquoteQualityHead(HistoryquoteQualityGrouped groupedBy,
      SecurityJpaRepository securityJpaRepository, MessageSource messages);
}
