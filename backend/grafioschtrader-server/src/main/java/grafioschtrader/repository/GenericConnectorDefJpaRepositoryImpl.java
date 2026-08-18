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

  /** One endpoint per feed support: history, intraday, dividend and split. */
  private static final int MAX_ENDPOINTS = 4;

  /** Request headers of a single connector. Generous for an HTTP client, far below a usable flooding vector. */
  private static final int MAX_HTTP_HEADERS = 20;

  /** Column or JSON path mappings of one endpoint. */
  private static final int MAX_FIELD_MAPPINGS = 40;

  @Autowired
  private GenericConnectorDefJpaRepository genericConnectorDefJpaRepository;

  @Override
  public GenericConnectorDef saveOnlyAttributes(final GenericConnectorDef entity, final GenericConnectorDef existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {

    validateCollectionSizes(entity);

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
   * Bounds the cascaded child collections of one request. The parent carries a daily budget of its own, but the three
   * collections below are {@code CascadeType.ALL} without a {@code @Size}, so a single accepted request could insert an
   * arbitrary number of rows into {@code generic_connector_endpoint},
   * {@code generic_connector_http_header} and {@code generic_connector_field_mapping}.
   *
   * <p>
   * The counts are structural rather than administrator-tunable - they follow from what a connector definition can
   * meaningfully hold - so they are constants here and not {@code entity_limit} keys.
   * </p>
   *
   * @param entity the posted connector definition, before any child row is written
   * @throws DataViolationException when a collection exceeds its cap
   */
  private void validateCollectionSizes(GenericConnectorDef entity) {
    checkSize(entity.getEndpoints(), MAX_ENDPOINTS, "generic.connector.endpoint", "gt.connector.endpoint.too.many");
    checkSize(entity.getHttpHeaders(), MAX_HTTP_HEADERS, "generic.connector.http.header",
        "gt.connector.http.header.too.many");
    if (entity.getEndpoints() != null) {
      for (GenericConnectorEndpoint endpoint : entity.getEndpoints()) {
        checkSize(endpoint.getFieldMappings(), MAX_FIELD_MAPPINGS, "generic.connector.field.mapping",
            "gt.connector.field.mapping.too.many");
      }
    }
  }

  private void checkSize(List<?> collection, int max, String field, String messageKey) {
    if (collection != null && collection.size() > max) {
      throw new DataViolationException(field, messageKey, new Object[] { max });
    }
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
