package grafioschtrader.reportviews;

import grafioschtrader.entities.Transaction;
import grafioschtrader.types.TransactionType;

/**
 * Represents an individual open margin transaction with its associated close transactions for sophisticated margin
 * position tracking. Maintains the relationship between opening and closing trades for accurate gain/loss calculation
 * in trading scenarios involving partial position closures and variable leverage.
 * 
 * This class handles the intricate calculations required for margin trading where positions can be opened and
 * closed in multiple transactions, each with potentially different prices, costs, and leverage factors. It maintains
 * the necessary state to accurately attribute gains and losses to specific opening transactions when positions are
 * closed partially or completely.
 * 
 */
public class TransactionsMarginOpenUnits implements Comparable<TransactionsMarginOpenUnits> {

  /** Original transaction that opened this margin position */
  public Transaction openTransaction;

  /** Remaining open units after partial closures */
  public double openUnits;

  /**
   * Counter for real units adjusted for corporate actions and position changes. Maintains accurate unit tracking
   * across stock splits events that affect the quantity of securities in the open position.
   */
  public double realUntisCounter;

  /**
   * Price per unit for the open portion of the position, always zero or positive. Represents the effective entry
   * price for this specific open position component, used as the reference point for gain/loss calculations when the
   * position is closed.
   */
  public double openPartPrice;

  /**
   * Total expenses and income associated with opening this margin position. Includes transaction costs, fees, and any
   * income received, forming part of the adjusted cost basis for accurate performance measurement.
   */
  public double expenseIncome;

  /**
   * Flag indicating whether this open position should be removed from tracking. Set to true when the position has
   * been fully closed and all associated calculations have been completed for final cleanup operations.
   */
  public boolean markForRemove;

  /**
   * Split factor adjustment from the original base transaction to this open position. Ensures consistent unit and
   * price calculations across corporate actions that occurred between the base transaction date and the opening of
   * this position.
   */
  private final double splitFactorFromBaseTransaction;

  /**
   * Cumulative split factor since this position was opened, tracking all corporate actions that have affected the
   * position since its opening date. Used to maintain accurate unit calculations and price adjustments for ongoing
   * position management.
   */
  private double splitFactorSinceOpen = 1.0;

  /**
   * Direction multiplier for the position: +1 for long (accumulate) positions, -1 for short (reduce) positions.
   * Ensures correct gain/loss calculation direction based on whether the position benefits from price increases or
   * decreases.
   */
  private double shortFactor = 1.0;

  /**
   * Creates a new open margin position tracker for sophisticated position management. Initializes all necessary
   * parameters for accurate gain/loss calculation across the life of the margin position, including corporate action
   * adjustments and directional position handling.
   *
   * @param openTransaction                the transaction that opened this margin position
   * @param openUnits                      number of units in the opening position
   * @param expenseIncome                  total expenses and income for the opening transaction
   * @param splitFactorFromBaseTransaction split factor for corporate action consistency
   */
  public TransactionsMarginOpenUnits(Transaction openTransaction, double openUnits, double expenseIncome,
      double splitFactorFromBaseTransaction) {
    this.openTransaction = openTransaction;
    this.openUnits = openUnits;
    this.expenseIncome = expenseIncome;
    this.splitFactorFromBaseTransaction = splitFactorFromBaseTransaction;
    this.shortFactor = openTransaction.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1;
    this.openPartPrice = openTransaction.getSeucritiesNetPrice();
    this.realUntisCounter = openTransaction.getUnits() * shortFactor;
  }

  @Override
  public int compareTo(TransactionsMarginOpenUnits transactionsMarginOpenUnits1) {
    return openTransaction.getTransactionTime()
        .compareTo(transactionsMarginOpenUnits1.openTransaction.getTransactionTime());
  }

  /**
   * Calculates gain/loss for closing this entire open position at the specified price. Convenience method that closes
   * the full remaining open position using the current split factor context and zero transaction costs for
   * mark-to-market calculations.
   *
   * @param price           closing price for gain/loss calculation
   * @param transactionCost transaction costs for the closing (currently not implemented)
   * @return gain or loss from closing the position at the specified price
   */
  public double calcGainLossOnPositionClose(double price, double transactionCost) {
    return this.calcGainLossOnClosePosition(price, transactionCost, Math.abs(this.openUnits),
        splitFactorFromBaseTransaction);
  }

  /**
   * Calculates gain or loss for closing a specified quantity of this open position. Performs sophisticated
   * calculations that account for corporate actions, leverage factors, and partial position closures to provide
   * accurate performance attribution for margin trading scenarios.
   * 
   * @param price             closing price for the position portion
   * @param transactionCost   transaction costs associated with the closure
   * @param unitsSplited      number of units being closed (split-adjusted)
   * @param splitFactorToOpen split factor from current date back to opening
   * @return gain or loss from closing the specified position portion
   */
  public double calcGainLossOnClosePosition(double price, double transactionCost, double unitsSplited,
      double splitFactorToOpen) {
    if (splitFactorToOpen != 1.0 && splitFactorToOpen != splitFactorSinceOpen) {
      realUntisCounter = realUntisCounter * splitFactorToOpen / splitFactorSinceOpen;
      splitFactorSinceOpen = splitFactorToOpen;
    }
    double openPartPositionPrice = openPartPrice / realUntisCounter * Math.abs(unitsSplited);
    openPartPrice += openPartPositionPrice;
    realUntisCounter += unitsSplited;
    return price * unitsSplited * shortFactor * openTransaction.getValuePerPoint() - transactionCost
        - openPartPositionPrice;
  }

  @Override
  public String toString() {
    return "TransactionsMarginOpenUnits [openTransaction=" + openTransaction + ", openUnits=" + openUnits
        + ", adjustedCostBase=" + expenseIncome + ", markForRemove=" + markForRemove
        + ", splitFactorFromBaseTransaction=" + splitFactorFromBaseTransaction + "]";
  }

}
