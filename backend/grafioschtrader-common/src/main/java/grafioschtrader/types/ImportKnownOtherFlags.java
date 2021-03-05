package grafioschtrader.types;

import java.util.EnumSet;

import grafioschtrader.common.EnumHelper;

public enum ImportKnownOtherFlags {
  
  /**
   * Bonds may pay interest two or more times in a year. But the platform shows
   * only the predetermined interest rate. In this case the system may correct
   * this interest rate for the frequency.
   */
  CAN_BOND_QUATION_CORRECTION(0), 
 
  /**
   * The Bond frequency was happened
   */
  USED_BOND_QUATION_CORRECTION(1),

  /**
   *  Some times a dividend are not paid in the currency of the security paper, in such cases the assigment of
   *  the security may be wrong it has to be checked against the holdings of security account
   */
  SECURITY_CURRENCY_MISSMATCH(2),

  
  /**
   * Some times a dividend is paid in a different currency than the security currency but there is no exchange rate.
   * System will involve the required currency and adjust the dividend per unity accordingly.   
   */
  CASH_SECURITY_CURRENY_MISSMATCH_BUT_EXCHANGE_RATE(3);
  
  
  private final int value;

  private ImportKnownOtherFlags(final int value) {
    this.value = value;
  }

  public int getValue() {
    return this.value;
  }

  public static int encode(EnumSet<ImportKnownOtherFlags> importKnownOtherFlagsSet) {
    return EnumHelper.encode(importKnownOtherFlagsSet);
  }

  public static EnumSet<ImportKnownOtherFlags> decode(int encoded) {
    return EnumHelper.decode(encoded, ImportKnownOtherFlags.class);
  }
}
