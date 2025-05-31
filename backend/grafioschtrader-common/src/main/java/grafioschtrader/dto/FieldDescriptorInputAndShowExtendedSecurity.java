package grafioschtrader.dto;

import java.util.EnumSet;

import grafiosch.dynamic.model.DataType;
import grafiosch.dynamic.model.FieldDescriptorInputAndShowExtended;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.swagger.v3.oas.annotations.media.Schema;

public class FieldDescriptorInputAndShowExtendedSecurity extends FieldDescriptorInputAndShowExtended {

  private static final long serialVersionUID = 1L;

  @Schema(description = "An instrument belongs to a group of asset classes, such as shares, bonds, etc.")
  private EnumSet<AssetclassType> categoryTypeEnums;

  @Schema(description = "An instrument belongs to a group of financial instruments, e.g. ETF, direct, etc.")
  private EnumSet<SpecialInvestmentInstruments> specialInvestmentInstrumentEnums;

  public FieldDescriptorInputAndShowExtendedSecurity(EnumSet<AssetclassType> categoryTypeEnums,
      EnumSet<SpecialInvestmentInstruments> specialInvestmentInstrumentEnums, String fieldName, String description,
      String descriptionHelp, byte uiOrder, DataType dataType, Double min, Double max, Byte udfSpecialType,
      int idUser) {
    super(fieldName, description, descriptionHelp, uiOrder, dataType, min, max, udfSpecialType, idUser);
    this.categoryTypeEnums = categoryTypeEnums;
    this.specialInvestmentInstrumentEnums = specialInvestmentInstrumentEnums;
  }

  public EnumSet<AssetclassType> getCategoryTypeEnums() {
    return categoryTypeEnums;
  }

  public EnumSet<SpecialInvestmentInstruments> getSpecialInvestmentInstrumentEnums() {
    return specialInvestmentInstrumentEnums;
  }

}
