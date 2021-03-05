package grafioschtrader.types;

public enum TemplateFormatType {
  PDF((byte) 0), CSV((byte) 1);

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
