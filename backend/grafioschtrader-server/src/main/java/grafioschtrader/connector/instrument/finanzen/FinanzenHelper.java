package grafioschtrader.connector.instrument.finanzen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Security;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class FinanzenHelper {
  
  private static final Map<ControllerUrlMapping, ControllerAssetClass> controllerUrlMapping = new ConcurrentHashMap<>();

  // intra‑URL segments
  private static final String INTRA_AKTIE = "aktien";
  private static final String INTRA_BOND = "obligationen";
  private static final String INTRA_ETF = "etf";
  private static final String INTRA_FUND = "fonds";
  private static final String INTRA_DERIVATE = "derivate";
  private static final String INTRA_INDEX = "index";
  private static final String INTRA_DEFAULT = INTRA_FUND;

  // history controllers
  public static final String HIST_SUFFIX = "_HistoricPriceList/";
  private static final String HIST_FUND_CONTROLLER = "FundController";
  private static final String HIST_COMMODITY_CONTROLLER = "CommodityController";
  private static final String HIST_BOND_CONTROLLER = "BondController";
  private static final String HIST_DEFAULT_CONTROLLER = HIST_FUND_CONTROLLER;

  static {
    // — bonds & convertibles
    controllerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        new ControllerAssetClass(HIST_BOND_CONTROLLER, INTRA_BOND));
    controllerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.CONVERTIBLE_BOND, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        new ControllerAssetClass(HIST_BOND_CONTROLLER, INTRA_BOND));

    // — equities
    controllerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        new ControllerAssetClass("SharesController", INTRA_AKTIE));
    controllerUrlMapping.put(new ControllerUrlMapping(AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF),
        new ControllerAssetClass(HIST_FUND_CONTROLLER, INTRA_ETF));
    controllerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.EQUITIES, SpecialInvestmentInstruments.MUTUAL_FUND),
        new ControllerAssetClass(HIST_FUND_CONTROLLER, INTRA_FUND));

    // — commodities
    controllerUrlMapping.put(new ControllerUrlMapping(AssetclassType.COMMODITIES, SpecialInvestmentInstruments.ETF),
        new ControllerAssetClass(HIST_FUND_CONTROLLER, INTRA_ETF));
    controllerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.COMMODITIES, SpecialInvestmentInstruments.NON_INVESTABLE_INDICES),
        new ControllerAssetClass(HIST_COMMODITY_CONTROLLER, INTRA_INDEX));
    controllerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.COMMODITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT),
        new ControllerAssetClass(HIST_COMMODITY_CONTROLLER, null));
    controllerUrlMapping.put(new ControllerUrlMapping(AssetclassType.COMMODITIES, SpecialInvestmentInstruments.CFD),
        new ControllerAssetClass(HIST_COMMODITY_CONTROLLER, null));

    // — fixed income ETFs & funds
    controllerUrlMapping.put(new ControllerUrlMapping(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.ETF),
        new ControllerAssetClass(HIST_FUND_CONTROLLER, INTRA_ETF));
    controllerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.MUTUAL_FUND),
        new ControllerAssetClass(HIST_FUND_CONTROLLER, INTRA_FUND));

    // — money market
    controllerUrlMapping.put(new ControllerUrlMapping(AssetclassType.MONEY_MARKET, SpecialInvestmentInstruments.ETF),
        new ControllerAssetClass(HIST_FUND_CONTROLLER, INTRA_FUND));
    controllerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.MONEY_MARKET, SpecialInvestmentInstruments.MUTUAL_FUND),
        new ControllerAssetClass(HIST_FUND_CONTROLLER, INTRA_FUND));

    // — real estate
    controllerUrlMapping.put(new ControllerUrlMapping(AssetclassType.REAL_ESTATE, SpecialInvestmentInstruments.ETF),
        new ControllerAssetClass(HIST_FUND_CONTROLLER, null));
    controllerUrlMapping.put(
        new ControllerUrlMapping(AssetclassType.REAL_ESTATE, SpecialInvestmentInstruments.MUTUAL_FUND),
        new ControllerAssetClass(HIST_FUND_CONTROLLER, INTRA_FUND));

    // — indices & derivatives fallbacks
    controllerUrlMapping.put(new ControllerUrlMapping(null, SpecialInvestmentInstruments.NON_INVESTABLE_INDICES),
        new ControllerAssetClass("IndicesController", INTRA_INDEX));
    controllerUrlMapping.put(new ControllerUrlMapping(null, SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT),
        new ControllerAssetClass("DerivativesController", INTRA_DERIVATE));
  }

  private static class ControllerAssetClass {
    private final String controllerHistory;
    private final String assetClassIntra;

    ControllerAssetClass(String controllerHistory, String assetClassIntra) {
      this.controllerHistory = controllerHistory;
      this.assetClassIntra = assetClassIntra;
    }
  }

  /**
   * Central lookup: 1) try [category, instrument] 2) fallback to [null, instrument] 3) finally default controller
   */
  private static ControllerAssetClass findMapping(Security security) {
    AssetclassType type = security.getAssetClass().getCategoryType();
    SpecialInvestmentInstruments sii = security.getAssetClass().getSpecialInvestmentInstrument();

    ControllerAssetClass cac = controllerUrlMapping.get(new ControllerUrlMapping(type, sii));
    if (cac != null) {
      return cac;
    }

    cac = controllerUrlMapping.get(new ControllerUrlMapping(null, sii));
    if (cac != null) {
      return cac;
    }

    // ultimate fallback
    return new ControllerAssetClass(HIST_DEFAULT_CONTROLLER, INTRA_DEFAULT);
  }

  /**
   * @return e.g. "FundController_HistoricPriceList/"
   */
  public static String getAjaxController(Security security) {
    ControllerAssetClass cac = findMapping(security);
    return cac.controllerHistory + HIST_SUFFIX;
  }

  /**
   * @return the intra‑asset‑class segment (e.g. "aktien", "etf", etc.), or null if none
   */
  public static String getAssetClassIntra(Security security) {
    return findMapping(security).assetClassIntra + "/";
  }

  private static final String[][] stockexchangeNormalMapperArray = {
      // NASDAQ Stock Exchange)
      { GlobalConstants.STOCK_EX_MIC_NASDAQ, "NAS" },
      // SIX Exchange
      { GlobalConstants.STOCK_EX_MIC_SIX, "SWX" },
      // Xetra
      { GlobalConstants.STOCK_EX_MIC_XETRA, "FSE" },
      // Frankfurt
      { GlobalConstants.STOCK_EX_MIC_FRANKFURT, "FSE" },
      // Madrid
      { GlobalConstants.STOCK_EX_MIC_SPAIN, "STN" },
      // Milano -> Take global market
      { GlobalConstants.STOCK_EX_MIC_ITALY, "GVIE" },
      // Japan / Tokio
      { GlobalConstants.STOCK_EX_MIC_JAPAN, "TOKIO" },
      // Austria / Wien
      { GlobalConstants.STOCK_EX_MIC_AUSTRIA, "WIEN" },
      // Zürcher Kantonalbank
      { GlobalConstants.STOCK_EX_MIC_ZKB, "ZKK" } };

  private static final String[][] stockexchangeCertificateMapperArray = {
      // GlobalConstants
      { GlobalConstants.STOCK_EX_MIC_SIX, "QMH" },
      // Stuttgart
      { GlobalConstants.STOCK_EX_MIC_STUTTGART, "EUWAX" },
      // Frankfurt
      { GlobalConstants.STOCK_EX_MIC_XETRA, "SCE" } };

  public static String getNormalMappedStockexchangeSymbol(String stockexchangeSymbol) {
    return geMappedStockexchangeSymbol(stockexchangeSymbol, stockexchangeNormalMapperArray);
  }

  public static String getCertificateMappedStockexchangeSymbol(String stockexchangeSymbol) {
    return geMappedStockexchangeSymbol(stockexchangeSymbol, stockexchangeCertificateMapperArray);
  }

  public static String geMappedStockexchangeSymbol(String stockexchangeSymbol, String[][] searchIn) {
    String symbol = stockexchangeSymbol.toLowerCase();
    for (String[] mapping : searchIn) {
      if (mapping[0].equalsIgnoreCase(symbol)) {
        return mapping[1];
      }
    }
    return symbol;
  }

}
