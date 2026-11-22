package grafioschtrader.reportviews.germantax;

/**
 * The names of the blocks, categories, lines, detail kinds, warnings and reasons of the German tax report. The client
 * translates a block with the prefix GERMAN_TAX_BLOCK_, a category with GERMAN_TAX_CATEGORY_, a line with
 * GERMAN_TAX_LINE_, a warning with GERMAN_TAX_WARN_ and a reason with GERMAN_TAX_REASON_.
 */
public final class GermanTaxKeys {

  private GermanTaxKeys() {
  }

  /** Block: gains and losses from selling shares and the dividends of shares, form Anlage KAP. */
  public static final String BLOCK_SHARES = "SHARES";
  /** Block: interest, accrued interest and gains from other capital claims, form Anlage KAP. */
  public static final String BLOCK_OTHER_INCOME = "OTHER_INCOME";
  /** Block: income of investment funds per fund class, form Anlage KAP-INV. */
  public static final String BLOCK_FUNDS = "FUNDS";
  /** Block: foreign tax, form Anlage KAP. */
  public static final String BLOCK_FOREIGN_TAX = "FOREIGN_TAX";
  /** Block: the same values of the portfolios with domestic withholding, for information only. */
  public static final String BLOCK_WITHHELD = "WITHHELD";
  /** Block: the same values of the portfolios that are not assigned yet, in no form. */
  public static final String BLOCK_UNREVIEWED = "UNREVIEWED";
  /** Block: sales of purchases before the transition dates, in no form. */
  public static final String BLOCK_LEGACY = "LEGACY";

  /** Category of the blocks WITHHELD, UNREVIEWED and LEGACY: shares. */
  public static final String CATEGORY_SHARES = "SHARES";
  /** Category: interest and other capital claims. */
  public static final String CATEGORY_OTHER_INCOME = "OTHER_INCOME";
  /** Category: investment funds. */
  public static final String CATEGORY_FUNDS = "FUNDS";
  /** Category: foreign tax. */
  public static final String CATEGORY_FOREIGN_TAX = "FOREIGN_TAX";

  public static final String LINE_SHARE_GAINS = "SHARE_GAINS";
  public static final String LINE_SHARE_LOSSES = "SHARE_LOSSES";
  public static final String LINE_SHARE_DIVIDENDS = "SHARE_DIVIDENDS";
  public static final String LINE_INTEREST = "INTEREST";
  public static final String LINE_ACCRUED_INTEREST_RECEIVED = "ACCRUED_INTEREST_RECEIVED";
  public static final String LINE_ACCRUED_INTEREST_PAID = "ACCRUED_INTEREST_PAID";
  public static final String LINE_CLAIM_GAINS = "CLAIM_GAINS";
  public static final String LINE_CLAIM_LOSSES = "CLAIM_LOSSES";
  public static final String LINE_FUND_DISTRIBUTIONS = "FUND_DISTRIBUTIONS";
  public static final String LINE_FUND_PRELIMINARY = "FUND_PRELIMINARY";
  public static final String LINE_FUND_DISPOSAL = "FUND_DISPOSAL";
  public static final String LINE_TAX_RECORDED = "TAX_RECORDED";
  public static final String LINE_TAX_CEILING = "TAX_CEILING";
  public static final String LINE_TAX_NOT_CREDITED = "TAX_NOT_CREDITED";
  public static final String LINE_LEGACY_DISPOSAL = "LEGACY_DISPOSAL";

  /** Kind of a detail row: a sale. */
  public static final String KIND_SALE = "SALE";
  /** Kind of a detail row: the distribution of a fund or the dividend of a share. */
  public static final String KIND_DIVIDEND = "DIVIDEND";
  /** Kind of a detail row: interest of a bond coupon or a cash account. */
  public static final String KIND_INTEREST = "INTEREST";
  /** Kind of a detail row: accrued interest paid with a purchase or received with a sale. */
  public static final String KIND_ACCRUED_INTEREST = "ACCRUED_INTEREST";
  /** Kind of a detail row: advance lump sum of a fund. */
  public static final String KIND_PRELIMINARY = "PRELIMINARY";

  /** Warning: the exchange rate of a currency is missing for a day. */
  public static final String WARN_MISSING_RATE = "MISSING_RATE";
  /** Warning: the base interest rate of a tax year is missing. */
  public static final String WARN_MISSING_BASE_RATE = "MISSING_BASE_RATE";
  /** Warning: the first or last price of a fund in a year is missing. */
  public static final String WARN_MISSING_PRICE = "MISSING_PRICE";
  /** Warning: the distributions of a fund in a year are not known. */
  public static final String WARN_MISSING_DISTRIBUTIONS = "MISSING_DISTRIBUTIONS";
  /** Warning: the purchases did not cover the units of a sale. */
  public static final String WARN_UNCOVERED_SALE = "UNCOVERED_SALE";
  /** Warning: a sale consumed a purchase whose cost or advance lump sum is not known completely. */
  public static final String WARN_SALE_FROM_INCOMPLETE_LOT = "SALE_FROM_INCOMPLETE_LOT";
  /** Warning: a security account transfer or ISIN change has no counterpart. */
  public static final String WARN_UNPAIRED_BOOKING = "UNPAIRED_BOOKING";
  /**
   * Note: a spin-off divided the euro cost of the parent lots between parent and child, the purchase times continue.
   * The detail names the fractional child units a bank may settle in cash, null when the allotment is whole.
   */
  public static final String WARN_SPIN_OFF = "SPIN_OFF";
  /** Note: a spin-off is taxed as a distribution; the market value of the new shares is income. */
  public static final String WARN_SPIN_OFF_DISTRIBUTION = "SPIN_OFF_DISTRIBUTION";
  /** Warning: the tax treatment of a spin-off is not recorded; its lots are a cost split, but incomplete. */
  public static final String WARN_SPIN_OFF_UNDETERMINED = "SPIN_OFF_UNDETERMINED";
  /** Warning: parent or child of a spin-off is a fund; the derived lots are incomplete. */
  public static final String WARN_SPIN_OFF_FUND = "SPIN_OFF_FUND";
  /** Warning: one of the three bookings of a spin-off is missing; the derived lots are incomplete. */
  public static final String WARN_SPIN_OFF_INCOMPLETE = "SPIN_OFF_INCOMPLETE";
  /** Warning: the source of a transfer or ISIN change did not hold the transferred units. */
  public static final String WARN_UNCOVERED_TRANSFER = "UNCOVERED_TRANSFER";
  /** Warning: the ISIN change of a fund may be a merger and follows own rules. */
  public static final String WARN_ISIN_CHANGE_FUND = "ISIN_CHANGE_FUND";
  /** Warning: a purchase without consideration has no known cost. */
  public static final String WARN_PURCHASE_WITHOUT_CONSIDERATION = "PURCHASE_WITHOUT_CONSIDERATION";

  /** Reason of an unclassified row: a fund without a fund class. */
  public static final String REASON_FUND_WITHOUT_CLASS = "FUND_WITHOUT_CLASS";
  /** Reason of an unclassified row: certificate, ETC or ETN. */
  public static final String REASON_ISSUER_RISK_PRODUCT = "ISSUER_RISK_PRODUCT";
  /** Reason of an unclassified row: a margin instrument such as a CFD or forex. */
  public static final String REASON_MARGIN_INSTRUMENT = "MARGIN_INSTRUMENT";
  /** Reason of an unclassified row: any other combination of instrument and asset class. */
  public static final String REASON_OTHER_INSTRUMENT = "OTHER_INSTRUMENT";
}
