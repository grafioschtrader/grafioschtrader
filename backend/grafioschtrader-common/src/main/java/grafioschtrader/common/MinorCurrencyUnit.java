package grafioschtrader.common;

import java.util.Map;

import grafioschtrader.GlobalConstants;

/**
 * Central definition of minor currency units and their relation to the ISO 4217 major currency. Some exchanges quote
 * prices in a fraction of the official currency: the London Stock Exchange in pence (GBX or GBp instead of GBP) and
 * the Johannesburg Stock Exchange in cents (ZAc instead of ZAR). GT stores every security, price history and
 * transaction exclusively in the major currency, so values quoted in a minor unit must be divided by the factor
 * defined here. See GitHub issue #10.
 */
public abstract class MinorCurrencyUnit {

  /** Divider between a minor currency unit and its major currency (pence to pounds, cents to rand). */
  public static final double MINOR_TO_MAJOR_DIVIDER = 100.0;

  /**
   * Maps a minor-unit currency code as it appears in trading platform documents to its ISO 4217 major currency. The
   * lookup is case-sensitive on purpose: uppercase "GBP" means pounds, only the exact spelling "GBp" denotes pence,
   * whereas "GBX" and "ZAX" are unambiguous.
   */
  private static final Map<String, String> MINOR_TO_MAJOR = Map.of(
      "GBp", GlobalConstants.MC_GBP,
      "GBX", GlobalConstants.MC_GBP,
      "ZAc", GlobalConstants.MC_ZAR,
      "ZAX", GlobalConstants.MC_ZAR);

  /**
   * Exchanges (by MIC) whose securities are quoted in the minor unit of the given major currency. Used by the price
   * data connectors to divide delivered quotes by {@link #MINOR_TO_MAJOR_DIVIDER}.
   */
  private static final Map<String, String> MIC_MINOR_QUOTED = Map.of(
      GlobalConstants.STOCK_EX_MIC_UK, GlobalConstants.MC_GBP,
      GlobalConstants.STOCK_EX_MIC_JSE, GlobalConstants.MC_ZAR);

  /**
   * Checks whether the given currency code denotes a minor currency unit (exact, case-sensitive match).
   *
   * @param currencyCode the currency code as found in an imported document, may be null
   * @return true if the code is a known minor-unit code like "GBp", "GBX", "ZAc" or "ZAX"
   */
  public static boolean isMinorUnit(String currencyCode) {
    return currencyCode != null && MINOR_TO_MAJOR.containsKey(currencyCode);
  }

  /**
   * Returns the ISO 4217 major currency for a minor-unit code.
   *
   * @param minorUnitCode a code for which {@link #isMinorUnit(String)} is true
   * @return the major currency code, e.g. "GBP" for "GBp", or null if the code is not a minor unit
   */
  public static String getMajorCurrency(String minorUnitCode) {
    return minorUnitCode == null ? null : MINOR_TO_MAJOR.get(minorUnitCode);
  }

  /**
   * Determines the price divider for a security listed on the given exchange with the given currency. Exchanges like
   * XLON (GBP) and XJSE (ZAR) quote in the minor unit, requiring division by 100 to reach the security's currency.
   *
   * @param mic      the Market Identifier Code of the security's stock exchange
   * @param currency the ISO 4217 currency of the security
   * @return {@link #MINOR_TO_MAJOR_DIVIDER} if the exchange quotes this currency in its minor unit, otherwise 1.0
   */
  public static double getExchangeDivider(String mic, String currency) {
    // Map.of() rejects null keys in get(); a stock exchange without a MIC never quotes in a minor unit
    return mic != null && currency != null && currency.equals(MIC_MINOR_QUOTED.get(mic)) ? MINOR_TO_MAJOR_DIVIDER
        : 1.0;
  }

}
