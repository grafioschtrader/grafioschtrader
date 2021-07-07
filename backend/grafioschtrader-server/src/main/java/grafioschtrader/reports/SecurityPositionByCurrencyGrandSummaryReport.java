package grafioschtrader.reports;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionCurrenyGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/**
 * Creates the report for the security accounts. It supports the tree levels,
 * that means all security accounts of a tenant or one or more security account
 * for a portfolio and a certain security account.
 *
 *
 * @author Hugo Graf
 */
@Component
public class SecurityPositionByCurrencyGrandSummaryReport extends SecurityPositionSummaryReport {

  @Autowired
  protected HistoryquoteJpaRepository historyquoteJpaRepository;

  private final SecurityCashaccountGroupByCurrencyBaseReport securityCashaccountGroupByCurrencyBaseReport;

  public SecurityPositionByCurrencyGrandSummaryReport(TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository,
      Map<String, Integer> currencyPrecisionMap) {
    securityCashaccountGroupByCurrencyBaseReport = new SecurityCashaccountGroupByCurrencyBaseReport(
        tradingDaysPlusJpaRepository, currencyPrecisionMap);
  }

  /**
   * Create totals for every single currency
   *
   * @param securityPositionSummary
   * @return
   */

  @Override
  public SecurityPositionGrandSummary createGroupsAndCalcGrandTotal(final Tenant tenant,
      final List<SecurityPositionSummary> securityPositionSummaryList,
      final DateTransactionCurrencypairMap dateCurrencyMap) {
    final Map<String, SecurityPositionCurrenyGroupSummary> currencyTotalMap = securityCashaccountGroupByCurrencyBaseReport
        .createAndCalcSubtotalsPerCurrency(historyquoteJpaRepository, securityPositionSummaryList, dateCurrencyMap);
    final SecurityPositionGrandSummary securityPositionGrandSummary = new SecurityPositionGrandSummary(
        dateCurrencyMap.getMainCurrency(),
        globalparametersJpaRepository.getPrecisionForCurrency(dateCurrencyMap.getMainCurrency()));
    for (final Map.Entry<String, SecurityPositionCurrenyGroupSummary> ospcs : currencyTotalMap.entrySet()) {
      securityPositionGrandSummary.calcGrandTotal(ospcs.getValue());
    }

    return securityPositionGrandSummary;
  }

}
