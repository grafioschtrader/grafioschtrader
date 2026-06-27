package grafiosch.dynamic.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Field descriptor for input forms whose definition is derived from a JPA entity's annotations
 * (via {@code @DynamicFormField} + Jakarta Bean Validation). In addition to the base constraint
 * metadata it carries the owning entity name, the {@code uiOrder} string (dialog membership +
 * position) and an optional explicit label key.
 *
 * <p>It is the annotation-driven counterpart to the persisted, user-defined
 * {@link FieldDescriptorInputAndShowExtendedGeneral}: the latter describes UDF fields stored in the
 * database, this one describes regular entity fields discovered through reflection. The UDF class
 * (with its {@code byte uiOrder}) is intentionally left untouched.</p>
 */
@Schema(description = """
    Field descriptor derived from a JPA entity's @DynamicFormField + Bean Validation annotations.
    Adds the owning entity, a dialogId.position uiOrder string and an optional explicit label key
    to the base constraint metadata.
    """)
public class FieldDescriptorInputAndShowExtendedEntity extends FieldDescriptorInputAndShow {

  private static final long serialVersionUID = 1L;

  @Schema(description = "Simple name of the entity this field belongs to.")
  private final String entity;

  @Schema(description = "Comma separated list of dialogId.position tokens controlling dialog membership and ordering.")
  private final String uiOrder;

  @Schema(description = "Explicit translation key for the label; null/empty means the frontend derives it from the field name.")
  private final String labelKey;

  /**
   * Wraps an already-analysed base descriptor, copying all of its constraint metadata and adding
   * the entity / ordering / label information.
   *
   * @param base     the base descriptor produced by the standard field analysis
   * @param entity   the simple name of the owning entity
   * @param uiOrder  the {@code dialogId.position} ordering string from {@code @DynamicFormField}
   * @param labelKey the explicit label key, or empty to derive it on the frontend
   */
  public FieldDescriptorInputAndShowExtendedEntity(FieldDescriptorInputAndShow base, String entity, String uiOrder,
      String labelKey) {
    super(base.fieldName, base.dataType, base.min, base.max);
    this.enumType = base.enumType;
    this.required = base.required;
    this.dynamicFormPropertyHelps = base.dynamicFormPropertyHelps;
    this.enumValues = base.enumValues;
    this.pattern = base.pattern;
    this.dateMin = base.dateMin;
    this.digitsInteger = base.digitsInteger;
    this.digitsFraction = base.digitsFraction;
    this.entity = entity;
    this.uiOrder = uiOrder;
    this.labelKey = labelKey;
  }

  public String getEntity() {
    return entity;
  }

  public String getUiOrder() {
    return uiOrder;
  }

  public String getLabelKey() {
    return labelKey;
  }
}
