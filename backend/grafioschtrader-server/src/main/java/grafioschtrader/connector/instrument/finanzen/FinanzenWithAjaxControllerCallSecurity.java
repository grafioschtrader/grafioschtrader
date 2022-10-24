package grafioschtrader.connector.instrument.finanzen;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Security;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/*
 *
 * For every call it needs a new instance of this class.
 *
 * ETF:
 * Xtrackers CSI300 Swap UCITS ETF 1C (LU0779800910)
 * https://www.finanzen.net/etf/historisch/xtrackers_csi300_swap_ucits_etf_1c";
 * https://www.finanzen.net/ajax/FundController_HistoricPriceList/xtrackers_csi300_swap_ucits_etf_1c/FSE/1.1.2018_10.10.2018
 * Link: etf/historisch/xtrackers_csi300_swap_ucits_etf_1c
 *
 * Stock:
 * Swiss Re  (CH0126881561)
 * https://www.finanzen.ch/kurse/historisch/swiss_re/swl
 * https://www.finanzen.ch/Ajax/SharesController_HistoricPriceList/swiss_re/SWL/23.4.2018_23.11.2018
 * Link: kurse/historisch/Swiss_Re/SWL
 *
 * https://www.finanzen.net/Ajax/IndicesController_HistoricPriceList/S&P_500/08.01.2004_07.10.2018/false
 * https://www.finanzen.net/Ajax/IndicesController_HistoricPriceList/S&P_500/1.9.2004_10.10.2018/false
 * https://www.finanzen.net/Ajax/IndicesController_HistoricPriceList/S&P_500/08.01.2000_07.10.2018/false
 *
 * Indices (TecDAX)
 * https://www.finanzen.net/index/TECDAX/Historisch
 * https://www.finanzen.net/Ajax/IndicesController_HistoricPriceList/TECDAX/1.9.2018_10.10.2018/false
 * Link: TECDAX/Historisch
 *
 * Fond (Schroder ISF Global Diversified Growth USD Hedged C Acc):
 * https://www.finanzen.net/fonds/historisch/schroder_isf_global_diversified_growth_usd_hedged_c_acc
 * https://www.finanzen.net/ajax/FundController_HistoricPriceList/schroder_isf_global_diversified_growth_usd_hedged_c_acc/FII/1.1.2018_10.10.2018
 * Link: fonds/historisch/schroder_isf_global_diversified_growth_usd_hedged_c_acc
 *
 * @author Hugo Graf
 *
 */
public class FinanzenWithAjaxControllerCallSecurity extends FinanzenWithAjaxControllerCall<Security> {

  public static String HISTORICAL_PRICE_LIST = "HistoricPriceList";
  public static String HISTORICAL_PRICE_LIST_REDESIGN = HISTORICAL_PRICE_LIST + "Redesign";

