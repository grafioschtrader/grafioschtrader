package grafioschtrader.config;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Scope(value = "singleton")
@Component
public class NegativeIdNumberCreater {
  private int negativeId;

  public int getNegativeId() {
    return --negativeId;
  }
}
