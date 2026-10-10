package grafioschtrader.repository;

import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import grafioschtrader.entities.HoldDailyTotal;
import grafioschtrader.entities.HoldDailyTotalState;
import grafioschtrader.reportviews.performance.IPeriodHolding;

/**
 * Reads the daily total value of a tenant or portfolio and attaches the marker of the tenant, which is kept in
 * {@code hold_daily_total_state}. Writes the rows of the task as a JDBC batch.
 */
public class HoldDailyTotalJpaRepositoryImpl implements HoldDailyTotalJpaRepositoryCustom {

  /** Rows sent to the database per JDBC batch. */
  private static final int BATCH_SIZE = 1000;

  private static final String INSERT_SQL = "INSERT INTO " + HoldDailyTotal.TABNAME
      + " (id_tenant, id_portfolio, hold_date, cash_balance_mc, securities_mc, margin_close_gain_mc, security_risk_mc,"
      + " external_cash_transfer_mc, dividend_real_mc, fee_real_mc, interest_cashaccount_real_mc, accumulate_reduce_mc,"
      + " gain_mc) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  @Autowired
  private HoldDailyTotalJpaRepository holdDailyTotalJpaRepository;

  @Autowired
  private HoldDailyTotalStateJpaRepository holdDailyTotalStateJpaRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Override
  public HoldDailyTotalSeries getSeries(Integer idTenant, Integer idPortfolio, LocalDate fromDate, LocalDate toDate) {
    List<HoldDailyTotal> rows = idPortfolio == null
        ? holdDailyTotalJpaRepository.findByIdTenantAndIdPortfolioIsNullAndHoldDateBetweenOrderByHoldDate(idTenant,
            fromDate, toDate)
        : holdDailyTotalJpaRepository.findByIdTenantAndIdPortfolioAndHoldDateBetweenOrderByHoldDate(idTenant,
            idPortfolio, fromDate, toDate);
    return new HoldDailyTotalSeries(rows, getRecalcFromDate(idTenant));
  }

  @Override
  public HoldDailyTotalValue getLastOnOrBefore(Integer idTenant, Integer idPortfolio, LocalDate date) {
    HoldDailyTotal row = (idPortfolio == null
        ? holdDailyTotalJpaRepository
            .findFirstByIdTenantAndIdPortfolioIsNullAndHoldDateLessThanEqualOrderByHoldDateDesc(idTenant, date)
        : holdDailyTotalJpaRepository
            .findFirstByIdTenantAndIdPortfolioAndHoldDateLessThanEqualOrderByHoldDateDesc(idTenant, idPortfolio, date))
                .orElse(null);
    return new HoldDailyTotalValue(row, getRecalcFromDate(idTenant));
  }

  @Override
  public int insertRows(Integer idTenant, Integer idPortfolio, List<? extends IPeriodHolding> holdings) {
    if (holdings.isEmpty()) {
      return 0;
    }
    jdbcTemplate.batchUpdate(INSERT_SQL, holdings, BATCH_SIZE, (ps, h) -> {
      ps.setInt(1, idTenant);
      ps.setObject(2, idPortfolio, Types.INTEGER);
      ps.setDate(3, Date.valueOf(h.getDate()));
      ps.setDouble(4, h.getCashBalanceMC());
      ps.setDouble(5, h.getSecuritiesMC());
      ps.setDouble(6, h.getMarginCloseGainMC());
      ps.setDouble(7, h.getSecurityRiskMC());
      ps.setDouble(8, h.getExternalCashTransferMC());
      ps.setDouble(9, h.getDividendRealMC());
      ps.setDouble(10, h.getFeeRealMC());
      ps.setDouble(11, h.getInterestCashaccountRealMC());
      ps.setDouble(12, h.getAccumulateReduceMC());
      ps.setDouble(13, h.getGainMC());
    });
    return holdings.size();
  }

  private LocalDate getRecalcFromDate(Integer idTenant) {
    return holdDailyTotalStateJpaRepository.findById(idTenant).map(HoldDailyTotalState::getRecalcFromDate).orElse(null);
  }
}
