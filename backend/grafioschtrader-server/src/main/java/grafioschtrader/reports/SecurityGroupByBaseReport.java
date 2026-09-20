package grafioschtrader.reports;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.AssetclassType;

/**
 * Generic security position report generator that enables dynamic grouping by any single field within the Security
 * entity hierarchy. Provides flexible portfolio analysis by allowing users to organize position data according to
 * various classification criteria such as asset class, investment instrument type, or custom security attributes.
 *
 * <p>
 * This class extends the base security position reporting functionality with sophisticated reflection-based field
 * access, enabling runtime specification of grouping criteria without requiring compile-time knowledge of the specific
 * field structure. It supports nested property access using Apache Commons BeanUtils for maximum flexibility.
 * </p>
 *
 * <h3>Common Grouping Scenarios:</h3>
 * <p>
 * The class provides predefined field constants for frequently used grouping criteria:
 * </p>
 * <ul>
 * <li><strong>Asset Class Category:</strong> Groups by broad investment categories (stocks, bonds, etc.)</li>
 * <li><strong>Investment Instrument:</strong> Groups by specific instrument types within asset classes</li>
 * <li><strong>Asset Class ID:</strong> Groups by unique asset class identifiers for detailed analysis</li>
 * </ul>
 *
 * @param <T> the type of the grouping field value, enabling type-safe group key management and ensuring consistent
 *            typing across the grouping hierarchy
 */
public class SecurityGroupByBaseReport<T> extends SecurityPositionSummaryReport {
  public String fieldName;
  public String value;
  public static String ASSETCLASS_CATEGORY_FIELD_NAME = "assetClass.categoryType";
  public static String ASSETCLASS_SPEC_INVEST_INST_FIELD_NAME = "assetClass.specialInvestmentInstrument";
  public static String ASSETCLASS_ID_ASSETCLASS_FIELD_NAME = "assetClass.idAssetClass";

  public SecurityGroupByBaseReport(String fieldName) {
    super();
    this.fieldName = fieldName;
  }

  @Override
  protected SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<T>> createGroupsAndCalcGrandTotal(
      final Tenant tenant, List<SecurityPositionSummary> securityPositionSummaryList,
      DateTransactionCurrencypairMap dateCurrencyMap) throws Exception {
    Map<T, SecurityPositionDynamicGroupSummary<T>> groupMap = new HashMap<>();

    ReportHelper.loadUntilDateHistoryquotes(historyquoteJpaRepository, dateCurrencyMap);

    for (final SecurityPositionSummary securityPositionSummary : securityPositionSummaryList) {
      Security security = securityPositionSummary.getSecurity();

      if (security.getIdSecuritycurrency() < 0 && securityPositionSummary.valueSecurity == 0) {
        continue;
      }
      double currencyExchangeRate = ReportHelper.getReportExchangeRate(security.getCurrency(), dateCurrencyMap,
          tradingDaysPlusJpaRepository);

      T groupValue = getGroupValue(security);
      SecurityPositionDynamicGroupSummary<T> securityPositionDynamicGroupSummary = groupMap.computeIfAbsent(groupValue,
          gv -> new SecurityPositionDynamicGroupSummary<>(gv));
      securityPositionSummary.calcMainCurrency(currencyExchangeRate);
      securityPositionDynamicGroupSummary.addToGroupSummaryAndCalcGroupTotals(securityPositionSummary);
    }
    return createAndCalcGrandTotal(groupMap, dateCurrencyMap);
  }

  /**
   * Creates the final grand summary by aggregating all group summaries and calculating comprehensive totals across the
   * entire portfolio. This method produces the top-level summary structure that provides both detailed group-by-group
   * analysis and overall portfolio totals for complete investment analysis.
   *
   * @param groupMap        the map of grouped position summaries organized by field value
   * @param dateCurrencyMap currency context for precision settings and final calculations
   * @return comprehensive grand summary with grouped positions and portfolio-wide totals
   */
  @SuppressWarnings("unchecked")
  protected SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<T>> createAndCalcGrandTotal(
      Map<T, SecurityPositionDynamicGroupSummary<T>> groupMap, DateTransactionCurrencypairMap dateCurrencyMap) {
    final SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<T>> securityPositionGrandSummary = new SecurityPositionDynamicGrandSummary<>(
        dateCurrencyMap.getMainCurrency(),
        globalparametersService.getPrecisionForCurrency(dateCurrencyMap.getMainCurrency()));
    for (final Map.Entry<T, SecurityPositionDynamicGroupSummary<T>> ospcs : groupMap.entrySet()) {
      securityPositionGrandSummary.calcGrandTotal(
          (SecurityPositionDynamicGroupSummary<SecurityPositionDynamicGroupSummary<T>>) ospcs.getValue());
    }
    securityPositionGrandSummary.roundGrandTotals();

    return securityPositionGrandSummary;
  }

