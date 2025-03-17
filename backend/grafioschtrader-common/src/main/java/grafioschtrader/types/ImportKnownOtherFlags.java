package grafioschtrader.types;

import java.util.EnumSet;

import grafiosch.common.EnumHelper;
import grafiosch.types.StableEnum;

/**
 * Attention: The order must not be changed. Flags that start with "CAN" can be
 * set be the user, others are set by the system.
 *
 */
public enum ImportKnownOtherFlags implements StableEnum{

  /**
   * Bonds may pay interest two or more times in a year. But the document shows
   * only the predetermined interest rate. In this case the system may correct
   * this interest rate for the frequency.
   */
  CAN_BOND_QUOTATION_CORRECTION((byte) 0),

  /**
   * The Bond frequency was happened
   */
  USED_BOND_QUOTATION_CORRECTION((byte) 1),

  /**
   * Some times a dividend are not paid in the currency of the security paper, in
   * such cases the assigment of the security may be wrong it has to be checked
   * against the holdings of security account
   */
  SECURITY_CURRENCY_MISMATCH((byte) 2),

  /**
   * Some times a dividend is paid in a different currency than the security
   * currency but there is no exchange rate. System will involve the required
   * currency and adjust the dividend per unity accordingly.
   */
  CAN_CASH_SECURITY_CURRENCY_MISMATCH_BUT_EXCHANGE_RATE((byte) 3),

  /**
   * Maybe the base currency for exchange rate is not the one of the instrument.
   * It could be the one of the cash account
   */
  CAN_BASE_CURRENCY_MAYBE_INVERSE((byte) 4),

  /**
   * Certain trading platforms show the value 1 in the units field of the CSV
   * document for interest transaction for bonds. GT wants the correct number as
   * specified at purchase. This configuration allows GT to adjust the number and
   * interest using the units and quotation fields. This adjustment is not made
   * until the transaction is created.
   */
  CAN_BOND_ADJUST_UNITS_AND_QUOTATION_WHEN_UNITS_EQUAL_ONE((byte) 5),

  /**
   * In the transaction, the Subject to tax property is not set with this
   * configuration for dividend or interest, i.e. the amount is tax free.
   * Otherwise, Subject to Tax is set.
   */
  CAN_NO_TAX_ON_DIVIDEND_INTEREST((byte) 6);
  
  private final Byte value;

  private ImportKnownOtherFlags(final Byte value) {
    this.value = value;
  }

  
  public Byte getValue() {
    return this.value;
  }


  public static long encode(EnumSet<ImportKnownOtherFlags> importKnownOtherFlagsSet) {
    return EnumHelper.encodeEnumSet(importKnownOtherFlagsSet);
  }

  public static EnumSet<ImportKnownOtherFlags> decode(long encoded) {
    return EnumHelper.decodeEnumSet(ImportKnownOtherFlags.class, encoded);
  }
}
