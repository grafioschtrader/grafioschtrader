package grafiosch.validation;

import java.lang.reflect.Field;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator of {@link AtLeastOneNotNull}: valid as soon as one of the named fields holds a value.
 */
public class AtLeastOneNotNullValidator implements ConstraintValidator<AtLeastOneNotNull, Object> {

  private String[] fields;

  @Override
  public void initialize(AtLeastOneNotNull atLeastOneNotNull) {
    fields = atLeastOneNotNull.fields();
  }

  @Override
  public boolean isValid(Object object, ConstraintValidatorContext constraintValidatorContext) {
    if (object == null) {
      return true;
    }
    for (String fieldName : fields) {
      Object value = getFieldValue(object, fieldName);
      if (value instanceof String s ? !s.isBlank() : value != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Reads a field of the validated object, also when it is declared by a superclass.
   *
   * @param object    the validated object
   * @param fieldName the field to read
   * @return the field value
   * @throws IllegalStateException if the annotation names a field the class does not have, which is a programming error
   *                               that must not pass as a violation of the user's input
   */
  static Object getFieldValue(Object object, String fieldName) {
    for (Class<?> c = object.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
      try {
        Field field = c.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
      } catch (NoSuchFieldException e) {
        // continue with the superclass
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("Field " + fieldName + " of " + object.getClass().getName() + " cannot be read",
            e);
      }
    }
    throw new IllegalStateException(object.getClass().getName() + " has no field " + fieldName);
  }

}
