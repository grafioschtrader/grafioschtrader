package grafioschtrader.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafioschtrader.dto.HistoryquoteChartResponse;
import grafioschtrader.entities.BaseHistoryquote;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.RiskFreeRateMappingJpaRepository;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Rounds the prices of index levels to the precision of their currency ({@code gt.currency.precision}) when they are
 * delivered to the user interface. A connector often supplies an index with many more fraction digits than the index is
 * published with, and showing them all suggests an accuracy that does not exist.
 *
 * <p>
 * Only the response is rounded; the stored intraday and historical prices keep their full precision, so calculations,
 * exports and GTNet are unaffected. Two kinds of instrument are left alone: everything that is not a
 * {@link SpecialInvestmentInstruments#NON_INVESTABLE_INDICES}, and the securities that serve as the risk-free rate of a
 * currency. The latter are indices too, but their quotes are interest rates rather than levels; with a currency
 * precision of zero (JPY) the rate would otherwise collapse to zero.
 * </p>
 */
@Service
public class IndexPriceRoundingService {

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private RiskFreeRateMappingJpaRepository riskFreeRateMappingJpaRepository;

  /**
   * Determines the number of fraction digits the prices of a single security are delivered with.
   *
   * @param security the security, may be null
   * @return the precision of the security currency for a rounded index level, null when the prices keep their full
   *         precision
   */
  public Integer resolvePriceFractionDigits(Security security) {
    if (!isIndexLevel(security)
        || riskFreeRateMappingJpaRepository.existsByIdSecuritycurrency(security.getIdSecuritycurrency())) {
      return null;
    }
    return globalparametersService.getPrecisionForCurrency(security.getCurrency());
  }

  /**
   * Marks every index level of the collection so that its intraday prices are delivered rounded to its currency
   * precision. Must be the last step before the response is returned, because the price getters return rounded values
   * afterwards. The precision setting and the risk-free securities are read once for the whole collection.
   *
   * @param securities the securities of the response
   */
  public void applyToSecurities(Collection<Security> securities) {
    if (securities.stream().noneMatch(IndexPriceRoundingService::isIndexLevel)) {
      return;
    }
    final Map<String, Integer> currencyPrecision = globalparametersService.getCurrencyPrecision();
    final Set<Integer> riskFreeIds = riskFreeRateMappingJpaRepository.findAllIdSecuritycurrency();
    securities.stream()
        .filter(security -> isIndexLevel(security) && !riskFreeIds.contains(security.getIdSecuritycurrency()))
        .forEach(security -> security.setPriceFractionDigits(
            currencyPrecision.getOrDefault(security.getCurrency(), BaseConstants.FID_STANDARD_FRACTION_DIGITS)));
  }

  /**
   * Rounds open, high, low and close of history quotes. The quotes must be detached from the persistence context,
   * otherwise the rounded values would be written back.
   *
   * @param historyquotes       detached history quotes of one instrument
   * @param priceFractionDigits the number of fraction digits
   */
  public void roundHistoryquotes(List<? extends BaseHistoryquote> historyquotes, int priceFractionDigits) {
    historyquotes.forEach(hq -> {
      hq.setOpen(round(hq.getOpen(), priceFractionDigits));
      hq.setHigh(round(hq.getHigh(), priceFractionDigits));
      hq.setLow(round(hq.getLow(), priceFractionDigits));
      hq.setClose(DataHelper.round(hq.getClose(), priceFractionDigits));
    });
  }

  /**
   * Rounds the price series of a chart response, whichever of its two shapes is populated.
   *
   * @param chartResponse       the chart data of one instrument
   * @param priceFractionDigits the number of fraction digits
   */
  public void roundChart(HistoryquoteChartResponse chartResponse, int priceFractionDigits) {
    if (chartResponse.getOhlcList() != null) {
      chartResponse.getOhlcList().forEach(ohlc -> {
        ohlc.setOpen(round(ohlc.getOpen(), priceFractionDigits));
        ohlc.setHigh(round(ohlc.getHigh(), priceFractionDigits));
        ohlc.setLow(round(ohlc.getLow(), priceFractionDigits));
        ohlc.setClose(round(ohlc.getClose(), priceFractionDigits));
      });
    }
    if (chartResponse.getDateCloseList() != null) {
      chartResponse.getDateCloseList()
          .forEach(dateClose -> dateClose.setClose(round(dateClose.getClose(), priceFractionDigits)));
    }
  }

  private static boolean isIndexLevel(Security security) {
    return security != null && security.getAssetClass() != null && security.getAssetClass()
        .getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.NON_INVESTABLE_INDICES;
  }

  private static Double round(Double value, int priceFractionDigits) {
    return value == null ? null : DataHelper.round(value, priceFractionDigits);
  }
}
