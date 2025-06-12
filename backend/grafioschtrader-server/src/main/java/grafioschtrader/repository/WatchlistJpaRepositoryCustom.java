package grafioschtrader.repository;

import java.util.List;

import grafiosch.dto.TenantLimit;
import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.dto.IntraHistoricalWatchlistProblem;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.search.SecuritycurrencySearch;

/**
 * Custom repository methods for the {@link Watchlist} entity. Extends {@link BaseRepositoryCustom} to provide base
 * repository functionalities. This interface defines operations specific to watchlists, such as managing the securities
 * and currency pairs they contain, handling data provider interactions, and performing searches within a watchlist.
 */
public interface WatchlistJpaRepositoryCustom extends BaseRepositoryCustom<Watchlist> {

  /**
   * Retrieves the limits for the number of securities and currency pairs that can be added to a specific watchlist.
   * These limits might be based on tenant settings or other application rules.
   *
   * @param idWatchlist The ID of the watchlist.
   * @return An array of {@link TenantLimit} objects, typically with two elements: one for securities and one for
   *         currency pairs.
   */
  TenantLimit[] getSecuritiesCurrenciesWachlistLimits(Integer idWatchlist);

  /**
   * Adds existing securities and/or currency pairs, identified by their IDs, to the specified watchlist.
   * <p>
   * This operation is subject to limits defined for the watchlist (e.g., maximum number of securities or currency
   * pairs). It typically checks if the instruments are not already present in the watchlist to avoid duplicates.
   *
   * @param idWatchlist           The ID of the watchlist to which the instruments will be added. This watchlist must
   *                              exist and be accessible to the user.
   * @param securitycurrencyLists A {@link SecuritycurrencyLists} object containing lists of unique IDs for existing
   *                              {@link grafioschtrader.entities.Security} and/or
   *                              {@link grafioschtrader.entities.Currencypair} entities to be added to the watchlist.
   * @return The updated {@link Watchlist} entity reflecting the newly added instruments.
   */
  Watchlist addSecuritycurrenciesToWatchlist(Integer idWatchlist, SecuritycurrencyLists securitycurrencyLists);

  /**
   * Removes multiple securities and/or currency pairs from a specified watchlist based on their IDs.
   *
   * @param idWatchlist         The ID of the watchlist from which to remove the instruments.
   * @param idsSecuritycurrency A list of IDs for the securities/currency pairs to be removed.
   * @return The number of instruments successfully removed from the watchlist.
   */
  int removeMultipleFromWatchlist(Integer idWatchlist, final List<Integer> idsSecuritycurrency);

  Watchlist removeSecurityFromWatchlistAndDelete(Integer idWatchlist, Integer idSecuritycurrency);

  Watchlist removeCurrencypairFromWatchlistAndDelete(Integer idWatchlist, Integer idSecuritycurrency);

  Watchlist removeSecurityFromWatchlist(Integer idWatchlist, Integer idSecuritycurrency);

  Watchlist removeCurrencypairFromWatchlist(Integer idWatchlist, Integer idSecuritycurrency);

  Watchlist removeAllSecurityCurrency(Integer idWatchlist);

  /**
   * Deletes a watchlist entity identified by its ID, but only if it belongs to the specified tenant.
   * This is a security measure to ensure users can only delete their own watchlists.
   *
   * @param id The ID of the watchlist to delete.
   * @param idTenant The ID of the tenant who is attempting to delete the watchlist.
   * @return The number of entities deleted (0 or 1). Returns 1 if deletion was successful, 0 otherwise.
   */
  int delEntityWithTenant(Integer id, Integer idTenant);

  /**
   * Searches for securities and currency pairs that match the given criteria and are eligible to be added to the
   * specified watchlist.
   * <p>
   * This method is typically used when a user wants to find <b>new</b> instruments to add to an existing watchlist.
   * Therefore, it <b>excludes</b> any securities or currency pairs that are already present in the specified
   * {@code idWatchlist} from the search results.
   * </p>
   * The search is performed within the security context of the currently authenticated user and their tenant.
   *
   * @param idWatchlist            The ID of the watchlist for which new instruments are being searched. Instruments
   *                               already in this watchlist will be excluded from the results. The watchlist must
   *                               belong to the current user's tenant.
   * @param securitycurrencySearch The {@link SecuritycurrencySearch} criteria object defining the parameters for
   *                               finding matching securities and currency pairs.
   * @return A {@link SecuritycurrencyLists} object containing lists of {@link Security} and {@link Currencypair}
   *         entities that match the search criteria and are not already in the specified watchlist.
   * @throws SecurityException if the watchlist with the given ID is not found or does not belong to the current tenant.
   */
  SecuritycurrencyLists searchByCriteria(Integer idWatchlist, SecuritycurrencySearch securitycurrencySearch);

