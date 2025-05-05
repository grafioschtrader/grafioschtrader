package grafioschtrader.validation;

import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import grafioschtrader.GlobalConstants;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidCurrencyCodeValidator implements ConstraintValidator<ValidCurrencyCode, String> {

  private boolean optional;

  // 1) Precompute all ISO-4217 fiat codes (e.g. USD, CHF, EUR)
  private static final Set<String> FIAT_CODES = Currency.getAvailableCurrencies().stream()
      .map(Currency::getCurrencyCode).collect(Collectors.toSet());

  // 2) Combine fiat codes with your crypto set (ensure upper-case)
  private static final Set<String> ALL_CODES;
  static {
    Set<String> tmp = new java.util.HashSet<>(FIAT_CODES);
    tmp.addAll(GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.stream().map(String::toUpperCase).collect(Collectors.toSet()));
    ALL_CODES = java.util.Collections.unmodifiableSet(tmp);
  }

  @Override
  public void initialize(ValidCurrencyCode constraint) {
    // Read the 'optional' flag from the annotation
    this.optional = constraint.optional();
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    // 3) If the value is null or empty, it's valid only if optional=true
    if (StringUtils.isBlank(value)) {
      return optional;
    }

    // 4) Normalize: trim whitespace and upper-case using a fixed locale
    String code = value.trim().toUpperCase(Locale.ROOT);

    // 5) Fast lookup in the precomputed set
    return ALL_CODES.contains(code);
  }
}