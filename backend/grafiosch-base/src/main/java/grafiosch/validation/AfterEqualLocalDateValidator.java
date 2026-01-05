package grafiosch.validation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for {@link java.time.LocalDate} fields using the {@link AfterEqual} annotation.
 * Validates that the date is on or after the specified minimum date.
 */
public class AfterEqualLocalDateValidator implements ConstraintValidator<AfterEqual, LocalDate> {
  private AfterEqual annotation;

  @Override
  public void initialize(AfterEqual constraintAnnotation) {
    this.annotation = constraintAnnotation;
  }

  @Override
  public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    try {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(annotation.format());
      LocalDate afterEqualDate = LocalDate.parse(annotation.value(), formatter);
      return !value.isBefore(afterEqualDate);
    } catch (DateTimeParseException e) {
      return false;
    }
  }
}
