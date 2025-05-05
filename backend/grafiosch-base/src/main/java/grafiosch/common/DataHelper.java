package grafiosch.common;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.reflect.FieldUtils;

import grafiosch.entities.ProposeChangeField;

public class DataHelper {

  public static double round(double valueToRound, int numberOfDecimalPlaces) {
    double multipicationFactor = Math.pow(10, numberOfDecimalPlaces);
    double interestedInZeroDPs = valueToRound * multipicationFactor;
    return Math.round(interestedInZeroDPs) / multipicationFactor;
  }

  public static String toStringWithAttributes(Object object) {
    ReflectionToStringBuilder builder = new ReflectionToStringBuilder(object, ToStringStyle.DEFAULT_STYLE) {
      @Override
      protected boolean accept(Field field) {
        try {
          return super.accept(field) && field.get(object) != null;
        } catch (IllegalAccessException e) {
          return super.accept(field);
        }
      }
    };
    return builder.toString();
  }

  /**
   * Properties with empty string of an object are set to null.
   *
   * @param object Object which empty strings are set to null.
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws NoSuchMethodException
   */
  public static void setEmptyStringToNullOrRemoveTraillingSpaces(Object object)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

    PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(object);
    for (PropertyDescriptor property : propertyDescriptors) {
      if (property.getPropertyType() == String.class && property.getWriteMethod() != null) {
        String valueStr = (String) PropertyUtils.getProperty(object, property.getName());
        if (valueStr != null) {
          valueStr = valueStr.trim();
          if (valueStr.isEmpty()) {
            PropertyUtils.setProperty(object, property.getName(), null);
          } else {
            PropertyUtils.setProperty(object, property.getName(), valueStr);
          }
        }
      }
    }
  }

  public static String generateGUID() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  /**
   * Compare the value of field of two objects of the same class. It is not a deep
   * comparison and only certain fields are compared.
   */
  public static List<ProposeChangeField> getDiffPropertiesOfEntity(Object newEntity, Object existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    List<ProposeChangeField> proposeChangeFieldList = new ArrayList<>();

    List<Field> fields = FieldUtils.getAllFieldsList(newEntity.getClass());
    for (Field field : fields) {
      if (fieldContainsAnnotation(field, updatePropertyLevelClasses)) {
        String name = field.getName();
        Object valueNew = PropertyUtils.getProperty(newEntity, name);
        Object valueExisting = PropertyUtils.getProperty(existingEntity, name);

        if (!Objects.equals(valueNew, valueExisting)) {
          proposeChangeFieldList
              .add(new ProposeChangeField(name, SerializationUtils.serialize((Serializable) valueNew)));
        }
      }
    }
    return proposeChangeFieldList;
  }

  public static boolean fieldContainsAnnotation(Field field,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    for (Class<? extends Annotation> annotationClass : updatePropertyLevelClasses) {
      if (field.getAnnotation(annotationClass) != null) {
        return true;
      }
    }
    return false;
  }

  public static List<Field> getFieldByPropertiesAnnotation(Class<?> clazz,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    return FieldUtils.getAllFieldsList(clazz).stream()
        .filter(field -> fieldContainsAnnotation(field, updatePropertyLevelClasses)).collect(Collectors.toList());

  }

  /**
   * Copy properties from the source to the target object.
   */
  public static void updateEntityWithUpdatable(Object source, Object target,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    updateEntityWithUpdatable(source, new Object[] { target }, updatePropertyLevelClasses);
  }

  public static <T extends Annotation> void updateEntityWithUpdatable(Object source, Object targets[],
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    for (Object target : targets) {
      List<Field> fields = FieldUtils.getAllFieldsList(target.getClass());
      for (Field field : fields) {
        if (fieldContainsAnnotation(field, updatePropertyLevelClasses)) {
          if (fieldContainsAnnotation(field, Set.of(PropertySelectiveUpdatableOrWhenNull.class))
              && PropertyUtils.getProperty(target, field.getName()) != null) {
            // copy value to target when value was not set in target
            continue;
          }
          Object sourceValue = PropertyUtils.getProperty(source, field.getName());
          PropertyUtils.setProperty(target, field.getName(), sourceValue);
        }
      }
    }
  }

}
