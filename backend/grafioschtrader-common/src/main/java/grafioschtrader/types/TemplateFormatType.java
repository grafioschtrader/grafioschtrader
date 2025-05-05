package grafioschtrader.types;

/**
 * The import templates are created for the two file types PDF and CSV.
 */
public enum TemplateFormatType {
  // The template is intended for PDF import
  PDF((byte) 0), 
  // The template is intended for CSV import
  CSV((byte) 1);

  private final Byte value;

  private TemplateFormatType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static TemplateFormatType getTemplateFormatTypeByValue(byte value) {
    for (TemplateFormatType templateFormatType : TemplateFormatType.values()) {
      if (templateFormatType.getValue() == value) {
        return templateFormatType;
      }
    }
    return null;
  }
}
