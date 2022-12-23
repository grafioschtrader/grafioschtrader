package grafioschtrader.alert;

import org.springframework.context.ApplicationEvent;

public class AlertEvent extends ApplicationEvent {

  private final AlertType alertType; 
  private final Object msgParam;
  
  
  public AlertEvent(Object source, AlertType alertType, Object msgParam) {
    super(source);
    this.alertType = alertType;
    this.msgParam = msgParam;
  }


  public AlertType getAlertType() {
    return alertType;
  }


  public Object getMsgParam() {
    return msgParam;
  }
  
  
}
