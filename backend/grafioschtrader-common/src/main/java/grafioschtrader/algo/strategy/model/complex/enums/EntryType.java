package grafioschtrader.algo.strategy.model.complex.enums;

/**
 * Type of entry strategy for opening a new position. Buying a dip is the only entry the mean reversion strategy
 * executes; any other entry would need configuration fields and decision logic of its own.
 */
public enum EntryType {
  dip_buy
}
