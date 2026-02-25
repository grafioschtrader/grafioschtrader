package grafioschtrader.algo.strategy.model.complex.profit;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.enums.SellFractionBasis;
import jakarta.validation.Valid;

/**
 * Profit management configuration: scale-out plan and final take-profit settings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfitManagementConfig {

  public Boolean scale_out_enabled;

  public SellFractionBasis sell_fraction_basis;

  @Valid
  public List<@Valid ScaleOutTrancheConfig> scale_out_plan;

  @Valid
  public TakeProfitConfig take_profit;
}
