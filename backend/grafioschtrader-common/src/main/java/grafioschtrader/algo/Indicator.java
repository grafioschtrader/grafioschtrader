package grafioschtrader.algo;

import java.time.LocalDate;

public interface Indicator {
  LocalDate getDate();

  double getValue();
}
