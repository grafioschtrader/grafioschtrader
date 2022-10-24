package grafioschtrader.connector.instrument.finanzen;

import grafioschtrader.GlobalConstants;

public class FinanzenHelper {
  private static final String[][] stockexchangeNormalMapperArray = {
      // NASDAQ Stock Exchange)
      { GlobalConstants.STOCK_EX_MIC_NASDAQ, "NAS" },
      // SIX Exchange
      { GlobalConstants.STOCK_EX_MIC_SIX, "SWX" },
      // Frankfurt
      { GlobalConstants.STOCK_EX_MIC_XETRA, "FSE" },
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
