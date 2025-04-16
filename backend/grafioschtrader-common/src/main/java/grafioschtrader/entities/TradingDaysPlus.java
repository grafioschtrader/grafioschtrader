package grafioschtrader.entities;

import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Schema(description = """
    Contains the combined amount of dates on which trading can take place.
    Normally these are all dates since 2000-01-03 excluding January 1 and December 25 of all years.""")
@Entity
@Table(name = TradingDaysPlus.TABNAME)
public class TradingDaysPlus {

  public static final String TABNAME = "trading_days_plus";

  @Schema(description = "Date on which trading can take place.")  
  @Id
  @Column(name = "trading_date")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
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
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TradingDaysPlus that = (TradingDaysPlus) o;
    return Objects.equals(tradingDate, that.tradingDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tradingDate);
  }

}
