package grafioschtrader.connector.instrument.finanzen;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class ControllerUrlMapping {
  public AssetclassType assetClassType;
  public SpecialInvestmentInstruments specialInvestmentInstrument;

  public ControllerUrlMapping(AssetclassType assetClassType, SpecialInvestmentInstruments specialInvestmentInstrument) {
    this.assetClassType = assetClassType;
    this.specialInvestmentInstrument = specialInvestmentInstrument;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((assetClassType == null) ? 0 : assetClassType.hashCode());
    result = prime * result + ((specialInvestmentInstrument == null) ? 0 : specialInvestmentInstrument.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if ((obj == null) || (getClass() != obj.getClass()))
      return false;
    ControllerUrlMapping other = (ControllerUrlMapping) obj;
    if (assetClassType != other.assetClassType)
      return false;
    if (specialInvestmentInstrument != other.specialInvestmentInstrument)
      return false;
    return true;
  }
}