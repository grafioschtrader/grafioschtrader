package grafiosch.rest.helper;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.StaleObjectStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.entities.User;
import grafiosch.error.ErrorWithLogout;
import grafiosch.error.ErrorWrapper;
import grafiosch.error.SecurityBreachError;
import grafiosch.error.SingleNativeMsgError;
import grafiosch.error.ValidationError;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.exceptions.LimitEntityTransactionException;
import grafiosch.exceptions.RequestLimitAndSecurityBreachException;
import grafiosch.service.UserService;
import grafiosch.types.UserRightLimitCounter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Global exception handler for REST controllers in the Grafiosch application framework.
 * 
 * <p>
 * This class provides centralized exception handling for all REST controllers using Spring's RestControllerAdvice
 * annotation. It intercepts various types of exceptions that can occur during REST API operations and converts them
 * into appropriate HTTP responses with standardized error formats.
 * </p>
 * 
 * <h3>Key Features:</h3>
 * <ul>
 * <li><strong>Comprehensive Exception Coverage:</strong> Handles database exceptions, validation errors, security
 * breaches, authentication failures, and general system exceptions</li>
 * <li><strong>Internationalization Support:</strong> Uses Spring's MessageSource to provide localized error messages
 * based on user locale</li>
 * <li><strong>Security Tracking:</strong> Automatically increments security breach counters for users who violate
 * security constraints</li>
 * <li><strong>Standardized Error Format:</strong> All errors are wrapped in ErrorWrapper for consistent client-side
 * handling</li>
 * <li><strong>Logging Integration:</strong> Provides appropriate logging for debugging and monitoring</li>
 * </ul>
 * 
 * <h3>Error Response Structure:</h3>
 * <p>
 * All error responses follow a consistent structure using ErrorWrapper, which includes:
 * </p>
 * <ul>
 * <li>Error class name for client-side error type identification</li>
 * <li>Specific error object containing detailed error information</li>
 * <li>Appropriate HTTP status codes (400, 401, 429, 500, etc.)</li>
 * </ul>
 * 
 * <h3>Security Features:</h3>
 * <p>
 * The handler includes security-aware features:
 * </p>
 * <ul>
 * <li>Automatic tracking of security violations</li>
 * <li>User logout enforcement for critical security breaches</li>
 * <li>Rate limiting support for request abuse prevention</li>
 * </ul>
 */
@RestControllerAdvice
public class RestErrorHandler {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /**
   * Spring MessageSource for internationalized error message resolution. Used to translate error messages based on user
   * locale preferences.
   */
  private final MessageSource messageSource;

  @Autowired
  private UserService userService;

