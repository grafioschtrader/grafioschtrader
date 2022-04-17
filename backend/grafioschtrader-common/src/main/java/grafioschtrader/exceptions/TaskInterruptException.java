package grafioschtrader.exceptions;

public class TaskInterruptException extends RuntimeException {
  
  private  InterruptedException interruptedException;
    
  public TaskInterruptException( InterruptedException InterruptedException) {
    this.interruptedException = interruptedException;
  }

  public InterruptedException getInterruptedException() {
    return interruptedException;
  }
    
 
    
}
