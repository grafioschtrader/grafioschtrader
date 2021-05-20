package grafioschtrader.connector.instrument.finanzen;

public class FinanzenHelper {
  private static final String[][] stockexchangeNormalMapperArray = {
      // NASDAQ
      { "NASDAQ", "NAS" },
      // Six Exchange
      { "SIX", "SWX" },
      // Frankfurt
      { "FSX", "FSE" },
      // Madrid
      { "MCE", "STN" },
      // Milano -> Take global market
      { "MIL", "GVIE" },
      // Japan / Tokio
      { "JPX", "TOKIO" },
      // Austria / Wien
      { "VIE", "WIEN" },
      // Zürcher Kantonalbank
      { "ZKB", "ZKK" } };

  private static final String[][] stockexchangeCertificateMapperArray = {
      // Stuttgart
      { "STU", "EUWAX" },
      // Frankfurt
      { "FSX", "SCE" }};
  
  
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
