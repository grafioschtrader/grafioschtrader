package grafioschtrader.entities;

import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Schema(description = """
        For securities, different fields can be defined for different instruments and asset classes. 
        For example, different information may be relevant to the user for a bond or stock.""")
@Entity
@Table(name = UDFMetadataSecurity.TABNAME)
public class UDFMetadataSecurity extends UDFMetadata {

  public static final String TABNAME = "udf_metadata_security";

  @Schema(description = "An instrument belongs to a group of asset classes, such as shares, bonds, etc.")
  @Column(name = "category_type")
  @PropertyAlwaysUpdatable
  private byte categoryType;

  @Schema(description = "An instrument belongs to a group of financial instruments, e.g. ETF, direct, etc.")
  @Column(name = "spec_invest_instrument")
  @PropertyAlwaysUpdatable
  private byte specialInvestmentInstrument;

  public UDFMetadataSecurity() {
  }

  public AssetclassType getCategoryType() {
    return AssetclassType.getAssetClassTypeByValue(this.categoryType);
  }

  public void setCategoryType(AssetclassType assetClassType) {
    this.categoryType = assetClassType.getValue();
  }

  public SpecialInvestmentInstruments getSpecialInvestmentInstrument() {
    return SpecialInvestmentInstruments.getSpecialInvestmentInstrumentsByValue(specialInvestmentInstrument);
  }

  public void setSpecialInvestmentInstrument(SpecialInvestmentInstruments specialInvestmentInstrument) {
    this.specialInvestmentInstrument = specialInvestmentInstrument.getValue();
  }


}
