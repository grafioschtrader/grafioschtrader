package grafioschtrader.entities;

import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = TradingDaysPlus.TABNAME)
public class TradingDaysPlus {

  public static final String TABNAME = "trading_days_plus";

  @Id
  @Column(name = "trading_date")
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  private LocalDate tradingDate;

  public TradingDaysPlus() {
  }

  public TradingDaysPlus(LocalDate tradingDate) {
    this.tradingDate = tradingDate;
  }

  public LocalDate getTradingDate() {
    return tradingDate;
  }

  public void setTradingDate(LocalDate tradingDate) {
    this.tradingDate = tradingDate;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    TradingDaysPlus that = (TradingDaysPlus) o;
    return Objects.equals(tradingDate, that.tradingDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tradingDate);
  }

}
