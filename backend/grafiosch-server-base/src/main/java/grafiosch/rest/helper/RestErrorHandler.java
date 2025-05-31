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

@RestControllerAdvice
public class RestErrorHandler {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private final MessageSource messageSource;

  @Autowired
  private UserService userService;

  public RestErrorHandler(final MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @ExceptionHandler(value = { Exception.class })
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorWrapper serverException(final Exception ex) {
    ex.printStackTrace();
    log.error(ex.getMessage(), ex);
    return new ErrorWrapper(new SingleNativeMsgError(ExceptionUtils.getRootCauseMessage(ex)));
  }

  @ExceptionHandler(value = { NoSuchElementException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper noSuchElementException(final NoSuchElementException ex) {
    ex.printStackTrace();
    return new ErrorWrapper(new SingleNativeMsgError(ex.getMessage()));
  }

  /**
   * A database produced exception.
   */
  @ExceptionHandler(value = { DataIntegrityViolationException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper processDataIntegrityViolationError(final DataIntegrityViolationException ex) {
    ex.printStackTrace();
    return new ErrorWrapper(new SingleNativeMsgError(ExceptionUtils.getRootCauseMessage(ex), true));
  }

  @ExceptionHandler(value = { RequestLimitAndSecurityBreachException.class })
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public ErrorWrapper processDataIntegrityViolationError(final RequestLimitAndSecurityBreachException ex) {
    ex.printStackTrace();
    return new ErrorWrapper(new ErrorWithLogout(ex.getMessage()));
  }

  @ExceptionHandler(value = { LimitEntityTransactionException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper processDataIntegrityViolationError(final LimitEntityTransactionException ex) {
    ex.printStackTrace();
    return new ErrorWrapper(ex.limitEntityTransactionError);
  }

  @ExceptionHandler(value = { InternalAuthenticationServiceException.class })
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ErrorWrapper processDisabledException(final SecurityException ex) {
    return new ErrorWrapper(new ErrorWithLogout(ex.getMessage()));
  }

  @ExceptionHandler(value = { SecurityException.class })
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ErrorWrapper processSecurityException(final SecurityException ex) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    userService.incrementRightsLimitCount(user.getIdUser(), UserRightLimitCounter.SECURITY_BREACH);
    ex.printStackTrace();
    final Locale currentLocale = LocaleContextHolder.getLocale();
    return new ErrorWrapper(new SecurityBreachError(messageSource.getMessage(ex.getMessage(), null, currentLocale)));
  }

  /**
   * Optimistic locking error.
   */
  @ExceptionHandler(value = { StaleObjectStateException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper processEntityVersionError(final StaleObjectStateException ex) {
    ex.printStackTrace();
    final Locale currentLocale = LocaleContextHolder.getLocale();
    return new ErrorWrapper(
        new SingleNativeMsgError(messageSource.getMessage("version.lock.error", null, currentLocale)));
  }

  @ExceptionHandler(value = { TransactionSystemException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper processValidationError(final TransactionSystemException ex) {
    if (ex.getRootCause() instanceof ConstraintViolationException) {
      return processValidationError((ConstraintViolationException) ex.getRootCause());
    }
    return new ErrorWrapper(new SingleNativeMsgError(ExceptionUtils.getRootCauseMessage(ex)));
  }

  /**
   * Spring Rest @valid
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

  @ExceptionHandler(value = { GeneralNotTranslatedWithArgumentsException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper restValidError(final GeneralNotTranslatedWithArgumentsException ex) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Locale userLocale = user.createAndGetJavaLocale();
    return new ErrorWrapper(
        new SingleNativeMsgError(translateArguments(ex.getMessageKey(), ex.getArguments(), userLocale)));
  }

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
   * From the application produced errors
   */
  @ExceptionHandler(value = { DataViolationException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorWrapper processDataViolationException(final DataViolationException dvex) {
    return new ErrorWrapper(RestHelper.createValidationError(dvex, messageSource));
  }

  public static void createErrorResponseForServlet(HttpServletResponse httpResponse, HttpStatus status, Object error)
      throws IOException {

    ObjectMapper mapper = new ObjectMapper();
    httpResponse.setContentType("application/json");
    httpResponse.setStatus(status.value());
    PrintWriter out = httpResponse.getWriter();
    mapper.writeValue(out, new ErrorWrapper(error));
  }

  /**
   * Sometimes the argument should also be translated. This can be done by defining it as ":{0}:T" instead of "{0}" in
   * the message.
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