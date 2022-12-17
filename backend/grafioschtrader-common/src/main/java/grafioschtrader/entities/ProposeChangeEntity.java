package grafioschtrader.entities;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Public data can only changed directly with privileged rights or by the owner
 * of the entity. Otherwise a change is a proposal and is written in this
 * entity. The user with the required rights may be accept or deny this
 * requested change.
 */
@Entity
@Table(name = ProposeChangeEntity.TABNAME)
@DiscriminatorValue("E")
public class ProposeChangeEntity extends ProposeRequest {

  public static final String TABNAME = "propose_change_entity";

  private static final long serialVersionUID = 1L;

  @Column(name = "id_entity")
  private Integer idEntity;

  @Column(name = "id_owner_entity")
  private Integer idOwnerEntity;

  public ProposeChangeEntity() {
  }

  public ProposeChangeEntity(String entity, Integer idEntity, Integer idOwnerEntity, String noteRequest) {
    this.entity = entity;
    this.idEntity = idEntity;
    this.idOwnerEntity = idOwnerEntity;
    this.noteRequest = noteRequest;
  }

  public Integer getIdOwnerEntity() {
    return idOwnerEntity;
  }

  public void setIdOwnerEntity(Integer idOwnerEntity) {
    this.idOwnerEntity = idOwnerEntity;
  }

  public Integer getIdEntity() {
    return idEntity;
  }

  public void setIdEntity(Integer idEntity) {
    this.idEntity = idEntity;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProposeChangeEntity that = (ProposeChangeEntity) o;
    return Objects.equals(idProposeRequest, that.idProposeRequest);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idProposeRequest);
  }

}
