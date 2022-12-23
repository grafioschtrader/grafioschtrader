package grafioschtrader.repository.dataverification;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.common.DateHelper;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.reports.AccountPositionGroupSummaryReport;
import grafioschtrader.reportviews.account.AccountPositionGrandSummary;
import grafioschtrader.reportviews.account.AccountPositionGroupSummary;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.repository.helper.GroupPortfolio;

/**
 * In GT there are two different ways to calculate the long term performance.
 * One calculates it from first day until a certain day. The second method uses
 * holding tables, this way it may be faster. But the holding tables have a
 * small risk to get outdated.
 */
@SpringBootTest
@Transactional
class CompareHoldingWithOtherSummaryTest {

  @Autowired
  private AccountPositionGroupSummaryReport accountPositionGroupSummaryReport;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

 
  @Test
  @Disabled
  void tenantCompareSummaryTest() {
    DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    LocalDate fromDate = LocalDate.parse("2020-12-30", format);
    LocalDate toDate = LocalDate.parse("2021-02-17", format);
    Integer idTenant = 7;

    List<IPeriodHolding> totalsOverPeriodList = holdSecurityaccountSecurityRepository
        .getPeriodHoldingsByTenant(idTenant, fromDate, toDate);
    compareSummary(fromDate, totalsOverPeriodList, idTenant, null);
  }

  @Test
  @Disabled
  void portfolioCompareSummaryTest() {
    DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    LocalDate fromDate = LocalDate.parse("2020-12-30", format);
    LocalDate toDate = LocalDate.parse("2021-02-17", format);
    Integer idTenant = 7;
    Integer idPortfolio = 6;

    List<IPeriodHolding> totalsOverPeriodList = holdSecurityaccountSecurityRepository
        .getPeriodHoldingsByPortfolio(idPortfolio, fromDate, toDate);
    compareSummary(fromDate, totalsOverPeriodList, idTenant, idPortfolio);

  }

  private void compareSummary(LocalDate fromDate, List<IPeriodHolding> totalsOverPeriodList, Integer idTenant,
      Integer idPortfolio) {
    List<TradingDaysPlus> tradingDays = tradingDaysPlusJpaRepository.findByTradingDateGreaterThanEqual(fromDate);

    for (TradingDaysPlus tdp : tradingDays) {
      Date date = Date.from(tdp.getTradingDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
      Optional<IPeriodHolding> totalsOverPeriodOpt = totalsOverPeriodList.stream()
          .filter(totalsOverPeriod -> totalsOverPeriod.getDate().equals(tdp.getTradingDate())).findFirst();
      if (totalsOverPeriodOpt.isPresent()) {
        IPeriodHolding top = totalsOverPeriodOpt.get();
        if (idPortfolio == null) {
          compareTenantValues(idTenant, date, top);
        } else {
          comparePortfolioValues(idTenant, idPortfolio, date, top);
        }
      }
    }
  }

  private void compareTenantValues(Integer idTenant, Date date, IPeriodHolding top) {
    AccountPositionGrandSummary accountPositionGrandSummary = accountPositionGroupSummaryReport
        .getAccountGrandSummaryIdTenant(idTenant, new GroupPortfolio(), DateHelper.setTimeToZeroAndAddDay(date, 0));

    int error = 0;
    double holdingSecurityMC = top.getSecuritiesMC() + top.getMarginCloseGainMC();
    if (Math.abs(accountPositionGrandSummary.grandValueSecuritiesMC - holdingSecurityMC) > 0.03) {
      System.err.println("Date: " + date + " Value of Securities A:"
          + accountPositionGrandSummary.grandValueSecuritiesMC + " H:" + holdingSecurityMC + " Differenz:"
          + (accountPositionGrandSummary.grandValueSecuritiesMC - holdingSecurityMC));
      error++;
    }
    if (error == 0) {
      System.out.println("Date: " + date);
    }
  }

  private void comparePortfolioValues(Integer idTenant, Integer idPortfolio, Date date, IPeriodHolding top) {
    AccountPositionGroupSummary accountPositionGroupSummary = accountPositionGroupSummaryReport
        .getAccountGrandSummaryPortfolio(idTenant, idPortfolio, DateHelper.setTimeToZeroAndAddDay(date, 0));

    int error = 0;
    double holdingSecurityMC = top.getSecuritiesMC() + top.getMarginCloseGainMC();
    if (Math.abs(accountPositionGroupSummary.groupValueSecuritiesMC - holdingSecurityMC) > 0.03) {
      System.err.println("Date: " + date + " Value of Securities A:"
          + accountPositionGroupSummary.groupValueSecuritiesMC + " H:" + holdingSecurityMC + " Differenz:"
          + (accountPositionGroupSummary.groupValueSecuritiesMC - holdingSecurityMC));
      error++;
    }

    if (error == 0) {
      System.out.println("Date: " + date);
    }
  }

}
