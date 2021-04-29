package grafioschtrader.exceptions;

import java.util.List;

public class TaskBackgroundException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private String errorMessagesKey;
  private List<String> errorMsgOfSystem;

  public TaskBackgroundException(String errorMessagesKey) {
    super();
    this.errorMessagesKey = errorMessagesKey;
  }

  public TaskBackgroundException(String errorMessagesKey, List<String> errorMsgOfSystem) {
    super();
    this.errorMessagesKey = errorMessagesKey;
    this.errorMsgOfSystem = errorMsgOfSystem;
  }

  public String getErrorMessagesKey() {
    return errorMessagesKey;
  }

  public List<String> getErrorMsgOfSystem() {
    return errorMsgOfSystem;
  }

}
