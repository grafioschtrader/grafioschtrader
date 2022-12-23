package grafioschtrader.reports.dataverification;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.common.DateHelper;
import grafioschtrader.reports.AccountPositionGroupSummaryReport;
import grafioschtrader.reportviews.account.AccountPositionGrandSummary;
import grafioschtrader.reportviews.account.AccountPositionGroupSummary;
import grafioschtrader.repository.helper.GroupPortfolio;
import grafioschtrader.test.start.GTforTest;


/**
 * TODO The following checks are very helpfull for checking the summaries of cash account and security accounts.
 * It should be integrated in integration test.
 *
 */
@SpringBootTest(classes = GTforTest.class)
@Transactional
class AccountPositionGroupSummaryReportTest {

  @Autowired
  private AccountPositionGroupSummaryReport accountPositionGroupSummaryReport;

  @Test
  @Disabled
  void getAccountGrandSummaryPortfolioTest() throws ParseException {
    final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
    Date startDate = dateFormat.parse("01.03.2020");
    final Date finishDate = dateFormat.parse("19.02.2021");

    do {
      startDate = DateHelper.setTimeToZeroAndAddDay(startDate, 1);
      final AccountPositionGroupSummary apgs = accountPositionGroupSummaryReport.getAccountGrandSummaryPortfolio(7,
          8, startDate);
      final double calculated = apgs.groupExternalCashTransferMC + apgs.groupAccountFeesMC * -1
          + apgs.groupCashAccountTransactionFeeMC * -1 + apgs.groupAccountInterestMC + apgs.groupGainLossCurrencyMC
          + apgs.groupGainLossSecuritiesMC;
      if(Math.abs(apgs.groupValueMC - calculated) > 5.00) {
        System.out.println(apgs);
        System.err.println(dateFormat.format(startDate) + " " + (apgs.groupValueMC - calculated));
      }

    } while (startDate.before(finishDate));
  }

  
  @Test
  @Disabled
  void getAccountGrandSummaryIdTenantTest() throws ParseException {
    final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
    Date startDate = dateFormat.parse("28.12.2020");
    final Date finishDate = dateFormat.parse("19.02.2021");
    do {
      startDate = DateHelper.setTimeToZeroAndAddDay(startDate, 1);
      final AccountPositionGrandSummary apgs = accountPositionGroupSummaryReport.getAccountGrandSummaryIdTenant(7,
          new GroupPortfolio(), startDate);
      final double calculated = apgs.grandExternalCashTransferMC + apgs.grandAccountFeesMC * -1
          + apgs.grandCashAccountTransactionFeeMC * -1 + apgs.grandAccountInterestMC + apgs.grandGainLossCurrencyMC
          + apgs.grandGainLossSecuritiesMC;
      if (Math.abs(apgs.grandValueMC - calculated) > 0.05) {
        System.out.println(apgs);
        System.err.println(dateFormat.format(startDate) + " " + (apgs.grandValueMC - calculated));
      }

    } while (startDate.before(finishDate));
  }

}
