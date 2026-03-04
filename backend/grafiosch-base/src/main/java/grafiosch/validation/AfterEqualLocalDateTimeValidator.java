package grafiosch.validation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for {@link java.time.LocalDateTime} fields using the {@link AfterEqual} annotation.
 * Validates that the date portion of the datetime is on or after the specified minimum date.
 */
public class AfterEqualLocalDateTimeValidator implements ConstraintValidator<AfterEqual, LocalDateTime> {
  private AfterEqual annotation;

  @Override
  public void initialize(AfterEqual constraintAnnotation) {
    this.annotation = constraintAnnotation;
  }

  @Override
  public boolean isValid(LocalDateTime value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    try {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(annotation.format());
      LocalDate afterEqualDate = LocalDate.parse(annotation.value(), formatter);
      return !value.toLocalDate().isBefore(afterEqualDate);
    } catch (DateTimeParseException e) {
      return false;
    }
  }
}
