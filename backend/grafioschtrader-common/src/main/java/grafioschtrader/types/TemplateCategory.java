package grafioschtrader.types;

public enum TemplateCategory {

  BUY_SELL_INSTRUMENT((byte) 0), BUY_SELL_EQUITY((byte) 1), BUY_SELL_BOND((byte) 2),

  // Buy instrument
  BUY_INSTRUMENT((byte) 3),
  // Buy equity
  BUY_EQUITY((byte) 4),
  // Buy bond
  BUY_BOND((byte) 4),

  // Sell instrument
  SELL_INSTRUMENT((byte) 6),
  // Sell equity and ETF
  SELL_EQUITY((byte) 7),
  // Sell bond
  SELL_BOND((byte) 8),
  // A repurches for bond was executed
  REPURCHASE_OFFER_ACCEPTED((byte) 9),
  // Bond expired
  REPAYMENT_BOND((byte) 10),

  // Paid for dividend or interest
  PAID_DIVIDEND_INTEREST((byte) 11),
  // Paid for dividend or interest with withholding tax
  PAID_DIVIDEND_INTEREST_INTEREST_WITHOLDING_TAX((byte) 12),
  // Paid dividend
  PAID_DIVIDEND((byte) 13),
  // Some times dividend docuemnts need other variant for import
  PAID_DIVIDEND_VARIANT_1((byte) 14),
  // Paid tax free dividend
  PAID_DIVIDEND_TAX_FREE((byte) 15),
  // Paid interest
  PAID_INTEREST((byte) 16),

  CSV_BASE((byte) 20), CSV_ADDITION((byte) 21);

  private final Byte value;

  private TemplateCategory(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static TemplateCategory getTemplateCategory(byte value) {
    for (TemplateCategory templateCategory : TemplateCategory.values()) {
      if (templateCategory.getValue() == value) {
        return templateCategory;
      }
    }
    return null;
  }
}
