package grafioschtrader.repository;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.SerializationUtils;

import grafioschtrader.entities.ProposeChangeField;

public abstract class ProposeRequestService<T> extends BaseRepositoryImpl<T> {

  protected void copyProposeChangeFieldToBusinessClass(List<ProposeChangeField> proposeChangeFieldList,
      Object targetEntity) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    for (ProposeChangeField proposeChangeField : proposeChangeFieldList) {
      Object value = SerializationUtils.deserialize(proposeChangeField.getValue());
      PropertyUtils.setProperty(targetEntity, proposeChangeField.getField(), value);
    }
  }

}
