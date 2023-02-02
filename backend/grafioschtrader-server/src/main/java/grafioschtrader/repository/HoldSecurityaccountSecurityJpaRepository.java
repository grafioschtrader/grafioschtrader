package grafioschtrader.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import grafioschtrader.entities.HoldSecurityaccountSecurity;
import grafioschtrader.entities.HoldSecurityaccountSecurityKey;
import grafioschtrader.reportviews.performance.IPeriodHolding;

public interface HoldSecurityaccountSecurityJpaRepository
    extends JpaRepository<HoldSecurityaccountSecurity, HoldSecurityaccountSecurityKey>,
    HoldSecurityaccountSecurityJpaRepositoryCustom {

  @Query(value = "DELETE FROM hold_securityaccount_security WHERE id_securitycash_account = ?1", nativeQuery = true)
  void removeAllByIdSecuritycashAccount(Integer idSecuritycashAccount);

  void deleteByHsskIdSecuritycashAccountAndHsskIdSecuritycurrency(Integer idSecuritycashAccount,
      Integer idSecuritycurrency);

  /**
   * Returns for a security account the buy and sell transactions mixed with
   * splits in the order of the transaction time.
   */
  @Query(nativeQuery = true)
  List<ITransactionSecuritySplit> getBuySellTransWithSecuritySplitByIdSecurityaccount(Integer idSecurityaccount);

  /**
   * Returns for a security account and security the buy and sell transactions
   * mixed with splits in the order of the transaction time.
   */
  @Query(nativeQuery = true)
  List<ITransactionSecuritySplit> getBuySellTransWithSecuritySplitByIdSecurityaccountAndSecurity(
      Integer idSecurityaccount, Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  List<ITransactionSecuritySplit> getBuySellTransWithSecuritySplitByIdSecurityaccountAndSecurityMargin(
      Integer idSecurityaccount, Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  List<IPeriodHolding> getPeriodHoldingsByTenant(Integer idTenant, LocalDate dateFrom, LocalDate dateTo);

  @Query(nativeQuery = true)
  List<IPeriodHolding> getPeriodHoldingsByPortfolio(Integer idPortfolio, LocalDate dateFrom, LocalDate dateTo);

  @Query(nativeQuery = true)
  List<DateSecurityQuoteMissing> getMissingQuotesForSecurityByTenantAndPeriod(Integer idTenant, LocalDate dateFrom,
      LocalDate dateTo);

  @Query(nativeQuery = true)
  List<HoldSecurityaccountSecurity> getByISINAndSecurityAccountAndDate(String isin, Integer idSecurityaccount,
      Date transactinDate);

  /**
   * Trading days which can't used for calculation of tenants portfolio
   * performance, because historical data for one or more security holding are not
   * available.
   *
   * @param idTenant
   * @return
   */
  @Query(nativeQuery = true)
  Set<Date> getMissingsQuoteDaysByTenant(Integer idTenant);

  @Query(nativeQuery = true)
  Set<Date> getMissingsQuoteDaysByPortfolio(Integer idPortfolio);

  /**
   * Returns the holidays of stock exchanges which depends on the tenants
   * holdings.
   *
   * @param idTenant
   * @return
   */
  @Query(nativeQuery = true)
  Set<Date> getCombinedHolidayOfHoldingsByTenant(Integer idTenant);

  @Query(nativeQuery = true)
  Set<Date> getCombinedHolidayOfHoldingsByPortfolio(Integer idPortfolio);

  @Query("""
        SELECT MIN(hss.hssk.fromHoldDate) AS firstTradingDate 
        FROM HoldSecurityaccountSecurity hss WHERE hss.idTenant = ?1""")
  LocalDate findByIdTenantMinFromHoldDate(Integer idTenant);

  @Query("""
         SELECT MIN(hss.hssk.fromHoldDate) AS firstTradingDate 
         FROM HoldSecurityaccountSecurity hss WHERE hss.idPortfolio = ?1""")
  LocalDate findByIdPortfolioMinFromHoldDate(Integer idTenant);

  @Query(nativeQuery = true)
  List<Integer> getIdSecurityByIdTenantWithHoldings(Integer idTenant);

  /**
   * A stored procedure is used so that only transactions affected by the split
   * are selected with the splits in chronological order.
   *
   * @param idSecurity
   * @return
   */
  @Query(value = "CALL holdSecuritySplitTransaction(:idSecurity);", nativeQuery = true)
  List<IHoldSecuritySplitTransactionBySecurity> getHoldSecuritySplitTransactionBySecurity(
      @Param("idSecurity") Integer idSecurity);

  @Query(value = "CALL holdSecuritySplitMarginTransaction(:idSecurity);", nativeQuery = true)
  List<IHoldSecuritySplitTransactionBySecurity> getHoldSecuritySplitMarginTransactionBySecurity(
      @Param("idSecurity") Integer idSecurity);

  public static interface ITransactionSecuritySplit {
    public Integer getIdTransaction();

    public Integer getIdSecuritycurrency();

    public LocalDateTime getTsDate();

    /**
     * When transaction: Units or with margin instrument units multiply by value per
     * point.</br>
     * When split: Split factor
     *
     * @return
     */
    public Double getFactorUnits();

    public Integer getIdTransactionMargin();

    public String getCurrency();
  }

  public static interface DateSecurityQuoteMissing {
    public LocalDate getTradingDate();

    public Integer getIdSecuritycurrency();
  }

  public static class TransactionSecuritySplit implements ITransactionSecuritySplit {

    private Integer idTransaction;

    private Integer idSecuritycurrency;

    private LocalDateTime tsDate;

    /**
     * When transaction: Units or with margin instrument units multiply by value per
     * point.</br>
     * When split: Spit factor
     *
     * @return
     */
    private Double factorUnits;

    private Integer idTransactionMargin;

    private String currency;

    public TransactionSecuritySplit(Integer idTransaction, Integer idSecuritycurrency, LocalDateTime tsDate,
        Double factorUnits, Integer idTransactionMargin, String currency) {
      this.idTransaction = idTransaction;
      this.idSecuritycurrency = idSecuritycurrency;
      this.tsDate = tsDate;
      this.factorUnits = factorUnits;
      this.idTransactionMargin = idTransactionMargin;
      this.currency = currency;
    }

    @Override
    public Integer getIdTransaction() {
      return idTransaction;
    }

    @Override
    public Integer getIdSecuritycurrency() {
      return idSecuritycurrency;
    }

    @Override
    public LocalDateTime getTsDate() {
      return tsDate;
    }

    @Override
    public Double getFactorUnits() {
      return factorUnits;
    }

    @Override
    public Integer getIdTransactionMargin() {
      return idTransactionMargin;
    }

    @Override
    public String getCurrency() {
      return currency;
    }

  }

  public static interface IHoldSecuritySplitTransactionBySecurity {
    public Integer getIdTenant();

    public Integer getIdPortfolio();

    public Integer getIdSecurityaccount();

    public LocalDateTime getTsDate();

    public Double getFactorUnits();

    public String getTenantCurrency();

    public String getPorfolioCurrency();

    public Integer getIdTransactionMargin();
  }

}
