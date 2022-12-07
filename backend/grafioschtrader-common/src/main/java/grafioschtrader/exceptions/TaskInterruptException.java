package grafioschtrader.exceptions;

public class TaskInterruptException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private InterruptedException interruptedException;

  public TaskInterruptException(InterruptedException interruptedException) {
    this.interruptedException = interruptedException;
  }

  public InterruptedException getInterruptedException() {
    return interruptedException;
  }

}
