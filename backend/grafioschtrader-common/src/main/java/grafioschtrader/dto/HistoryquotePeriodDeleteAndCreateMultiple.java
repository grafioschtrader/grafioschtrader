package grafioschtrader.dto;

import javax.validation.Valid;
import javax.validation.constraints.Size;

import grafioschtrader.entities.HistoryquotePeriod;

public class HistoryquotePeriodDeleteAndCreateMultiple extends DeleteAndCreateMultiple {

  @Size(max = 20)
  @Valid
  private HistoryquotePeriod[] historyquotePeriods;

  public HistoryquotePeriod[] getHistoryquotePeriods() {
    return (historyquotePeriods == null) ? new HistoryquotePeriod[0] : historyquotePeriods;
  }

  public void setHistoryquotePeriods(HistoryquotePeriod[] historyquotePeriods) {
    this.historyquotePeriods = historyquotePeriods;
  }

}
