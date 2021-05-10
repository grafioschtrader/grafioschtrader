/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.types;

/**
 *
 * @author Hugo Graf
 */
public enum AssetclassType {

  // Equities, can have dividends
  EQUITIES((byte) 0),

  // Fixed income, can have dividends when ETF
  FIXED_INCOME((byte) 1),

  // Money market, can have dividends when ETF
  MONEY_MARKET((byte) 2),

  // Commodities, may never have dividends
  COMMODITIES((byte) 3),

  // Real estate, can have dividends
  REAL_ESTATE((byte) 4),

  // equities, bonds, cash, may have dividends
  MULTI_ASSET((byte) 5),

  // Convertible bond, may have dividends when ETF or Fonds
  CONVERTIBLE_BOND((byte) 6),

  // Credit derivative, may never have dividends
  CREDIT_DERIVATIVE((byte) 7),

  // Forex, may never have dividends
  CURRENCY_PAIR((byte) 8),

  // Exist only for Client and is not saved to repository. It may be used for
  // grouping
  CURRENCY_CASH((byte) 11),
  // Exist only for Client and is not saved to repository. It may be used for
  // grouping
  CURRENCY_FOREIGN((byte) 12);

  private final Byte value;

  private AssetclassType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public String getName() {
    return java.util.ResourceBundle.getBundle("grafioschtrader/typenames").getString("assetClass_" + this.getValue());
  }

  public static AssetclassType getAssetClassTypeByValue(byte value) {
    for (AssetclassType assetClassType : AssetclassType.values()) {
      if (assetClassType.getValue() == value) {
        return assetClassType;
      }
    }
    return null;
  }

}
