package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contains which dates are to be added or deleted from the trading calendar.")
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
