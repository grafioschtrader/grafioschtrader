/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package grafioschtrader.types;

/**
 *
 * @author Hugo Graf
 */
public enum TransactionType {
  /** Withdrawal cash */
  WITHDRAWAL((byte) 0),
  /** Deposit cash */
  DEPOSIT((byte) 1),
  /** Interest on cash account */
  INTEREST_CASHACCOUNT((byte) 2),
  /** Fee cash or security account, not on a finance instrument */
  FEE((byte) 3),
  /** Accumulate (buy) shares */
  ACCUMULATE((byte) 4),
  /** Reduce (sell) shares */
  REDUCE((byte) 5),
  /** Dividend and Interest on security, can be +/- */
  DIVIDEND((byte) 6),
  /** Finance cost on a finance instrument, can be +/- */
  FINANCE_COST((byte) 7),

  // ---- the following are not written to the database ----///
  HYPOTHETICAL_BUY((byte) 9),
  /**
   * Not used for a real Transaction, it may be used for a simulated position
   * sell. It is not save in the database
   */
  HYPOTHETICAL_SELL((byte) 10),
  /** accrued interest */
  ACCRUED_INTEREST((byte) 11);

  private final Byte value;

  private TransactionType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public String getNameAsKey() {
    return java.util.ResourceBundle.getBundle("grafioschtrader/typenames").getString("trans_" + this.getValue());
  }

  public String getName() {
    return this.name();
  }

  public static TransactionType getTransactionTypeByName(String name) {
    return TransactionType.valueOf(name.toUpperCase());
  }

  public static TransactionType getTransactionTypeByValue(byte value) {
    for (TransactionType transactionType : TransactionType.values()) {
      if (transactionType.getValue() == value) {
        return transactionType;
      }
    }
    return null;
  }
}
