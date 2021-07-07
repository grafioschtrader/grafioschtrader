package grafioschtrader.reports;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;

/**
 * Creates a Report which can be grouped by single field.
 *
 * @author Hugo Graf
 *
 * @param <T>
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

      double currencyExchangeRate = (security.getCurrency().equals(dateCurrencyMap.getMainCurrency())) ? 1.0
          : dateCurrencyMap.isUntilDateEqualNowOrAfter()
              ? dateCurrencyMap.getCurrencypairByFromCurrency(security.getCurrency()).getSLast()
              : dateCurrencyMap.getExactDateAndFromCurrency(dateCurrencyMap.getUntilDate(), security.getCurrency());

      T groupValue = getGroupValue(security);
      SecurityPositionDynamicGroupSummary<T> securityPositionDynamicGroupSummary = groupMap.computeIfAbsent(groupValue,
          gv -> new SecurityPositionDynamicGroupSummary<>(gv));
      securityPositionSummary.calcMainCurrency(currencyExchangeRate);
      securityPositionDynamicGroupSummary.addToGroupSummaryAndCalcGroupTotals(securityPositionSummary);
    }

    return createAndCalcGrandTotal(groupMap, dateCurrencyMap);
  }

  @SuppressWarnings("unchecked")
  protected SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<T>> createAndCalcGrandTotal(
      Map<T, SecurityPositionDynamicGroupSummary<T>> groupMap, DateTransactionCurrencypairMap dateCurrencyMap) {
    final SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<T>> securityPositionGrandSummary = new SecurityPositionDynamicGrandSummary<>(
        dateCurrencyMap.getMainCurrency(),
        globalparametersJpaRepository.getPrecisionForCurrency(dateCurrencyMap.getMainCurrency()));
    for (final Map.Entry<T, SecurityPositionDynamicGroupSummary<T>> ospcs : groupMap.entrySet()) {
      securityPositionGrandSummary.calcGrandTotal(
          (SecurityPositionDynamicGroupSummary<SecurityPositionDynamicGroupSummary<T>>) ospcs.getValue());
    }
    securityPositionGrandSummary.roundGrandTotals();

    return securityPositionGrandSummary;
  }

  @SuppressWarnings("unchecked")
  protected T getGroupValue(Security security)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    return (T) PropertyUtils.getNestedProperty(security, fieldName);
  }

}
