package grafioschtrader.algo.strategy.model.complex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.enums.OrderType;

/**
 * Execution parameters: order type, slippage and fees models.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionConfig {

  public OrderType order_type;

  public String slippage_model;

  public String fees_model;
}
