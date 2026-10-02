package grafiosch.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Class-level constraint for a group of optional fields of which at least one must be entered, for example the
 * thresholds of an alert where any single one is enough but none at all describes nothing. A string counts as entered
 * only when it is not blank.
 *
 * <p>
 * The dynamic form definition passes the constraint on to the frontend as
 * {@link grafiosch.dynamic.model.ConstraintValidatorType#AtLeastOneNotNull}, which turns the named fields into one form
 * group carrying the same rule. That keeps the form and the server from disagreeing about a form the server rejects.
 * </p>
 */
@Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AtLeastOneNotNullValidator.class)
@Documented
public @interface AtLeastOneNotNull {
  String message() default "{at.least.one.not.null}";

  /** Names of the fields of which at least one must hold a value. */
  String[] fields();

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
