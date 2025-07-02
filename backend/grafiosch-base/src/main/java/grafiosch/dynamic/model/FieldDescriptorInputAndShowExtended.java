package grafiosch.dynamic.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
* Abstract base class for extended field descriptors that includes additional metadata
* for enhanced dynamic form generation. This class extends the basic field descriptor
* with user interface presentation information, ordering, and user-specific configurations.
* 
* <p>This extended descriptor provides additional properties that are essential for
* creating rich, user-friendly dynamic forms including:</p>
* <ul>
* <li>User-facing labels and help text for better usability</li>
* <li>Field ordering information for consistent form layouts</li>
* <li>User-specific configurations for personalized form experiences</li>
* <li>Special type indicators for user-defined field behaviors</li>
* </ul>
*/
public abstract class FieldDescriptorInputAndShowExtended extends FieldDescriptorInputAndShow {
  private static final long serialVersionUID = 1L;

  @Schema(description = "This information is displayed to the user as the label of this property.")
  private final String description;

  @Schema(description = "This optional help text is provided as a tooltip in the user interface of the property")
  private final String descriptionHelp;

  @Schema(description = "Controls the order of the fields during user input")
  private final byte uiOrder;

  @Schema(description = "Optional special type identifier for user-defined field behaviors and custom processing")
  protected final Byte udfSpecialType;

  @Schema(description = "The ID of the user associated with this field configuration, used for user-specific customizations")
  private final int idUser;

  /**
   * Creates a new extended field descriptor with enhanced metadata for dynamic form generation.
   * All parameters are immutable after construction to ensure consistent field behavior.
   * 
   * @param fieldName the name of the field as it appears in the model class or form
   * @param description the user-facing label displayed for this field in the form interface
   * @param descriptionHelp optional help text shown as a tooltip or help popup; may be null
   * @param uiOrder the display order of this field relative to other fields (lower values appear first)
   * @param dataType the data type that determines the input component type and validation rules
   * @param min the minimum value for numeric types or minimum length for string types; may be null
   * @param max the maximum value for numeric types or maximum length for string types; may be null
   * @param udfSpecialType optional special type identifier for user-defined field behaviors; may be null
   * @param idUser the ID of the user associated with this field configuration for personalization
   */
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
