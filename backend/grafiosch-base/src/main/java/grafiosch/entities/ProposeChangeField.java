package grafiosch.entities;

import java.util.Objects;

import org.apache.commons.lang3.SerializationUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Schema(description = """
    More fields can be affected by a data change request.
    The field name and the corresponding value are therefore saved here.""")
@Entity
@Table(name = ProposeChangeField.TABNAME)
public class ProposeChangeField {

  public static final String TABNAME = "propose_change_field";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_propose_field")
  private Integer idProposeField;

  @Column(name = "id_propose_request")
  private Integer idProposeRequest;

  @Schema(description = "The name of the field that is affected by the change. Reflection sets the value of this field for the proposed entity.")
  @Column(name = "field")
  private String field;

  @Schema(description = """
      The data type for the proposed data can of course be different. Therefore,
      byte is used here and the corresponding value is serialized.""")
  @Column(name = "value")
  private byte[] value;

  public ProposeChangeField() {
  }

  public ProposeChangeField(String field, byte[] value, Integer idProposeRequest) {
    this.field = field;
    this.value = value;
    this.idProposeRequest = idProposeRequest;
  }

  public ProposeChangeField(String field, byte[] value) {
    this(field, value, null);
  }

  public Integer getIdProposeField() {
    return idProposeField;
  }

  public void setIdProposeField(Integer idProposeField) {
    this.idProposeField = idProposeField;
  }

  public Integer getIdProposeRequest() {
    return idProposeRequest;
  }

  public void setIdProposeRequest(Integer idProposeRequest) {
    this.idProposeRequest = idProposeRequest;
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  @JsonIgnore
  public byte[] getValue() {
    return value;
  }

  public String getValueDesarialized() {
    Object object = SerializationUtils.deserialize(value);
    return object == null ? null : object.toString();
  }

  public void setValueDesarialized(String valueDesarialized) {
    value = SerializationUtils.serialize(valueDesarialized);
  }

  public void setValue(byte[] value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProposeChangeField that = (ProposeChangeField) o;
    return Objects.equals(idProposeField, that.idProposeField);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idProposeField);
  }

}
