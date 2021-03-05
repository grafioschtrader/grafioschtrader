package grafioschtrader.entities.projection;

public class SuccessfullyChanged {
  public boolean wasChanged;
  public String message;

  public SuccessfullyChanged(boolean wasChanged, String message) {
    this.wasChanged = wasChanged;
    this.message = message;
  }

}
