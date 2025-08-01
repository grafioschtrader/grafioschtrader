package grafioschtrader.types;

import grafiosch.types.StableEnum;

/**
 * An asset class can be offered in different instruments. Shares can be invested in directly or through an ETF or fund,
 * etc. GT must be able to distinguish whether, for example, a share is traded directly or CFT.
 */
public enum SpecialInvestmentInstruments implements StableEnum {

  /** Can have dividends */
  DIRECT_INVESTMENT((byte) 0),
  
  /** Can have dividends */
  ETF((byte) 1),
  
  /** Can have dividends */
  MUTUAL_FUND((byte) 2),
  
  /** Can have dividends */
  PENSION_FUNDS((byte) 3),
  
  /** Can never have dividends */
  CFD((byte) 4),
  
  /** Can never have dividends */
  FOREX((byte) 5),
  
  /** A product with issuer risk like certificate, ETC, ETN */
  ISSUER_RISK_PRODUCT((byte) 6),
  
  /** Can never have dividends */
  NON_INVESTABLE_INDICES((byte) 10);

  private final Byte value;

  private SpecialInvestmentInstruments(final Byte value) {
    this.value = value;
  }

  @Override
  public Byte getValue() {
    return this.value;
  }

  public String getName() {
    return java.util.ResourceBundle.getBundle("grafioschtrader/typenames")
        .getString("specialInvestmentInstruments_" + this.getValue());
  }

  public static SpecialInvestmentInstruments getSpecialInvestmentInstrumentsByValue(byte value) {
    for (SpecialInvestmentInstruments specialInvestmentInstruments : SpecialInvestmentInstruments.values()) {
      if (specialInvestmentInstruments.getValue() == value) {
        return specialInvestmentInstruments;
      }
    }
    return null;
  }

}
