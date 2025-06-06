package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import grafiosch.entities.TenantBaseID;
import grafiosch.entities.User;
import grafiosch.types.OperationType;
import io.swagger.v3.oas.annotations.Operation;

/**
 * Delete entity which is connected to a tenant. It is checked if the user has the right to delete the entity.
 *
 * @param <T>
 */
public abstract class UpdateCreateDeleteWithTenantResource<T extends TenantBaseID> extends UpdateCreateResource<T> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());
  private final Class<T> type;

  @Override
  protected abstract UpdateCreateDeleteWithTenantJpaRepository<T> getUpdateCreateJpaRepository();

  public UpdateCreateDeleteWithTenantResource(Class<T> type) {
    this.type = type;
  }

  @Operation(summary = "Delete a record of an entity by its ID and the ID of tenant", description = "User / tenant is checked before deletion")
  @DeleteMapping(value = "/{id}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteResource(@PathVariable final Integer id) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    log.debug("Delete by id : {}", id);
    getUpdateCreateJpaRepository().delEntityWithTenant(id, user.getIdTenant());
    this.logAddUpdDel(user.getIdUser(), type.getSimpleName(), OperationType.DELETE);
    return ResponseEntity.noContent().build();
  }
}