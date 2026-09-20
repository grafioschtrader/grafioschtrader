package grafioschtrader.algo.strategy.model.complex;

import java.util.List;

/**
 * Configuration for strategy outputs: which events to emit and which metrics to track.
 */

public class OutputsConfig {

  public List<String> emit_events;

  public List<String> track_metrics;
}
