package grafiosch.rest.helper;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.User;
import grafiosch.error.ValidationError;
import grafiosch.exceptions.DataViolation;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;

/**
* Utility interface providing static helper methods for REST API operations.
* 
* <p>This interface contains common utility methods used across REST controllers and
* services for validation, error handling, and security operations. All methods are
* static and designed to be used without instantiation, providing centralized
* functionality for REST API processing.</p>
*/
public interface RestHelper {
  
  
  /**
   * Validates that a username does not match a demo account pattern and throws exception if it does.
   * 
   * <p>This method protects demo accounts from certain operations that could compromise
   * the demonstration environment or expose security vulnerabilities. Demo accounts are
   * typically used for evaluation purposes and should not be able to perform sensitive
   * operations like password changes, account modifications, or data exports.</p>
   *
   * @param demoAccountPattern regular expression pattern to match demo account usernames
   * @param userName the username to validate against the demo account pattern
   * @throws GeneralNotTranslatedWithArgumentsException if the username matches the demo pattern
   */
  public static void isDemoAccount(String demoAccountPattern, String userName) {
    Pattern pattern = Pattern.compile(demoAccountPattern);
    if (pattern.matcher(userName).matches()) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.demo.func.not.available", null);
    }
  }

  /**
   * Converts a DataViolationException into a localized ValidationError for REST API responses.
   * 
   * <p>This method transforms internal validation violations into user-friendly error responses
   * with proper internationalization support. It handles locale resolution, message translation,
   * and field name localization to provide comprehensive error information for REST API clients.</p>
   * 
   * <p><strong>Locale Resolution Strategy:</strong></p>
   * <ol>
   *   <li><strong>Security Context:</strong> Uses authenticated user's locale preference if available</li>
   *   <li><strong>Exception Locale:</strong> Falls back to locale string provided in the exception</li>
   *   <li><strong>Default Locale:</strong> Uses English as the final fallback if no locale is available</li>
   * </ol>
   * 
   * <p><strong>Message Translation Process:</strong></p>
   * <p>For each validation violation in the exception:</p>
   * <ol>
   *   <li>Resolves the error message using the message key and data parameters</li>
   *   <li>Translates the field name if translation is enabled for that field</li>
   *   <li>Adds the localized field error to the validation error response</li>
   * </ol>
   * 
   * <p><strong>Field Name Translation:</strong></p>
   * <p>Field names can be optionally translated based on the translateFieldName flag in each
   * violation. This allows technical field names to be converted to user-friendly labels
   * while preserving technical identifiers where appropriate.</p>
   * 
   * <p><strong>Error Response Structure:</strong></p>
   * <p>The resulting ValidationError contains a collection of field-specific error messages
   * that can be easily consumed by REST API clients for form validation feedback and
   * user interface error display.</p>
   * 
   * <p><strong>Thread Safety:</strong></p>
   * <p>This method accesses the Spring Security context in a thread-safe manner and handles
   * cases where the security context may not be available or may contain non-User authentication
   * details.</p>
   * 
   * @param dvex the DataViolationException containing validation violations to process
   * @param messageSource Spring MessageSource for resolving localized messages and field names
   * @return ValidationError object containing localized field errors suitable for REST API responses
   */
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
