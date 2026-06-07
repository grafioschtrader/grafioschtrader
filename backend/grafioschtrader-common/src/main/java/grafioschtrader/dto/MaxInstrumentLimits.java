package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-instrument editing limits sourced from the {@code globalparameters} table. Tells the user interface how many
 * split entries and history-quote periods a user may record for a single instrument, so these caps can be adjusted by
 * an administrator without a frontend rebuild.
 */
@Schema(description = "Maximum number of split entries and history-quote periods allowed per instrument.")
public class MaxInstrumentLimits {

  @Schema(description = "Maximum number of split entries per instrument (gt.max.instrument.splits).")
  public final int maxInstrumentSplits;

  @Schema(description = "Maximum number of history-quote periods per instrument (gt.max.instrument.historyquote.periods).")
  public final int maxInstrumentHistoryquotePeriods;

  public MaxInstrumentLimits(int maxInstrumentSplits, int maxInstrumentHistoryquotePeriods) {
    this.maxInstrumentSplits = maxInstrumentSplits;
    this.maxInstrumentHistoryquotePeriods = maxInstrumentHistoryquotePeriods;
  }
}
