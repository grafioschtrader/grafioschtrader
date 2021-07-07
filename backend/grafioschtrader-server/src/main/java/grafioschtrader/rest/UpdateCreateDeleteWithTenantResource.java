package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import grafioschtrader.entities.TenantBaseID;
import grafioschtrader.entities.User;
import grafioschtrader.types.OperationType;
import io.swagger.v3.oas.annotations.Operation;

/**
 * Delete entity which is connected to a tenant. It is checked if the user has
 * the right to delete the entity.
 *
 * @author Hugo Graf
 *
 * @param <T>
 */
public abstract class UpdateCreateDeleteWithTenantResource<T extends TenantBaseID> extends UpdateCreateResource<T> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());
  private final Class<T> type;

  @Override
  protected abstract UpdateCreateDeleteWithTenantJpaRepository<T> getUpdateCreateJpaRepository();

  UpdateCreateDeleteWithTenantResource(Class<T> type) {
    this.type = type;
  }

  @Operation(summary = "Delete a record of an enity by its Id, it is for entities of tenant", description = "User / tenant is checked before deletion")
  @DeleteMapping(value = "/{id}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteResource(@PathVariable final Integer id) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    log.debug("Delete by id : {}", id);
    getUpdateCreateJpaRepository().delEntityWithTenant(id, user.getIdTenant());
    this.logAddUpdDel(user.getIdUser(), type.getSimpleName(), OperationType.DELETE, false);
    return ResponseEntity.noContent().build();
  }
}