  /**
   * Transforms cash accounts into pseudo-security representations for inclusion in asset class-based portfolio
   * analysis. This sophisticated process creates Security-like objects for each cash account, enabling unified
   * treatment of both invested assets and liquid cash within the portfolio reporting framework.
   *
   * <p>
   * The transformation process implements several critical features:
   * </p>
   *
   * <h4>Asset Class Assignment Strategy:</h4>
   * <ul>
   * <li><strong>Main Currency Cash:</strong> Assigned to CURRENCY_CASH asset class for domestic currency liquidity
   * tracking</li>
   * <li><strong>Foreign Currency Cash:</strong> Assigned to CURRENCY_FOREIGN asset class for international exposure and
   * currency risk assessment</li>
   * </ul>
   *
   * <h4>Pseudo-Security Creation:</h4>
   * <p>
   * Each cash account is transformed into a Security object with:
   * </p>
   * <ul>
   * <li><strong>Negative ID Convention:</strong> Uses negative cash account ID to distinguish from real securities and
   * prevent ID conflicts</li>
   * <li><strong>Descriptive Naming:</strong> Preserves cash account name for clear identification in reports and
   * drill-down analysis</li>
   * </ul>
   *
   * <h4>Balance Calculation:</h4>
   * <p>
   * Implements accurate balance determination using transaction-based calculation rather than cached balances, ensuring
   * temporal consistency with the report's date parameters. The calculation includes all transactions up to and
   * including the report date, providing accurate point-in-time cash position representation.
   * </p>
   *
   * <h4>Integration with Portfolio Structure:</h4>
   * <p>
   * Processes all portfolios within the tenant's structure, ensuring comprehensive coverage of cash positions across
   * different investment accounts and strategies. Each cash account is processed individually to maintain granular
   * visibility while enabling aggregation at the asset class level.
   * </p>
   *
   * <p>
   * The resulting pseudo-securities integrate seamlessly with the base reporting infrastructure, participating in
   * currency normalization, precision handling, and hierarchical aggregation alongside traditional securities.
   * </p>
   *
   * @param tenant                      the tenant whose portfolios contain cash accounts to be processed
   * @param securityPositionSummaryList the list to be augmented with cash position summaries
   * @param dateCurrencyMap             currency context and date parameters for balance calculation
   */
  protected void addCashaccountAsASecurity(final Tenant tenant,
      List<SecurityPositionSummary> securityPositionSummaryList, DateTransactionCurrencypairMap dateCurrencyMap) {
    Assetclass assetclassMainCurrency = new Assetclass();
    assetclassMainCurrency.setCategoryType(AssetclassType.CURRENCY_CASH);
    Assetclass assetclassForeignCurrency = new Assetclass();
    assetclassForeignCurrency.setCategoryType(AssetclassType.CURRENCY_FOREIGN);
    LocalDate untilDatePlus = dateCurrencyMap.getUntilDate().plusDays(1);

    tenant.getPortfolioList().forEach(portfolio -> {
      portfolio.getCashaccountList().forEach(cashaccount -> {
        Security security = new Security();
        security.setIdSecuritycurrency(cashaccount.getId() * -1);
        security.setName(cashaccount.getName());
        security.setCurrency(cashaccount.getCurrency());
        SecurityPositionSummary securityPositionSummary = new SecurityPositionSummary(dateCurrencyMap.getMainCurrency(),
            security, globalparametersService.getCurrencyPrecision());
        securityPositionSummaryList.add(securityPositionSummary);

        securityPositionSummary.valueSecurity = cashaccount
            .calculateBalanceOnTransactions(untilDatePlus.atStartOfDay());
        // securityPositionSummary.valueSecurity = cashaccount.getBalance();
        if (cashaccount.getCurrency().equals(dateCurrencyMap.getMainCurrency())) {
          security.setAssetClass(assetclassMainCurrency);
        } else {
          security.setAssetClass(assetclassForeignCurrency);
        }
      });
    });
  }

  @SuppressWarnings("unchecked")
  protected T getGroupValue(Security security)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    return (T) PropertyUtils.getNestedProperty(security, fieldName);
  }

}