  /**
   * Attempts to update intraday price data for instruments (securities and currency pairs) in a given watchlist, but
   * only for those instruments whose retry counter for intraday loading ({@code retryIntraLoad}) is greater than 0.
   * Only users with the ADMIN role and ALL_EDIT may use this function.
   *
   * @param idWatchlist The ID of the watchlist whose instruments are to be processed.
   * @return A {@link SecuritycurrencyLists} object containing the securities and currency pairs for which an intraday
   *         data update attempt was made.
   */
  SecuritycurrencyLists tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(Integer idWatchlist);

  /**
   * Adds instruments (securities or currency pairs) that have known price data problems (e.g., missing historical or
   * intraday data, as specified by {@code ihwp}) to a specified watchlist. This is useful for creating a watchlist that
   * specifically tracks instruments requiring data attention. Only users with the ADMIN role and ALL_EDIT may use this
   * function.
   *
   * @param idWatchlist The ID of the watchlist to which the problematic instruments will be added.
   * @param ihwp        An {@link IntraHistoricalWatchlistProblem} object specifying the criteria for identifying
   *                    instruments with price data problems (e.g., type of problem, date ranges).
   * @return The updated {@link Watchlist} entity, now containing the added problematic instruments.
   */
  Watchlist addInstrumentsWithPriceDataProblems(Integer idWatchlist, IntraHistoricalWatchlistProblem ihwp);

  /**
   * Attempts to update historical end-of-day price data for instruments (securities and currency pairs) in a given
   * watchlist, but only for those instruments whose retry counter for historical data loading
   * ({@code retryHistoryLoad}) is greater than 0. Only users with the ADMIN role and ALL_EDIT may use this function.
   *
   * @param idWatchlist The ID of the watchlist whose instruments are to be processed.
   * @return A {@link SecuritycurrencyLists} object containing the securities and currency pairs for which a historical
   *         data update attempt was made.
   */
  SecuritycurrencyLists tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idWatchlist);

  /**
   * Moves a specific security or currency pair from a source watchlist to a target watchlist. The instrument must exist
   * in the source watchlist and the user must have appropriate permissions for both source and target watchlists.
   *
   * @param idWatchlistSource  The ID of the source watchlist from which the instrument will be moved.
   * @param idWatchlistTarget  The ID of the target watchlist to which the instrument will be moved.
   * @param idSecuritycurrency The ID of the security or currencypair to move.
   * @return {@code true} if the instrument was successfully moved, {@code false} otherwise (e.g., if the instrument is
   *         not in the source watchlist, or if limits on the target watchlist are exceeded).
   * @throws SecurityException if the user does not have rights to modify either the source or target watchlist.
   */
  Boolean moveSecuritycurrency(Integer idWatchlistSource, Integer idWatchlistTarget, Integer idSecuritycurrency);

  /**
   * Retrieves the raw response string from the configured data provider for an instrument's price data. This method is
   * used when the frontend needs to display data directly from the provider, especially in cases where API keys are
   * involved and handled by the backend.
   *
   * @param idSecuritycurrency The ID of the security or currencypair.
   * @param isIntraday         {@code true} to fetch intraday data, {@code false} for historical end-of-day data.
   * @param isSecurity         {@code true} if the instrument is a {@link Security}, {@code false} if it's a
   *                           {@link Currencypair}.
   * @return A string containing the raw data response from the provider, or an error message/empty string if
   *         unsuccessful.
   */
  String getDataProviderResponseForUser(Integer idSecuritycurrency, boolean isIntraday, boolean isSecurity);

  /**
   * Retrieves the raw response string from the configured data provider for a security's dividend or stock split
   * information. This method is used when the frontend needs to display data directly from the provider, especially in
   * cases where API keys are involved and handled by the backend.
   *
   * @param idSecuritycurrency The ID of the {@link Security}.
   * @param isDiv              {@code true} to fetch dividend data, {@code false} for stock split data.
   * @return A string containing the raw data response from the provider, or an error message/empty string if
   *         unsuccessful.
   */
  String getDataProviderDivSplitResponseForUser(Integer idSecuritycurrency, boolean isDiv);

  /**
   * Generates a URL for accessing an instrument's price data from its provider. If the provider requires an API key
   * that should not be exposed to the frontend, this method returns a backend-redirect URL. Otherwise, it may return a
   * direct link.
   *
   * @param idSecuritycurrency The ID of the security or currencypair.
   * @param isIntraday         {@code true} for an intraday data link, {@code false} for a historical data link.
   * @param isSecurity         {@code true} if the instrument is a {@link Security}, {@code false} if it's a
   *                           {@link Currencypair}.
   * @return A URL string pointing either directly to the data provider or to a backend endpoint that proxies the
   *         request.
   */
  String getDataProviderLinkForUser(Integer idSecuritycurrency, boolean isIntraday, boolean isSecurity);
}
