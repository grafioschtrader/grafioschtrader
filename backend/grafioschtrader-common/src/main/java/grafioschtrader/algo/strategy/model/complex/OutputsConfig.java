package grafioschtrader.algo.strategy.model.complex;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configuration for strategy outputs: which events to emit and which metrics to track.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OutputsConfig {

  public List<String> emit_events;

  public List<String> track_metrics;
}
