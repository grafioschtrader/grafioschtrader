package grafioschtrader.dto;

import java.util.List;

import grafioschtrader.dto.SupportedCSVFormat.SupportedCSVFormats;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;

public class HistoryquotesWithMissings<S extends Securitycurrency<S>> {

  public IHistoryquoteQuality historyquoteQuality;
  public S securitycurrency;
  public List<Historyquote> historyquoteList;
  public SupportedCSVFormats supportedCSVFormats = new SupportedCSVFormats();

  public HistoryquotesWithMissings(S securitycurrency, IHistoryquoteQuality historyquoteQuality,
      List<Historyquote> historyquoteList) {
    this.securitycurrency = securitycurrency;
    this.historyquoteQuality = historyquoteQuality;
    this.historyquoteList = historyquoteList;

  }

}
