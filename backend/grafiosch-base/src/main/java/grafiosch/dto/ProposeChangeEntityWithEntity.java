package grafiosch.dto;

import java.io.Serializable;

import grafiosch.entities.ProposeChangeEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contains a change request for a shared entity")
public class ProposeChangeEntityWithEntity implements Serializable {
  private static final long serialVersionUID = 1L;

  @Schema(description = "Contains the proposal for changing the entity.")
  public ProposeChangeEntity proposeChangeEntity;
  @Schema(description = "The entity as it currently is")
  public Object entity;
  @Schema(description = "The entity as proposed, so this contains the changes")
  public Object proposedEntity;

  public ProposeChangeEntityWithEntity(ProposeChangeEntity proposeChangeEntity, Object entity,
      Object proposedEntity) {
    super();
    this.proposeChangeEntity = proposeChangeEntity;
    this.entity = entity;
    this.proposedEntity = proposedEntity;
  }
}
