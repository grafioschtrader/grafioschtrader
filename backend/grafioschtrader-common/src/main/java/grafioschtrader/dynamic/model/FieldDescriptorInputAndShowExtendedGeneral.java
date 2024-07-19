package grafioschtrader.dynamic.model;

public class FieldDescriptorInputAndShowExtendedGeneral extends FieldDescriptorInputAndShowExtended {

  private static final long serialVersionUID = 1L;
  private String entity;

  public FieldDescriptorInputAndShowExtendedGeneral(String fieldName, String description, String descriptionHelp,
      byte uiOrder, DataType dataType, Double min, Double max, Byte udfSpecialType, int idUser, String entity) {
    super(fieldName, description, descriptionHelp, uiOrder, dataType, min, max, udfSpecialType, idUser);
    this.entity = entity;
  }

  public String getEntity() {
    return entity;
  }
}
