package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

public interface TradingDaysBase {
  static class SaveTradingDays {
    public int year;
    public List<AddRemoveDay> addRemoveDays;
  }

  static class AddRemoveDay {
    public LocalDate date;
    public boolean add;
  }
}
