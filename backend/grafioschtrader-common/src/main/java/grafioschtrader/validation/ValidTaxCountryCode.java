package grafioschtrader.validation;

import java.lang.annotation.*;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Explicit ISO country metadata; unknown remains null. */
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TaxCountryCodeValidator.class)
public @interface ValidTaxCountryCode {
  String message() default "{gt.tax.country.invalid}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
