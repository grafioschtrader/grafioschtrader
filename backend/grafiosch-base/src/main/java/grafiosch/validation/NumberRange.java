package grafiosch.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Class-level constraint for a lower and an upper numeric bound: when both are entered, the lower one must be strictly
 * below the upper one. Either bound may be left empty; whether at least one is required is expressed separately with
 * {@link AtLeastOneNotNull}.
 *
 * <p>
 * The dynamic form definition passes the constraint on to the frontend as
 * {@link grafiosch.dynamic.model.ConstraintValidatorType#NumberRange}.
 * </p>
 */
@Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NumberRangeValidator.class)
@Documented
public @interface NumberRange {
  String message() default "{lower.below.upper}";

  /** Name of the field holding the lower bound. */
  String lower();

  /** Name of the field holding the upper bound. */
  String upper();

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
