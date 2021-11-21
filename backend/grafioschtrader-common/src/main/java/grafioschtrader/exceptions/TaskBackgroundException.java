package grafioschtrader.exceptions;

import java.util.List;

public class TaskBackgroundException extends Exception {
  private static final long serialVersionUID = 1L;

  private String errorMessagesKey;
  private List<String> errorMsgOfSystem;
  private boolean rollback = true;

  public TaskBackgroundException(String errorMessagesKey, boolean rollback) {
    super();
    this.errorMessagesKey = errorMessagesKey;
    this.rollback = rollback;
  }

  public TaskBackgroundException(String errorMessagesKey, List<String> errorMsgOfSystem, boolean rollback) {
    super();
    this.errorMessagesKey = errorMessagesKey;
    this.errorMsgOfSystem = errorMsgOfSystem;
    this.rollback = rollback;
  }

  public String getErrorMessagesKey() {
    return errorMessagesKey;
  }

  public List<String> getErrorMsgOfSystem() {
    return errorMsgOfSystem;
  }

  public boolean isRollback() {
    return rollback;
  }

}
