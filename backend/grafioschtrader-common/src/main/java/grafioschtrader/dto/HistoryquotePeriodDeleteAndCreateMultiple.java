package grafioschtrader.dto;

import grafioschtrader.entities.HistoryquotePeriod;
import jakarta.validation.Valid;

public class HistoryquotePeriodDeleteAndCreateMultiple extends DeleteAndCreateMultiple {

  @Valid
  private HistoryquotePeriod[] historyquotePeriods;

  public HistoryquotePeriod[] getHistoryquotePeriods() {
    return (historyquotePeriods == null) ? new HistoryquotePeriod[0] : historyquotePeriods;
  }

  public void setHistoryquotePeriods(HistoryquotePeriod[] historyquotePeriods) {
    this.historyquotePeriods = historyquotePeriods;
  }

}
