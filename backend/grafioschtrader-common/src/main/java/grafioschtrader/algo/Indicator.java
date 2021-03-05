package grafioschtrader.algo;

import java.util.Date;

public interface Indicator {
  Date getDate();

  double getValue();
}
