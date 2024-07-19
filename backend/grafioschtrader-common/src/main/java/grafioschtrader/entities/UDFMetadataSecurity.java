package grafioschtrader.entities;

import java.util.EnumSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.common.EnumHelper;
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
  @Column(name = "category_types")
  @PropertyAlwaysUpdatable
  @JsonIgnore
  private long categoryTypes;

  @Schema(description = "An instrument belongs to a group of financial instruments, e.g. ETF, direct, etc.")
  @Column(name = "spec_invest_instruments")
  @PropertyAlwaysUpdatable
  @JsonIgnore
  private long specialInvestmentInstruments;

  public UDFMetadataSecurity() {
  }

  public long getCategoryTypes() {
    return categoryTypes;
  }

  public void setCategoryTypes(long categoryTypes) {
    this.categoryTypes = categoryTypes;
  }

  public long getSpecialInvestmentInstruments() {
    return specialInvestmentInstruments;
  }

  public void setSpecialInvestmentInstruments(long specialInvestmentInstruments) {
    this.specialInvestmentInstruments = specialInvestmentInstruments;
  }

  public void setSpecialInvestmentInstrumentEnums(
      EnumSet<SpecialInvestmentInstruments> specialInvestmentInstrumentEnums) {
    this.specialInvestmentInstruments = EnumHelper.encodeEnumSet(specialInvestmentInstrumentEnums);
  }

  public EnumSet<SpecialInvestmentInstruments> getSpecialInvestmentInstrumentEnums() {
    return EnumHelper.decodeEnumSet(SpecialInvestmentInstruments.class, specialInvestmentInstruments);
  }

  public void setCategoryTypeEnums(EnumSet<AssetclassType> categoryTypeEnums) {
    this.categoryTypes = EnumHelper.encodeEnumSet(categoryTypeEnums);
  }

  public EnumSet<AssetclassType> getCategoryTypeEnums() {
    return EnumHelper.decodeEnumSet(AssetclassType.class, categoryTypes);
  }

  @Override
  public String toString() {
    return "UDFMetadataSecurity [categoryTypes=" + categoryTypes + ", specialInvestmentInstruments="
        + specialInvestmentInstruments + ", idUser=" + idUser + ", udfSpecialType=" + udfSpecialType
        + ", description=" + description + ", descriptionHelp=" + descriptionHelp + ", udfDataType=" + udfDataType
        + ", fieldSize=" + fieldSize + ", uiOrder=" + uiOrder + "]";
  }

}
