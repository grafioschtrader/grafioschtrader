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

/**
 * Utility class providing helper methods for data manipulation and reflection.
 */
public class DataHelper {

  /**
   * Rounds a double value to a specified number of decimal places.
   *
   * @param valueToRound          The double value to round.
   * @param numberOfDecimalPlaces The number of decimal places to round to.
   * @return The rounded double value.
   */
  public static double round(double valueToRound, int numberOfDecimalPlaces) {
    double multipicationFactor = Math.pow(10, numberOfDecimalPlaces);
    double interestedInZeroDPs = valueToRound * multipicationFactor;
    return Math.round(interestedInZeroDPs) / multipicationFactor;
  }

  /**
   * Generates a string representation of an object, including its field names and non-null values.
   *
   * @param object The object to convert to a string.
   * @return A string representation of the object with its attributes.
   */
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
   * Sets empty string properties of an object to null and trims trailing spaces from non-empty string properties.
   *
   * @param object The object whose string properties are to be processed.
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

  /**
   * Generates a Globally Unique Identifier (GUID) string. Dashes are removed from the standard UUID format.
   *
   * @return A GUID string without dashes.
   */
  public static String generateGUID() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  /**
   * Compares the properties of two entities of the same class that are annotated with specified update property level
   * annotations. It returns a list of {@link ProposeChangeField} objects representing the differences. This is not a
   * deep comparison.
   *
   * @param newEntity                  The new entity with potentially changed values.
   * @param existingEntity             The existing entity to compare against.
   * @param updatePropertyLevelClasses A set of annotation classes that mark the properties to be compared.
   * @return A list of {@link ProposeChangeField} objects detailing the differing properties and their new values.
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

  /**
   * Checks if a field is annotated with any of the specified annotation classes.
   *
   * @param field                      The field to check.
   * @param updatePropertyLevelClasses A set of annotation classes to look for.
   * @return {@code true} if the field is annotated with at least one of the specified annotations, {@code false}
   *         otherwise.
   */
  public static boolean fieldContainsAnnotation(Field field,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    for (Class<? extends Annotation> annotationClass : updatePropertyLevelClasses) {
      if (field.getAnnotation(annotationClass) != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Retrieves all fields of a class that are annotated with any of the specified annotation classes.
   *
   * @param clazz                      The class to inspect.
   * @param updatePropertyLevelClasses A set of annotation classes that mark the properties to be retrieved.
   * @return A list of {@link Field} objects that are annotated with at least one of the specified annotations.
   */
  public static List<Field> getFieldByPropertiesAnnotation(Class<?> clazz,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    return FieldUtils.getAllFieldsList(clazz).stream()
        .filter(field -> fieldContainsAnnotation(field, updatePropertyLevelClasses)).collect(Collectors.toList());

  }

  /**
   * Copies properties from a source object to a target object, but only for fields in the target object that are
   * annotated with specified update property level annotations. If a field is annotated with
   * {@link PropertySelectiveUpdatableOrWhenNull} and the corresponding property in the target is not null, it will not
   * be overwritten.
   *
   * @param source                     The source object from which to copy properties.
   * @param target                     The target object to which properties are copied.
   * @param updatePropertyLevelClasses A set of annotation classes that mark the properties to be updated in the target
   *                                   object.
   */
  public static void updateEntityWithUpdatable(Object source, Object target,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    updateEntityWithUpdatable(source, new Object[] { target }, updatePropertyLevelClasses);
  }

  /**
   * Copies properties from a source object to an array of target objects, applying the same logic as
   * {@link #updateEntityWithUpdatable(Object, Object, Set)}.
   *
   * @param <T>                        The type of the annotation.
   * @param source                     The source object from which to copy properties.
   * @param targets                    An array of target objects to which properties are copied.
   * @param updatePropertyLevelClasses A set of annotation classes that mark the properties to be updated in the target
   *                                   objects.
   */
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
