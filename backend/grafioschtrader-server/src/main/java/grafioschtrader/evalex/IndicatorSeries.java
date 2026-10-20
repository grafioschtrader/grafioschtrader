package grafioschtrader.evalex;

import java.time.LocalDate;
import java.util.List;

import grafioschtrader.dto.IDateCloseHighLow;

/** Immutable request inputs. Dividend amounts are in the instrument currency; null means a missing FX rate. */
public record IndicatorSeries(List<? extends IDateCloseHighLow> series, List<? extends IDateCloseHighLow> benchmark,
    List<DividendAmount> dividends, boolean dividendDataKnown, boolean security, LocalDate asOfDate, Double price) {
  public record DividendAmount(LocalDate exDate, Double amount) {
  }

  public static IndicatorSeries history(List<? extends IDateCloseHighLow> history) {
    return new IndicatorSeries(history, List.of(), List.of(), false, true, LocalDate.MAX, null);
  }
}
