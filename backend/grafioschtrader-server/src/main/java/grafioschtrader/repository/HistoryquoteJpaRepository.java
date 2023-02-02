package grafioschtrader.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import grafioschtrader.dto.HistoryquoteDateClose;
import grafioschtrader.dto.IDateAndClose;
import grafioschtrader.dto.IHistoryquoteQuality;
import grafioschtrader.dto.IMinMaxDateHistoryquote;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.rest.UpdateCreateJpaRepository;
import jakarta.transaction.Transactional;

public interface HistoryquoteJpaRepository extends JpaRepository<Historyquote, Integer>,
    HistoryquoteJpaRepositoryCustom, UpdateCreateJpaRepository<Historyquote> {

  void deleteByIdSecuritycurrency(Integer idSecuritycurrency);

  void deleteByIdSecuritycurrencyAndDate(Integer idSecuritycurrency, Date date);

  int deleteByIdSecuritycurrencyAndCreateType(Integer idSecuritycurrency, byte createType);

  @Transactional
  int deleteByIdSecuritycurrencyAndDateGreaterThanEqual(Integer idSecuritycurrency, Date date);
  
  Optional<Historyquote> findByIdSecuritycurrencyAndDate(Integer idSecuritycurrency, Date date);

  List<Historyquote> findByIdSecuritycurrencyOrderByDateAsc(Integer idSecuritycurrency);

  List<SecurityCurrencyIdAndDate> findByIdSecuritycurrency(Integer idSecuritycurrency);

  List<Historyquote> findByIdSecuritycurrencyAndDateBetweenOrderByDate(Integer idSecuritycurrency, Date fromDate,
      Date toDate);

  List<Historyquote> findByIdSecuritycurrencyAndDateGreaterThanOrderByDateAsc(Integer idSecuritycurrency, Date date,
      Pageable pageable);

  void removeByIdSecuritycurrencyAndCreateType(Integer idSecuritycurrency, byte createType);

  /**
   * For user interface, do not show history quotes which fills day holes.
   *
   * @param idSecuritycurrency
   * @return
   */
  @Query(value = "SELECT h FROM Historyquote h WHERE h.idSecuritycurrency = ?1 AND h.createType != 1 ORDER BY h.date ASC", nativeQuery = false)
  List<Historyquote> findByIdSecuritycurrencyAndCreateTypeFalseOrderByDateAsc(Integer idSecuritycurrency);

  @Query(value = "SELECT h FROM Historyquote h WHERE h.idSecuritycurrency = ?1 AND DAYOFWEEK(h.date) IN (1, 6, 7) ORDER BY h.date DESC", nativeQuery = false)
  List<Historyquote> findByIdFridayAndWeekend(Integer idSecuritycurrency);

  @Query(value = "SELECT h FROM Historyquote h WHERE h.idSecuritycurrency = ?1 AND h.createType != 1 ORDER BY h.date DESC", nativeQuery = false)
  List<Historyquote> findByIdSecuritycurrencyAndCreateTypeFalseOrderByDateDesc(Integer idSecuritycurrency);

  @Query(value = "SELECT h FROM Historyquote h WHERE h.idSecuritycurrency = ?1 AND h.date = ?2 AND h.createType = 1", nativeQuery = false)
  Historyquote findByIdSecuritycurrencyAndDateAndCreateTypeFilled(Integer idSecuritycurrency, Date date);

  @Query(value = "DELETE FROM historyquote WHERE id_securitycurrency = ?1", nativeQuery = true)
  void removeAllSecurityHistoryquote(Integer idSecuritycurrency);

  @Query(value = "SELECT MAX(date) FROM Historyquote h WHERE h.idSecuritycurrency = ?1")
  Date getMaxDateByIdSecurity(Integer idSecuritycurrency);

  @Query(nativeQuery = false)
  List<HistoryquoteDateClose> findDateCloseByIdSecuritycurrencyAndCreateTypeFalseOrderByDateAsc(
      Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  IHistoryquoteQuality getMissingsDaysCountByIdSecurity(Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  IHistoryquoteQuality getMissingsDaysCountByIdCurrency(Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  List<ISecuritycurrencyIdDateClose> getYoungestHistorquoteForSecuritycurrencyByWatchlist(Integer idWatchlist);

  @Query(nativeQuery = true)
  List<Historyquote> getYoungestFeedHistorquoteForSecuritycurrencyByWatchlist(Integer idWatchlist, Integer idTenant);

  @Query(nativeQuery = true)
  Integer countSecuritycurrencyForHistoryquoteAccess(Integer idTenant, Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  List<Historyquote> getHistoryquoteFromDerivedLinksByIdSecurityAndDate(Integer idSecurity, Date fromDate, Date toDate,
      int requiredDayCount);

  @Query(nativeQuery = true)
  List<IMinMaxDateHistoryquote> getMinMaxDateByIdSecuritycurrencyIds(List<Integer> idsSecuritycurrency);

  /**
   * Return of historical prices based on the trading calendar of the security.
   * That means a closed price can be null.
   *
   * @param idSecuritycurrency
   * @return
   */
  @Query(nativeQuery = true)
  List<IDateAndClose> getClosedAndMissingHistoryquoteByIdSecurity(Integer idSecuritycurrency);

  List<IDateAndClose> getByIdSecuritycurrencyAndCreateTypeNotOrderByDate(Integer idSecuritycurrency, byte createType);

  List<Historyquote> getByIdSecuritycurrencyOrderByDate(Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  List<ISecuritycurrencyIdDateClose> getCertainOrOlderDayInHistorquoteForSecuritycurrencyByWatchlist(
      Integer idWatchlist, Date date);

  @Query(nativeQuery = true)
  List<ISecuritycurrencyIdDateClose> getIdDateCloseByIdsAndDate(@Param("ids") List<Integer> idSecuritycurrencies,
      @Param("date") Date date);

  @Query(nativeQuery = true)
  List<Historyquote> getSecuritycurrencyHistoryEndOfYearsByIdTenant(Integer idTenant);

  @Query(nativeQuery = true)
  List<Object[]> getUsedCurrencyHistoryquotesByIdTenantAndDate(Integer idTenant, Date date);

  /**
   * Return exchange rate for dividend transactions depending on tenant and main
   * currency. This include all exchange rates from history quotes with
   * transactions on foreign cash account.
   *
   * @param idTenant
   * @param mainCurrency
   * @return
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquoteCurrenciesForDividendsByIdTenantAndMainCurrency(Integer idTenant, String mainCurrency);

  /**
   * Return exchange rate for buy/sell transactions depending on tenant and main
   * currency. This include all exchange rates from history quotes with
   * transactions on foreign cash account.
   *
   * @param idTenant
   * @param mainCurrency
   * @return
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquoteCurrenciesForBuyAndSellByIdTenantAndMainCurrency(Integer idTenant, String mainCurrency);

  /**
   * Return exchange rate for buy/sell/dividend transactions depending on tenant
   * and main currency. This include all exchange rates from history quotes with
   * transactions on foreign cash account.
   *
   * @param idTenant
   * @param mainCurrency
   * @return
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquoteCurrenciesForIntrFeeBuySellDivByIdTenantAndMainCurrency(Integer idTenant,
      String mainCurrency);

  /**
   * For every transaction of a tenant gets the corresponding exchange rate to the
   * main currency. It includes all transactions, that means transaction with
   * security or no security involved.
   *
   * @param idTenant
   * @return
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquotesForAllForeignTransactionsByIdTenant(Integer idTenant);

  @Query(nativeQuery = true)
  List<Object[]> getHistoryquotesForAllForeignTransactionsByIdPortfolio(Integer idPortfolio);

  @Query(nativeQuery = true)
  List<Object[]> getHistoryquotesForAllForeignTransactionsByIdSecuritycashAccount(Integer idSecurity);

  /**
   * For every transaction of a certain Tenant and Security combination gets the
   * corresponding exchange rate to the main currency.
   *
   * @param idTenant
   * @param idSecuritycurrency
   * @return
   */
  @Query(nativeQuery = true)
  List<Object[]> findByIdTenantAndIdSecurityFoCuHistoryquotes(Integer idTenant, Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  List<Object[]> findByIdPortfolioAndIdSecurityFoCuHistoryquotes(Integer idPortfolio, Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  List<Object[]> findByIdSecurityaccountAndIdSecurityFoCuHistoryquotes(Integer idSecuritycashAccount,
      Integer idSecuritycurrency);

  /**
   * Return of all missing dates of the EOD for a security. The missing dates are
   * determined via the index referenced by the stock exchange.
   * 
   * @param idSecuritycurrencyIndex
   * @param idSecuritycurrency
   * @return
   */
  @Query(nativeQuery = true)
  List<Date> getMissingEODForSecurityByUpdCalendarIndex(Integer idSecuritycurrencyIndex, Integer idSecuritycurrency);

  public interface SecurityCurrencyIdAndDate {
    Integer getIdSecuritycurrency();

    Date getDate();
  }

}
