package grafiosch.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator of {@link NumberRange}: an empty bound is always valid, two entered bounds must be strictly ascending.
 */
public class NumberRangeValidator implements ConstraintValidator<NumberRange, Object> {

  private String lowerField;
  private String upperField;

  @Override
  public void initialize(NumberRange numberRange) {
    lowerField = numberRange.lower();
    upperField = numberRange.upper();
  }

  @Override
  public boolean isValid(Object object, ConstraintValidatorContext constraintValidatorContext) {
    if (object == null) {
      return true;
    }
    Object lower = AtLeastOneNotNullValidator.getFieldValue(object, lowerField);
    Object upper = AtLeastOneNotNullValidator.getFieldValue(object, upperField);
    if (lower instanceof Number l && upper instanceof Number u) {
      return l.doubleValue() < u.doubleValue();
    }
    return true;
  }

}
