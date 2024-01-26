package grafioschtrader.entities;

import java.util.Map;
import java.util.Objects;

import org.hibernate.annotations.Type;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Schema(description = "The data for the user defined fields are persisted into this entity. The JSON support of MaraDB provides the necessary flexibility.")
@Entity
@Table(name = UDFData.TABNAME)
public class UDFData {

  public static final String TABNAME = "udf_data";

  @EmbeddedId
  private UDFDataKey uDFDataKey;

  @Schema(description = "Storage of a map in JSON. This gives us great flexibility. The idUDFMetadata with the prefix 'F' is used as the key.")
  @Type(JsonType.class)
  @Column(columnDefinition = "json_values")
  private Map<String, String> jsonValues;


  public UDFData() {
  }

  public UDFData(UDFDataKey uDFDataKey, Map<String, String> jsonValues) {
    super();
    this.uDFDataKey = uDFDataKey;
    this.jsonValues = jsonValues;
  }

  public UDFDataKey getuDFDataKey() {
    return uDFDataKey;
  }

  public void setuDFDataKey(UDFDataKey uDFDataKey) {
    this.uDFDataKey = uDFDataKey;
  }

  public Map<String, String> getJsonValues() {
    return jsonValues;
  }

  public void setJsonValues(Map<String, String> jsonValues) {
    this.jsonValues = jsonValues;
  }


  public static class UDFDataKey {

    @Schema(description = "Possibly this construct of the UDF is also used for system-wide input fields. In this case, the user with ID = 0 would be used.")
    @Column(name= "id_user")
    @NotNull
    private Integer idUser;

    @Schema(description = "The name of the entity to which this UDF refers.")
    @Column(name = "entity")
    @NotNull
    private String entity;

    @Schema(description = "The ID of the entity which this record extends.")
    @Column(name = "id_entity")
    @NotNull
    private Integer idEntity;


    @Override
    public int hashCode() {
      return Objects.hash(entity, idEntity, idUser);
    }

    public UDFDataKey() {
    }

    public UDFDataKey(Integer idUser, String entity, Integer idEntity) {
      this.idUser = idUser;
      this.entity = entity;
      this.idEntity = idEntity;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if ((obj == null) || (getClass() != obj.getClass())) {
        return false;
      }
      UDFDataKey other = (UDFDataKey) obj;
      return Objects.equals(entity, other.entity) && Objects.equals(idEntity, other.idEntity)
          && Objects.equals(idUser, other.idUser);
    }



  }
}
