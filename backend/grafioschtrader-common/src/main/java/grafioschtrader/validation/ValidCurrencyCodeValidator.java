package grafioschtrader.validation;

import java.util.Currency;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

import grafioschtrader.GlobalConstants;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidCurrencyCodeValidator implements ConstraintValidator<ValidCurrencyCode, String> {

  private Boolean isOptional;

  @Override
  public void initialize(ValidCurrencyCode validCurrencyCode) {
    this.isOptional = validCurrencyCode.optional();
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
    boolean containsIsoCode = false;
    if (value != null) {
      containsIsoCode = GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.contains(value) || Currency.getAvailableCurrencies()
          .stream().map(Currency::getCurrencyCode).anyMatch(Predicate.isEqual(value));
    }
    return isOptional ? (containsIsoCode || (StringUtils.isEmpty(value))) : containsIsoCode;
  }
}