  protected static Map<ControllerUrlMapping, String> contollerUrlMapping;
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  static {
    contollerUrlMapping = new ConcurrentHashMap<>();
    contollerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        "BondController");
    contollerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.CONVERTIBLE_BOND, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        "BondController");
    contollerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        "BondController");
    contollerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        "SharesController");
    contollerUrlMapping.put(new ControllerUrlMapping(AssetclassType.COMMODITIES, SpecialInvestmentInstruments.ETF),
        "FundController");
    contollerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.COMMODITIES, SpecialInvestmentInstruments.NON_INVESTABLE_INDICES),
        "CommodityController");
    contollerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.COMMODITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        "CommodityController");
    contollerUrlMapping.put(new ControllerUrlMapping(AssetclassType.COMMODITIES, SpecialInvestmentInstruments.CFD),
        "CommodityController");
    contollerUrlMapping.put(new ControllerUrlMapping(AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF),
        "FundController");
    contollerUrlMapping.put(new ControllerUrlMapping(AssetclassType.MONEY_MARKET, SpecialInvestmentInstruments.ETF),
        "FundController");
    contollerUrlMapping.put(new ControllerUrlMapping(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.ETF),
        "FundController");
    contollerUrlMapping.put(new ControllerUrlMapping(AssetclassType.REAL_ESTATE, SpecialInvestmentInstruments.ETF),
        "FundController");
    contollerUrlMapping.put(new ControllerUrlMapping(AssetclassType.EQUITIES, SpecialInvestmentInstruments.MUTUAL_FUND),
        "FundController");
    contollerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.MONEY_MARKET, SpecialInvestmentInstruments.MUTUAL_FUND),
        "FundController");
    contollerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.MUTUAL_FUND),
        "FundController");
    contollerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.REAL_ESTATE, SpecialInvestmentInstruments.MUTUAL_FUND),
        "FundController");
    contollerUrlMapping.put(new ControllerUrlMapping(null, SpecialInvestmentInstruments.NON_INVESTABLE_INDICES),
        "IndicesController");
    contollerUrlMapping.put(new ControllerUrlMapping(null, SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT),
        "DerivativesController");
  }

  private UseLastPartUrl useLastPartUrl;
  private int indicesNameUrlPart;
  private String controller;

  /**
   *
   *
   * @param domain
   * @param feedConnector
   * @param locale
   * @param useLastPartUrl
   */
  public FinanzenWithAjaxControllerCallSecurity(String domain, String controller, IFeedConnector feedConnector,
      Locale locale, UseLastPartUrl useLastPartUrl, int indicesNameUrlPart) {
    super(domain, feedConnector, locale);
    this.controller = controller;
    this.useLastPartUrl = useLastPartUrl;
    this.indicesNameUrlPart = indicesNameUrlPart;
  }

  @Override
  protected String getAjaxUrl(final Security security, final Date from, final Date to) {
    String url;
    String[] possibleTwoPartUrl = security.getUrlHistoryExtend().split(Pattern.quote("|"));

    SpecialInvestmentInstruments sii = security.getAssetClass().getSpecialInvestmentInstrument();
    if (possibleTwoPartUrl.length == 1) {
      if (sii == SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT) {
        url = getAjaxController(security, from, to, sii,
            security.getIsin() + "/"
                + FinanzenHelper.getCertificateMappedStockexchangeSymbol(security.getStockexchange().getMic()) + "/",
            "");
      } else {
        url = getOnePartAjaxUrl(security, from, to, sii);
      }
    } else {
      // example with 2: "rohstoffe/goldpreis/historisch|goldpreis/CHF"
      // example with 3: "rohstoffe/oelpreis/historisch|oelpreis/USD|?type=Brent"
      String suffix = possibleTwoPartUrl.length == 3 ? possibleTwoPartUrl[2] : "";
      url = getAjaxController(security, from, to, sii, possibleTwoPartUrl[1] + "/" + "", suffix);
    }
    log.info("In {} for security {} is URL for Ajax call {}", feedConnector.getID(), security.getName(), url);
    return url;
  }

  private String getOnePartAjaxUrl(final Security security, final Date from, final Date to,
      SpecialInvestmentInstruments sii) {
    String urlExtendedParts[] = security.getUrlHistoryExtend().split("/");
    String productName = (sii == SpecialInvestmentInstruments.NON_INVESTABLE_INDICES)
        ? urlExtendedParts[indicesNameUrlPart]
        : urlExtendedParts[2];
    productName = (security.getAssetClass().getCategoryType() == AssetclassType.FIXED_INCOME)
        ? productName.replaceAll("\\-anleihe$", "")
        : productName;
    String stockExchange = "";
    if (useLastPartUrl == UseLastPartUrl.AS_STOCKEXCHANGE_SYMBOL
        && sii != SpecialInvestmentInstruments.NON_INVESTABLE_INDICES) {
      stockExchange = urlExtendedParts[urlExtendedParts.length - 1] + "/";
    } else {
      // TODO Not working for Funds
      stockExchange = (sii != SpecialInvestmentInstruments.NON_INVESTABLE_INDICES)
          ? (FinanzenHelper.getNormalMappedStockexchangeSymbol(security.getStockexchange().getMic()) + "/")
          : "";
    }
    String suffix = (sii == SpecialInvestmentInstruments.NON_INVESTABLE_INDICES) ? "/false" : "";
    return getAjaxController(security, from, to, sii, productName + "/" + stockExchange, suffix);
  }

  private String getAjaxController(Security security, final Date from, final Date to, SpecialInvestmentInstruments sii,
      String productStockExchangeStr, String suffix) {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(URL_DATE_FORMAT);
    String ajaxController = contollerUrlMapping.computeIfAbsent(
        new ControllerUrlMapping(security.getAssetClass().getCategoryType(), sii),
        f -> contollerUrlMapping.computeIfAbsent(new ControllerUrlMapping(null, sii), c -> DEFAULT_URL_CONTROLLER));

    return domain + "Ajax/" + ajaxController + "_" + controller + "/" + productStockExchangeStr
        + dateFormat.format(from) + "_" + dateFormat.format(to) + suffix;
  }

  @Override
  protected String getHistoricalDownloadLink(Security security) {
    return feedConnector.getSecurityHistoricalDownloadLink(security);
  }

}