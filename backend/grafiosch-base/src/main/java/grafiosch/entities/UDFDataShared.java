package grafiosch.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = UDFDataShared.TABNAME)
public class UDFDataShared {
  public static final String TABNAME = "udf_data_shared";
  @EmbeddedId
  private UDFDataSharedKey id;
  @Column(name = "entity")
  private String entity;
  @Column(name = "field_value")
  private String fieldValue;

  public UDFDataShared() {
  }

  public UDFDataShared(UDFDataSharedKey id, String entity, String fieldValue) {
    this.id = id;
    this.entity = entity;
    this.fieldValue = fieldValue;
  }

  public UDFDataSharedKey getId() {
    return id;
  }

  public String getEntity() {
    return entity;
  }

  public String getFieldValue() {
    return fieldValue;
  }

  public void setFieldValue(String fieldValue) {
    this.fieldValue = fieldValue;
  }
}
