package grafioschtrader.algo.strategy.model.complex.entry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.SizingConfig;
import grafioschtrader.algo.strategy.model.complex.enums.EntryType;
import jakarta.validation.Valid;

/**
 * Entry strategy configuration: how and when to open a new position.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntryConfig {

  public EntryType type;

  public Integer lookback_T;

  @Valid
  public DipReferenceConfig dip_reference;

  public Double dip_threshold_pct;

  @Valid
  public SizingConfig initial_buy_sizing;
}