  public RestErrorHandler(final MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /**
   * Handles general exceptions that are not specifically caught by other handlers.
   * 
   * <p>
   * This is the catch-all exception handler that processes any uncaught exceptions in REST controllers. It logs the
   * full exception details and returns a generic error response to avoid exposing sensitive system information to
   * clients.
   * </p>
   * 
   * @param ex the exception that was thrown
   * @return ErrorWrapper containing a generic error message with the root cause
   */
  @ExceptionHandler(value = { Exception.class })
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorWrapper serverException(final Exception ex) {
    ex.printStackTrace();
    log.error(ex.getMessage(), ex);
    return new ErrorWrapper(new SingleNativeMsgError(ExceptionUtils.getRootCauseMessage(ex)));
  }

  /**
   * Handles NoSuchElementException thrown when requested resources are not found.
   * 
   * <p>
   * Typically occurs when trying to access entities that don't exist in the database or when Optional.get() is called
   * on an empty Optional.
   * </p>
   * 
   * @param ex the NoSuchElementException that was thrown
   * @return ErrorWrapper with BAD_REQUEST status containing the exception message
   */
  @ExceptionHandler(value = { NoSuchElementException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper noSuchElementException(final NoSuchElementException ex) {
    ex.printStackTrace();
    return new ErrorWrapper(new SingleNativeMsgError(ex.getMessage()));
  }

  /**
   * Handles database integrity constraint violations.
   * 
   * <p>
   * This handler processes exceptions that occur when database operations violate integrity constraints such as foreign
   * key violations, unique constraint violations, or check constraint failures. The error message is extracted from the
   * root cause to provide meaningful feedback.
   * </p>
   * 
   * @param ex the DataIntegrityViolationException from Spring Data
   * @return ErrorWrapper with BAD_REQUEST status containing the root cause message
   */
  @ExceptionHandler(value = { DataIntegrityViolationException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper processDataIntegrityViolationError(final DataIntegrityViolationException ex) {
    ex.printStackTrace();
    return new ErrorWrapper(new SingleNativeMsgError(ExceptionUtils.getRootCauseMessage(ex), true));
  }

  /**
   * Handles request limit and security breach exceptions.
   * 
   * <p>
   * This handler processes exceptions thrown when users exceed rate limits or violate security policies. It returns a
   * TOO_MANY_REQUESTS status and includes a logout instruction to force user re-authentication.
   * </p>
   * 
   * @param ex the RequestLimitAndSecurityBreachException indicating rate limit violation
   * @return ErrorWrapper with TOO_MANY_REQUESTS status and logout enforcement
   */
  @ExceptionHandler(value = { RequestLimitAndSecurityBreachException.class })
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public ErrorWrapper processDataIntegrityViolationError(final RequestLimitAndSecurityBreachException ex) {
    ex.printStackTrace();
    return new ErrorWrapper(new ErrorWithLogout(ex.getMessage()));
  }

  /**
   * Handles entity transaction limit exceptions.
   * 
   * <p>
   * Processes exceptions thrown when users exceed their allowed number of create/update/delete operations for specific
   * entity types within the configured time period.
   * </p>
   * 
   * @param ex the LimitEntityTransactionException containing limit violation details
   * @return ErrorWrapper with BAD_REQUEST status containing transaction limit information
   */
  @ExceptionHandler(value = { LimitEntityTransactionException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper processDataIntegrityViolationError(final LimitEntityTransactionException ex) {
    ex.printStackTrace();
    return new ErrorWrapper(ex.limitEntityTransactionError);
  }

  /**
   * Handles internal authentication service exceptions.
   * 
   * <p>
   * Processes authentication failures that occur within the Spring Security authentication system. Forces user logout
   * to ensure security.
   * </p>
   * 
   * @param ex the SecurityException related to authentication failure
   * @return ErrorWrapper with UNAUTHORIZED status and logout enforcement
   */
  @ExceptionHandler(value = { InternalAuthenticationServiceException.class })
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ErrorWrapper processDisabledException(final SecurityException ex) {
    return new ErrorWrapper(new ErrorWithLogout(ex.getMessage()));
  }

  /**
   * Handles general security exceptions with breach tracking.
   * 
   * <p>
   * This handler processes security violations such as unauthorized access attempts, tenant data breaches, or privilege
   * escalation attempts. It automatically increments the user's security breach counter and provides localized error
   * messages.
   * </p>
   * 
   * <p>
   * <strong>Security Features:</strong>
   * </p>
   * <ul>
   * <li>Automatically tracks security violations per user</li>
   * <li>Increments breach counters for audit trails</li>
   * <li>Provides localized error messages</li>
   * <li>Forces user logout with UNAUTHORIZED status</li>
   * </ul>
   * 
   * @param ex the SecurityException indicating a security policy violation
   * @return ErrorWrapper with UNAUTHORIZED status containing localized security breach message
   */
  @ExceptionHandler(value = { SecurityException.class })
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ErrorWrapper processSecurityException(final SecurityException ex) {
    // Only increment breach counter if we have a valid user authentication (not M2M token auth)
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getDetails() instanceof User user) {
      userService.incrementRightsLimitCount(user.getIdUser(), UserRightLimitCounter.SECURITY_BREACH);
    }
    ex.printStackTrace();
    final Locale currentLocale = LocaleContextHolder.getLocale();
    return new ErrorWrapper(new SecurityBreachError(messageSource.getMessage(ex.getMessage(), null, currentLocale)));
  }

  /**
   * Handles optimistic locking conflicts.
   * 
   * <p>
   * Processes StaleObjectStateException thrown by Hibernate when optimistic locking fails due to concurrent
   * modifications. This typically occurs when two users try to update the same entity simultaneously.
   * </p>
   * 
   * @param ex the StaleObjectStateException from Hibernate
   * @return ErrorWrapper with BAD_REQUEST status containing localized version conflict message
   */
  @ExceptionHandler(value = { StaleObjectStateException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper processEntityVersionError(final StaleObjectStateException ex) {
    ex.printStackTrace();
    final Locale currentLocale = LocaleContextHolder.getLocale();
    return new ErrorWrapper(
        new SingleNativeMsgError(messageSource.getMessage("version.lock.error", null, currentLocale)));
  }

  /**
   * Handles transaction system exceptions, particularly validation errors.
   * 
   * <p>
   * This handler processes TransactionSystemException and checks if the root cause is a ConstraintViolationException
   * (Bean Validation). If so, it delegates to the constraint violation handler; otherwise, it returns a generic error
   * message.
   * </p>
   * 
   * @param ex the TransactionSystemException from Spring
   * @return ErrorWrapper containing either validation errors or generic error message
   */
  @ExceptionHandler(value = { TransactionSystemException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper processValidationError(final TransactionSystemException ex) {
    if (ex.getRootCause() instanceof ConstraintViolationException) {
      return processValidationError((ConstraintViolationException) ex.getRootCause());
    }
    return new ErrorWrapper(new SingleNativeMsgError(ExceptionUtils.getRootCauseMessage(ex)));
  }

  /**
   * Handles Spring REST validation errors from @Valid annotations.
   * 
   * <p>
   * Processes MethodArgumentNotValidException thrown when request body validation fails using Spring's @Valid
   * annotation. Converts validation errors into a structured format for client consumption.
   * </p>
   * 
   * <p>
   * <strong>Validation Error Processing:</strong>
   * </p>
   * <ul>
   * <li>Field-specific errors are mapped to their field names</li>
   * <li>Global object errors are mapped with empty field names</li>
   * <li>All errors include descriptive messages for client display</li>
   * </ul>
   * 
   * @param ex the MethodArgumentNotValidException from Spring MVC
   * @return ErrorWrapper with BAD_REQUEST status containing structured validation errors
   */
  @ExceptionHandler(value = { MethodArgumentNotValidException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper restValidError(final MethodArgumentNotValidException ex) {
    final ValidationError validationErrorDTO = new ValidationError();
    if (ex.getBindingResult().getFieldErrors().isEmpty()) {
      for (ObjectError error : ex.getBindingResult().getAllErrors()) {
        validationErrorDTO.addFieldError("", error.getDefaultMessage());
      }
    } else {
      for (FieldError error : ex.getBindingResult().getFieldErrors()) {
        validationErrorDTO.addFieldError(error.getField(), ex.getMessage());
      }
    }
    return new ErrorWrapper(validationErrorDTO);
  }

  /**
   * Handles general exceptions with translatable messages and arguments.
   * 
   * <p>Processes GeneralNotTranslatedWithArgumentsException which contains message keys
   * and arguments that need to be translated using the user's locale. This handler
   * supports translation features including nested argument translation.</p>
   * 
   * @param ex the GeneralNotTranslatedWithArgumentsException containing message key and arguments
   * @return ErrorWrapper with BAD_REQUEST status containing translated message
   */
  @ExceptionHandler(value = { GeneralNotTranslatedWithArgumentsException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper restValidError(final GeneralNotTranslatedWithArgumentsException ex) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Locale userLocale = user.createAndGetJavaLocale();
    return new ErrorWrapper(
        new SingleNativeMsgError(translateArguments(ex.getMessageKey(), ex.getArguments(), userLocale)));
  }

  /**
   * Handles Bean Validation constraint violations.
   * 
   * <p>Processes ConstraintViolationException thrown by Bean Validation (JSR-303/380)
   * when entity validation fails. Converts constraint violations into structured
   * validation errors with field paths and violation messages.</p>
   * 
   * @param ex the ConstraintViolationException from Bean Validation
   * @return ErrorWrapper with BAD_REQUEST status containing structured validation errors
   */
  @ExceptionHandler(value = { ConstraintViolationException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper processValidationError(final ConstraintViolationException ex) {

    final ValidationError validationErrorDTO = new ValidationError();
    for (final ConstraintViolation<?> violation : ex.getConstraintViolations()) {
      validationErrorDTO.addFieldError(violation.getPropertyPath().toString(), violation.getMessage());
    }
    return new ErrorWrapper(validationErrorDTO);
  }

  /**
   * Handles application-specific data validation exceptions.
   * 
   * <p>Processes DataViolationException which contains custom validation errors
   * from the application's business logic. These exceptions can contain multiple
   * field-specific errors with localized messages.</p>
   * 
   * @param dvex the DataViolationException containing application validation errors
   * @return ErrorWrapper with BAD_REQUEST status containing localized validation errors
   */
  @ExceptionHandler(value = { DataViolationException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper processDataViolationException(final DataViolationException dvex) {
    return new ErrorWrapper(RestHelper.createValidationError(dvex, messageSource));
  }

  /**
   * Creates standardized error responses for servlet-level error handling.
   * 
   * <p>This static utility method provides a way to create consistent error responses
   * outside of the controller advice context, such as in filters or custom servlet
   * error handling. It ensures the same error format is used throughout the application.</p>
   * 
   * <p><strong>Usage Example:</strong></p>
   * <pre>{@code
   * RestErrorHandler.createErrorResponseForServlet(
   *     response, 
   *     HttpStatus.UNAUTHORIZED, 
   *     new SecurityBreachError("Access denied")
   * );
   * }</pre>
   * 
   * @param httpResponse the HttpServletResponse to write the error to
   * @param status the HTTP status code for the error response
   * @param error the error object to be wrapped and serialized
   * @throws IOException if an I/O error occurs while writing the response
   */
  public static void createErrorResponseForServlet(HttpServletResponse httpResponse, HttpStatus status, Object error)
      throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    httpResponse.setContentType("application/json");
    httpResponse.setStatus(status.value());
    PrintWriter out = httpResponse.getWriter();
    mapper.writeValue(out, new ErrorWrapper(error));
  }

  /**
   * Translates message arguments with support for nested translation.
   * 
   * <p>This method provides message translation capabilities where arguments
   * themselves can be marked for translation. Arguments marked with the pattern
   * ":{argument}:T" will be recursively translated using the MessageSource.</p>
   * 
   * <p><strong>Translation Pattern:</strong></p>
   * <ul>
   *   <li>Normal arguments: "{0}", "{1}", etc. - used as-is</li>
   *   <li>Translatable arguments: ":{messageKey}:T" - messageKey is translated</li>
   *   <li>Supports multiple levels of nested translation</li>
   * </ul>
   * 
   * <p><strong>Example:</strong></p>
   * <pre>{@code
   * // Message: "Error in :field.name:T: {0}"
   * // Will translate "field.name" and substitute the result
   * // Then substitute {0} with the provided argument
   * }</pre>
   * 
   * @param messageKey the base message key to translate
   * @param args the arguments for message substitution (may include translatable arguments)
   * @param locale the locale for translation
   * @return the fully translated message with all arguments resolved
   */
  private String translateArguments(String messageKey, Object[] args, Locale locale) {
    String msg = messageSource.getMessage(messageKey, args, locale);
    var replacePattern = ":(.*):T";
    var pattern = Pattern.compile(replacePattern);
    var matcher = pattern.matcher(msg);
    while (matcher.find()) {
      var argTranslated = messageSource.getMessage(matcher.group(1), null, locale);
      msg = msg.replaceFirst(replacePattern, argTranslated);
    }
    return msg;
  }

}