package grafiosch.entities;

import static jakarta.persistence.InheritanceType.JOINED;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.BaseConstants.UDFPrefixSuffix;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyOnlyCreation;
import grafiosch.dynamic.model.udf.UDFDataHelper;
import grafiosch.types.IUDFSpecialType;
import grafiosch.types.UDFDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = UDFMetadata.TABNAME)
@Inheritance(strategy = JOINED)
@Schema(description = """
    An instance corresponds to an input or output field.  A meta description is required for the user defined fields.
    This is the general description of this metadata.""")
public abstract class UDFMetadata extends UserBaseID {

  public static final String TABNAME = "udf_metadata";
  
  public static final EnumRegistry<Byte, IUDFSpecialType> UDF_SPECIAL_TYPE_REGISTRY = new EnumRegistry<>();
  
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_udf_metadata")
  private Integer idUDFMetadata;

  @Schema(description = """
      Possibly this construct of the UDF is also used for system-wide input fields.
      In this case, the user with ID = 0 would be used.""")
  @Column(name = "id_user")
  @NotNull
  protected Integer idUser;

  @Schema(description = """
      There are also user-defined fields that are intended for all users. This prevents the database table
      from having to be unnecessarily extended with fields. This can be used primarily for securities,
      as not all securities have the same properties. If this property is not NULL, the idUser should be 0.""")
  @Column(name = "udf_special_type")
  protected Byte udfSpecialType;

  @Schema(description = "This information is displayed to the user as the label of this property.")
  @Column(name = "description")
  @NotNull
  @PropertyAlwaysUpdatable
  protected String description;

  @Schema(description = "This optional help text is provided as a tooltip in the user interface of the property")
  @Column(name = "description_help")
  @PropertyAlwaysUpdatable
  protected String descriptionHelp;

  @Schema(description = "For validation and other purposes the data type is required")
  @Column(name = "udf_data_type")
  @NotNull
  @PropertyOnlyCreation
  protected byte udfDataType;

  @Schema(description = """
      Format for character strings "min,max" Number of characters. Example 0,128.
      For integers "min,max" number of digits. Example 1,99999999 means a range from 1 - 99999999.
      The first value stands for the minimum value, the second value for the maximum value.
      For decimal numbers "total length, number of decimal places". Example 12,5.""")
  @Column(name = "field_size")
  @PropertyAlwaysUpdatable
  @Pattern(regexp = "-?[0-9]+,-?[0-9]+")
  @Size(min = 3, max = 20)
  protected String fieldSize;

  @Schema(description = "Controls the order of the fields during user input")
  @Column(name = "ui_order")
  @NotNull
  @PropertyAlwaysUpdatable
  protected byte uiOrder;

  public UDFMetadata() {
  }
    
  public UDFMetadata(@NotNull Integer idUser, Byte udfSpecialType, @NotNull String description, String descriptionHelp,
      @NotNull byte udfDataType, @Pattern(regexp = "-?[0-9]+,-?[0-9]+") @Size(min = 3, max = 20) String fieldSize,
      @NotNull byte uiOrder) {
    super();
    this.idUser = idUser;
    this.udfSpecialType = udfSpecialType;
    this.description = description;
    this.descriptionHelp = descriptionHelp;
    this.udfDataType = udfDataType;
    this.fieldSize = fieldSize;
    this.uiOrder = uiOrder;
  }


  @Override
  public Integer getIdUser() {
    return idUser;
  }

  @Override
  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  @JsonIgnore
  public Byte getUdfSpecialTypeAsByte() {
    return udfSpecialType;
  }
  
  public IUDFSpecialType getUdfSpecialType() {
    return udfSpecialType == null ? null : UDF_SPECIAL_TYPE_REGISTRY.getTypeByValue(udfSpecialType);
  }

