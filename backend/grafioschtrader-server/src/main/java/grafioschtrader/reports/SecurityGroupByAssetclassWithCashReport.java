package grafioschtrader.reports;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.AssetclassType;

/**
 * Comprehensive portfolio report that groups security positions by asset class type and includes cash account holdings
 * as pseudo-securities for complete portfolio allocation analysis. Provides a unified view of both invested assets and
 * liquid cash positions across all currencies, enabling accurate portfolio composition and allocation assessment.
 *
 * <p>
 * This specialized report extends the base grouping functionality to address the critical requirement of including cash
 * holdings in portfolio analysis.
 *
 * <h3>Cash Account Handling:</h3>
 * <p>
 * The report implements sophisticated cash account integration:
 * </p>
 * <ul>
 * <li><strong>Pseudo-Security Creation:</strong> Transforms cash accounts into security-like objects</li>
 * <li><strong>Currency Classification:</strong> Separates main currency from foreign currency cash</li>
 * <li><strong>Balance Calculation:</strong> Accurate cash balances based on transaction history</li>
 * <li><strong>Asset Class Assignment:</strong> Appropriate categorization for reporting consistency</li>
 * <li><strong>Negative ID Convention:</strong> Uses negative security IDs to distinguish cash from securities</li>
 * </ul>
 *
 * <h3>Asset Class Categories:</h3>
 * <p>
 * Cash holdings are automatically classified into appropriate asset class types:
 * </p>
 * <ul>
 * <li><strong>CURRENCY_CASH:</strong> Cash in the portfolio's main reporting currency</li>
 * <li><strong>CURRENCY_FOREIGN:</strong> Cash in foreign currencies requiring conversion</li>
 * <li><strong>Traditional Asset Classes:</strong> Securities grouped by their natural classifications</li>
 * </ul>
 */
@Component
public class SecurityGroupByAssetclassWithCashReport extends SecurityGroupByBaseReport<AssetclassType> {

  public SecurityGroupByAssetclassWithCashReport() {
    super(SecurityGroupByBaseReport.ASSETCLASS_CATEGORY_FIELD_NAME);
  }

  @Override
  protected boolean includeEmptyCashAccounts() {
    return true;
  }

  @Override
  protected SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<AssetclassType>> createGroupsAndCalcGrandTotal(
      final Tenant tenant, List<SecurityPositionSummary> securityPositionSummaryList,
      DateTransactionCurrencypairMap dateCurrencyMap) throws Exception {
    return createGroupsAndCalcGrandTotal(tenant, securityPositionSummaryList, dateCurrencyMap, null);
  }

  @Override
  protected SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<AssetclassType>> createGroupsAndCalcGrandTotal(
      Tenant tenant, List<SecurityPositionSummary> positions, DateTransactionCurrencypairMap currencyMap,
      Integer idPortfolio) throws Exception {
    addCashaccountAsASecurity(tenant, positions, currencyMap, idPortfolio);
    var summary = super.createGroupsAndCalcGrandTotal(tenant, positions, currencyMap);
    summary.holdingsTreemap = HoldingsTreemapBuilder.build(assetclassGroups(summary), currencyMap.getMainCurrency(),
        globalparametersService.getPrecisionForCurrency(currencyMap.getMainCurrency()),
        summary.grandAccountValueSecurityMC);
    return summary;
  }

  @SuppressWarnings("unchecked")
  private List<SecurityPositionDynamicGroupSummary<AssetclassType>> assetclassGroups(
      SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<AssetclassType>> summary) {
    return summary.securityPositionGroupSummaryList.stream()
        .map(group -> (SecurityPositionDynamicGroupSummary<AssetclassType>) group).toList();
  }

  /** Historical valuations use only rates up to the cutoff, also on non-trading days and in portfolio currency. */
  @Override
  protected Map<String, Double> reportExchangeRates(List<SecurityPositionSummary> positions,
      DateTransactionCurrencypairMap currencyMap) {
    Map<String, Double> rates = new HashMap<>();
    rates.put(currencyMap.getMainCurrency(), 1.0);
    var currencies = positions.stream().map(p -> p.getSecurity().getCurrency()).distinct().toList();
    var pairs = currencyMap.getCurrencypairFromToCurrencyMap().values().stream()
        .filter(
            p -> p.getToCurrency().equals(currencyMap.getMainCurrency()) && currencies.contains(p.getFromCurrency()))
        .toList();
    if (!currencyMap.isUntilDateEqualNowOrAfter()) {
      if (!pairs.isEmpty()) {
        var quotes = historyquoteJpaRepository.getIdDateCloseByIdsAndDate(
            pairs.stream().map(p -> p.getIdSecuritycurrency()).toList(), currencyMap.getUntilDate());
        quotes.forEach(q -> pairs.stream().filter(p -> p.getIdSecuritycurrency().equals(q.getIdSecuritycurrency()))
            .findFirst().ifPresent(p -> rates.put(p.getFromCurrency(), q.getClose())));
      }
    } else {
      pairs.stream().filter(p -> p.getSLast() != null).forEach(p -> rates.put(p.getFromCurrency(), p.getSLast()));
    }
    rates.entrySet().removeIf(e -> !Double.isFinite(e.getValue()) || e.getValue() <= 0);
    return rates;
  }

}
