package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import grafiosch.BaseConstants;
import grafiosch.entities.TenantBaseID;
import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithTenantResource;
import grafiosch.types.OperationType;

/**
 * Abstract base class for Algo entity REST resources. Overrides tenant validation to use the user's main (actual)
 * tenant ID, enabling shared strategy entities to be created, modified, and deleted from both the main tenant and
 * simulation tenant contexts.
 *
 * <p>
 * Algo strategy entities (AlgoTop, AlgoAssetclass, AlgoSecurity, AlgoStrategy) always belong to the main tenant even
 * when accessed from a simulation environment. The standard tenant check in
 * {@link grafiosch.rest.UpdateCreate#checkAndSetEntityWithTenant} compares against {@code user.getIdTenant()}, which in
 * simulation mode returns the simulation tenant ID. This class overrides that check to use
 * {@code user.getActualIdTenant()}, which always returns the main tenant ID.
 * </p>
 *
 * @param <T> the entity type, must implement TenantBaseID
 */
public abstract class AlgoBaseResource<T extends TenantBaseID> extends UpdateCreateDeleteWithTenantResource<T> {

  private final Class<T> entityType;

  protected AlgoBaseResource(Class<T> entityType) {
    super(entityType);
    this.entityType = entityType;
  }

  /**
   * Validates entity tenant ownership using the user's main tenant ID. Algo entities are shared between main and
   * simulation tenants, so validation must always use the actual (main) tenant rather than the potentially overridden
   * simulation tenant.
   */
  @Override
  protected T checkAndSetEntityWithTenant(T entity, User user) {
    Integer mainIdTenant = user.getActualIdTenant();
    T existingEntity = null;
    if (entity.getIdTenant() != null && !mainIdTenant.equals(entity.getIdTenant())) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    entity.setIdTenant(mainIdTenant);
    if (entity.getId() != null) {
      existingEntity = getUpdateCreateJpaRepository().findById(entity.getId()).orElse(null);
      if (existingEntity != null && !mainIdTenant.equals(existingEntity.getIdTenant())) {
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
      }
    } else {
      return entity;
    }
    return existingEntity;
  }

  /**
   * Deletes an Algo entity using the user's main tenant ID for ownership validation.
   */
  @Override
  @DeleteMapping(value = "/{id}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteResource(@PathVariable Integer id) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    getUpdateCreateJpaRepository().delEntityWithTenant(id, user.getActualIdTenant());
    logAddUpdDel(user.getIdUser(), entityType.getSimpleName(), OperationType.DELETE);
    return ResponseEntity.noContent().build();
  }
}
