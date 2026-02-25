package grafioschtrader.algo.strategy.model.complex.enums;

/**
 * Rule for determining when to add to a position during averaging down.
 */
public enum AddStepType {
  each_n_pct_drop, indicator_based, custom
}
