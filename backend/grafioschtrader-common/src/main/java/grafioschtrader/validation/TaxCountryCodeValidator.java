package grafioschtrader.validation;

import java.util.Locale;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Uses the same ISO registry as the backend country option endpoint. */
public class TaxCountryCodeValidator implements ConstraintValidator<ValidTaxCountryCode, String> {
  public static final Set<String> CODES = Set.of(Locale.getISOCountries());

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || CODES.contains(value);
  }
}
