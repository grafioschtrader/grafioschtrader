package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;

import grafioschtrader.dto.IHistoryquoteQualityFlat;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.projection.IFormulaSecurityLoad;
import grafioschtrader.entities.projection.SecurityYearClose;
import grafioschtrader.priceupdate.historyquote.SecurityCurrencyMaxHistoryquoteData;
import grafioschtrader.reportviews.historyquotequality.IHistoryquoteQualityWithSecurityProp;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface SecurityJpaRepository extends SecurityCurrencypairJpaRepository<Security>,
    JpaSpecificationExecutor<Security>, SecurityJpaRepositoryCustom, UpdateCreateJpaRepository<Security> {

  @Procedure(procedureName = "deleteUpdateHistoryQuality")
  void deleteUpdateHistoryQuality();

  Security findByName(String name);

  List<Security> findByIdSecuritycurrencyInOrderByName(Set<Integer> idSecuritycurrencyIds);

  Security findByIsinAndCurrency(String isin, String currencySecurity);

  List<Security> findByIsin(String isin);

  Security findByTickerSymbolAndCurrency(String tickerSymbol, String currencySecurity);

  List<Security> findByActiveToDateGreaterThanEqualAndIdTenantPrivateIsNullOrIdTenantPrivateOrderByName(Date date,
      Integer idTenantPrivate);

  List<Security> findByTickerSymbolInOrderByIdSecuritycurrency(Set<String> tickers);

  /**
   * Determination of the positions held by instruments that are no longer traded, using the "active until" property.
   */
  @Query(nativeQuery = true)
  List<CheckSecurityTransIntegrity> getHoldingsOfInactiveSecurties();
  
  /**
   * A new payment of interest or dividend is expected: 
   * - Last payment with this client plus the addition of days determined based on the frequency of payments.</br>
   * TODO First interest of a bond determined on the basis of the trading date plus addition of the frequency.</br>
   * TODO Use of the dividend table with its optional "Pay day".</br> 
   */
  @Query(nativeQuery = true)
  List<CheckSecurityTransIntegrity> getPossibleMissingDivInterest();
  

  @Query(nativeQuery = true)
  List<Security> getUnusedSecurityForAlgo(Integer idTenantPrivate, Integer idAlgoAssetclassSecurity);

  @Query(value = """
      SELECT s FROM Watchlist w JOIN w.securitycurrencyList s
      WHERE w.idTenant = ?1 AND w.idWatchlist = ?2 AND s.retryIntraLoad > 0 AND s.activeToDate >= CURDATE()
      AND s.idConnectorIntra IS NOT NULL""")
  List<Security> findWithConnectorByIdTenantAndIdWatchlistWhenRetryIntraGreaterThan0(Integer idTenant,
      Integer idWatchlist);

  @Query(value = """
      SELECT s FROM Watchlist w JOIN w.securitycurrencyList s
      WHERE w.idTenant = ?1 AND w.idWatchlist = ?2 AND s.retryIntraLoad > 0
      AND s.activeToDate >= CURDATE()AND s.idLinkSecuritycurrency IS NOT NULL""")
  List<Security> findDerivedByIdTenantAndIdWatchlistWhenRetryIntraGreaterThan0(Integer idTenant, Integer idWatchlist);

  @Query(value = """
      SELECT s FROM Security s WHERE s.idLinkSecuritycurrency IS NOT NULL
      AND s.retryHistoryLoad < ?1 AND NOT EXISTS (SELECT h FROM s.historyquoteList h)""")
  List<Security> findDerivedEmptyHistoryquoteByMaxRetryHistoryLoad(short retryHistoryLoad);

  @Query(value = """
      SELECT s as securityCurrency, MAX(h.date) AS date FROM Watchlist w JOIN w.securitycurrencyList s JOIN s.historyquoteList h
      WHERE w.idTenant = ?1 AND w.idWatchlist = ?2 AND s.retryHistoryLoad > 0 AND s.activeToDate >= h.date
      AND s.idConnectorHistory IS NOT NULL GROUP BY s.idSecuritycurrency HAVING s.activeToDate >= MAX(h.date)""")
  List<SecurityCurrencyMaxHistoryquoteData<Security>> findByIdTenantAndIdWatchlistWhenRetryHistroyGreaterThan0(
      Integer idTenant, Integer idWatchlist);

  @Query(value = """
      SELECT s FROM Watchlist w JOIN w.securitycurrencyList s
      WHERE w.idTenant = ?1 AND w.idWatchlist = ?2 AND s.assetClass.specialInvestmentInstrument <> 10 ORDER BY s.name""")
  List<Security> getTradableSecuritiesByTenantAndIdWatschlist(Integer idTenant, Integer idWatchlist);

  @Query(value = "SELECT s FROM Security s WHERE s.idSecuritycurrency = ?1 AND (s.idTenantPrivate IS NULL OR s.idTenantPrivate = ?2)")
  Security findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(Integer idSecuritycurrency,
      Integer idTenantPrivate);

  /**
   * With or without this clause "ORDER BY s.idSecuritycurrency" we get more rows
   * than without it. Why?
   *
   * @param maxHistoryRetry
   * @return
   */
  @Query(value = """
      SELECT s as securityCurrency, MAX(h.date) AS date FROM Security s JOIN s.historyquoteList h
      WHERE s.retryHistoryLoad < ?1 AND s.idConnectorHistory IS NOT NULL GROUP BY h.idSecuritycurrency
      HAVING s.activeToDate >= MAX(h.date) ORDER BY s.idSecuritycurrency""")
  List<SecurityCurrencyMaxHistoryquoteData<Security>> getMaxHistoryquoteWithConnector(short maxHistoryRetry);

  @Query(nativeQuery = false)
  List<SecurityCurrencyMaxHistoryquoteData<Security>> getMaxHistoryquoteWithConnectorForExchange(short maxHistoryRetry,
      List<Integer> idsStockexchange);

  @Query(value = """
      SELECT s as securityCurrency, MAX(h.date) AS date FROM Security s JOIN s.historyquoteList h
      WHERE s.retryHistoryLoad < ?1 AND s.idLinkSecuritycurrency IS NOT NULL GROUP BY h.idSecuritycurrency
      HAVING s.activeToDate >= MAX(h.date) ORDER BY s.idSecuritycurrency""")
  List<SecurityCurrencyMaxHistoryquoteData<Security>> getMaxHistoryquoteWithCalculation(short maxHistoryRetry);

  // Catch history quotes as well for this Security
  @EntityGraph(value = "graph.security.historyquote", type = EntityGraphType.FETCH)
  Security findByIdSecuritycurrency(Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  Stream<IHistoryquoteQualityFlat> getHistoryquoteQualityConnectorFlat();

  @Query(nativeQuery = true)
  Stream<IHistoryquoteQualityFlat> getHistoryquoteQualityStockexchangeFlat();

  @Query(nativeQuery = true)
  List<IHistoryquoteQualityWithSecurityProp> getHistoryquoteQualityByIds(String idConnectorHistory,
      Integer idStockexchange, Byte categoryType, Byte specialInvestmentInstrument);

  @Query(nativeQuery = true)
  List<IFormulaSecurityLoad> getBySecurityDerivedLinkByIdSecurityLink(Integer idLinkSecuritycurrency);

  @Query(nativeQuery = true)
  List<SecurityYearClose> getSecurityYearCloseDivSum(Integer idSecurity);

  @Query(nativeQuery = true)
  List<SecurityYearClose> getSecurityYearDivSumCurrencyClose(Integer idSecurity, Integer idCurrencypair);

  @Override
  void calcGainLossBasedOnDateOrNewestPrice(List<SecurityPositionSummary> securitycurrencyPositionSummary,
      Date untilDate);

  public enum SplitAdjustedHistoryquotes {
    NOT_DETCTABLE, ADJUSTED, NOT_ADJUSTED
  }

  public static interface CheckSecurityTransIntegrity {
    int getIdUser();

    String getLocaleStr();
    
    String getCurrency();

    int getIdSecuritycurrency();

    String getName();

    LocalDate getMarkDate();
  }

}
