package grafiosch.dynamic.model;

import io.swagger.v3.oas.annotations.media.Schema;

public abstract class FieldDescriptorInputAndShowExtended extends FieldDescriptorInputAndShow {
  private static final long serialVersionUID = 1L;

  @Schema(description = "This information is displayed to the user as the label of this property.")
  private final String description;

  @Schema(description = "This optional help text is provided as a tooltip in the user interface of the property")
  private final String descriptionHelp;

  @Schema(description = "Controls the order of the fields during user input")
  private final byte uiOrder;

  protected final Byte udfSpecialType;

  private final int idUser;

  public FieldDescriptorInputAndShowExtended(String fieldName, String description, String descriptionHelp, byte uiOrder,
      DataType dataType, Double min, Double max, Byte udfSpecialType, int idUser) {
    super(fieldName, dataType, min, max);
    this.description = description;
    this.descriptionHelp = descriptionHelp;
    this.uiOrder = uiOrder;
    this.udfSpecialType = udfSpecialType;
    this.idUser = idUser;
  }

  public String getDescription() {
    return description;
  }

  public String getDescriptionHelp() {
    return descriptionHelp;
  }

  public byte getUiOrder() {
    return uiOrder;
  }

  public Byte getUdfSpecialType() {
    return udfSpecialType;
  }

  public int getIdUser() {
    return idUser;
  }

}
