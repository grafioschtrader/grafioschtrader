package grafioschtrader.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.HoldDailyTotal;

/**
 * Daily total value per tenant and portfolio. Written only by the task {@code HOLD_DAILY_TOTAL_UPDATE}; no REST
 * repository is exposed. Consumers should prefer the methods of {@link HoldDailyTotalJpaRepositoryCustom}, which also
 * report from which day the rows may be outdated or missing.
 */
public interface HoldDailyTotalJpaRepository
    extends JpaRepository<HoldDailyTotal, Integer>, HoldDailyTotalJpaRepositoryCustom {

  /**
   * Deletes the rows of a tenant from the given day on, those of the tenant total and those of every portfolio. Runs in
   * the transaction of the caller.
   *
   * @param idTenant the tenant
   * @param fromDate the first day to delete, inclusive
   * @return the number of deleted rows
   */
  @Modifying
  @Query(value = "DELETE FROM hold_daily_total WHERE id_tenant = ?1 AND hold_date >= ?2", nativeQuery = true)
  int removeByIdTenantFromDate(Integer idTenant, LocalDate fromDate);

  /**
   * Deletes every row of a tenant, before it is computed in full. Runs in the transaction of the caller.
   *
   * @param idTenant the tenant
   * @return the number of deleted rows
   */
  @Modifying
  @Query(value = "DELETE FROM hold_daily_total WHERE id_tenant = ?1", nativeQuery = true)
  int removeByIdTenant(Integer idTenant);

  List<HoldDailyTotal> findByIdTenantAndIdPortfolioIsNullAndHoldDateBetweenOrderByHoldDate(Integer idTenant,
      LocalDate fromDate, LocalDate toDate);

  List<HoldDailyTotal> findByIdTenantAndIdPortfolioAndHoldDateBetweenOrderByHoldDate(Integer idTenant,
      Integer idPortfolio, LocalDate fromDate, LocalDate toDate);

  Optional<HoldDailyTotal> findFirstByIdTenantAndIdPortfolioIsNullAndHoldDateLessThanEqualOrderByHoldDateDesc(
      Integer idTenant, LocalDate date);

  Optional<HoldDailyTotal> findFirstByIdTenantAndIdPortfolioAndHoldDateLessThanEqualOrderByHoldDateDesc(
      Integer idTenant, Integer idPortfolio, LocalDate date);

  //@formatter:off
  /**
   * Finds the earliest trading day on which a price the tenant depends on was created or changed after the given time.
   * This is the timestamp technique of {@code Transaction.getTransactionWhyHistoryquoteYounger}: a back-filled gap, a
   * corrected or reloaded history and a price of the gap filler all leave a younger
   * {@code historyquote.create_modify_time}.
   *
   * <p>
   * A price counts when the tenant held the instrument on that day: the security itself, the currency pair converting
   * a position into the tenant or the portfolio currency, or the currency pair converting a cash balance. The periodic
   * prices of {@code historyquote_period} carry no timestamp and are not covered.
   * </p>
   *
   * Named query: HoldDailyTotal.getEarliestChangedQuoteDateByTenant
   * Parameters in SQL:
   * - ?1 - the tenant, applied to hold_securityaccount_security and hold_cashaccount_balance
   * - ?2 - the time of the previous scan; only prices with a younger create_modify_time are considered
   *
   * Each of the five branches joins one id column of a hold table to historyquote, so that the lookup uses the
   * unique key (id_securitycurrency, date) instead of scanning the price table by its timestamp.
   *
   * @param idTenant    the tenant
   * @param checkedTime the time of the previous scan
   * @return the earliest affected day, or null when no such price exists
   */
  //@formatter:on
  @Query(nativeQuery = true)
  LocalDate getEarliestChangedQuoteDateByTenant(Integer idTenant, LocalDateTime checkedTime);

  //@formatter:off
  /**
   * Lists for every tenant holding a security the first day it held it. A rebuild of the holdings of that security,
   * after a split was added, changed or removed, can change every day of those hold periods.
   *
   * Named query: HoldDailyTotal.getFirstHoldDatePerTenantBySecurity
   * Parameters in SQL:
   * - ?1 - the security
   *
   * @param idSecuritycurrency the security
   * @return one row per tenant
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<ITenantFirstHoldDate> getFirstHoldDatePerTenantBySecurity(Integer idSecuritycurrency);

  /** A tenant and the first day it held a certain security. */
  interface ITenantFirstHoldDate {
    Integer getIdTenant();

    LocalDate getFirstHoldDate();
  }
}
