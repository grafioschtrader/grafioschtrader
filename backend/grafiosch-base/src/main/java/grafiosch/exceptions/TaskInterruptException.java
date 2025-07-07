package grafiosch.exceptions;

/**
 * Is thrown when an abortbase background task of the grafiosch background processing has been canceled.<br> 
 * Most background tasks do not support direct abort.
 */
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
