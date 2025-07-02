package grafiosch.dynamic.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
Extended field descriptor for general-purpose dynamic form fields that includes entity association.
Contains all standard field metadata plus information about which entity the field belongs to,
enabling cross-entity form generation and multi-entity form scenarios.
""")
public class FieldDescriptorInputAndShowExtendedGeneral extends FieldDescriptorInputAndShowExtended {

  private static final long serialVersionUID = 1L;
  
  @Schema(description = "The name or identifier of the entity that this field belongs to, used for associating fields with specific business objects")
  private String entity;

  /**
   * Creates a new extended general field descriptor with entity association.
   * 
   * @param fieldName the name of the field
   * @param description the user-facing label for this field
   * @param descriptionHelp optional help text displayed as tooltip
   * @param uiOrder the display order of this field in the form
   * @param dataType the data type that determines input component and validation
   * @param min the minimum value/length constraint, or null if no minimum
   * @param max the maximum value/length constraint, or null if no maximum
   * @param udfSpecialType optional special type identifier for user-defined fields
   * @param idUser the ID of the user associated with this field configuration
   * @param entity the name or identifier of the entity this field belongs to
   */
  public FieldDescriptorInputAndShowExtendedGeneral(String fieldName, String description, String descriptionHelp,
      byte uiOrder, DataType dataType, Double min, Double max, Byte udfSpecialType, int idUser, String entity) {
    super(fieldName, description, descriptionHelp, uiOrder, dataType, min, max, udfSpecialType, idUser);
    this.entity = entity;
  }

  public String getEntity() {
    return entity;
  }
}
