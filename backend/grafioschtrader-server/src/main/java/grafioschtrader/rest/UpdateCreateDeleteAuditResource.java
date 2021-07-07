package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import grafioschtrader.entities.BaseID;
import io.swagger.v3.oas.annotations.Operation;

/**
 * Delete a shared entity, that means the owner of the entity or an user with
 * the required privileges can delete it.
 *
 * @author Hugo Graf
 *
 * @param <T>
 */
public abstract class UpdateCreateDeleteAuditResource<T extends BaseID> extends UpdateCreateDeleteAudit<T> {

  @Override
  protected abstract UpdateCreateJpaRepository<T> getUpdateCreateJpaRepository();

  @Operation(summary = "Delete a record of an enity by its Id, it is for entities which share data", description = "User privileges are checked for deletion")
  @DeleteMapping(value = "/{id}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteResource(@PathVariable final Integer id) {
    deleteById(id);
    return ResponseEntity.noContent().build();
  }

}
