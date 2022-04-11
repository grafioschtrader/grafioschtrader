package grafioschtrader.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class NonZeroFloatValidator implements ConstraintValidator<NonZeroFloatConstraint, Float> {
  @Override
  public void initialize(NonZeroFloatConstraint cons) {
  }

  @Override
  public boolean isValid(Float f, ConstraintValidatorContext cxt) {
    return Float.compare(f, 0.0f) != 0;
  }
}