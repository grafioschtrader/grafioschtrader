package grafioschtrader.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotation to validate the ISO Currency code and some crypto currencies.
 *
 */
@Target({ FIELD, PARAMETER })
@Retention(RUNTIME)
@Constraint(validatedBy = ValidCurrencyCodeValidator.class)
@Documented
public @interface ValidCurrencyCode {
  String message() default "{gt.validCurrencyCode}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  boolean optional() default false;
}