  public void setUdfSpecialType(IUDFSpecialType udfSpecialType) {
    this.udfSpecialType = udfSpecialType == null ? null : udfSpecialType.getValue();
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDescriptionHelp() {
    return descriptionHelp;
  }

  public void setDescriptionHelp(String descriptionHelp) {
    this.descriptionHelp = descriptionHelp;
  }

  public UDFDataType getUdfDataType() {
    return UDFDataType.getUDFDataType(udfDataType);
  }

  public void setUdfDataType(UDFDataType udfDataType) {
    this.udfDataType = udfDataType.getValue();
  }

  public Integer getIdUDFMetadata() {
    return idUDFMetadata;
  }

  public String getFieldSize() {
    return fieldSize;
  }

  public void setFieldSize(String fieldSize) {
    this.fieldSize = fieldSize;
  }

  @JsonIgnore
  public int getFieldSizeSuffix() {
    return Integer.valueOf(fieldSize.split(",")[1]);
  }

  @JsonIgnore
  public int[] getPrefixSuffix() {
    String[] prefixSuffixStr = fieldSize.replace(" ", "").split(",");
    return new int[] { Integer.parseInt(prefixSuffixStr[0]), Integer.parseInt(prefixSuffixStr[1]) };
  }

  public byte getUiOrder() {
    return uiOrder;
  }

  public void setUiOrder(byte uiOrder) {
    this.uiOrder = uiOrder;
  }

  @Override
  public Integer getId() {
    return idUDFMetadata;
  }

  /**
   * Return of the format (P,S) in real values. A calculation is only required for
   * the format of a decimal number.
   */
  @JsonIgnore
  public Double[] getFieldLength() {
    Double[] minMaxValue = new Double[2];
    if (UDFDataHelper.isFieldSizeForDataType(getUdfDataType())) {
      int[] prefixSuffix = this.getPrefixAndSuffix(getFieldSize());
      switch (getUdfDataType()) {
      case UDF_Numeric:
        minMaxValue[1] = UDFDataHelper.getMaxDecimalValue(prefixSuffix[0], prefixSuffix[1]);
        break;
      default:
        // UDF_NumericInteger:
        // String
        minMaxValue[0] = (double) prefixSuffix[0];
        minMaxValue[1] = (double) prefixSuffix[1];
        break;
      }
    }
    return minMaxValue;
  }
 
  public void checkFieldSize() {
    if (UDFDataHelper.isFieldSizeForDataType(getUdfDataType())) {
      int[] prefixSuffix = getPrefixAndSuffix(getFieldSize());
      switch (getUdfDataType()) {
      case UDF_Numeric:
        checkDoublePrecision(prefixSuffix);
        break;
      default:
        checkIntegerString(prefixSuffix, getUdfDataType());
      }
    }
  }

  /**
   * Checks the format of a decimal number against a default. The digits before
   * and after the decimal point and the total length are checked.
   * 
   * @param prefixSuffix
   */
  private void checkDoublePrecision(int[] prefixSuffix) {
    UDFPrefixSuffix ups = BaseConstants.uDFPrefixSuffixMap.get(UDFDataType.UDF_Numeric);
    if (prefixSuffix[0] > ups.prefix || prefixSuffix[1] > ups.suffix || prefixSuffix[0] <= prefixSuffix[1]
        || prefixSuffix[1] < ups.together) {
      throw new IllegalArgumentException("The format for decimal numbers is outside its limits.");
    }
  }

  private void checkIntegerString(int[] prefixSuffix, UDFDataType uDFDataType) {
    UDFPrefixSuffix ups = BaseConstants.uDFPrefixSuffixMap.get(uDFDataType);
    if (prefixSuffix[0] < ups.prefix || prefixSuffix[1] > ups.suffix || prefixSuffix[0] > prefixSuffix[1]) {
      throw new IllegalArgumentException("The format is outside its limits.");
    }
  }

  private int[] getPrefixAndSuffix(String fieldSize) {
    int[] prefixSuffix = new int[2];
    String fieldSizes[] = fieldSize.replaceAll(" ", "").split(",");
    prefixSuffix[0] = Integer.parseInt(fieldSizes[0]);
    prefixSuffix[1] = Integer.parseInt(fieldSizes[1]);
    return prefixSuffix;
  }

}
