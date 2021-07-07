package grafioschtrader.reportviews.historyquotequality;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;

/**
 * Group by connector, stock exchange, asset class (equities, bond, ...),
 * special investment like (ETF, Direct investment, ...)
 *
 * @author Hugo Graf
 */
public class HistoryquoteQualityHead extends HistoryquoteQualityGroup {
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate lastUpdate;

  public HistoryquoteQualityHead(String name, LocalDate lastUpdate) {
    super(name);
    this.lastUpdate = lastUpdate;
  }

  public LocalDate getLastUpdate() {
    return lastUpdate;
  }

}
