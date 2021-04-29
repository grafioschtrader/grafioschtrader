package grafioschtrader.connector.instrument.finanzen;

public class FinanzenHelper {
  private static final String[][] stockexchangeMapperArray = {
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

  public static String getMappedStockexchangeSymbol(String stockexchangeSymbol) {
    String symbol = stockexchangeSymbol.toLowerCase();
    for (String[] mapping : stockexchangeMapperArray) {
      if (mapping[0].equalsIgnoreCase(symbol)) {
        return mapping[1];
      }
    }
    return symbol;
  }
}
