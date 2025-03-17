package grafiosch.alert;

import org.springframework.context.ApplicationEvent;

public class AlertEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1L;
  private final IAlertType alertType;
  private final Object msgParam;


  public AlertEvent(Object source, IAlertType alertType, Object msgParam) {
    super(source);
    this.alertType = alertType;
    this.msgParam = msgParam;
  }

  public IAlertType getAlertType() {
    return alertType;
  }

  public Object getMsgParam() {
    return msgParam;
  }


}
