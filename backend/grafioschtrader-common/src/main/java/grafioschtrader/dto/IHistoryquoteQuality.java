package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;

public interface IHistoryquoteQuality {
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate getMinDate();

  public int getMissingStart();

  public int getMissingEnd();

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate getMaxDate();

  public int getTotalMissing();

  public int getExpectedTotal();

  public double getQualityPercentage();

  public Integer getToManyAsCalendar();

  public Integer getQuoteSaturday();

  public Integer getQuoteSunday();

  public Integer getManualImported();

  public Integer getConnectorCreated();

  public Integer getFilledLinear();

  public Integer getCalculated();

  public Integer getUserModified();
}
