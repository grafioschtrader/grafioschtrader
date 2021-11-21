package grafioschtrader.rest.helper;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.entities.User;
import grafioschtrader.error.ValidationError;
import grafioschtrader.exceptions.DataViolation;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.exceptions.GeneralNotTranslatedWithArgumentsException;

public interface RestHelper {
  public static void isDemoAccount(String demoAccountPattern, String userName) {
    Pattern pattern = Pattern.compile(demoAccountPattern);
    if (pattern.matcher(userName).matches()) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.demo.func.not.available", null);
    }
  }

  public static ValidationError createValidationError(final DataViolationException dvex, MessageSource messageSource) {
    Object user = SecurityContextHolder.getContext().getAuthentication().getDetails();
    Locale locale = user instanceof User ? ((User) user).createAndGetJavaLocale()
        : dvex.getLocaleStr() != null ? Locale.forLanguageTag(dvex.getLocaleStr()) : Locale.ENGLISH;
    final ValidationError validationErrorDTO = new ValidationError();

    for (final DataViolation dataViolation : dvex.getDataViolation()) {
      final String localizedErrorMessage = messageSource.getMessage(dataViolation.getMessageKey(),
          dataViolation.getData(), locale);
      final String field = dataViolation.isTranslateFieldName()
          ? messageSource.getMessage(dataViolation.getField(), null, locale)
          : dataViolation.getField();
      validationErrorDTO.addFieldError(field, localizedErrorMessage);
    }
    return validationErrorDTO;
  }
}
