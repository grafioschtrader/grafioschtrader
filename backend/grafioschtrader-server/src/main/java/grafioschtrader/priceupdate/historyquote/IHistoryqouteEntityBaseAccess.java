package grafioschtrader.priceupdate.historyquote;

import java.util.Date;
import java.util.List;

import grafioschtrader.entities.Securitycurrency;

/**
 * Base access interface for historical quote entity operations.
 * 
 * <p>
 * Provides low-level data access methods for querying and updating historical quote data for securities and currency
 * pairs. This interface abstracts database operations related to identifying instruments that need historical data
 * updates and loading their quote history.
 * </p>
 *
 * @param <S> the type of security or currency, must extend {@link Securitycurrency}
 */
public interface IHistoryqouteEntityBaseAccess<S extends Securitycurrency<S>> {

  /**
   * Retrieves securities or currency pairs that require historical quote updates.
   * 
   * <p>
   * Queries for instruments based on retry limit, historical quote threshold criteria, and optionally filtered by stock
   * exchanges. Returns metadata including the maximum historical quote date for each instrument.
   * </p>
   *
   * @param maxHistoryRetry  maximum number of retry attempts allowed for failed downloads
   * @param baseHisotryquote criteria for determining which instruments need updates
   * @param idsStockexchange list of stock exchange IDs to filter by (null = all exchanges)
   * @return list of securities/currencies with their maximum historical quote dates
   */
  List<SecurityCurrencyMaxHistoryquoteData<S>> getMaxHistoryquoteResult(short maxHistoryRetry,
      BaseHistoryquoteThru<S> baseHisotryquote, List<Integer> idsStockexchange);

  /**
   * Loads a security or currency pair with its historical quotes and retrieves additional missing prices.
   * 
   * <p>
   * Fetches the instrument's existing historical data and calls the appropriate method to load any missing quotes
   * within the specified date range.
   * </p>
   *
   * @param securitycurrency the security or currency pair to update
   * @param fromDate         start date for historical data retrieval
   * @param toDate           end date for historical data retrieval
   * @return the security or currency pair with updated historical quotes
   */
  S catchUpSecurityCurrencypairHisotry(S securitycurrency, Date fromDate, Date toDate);

}
