package grafioschtrader.algo.strategy.model.complex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.downside.DownsideManagementConfig;
import grafioschtrader.algo.strategy.model.complex.entry.EntryConfig;
import grafioschtrader.algo.strategy.model.complex.profit.ProfitManagementConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Root configuration for complex trading strategies. Deserialized from the JSON stored in
 * {@code AlgoStrategy.strategyConfig}. All nested config objects use {@code @Valid} to cascade Jakarta Bean Validation
 * through the entire tree.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StrategyConfig {

  @NotNull
  @Size(max = 100)
  public String strategy_name;

  @Size(max = 10)
  public String version;

  @Valid
  public UniverseConfig universe;

  @Valid
  public DataConfig data;

  @Valid
  public ExecutionConfig execution;

  @Valid
  public CooldownsConfig cooldowns;

  @Valid
  public EntryConfig entry;

  @Valid
  public ProfitManagementConfig profit_management;

  @Valid
  public DownsideManagementConfig downside_management;

  @Valid
  public RiskControlsConfig risk_controls;

  @Valid
  public OutputsConfig outputs;
}
