package grafioschtrader.reportviews.historyquotequality;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.dto.IHistoryquoteQuality;

public interface IHistoryquoteQualityWithSecurityProp extends IHistoryquoteQuality {
  public String getName();

  public String getCurrency();

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate getActiveFromDate();

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
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
