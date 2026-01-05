package grafiosch.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validation annotation that ensures a date field value is on or after a specified minimum date.
 * Supports both {@link java.util.Date} and {@link java.time.LocalDate} field types.
 * Null values are considered valid (use @NotNull for mandatory fields).
 */
@Documented
@Constraint(validatedBy = {AfterEqualDateValidator.class, AfterEqualLocalDateValidator.class})
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
public @interface AfterEqual {

  /**
   * The minimum date value in the format specified by {@link #format()}.
   * The validated field must be on or after this date.
   */
  String value();

  String message() default "{gt.afterEqual}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  /**
   * The date format pattern used to parse the {@link #value()}.
   * For LocalDate fields, only the date portion (yyyy-MM-dd) is used.
   * Default format supports ISO date-time with timezone.
   */
  String format() default "yyyy-MM-dd";
}
