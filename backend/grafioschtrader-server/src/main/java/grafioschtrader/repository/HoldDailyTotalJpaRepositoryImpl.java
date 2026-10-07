package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.HoldDailyTotal;
import grafioschtrader.entities.HoldDailyTotalState;

/**
 * Reads the daily total value of a tenant or portfolio and attaches the marker of the tenant, which is kept in
 * {@code hold_daily_total_state}.
 */
public class HoldDailyTotalJpaRepositoryImpl implements HoldDailyTotalJpaRepositoryCustom {

  @Autowired
  private HoldDailyTotalJpaRepository holdDailyTotalJpaRepository;

  @Autowired
  private HoldDailyTotalStateJpaRepository holdDailyTotalStateJpaRepository;

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

  private LocalDate getRecalcFromDate(Integer idTenant) {
    return holdDailyTotalStateJpaRepository.findById(idTenant).map(HoldDailyTotalState::getRecalcFromDate).orElse(null);
  }
}
