package grafioschtrader.usertask;

import java.time.LocalDate;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class LimitCudChange {

  @NotNull
  @Size(min = 1, max = 40)
  public String entity;

  @NotNull
  public LocalDate untilDate;

  @NotNull
  @Min(value = 1)
  @Max(value = 99)
  public Integer dayLimit;

  public LocalDate getUntilDate() {
    return untilDate;
  }

  public void setUntilDate(LocalDate untilDate) {
    this.untilDate = untilDate;
  }

  public Integer getDayLimit() {
    return dayLimit;
  }

  public void setDayLimit(Integer dayLimit) {
    this.dayLimit = dayLimit;
  }

  public String getEntity() {
    return entity;
  }

  public void setEntity(String entity) {
    this.entity = entity;
  }

}
