package grafioschtrader.exceptions;

/**
 * Sometime the language of the user is not accesible in a such case it will be
 * translated later.
 *
 * @author Hugo Graf
 *
 */
public class GeneralNotTranslatedWithArgumentsException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final String messageKey;
  private final Object arguments[];

  public GeneralNotTranslatedWithArgumentsException(String messageKey, Object arguments[]) {
    this.messageKey = messageKey;
    this.arguments = arguments;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public Object[] getArguments() {
    return arguments;
  }

}
