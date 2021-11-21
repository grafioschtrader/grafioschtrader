package grafioschtrader.reportviews.historyquotequality;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.IHistoryquoteQuality;

public interface IHistoryquoteQualityWithSecurityProp extends IHistoryquoteQuality {
  public String getName();

  public String getCurrency();

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate getActiveFromDate();

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate getActiveToDate();

  public int getIdSecurity();

  @Override
  public Integer getConnectorCreated();

  public Integer getFilledNoTradeDay();

  @Override
  public Integer getManualImported();

  @Override
  public Integer getFilledLinear();

}
