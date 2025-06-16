package grafiosch.repository;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.SerializationUtils;

import grafiosch.entities.ProposeChangeField;

/**
 * Abstract base service class for handling proposal request operations. This class provides common functionality for
 * processing change proposals that contain serialized field values which need to be applied to target entities.
 * 
 * The service acts as a bridge between the proposal system and actual entity modification by deserializing proposed
 * changes and applying them to business objects using reflection. This enables the system to preview proposed changes
 * and create modified entity versions for administrative review.
 * 
 * Subclasses should extend this service to implement specific proposal types such as entity change proposals or user
 * task proposals, while inheriting the common field copying functionality.
 *
 * @param <T> the type of proposal entity this service handles
 */
public abstract class ProposeRequestService<T> extends BaseRepositoryImpl<T> {

  /**
   * Applies proposed field changes to a target business entity. This method processes a list of proposed changes by
   * deserializing each field value and setting it on the target entity using reflection. The operation creates a
   * modified version of the entity that represents what the entity would look like if the proposed changes were
   * applied.</br>
   * 
   * Each proposed change field contains:</br>
   * - The field name to be modified</br>
   * - A serialized value representing the proposed new value</br>
   * 
   * The method deserializes each value and uses Apache Commons PropertyUtils to dynamically set the property on the
   * target entity, enabling type-safe property assignment without compile-time knowledge of the entity structure.
   *
   * @param proposeChangeFieldList list of proposed field changes to apply
   * @param targetEntity           the business entity to receive the proposed changes
   */
  protected void copyProposeChangeFieldToBusinessClass(List<ProposeChangeField> proposeChangeFieldList,
      Object targetEntity) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    for (ProposeChangeField proposeChangeField : proposeChangeFieldList) {
      Object value = SerializationUtils.deserialize(proposeChangeField.getValue());
      PropertyUtils.setProperty(targetEntity, proposeChangeField.getField(), value);
    }
  }

}
