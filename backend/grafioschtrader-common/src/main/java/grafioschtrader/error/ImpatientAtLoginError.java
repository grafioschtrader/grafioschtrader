package grafioschtrader.error;

import grafiosch.error.SecurityBreachError;

public class ImpatientAtLoginError extends SecurityBreachError {

  public ImpatientAtLoginError(String message) {
    super(message);
  }
}
