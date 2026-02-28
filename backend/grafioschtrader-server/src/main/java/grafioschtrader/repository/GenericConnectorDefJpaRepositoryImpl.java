package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.common.DataHelper;
import grafiosch.common.LockedWhenUsed;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.RepositoryHelper;
import grafioschtrader.entities.GenericConnectorDef;
import grafioschtrader.entities.GenericConnectorEndpoint;
import grafioschtrader.entities.GenericConnectorFieldMapping;
import grafioschtrader.entities.GenericConnectorHttpHeader;

/**
 * Implementation of custom repository methods for GenericConnectorDef. Handles selective attribute updates based on
 * connector activation state, usage-based permission validation, and wires bidirectional parent references before
 * persisting.
 */
public class GenericConnectorDefJpaRepositoryImpl extends BaseRepositoryImpl<GenericConnectorDef>
    implements GenericConnectorDefJpaRepositoryCustom {

  @Autowired
  private GenericConnectorDefJpaRepository genericConnectorDefJpaRepository;

  @Override
  public GenericConnectorDef saveOnlyAttributes(final GenericConnectorDef entity, final GenericConnectorDef existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {

    // Usage-based permission check for non-admin users
    if (existingEntity != null) {
      User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
      if (!UserAccessHelper.hasHigherPrivileges(user)) {
        validateUsageBasedPermissions(entity, existingEntity);
      }
    }

    if (existingEntity != null) {
      // Sync nested collections that updateEntityWithUpdatable cannot handle
      existingEntity.getEndpoints().clear();
      existingEntity.getEndpoints().addAll(entity.getEndpoints());
      existingEntity.getHttpHeaders().clear();
      existingEntity.getHttpHeaders().addAll(entity.getHttpHeaders());
      // Wire parent references to the managed entity
      setParentReferences(existingEntity);
    } else {
      setParentReferences(entity);
    }
    return RepositoryHelper.saveOnlyAttributes(genericConnectorDefJpaRepository, entity, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  public Set<Class<? extends Annotation>> getUpdatePropertyLevels(final GenericConnectorDef existingEntity) {
    if (existingEntity.isActivated()) {
      return Set.of(PropertyAlwaysUpdatable.class);
    }
    return Set.of(PropertySelectiveUpdatableOrWhenNull.class, PropertyAlwaysUpdatable.class);
  }

  /**
   * Validates that a non-admin creator respects usage-based locks. When any endpoint has been used successfully,
   * the creator cannot change def-level fields or HTTP headers. Used endpoints themselves cannot be modified or deleted.
   */
  private void validateUsageBasedPermissions(GenericConnectorDef incoming, GenericConnectorDef existing) {
    boolean anyEndpointUsed = existing.getEndpoints().stream().anyMatch(GenericConnectorEndpoint::isEverUsedSuccessfully);

    if (anyEndpointUsed) {
      validateNoDefOrHeaderChanges(incoming, existing);
    }
    validateNoUsedEndpointChanges(incoming, existing);
  }

  /**
   * Compares def-level fields (via {@link LockedWhenUsed} annotation) and HTTP headers between incoming and existing.
   * Throws if any differ.
   */
  private void validateNoDefOrHeaderChanges(GenericConnectorDef incoming, GenericConnectorDef existing) {
    if (!DataHelper.areAnnotatedFieldsEqual(incoming, existing, LockedWhenUsed.class)
        || !headersEqual(incoming.getHttpHeaders(), existing.getHttpHeaders())) {
      throw new DataViolationException("generic.connector.def", "gt.connector.def.locked", null);
    }
  }

  /**
   * Checks that used endpoints in existing are still present in incoming and their {@link LockedWhenUsed} config fields
   * have not changed.
   */
  private void validateNoUsedEndpointChanges(GenericConnectorDef incoming, GenericConnectorDef existing) {
    for (GenericConnectorEndpoint existingEp : existing.getEndpoints()) {
      if (!existingEp.isEverUsedSuccessfully()) {
        continue;
      }
      GenericConnectorEndpoint incomingEp = incoming.getEndpoints().stream()
          .filter(ep -> existingEp.getIdEndpoint() != null && existingEp.getIdEndpoint().equals(ep.getIdEndpoint()))
          .findFirst().orElse(null);
      if (incomingEp == null || !DataHelper.areAnnotatedFieldsEqual(incomingEp, existingEp, LockedWhenUsed.class)) {
        throw new DataViolationException("generic.connector.endpoint", "gt.connector.endpoint.locked", null);
      }
    }
  }

  private boolean headersEqual(List<GenericConnectorHttpHeader> a, List<GenericConnectorHttpHeader> b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    if (a.size() != b.size()) return false;
    for (int i = 0; i < a.size(); i++) {
      if (!Objects.equals(a.get(i).getHeaderName(), b.get(i).getHeaderName())
          || !Objects.equals(a.get(i).getHeaderValue(), b.get(i).getHeaderValue())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Sets bidirectional parent references on child entities after deserialization. Jackson does not set the @JsonIgnore
   * back-references, so we must wire them manually before persisting.
   */
  private void setParentReferences(GenericConnectorDef connectorDef) {
    if (connectorDef.getEndpoints() != null) {
      for (GenericConnectorEndpoint endpoint : connectorDef.getEndpoints()) {
        endpoint.setGenericConnectorDef(connectorDef);
        if (endpoint.getFieldMappings() != null) {
          for (GenericConnectorFieldMapping mapping : endpoint.getFieldMappings()) {
            mapping.setEndpoint(endpoint);
          }
        }
      }
    }
    if (connectorDef.getHttpHeaders() != null) {
      for (GenericConnectorHttpHeader header : connectorDef.getHttpHeaders()) {
        header.setGenericConnectorDef(connectorDef);
      }
    }
  }
}
