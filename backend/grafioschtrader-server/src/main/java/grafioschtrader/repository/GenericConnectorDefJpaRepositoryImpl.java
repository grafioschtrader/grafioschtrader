package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.RepositoryHelper;
import grafioschtrader.entities.GenericConnectorDef;
import grafioschtrader.entities.GenericConnectorEndpoint;
import grafioschtrader.entities.GenericConnectorFieldMapping;
import grafioschtrader.entities.GenericConnectorHttpHeader;

/**
 * Implementation of custom repository methods for GenericConnectorDef. Handles selective attribute updates based on
 * connector activation state and wires bidirectional parent references before persisting.
 */
public class GenericConnectorDefJpaRepositoryImpl extends BaseRepositoryImpl<GenericConnectorDef>
    implements GenericConnectorDefJpaRepositoryCustom {

  @Autowired
  private GenericConnectorDefJpaRepository genericConnectorDefJpaRepository;

  @Override
  public GenericConnectorDef saveOnlyAttributes(final GenericConnectorDef entity, final GenericConnectorDef existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
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
