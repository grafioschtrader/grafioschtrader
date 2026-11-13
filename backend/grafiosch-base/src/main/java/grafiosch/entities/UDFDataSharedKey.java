package grafiosch.entities;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UDFDataSharedKey implements Serializable {
  private static final long serialVersionUID = 1L;
  @Column(name = "id_udf_metadata")
  private Integer idUDFMetadata;
  @Column(name = "id_entity")
  private Integer idEntity;

  public UDFDataSharedKey() {
  }

  public UDFDataSharedKey(Integer idUDFMetadata, Integer idEntity) {
    this.idUDFMetadata = idUDFMetadata;
    this.idEntity = idEntity;
  }

  public Integer getIdUDFMetadata() {
    return idUDFMetadata;
  }

  public Integer getIdEntity() {
    return idEntity;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof UDFDataSharedKey other && Objects.equals(idUDFMetadata, other.idUDFMetadata)
        && Objects.equals(idEntity, other.idEntity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idUDFMetadata, idEntity);
  }
}
