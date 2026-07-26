package grafioschtrader.receipt;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.TransactionType;

/**
 * Language dependent label definitions for the Grafioschtrader transaction receipt PDFs.
 *
 * <p>
 * <b>These labels are a wire format, not UI text.</b> The generated receipt PDFs must be re-importable through the
 * PDF transaction import. The import matches the text extracted from the PDF against the Grafioschtrader import
 * templates in <code>grafioschtrader-server/src/test/resources/testdata/import_template/*.tmpl</code>. Those
 * templates anchor the transaction values on exactly these label words. For this reason the labels are deliberately
 * <b>not</b> placed in <code>messages.properties</code>: a harmless looking translation edit there would silently
 * break the template matching. Any change to a label here requires the same change in the corresponding template
 * files; the round-trip test <code>TransactionReceiptRoundTripTest</code> fails when generator and templates drift
 * apart.
 * </p>
 *
 * <p>
 * Constraints imposed by the template matching engine ({@code TemplateConfigurationPDFasTXT}):
 * </p>
 * <ul>
 * <li>Value lines are anchored on the <em>first word</em> of the line (SL anchor). Within one receipt every value
 * line must therefore start with a distinct first word.</li>
 * <li>The transaction type word is captured as a single word and mapped via the template's {@code transType=}
 * configuration, so type words must not contain spaces.</li>
 * <li>Numbers are always printed with Swiss separators (apostrophe grouping, dot decimal) and the templates declare
 * {@code overRuleSeparators=All<'|.>}, making the import independent of the importing user's locale.</li>
 * </ul>
 */
public final class GtReceiptDefs {

  /** Date format printed on the receipt; must match {@code dateFormat=dd.MM.yyyy} in the templates. */
  public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  /** Time format printed on the receipt; must match {@code timeFormat=HH:mm:ss} in the templates. */
  public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

  /** Prefix of the reference number printed on every receipt, followed by the transaction ID. */
  public static final String REFERENCE_PREFIX = "GT-";

  private GtReceiptDefs() {
  }

  /**
   * Returns the label set for the given locale. German gets the German receipt; every other language falls back to
   * English, mirroring the two languages supported by the shipped import templates.
   */
  public static Labels forLocale(Locale locale) {
    return "de".equals(locale.getLanguage()) ? Labels.DE : Labels.EN;
  }

  /**
   * Creates the number format for monetary amounts and rates: Swiss apostrophe grouping, dot decimal, minimum 2 and
   * maximum 8 fraction digits so stored double values survive the print/parse round trip.
   */
  public static DecimalFormat createAmountFormat() {
    DecimalFormat df = new DecimalFormat("#,##0.00######", createSwissSymbols());
    return df;
  }

  /** Creates the number format for units: like the amount format but without forced fraction digits. */
  public static DecimalFormat createUnitsFormat() {
    return new DecimalFormat("#,##0.########", createSwissSymbols());
  }

  private static DecimalFormatSymbols createSwissSymbols() {
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
    symbols.setGroupingSeparator('\'');
    symbols.setDecimalSeparator('.');
    return symbols;
  }

  /**
   * Returns the localized transaction type word printed in the receipt title and used in the PDF file name. For
   * dividends on interest bearing instruments (bonds, money market) the word "Zins"/"Interest" is used; both words
   * map to DIVIDEND in the import templates.
   *
   * @param transaction the transaction whose type word is requested
   * @param labels      the language dependent label set
   * @return single word identifying the transaction type on the receipt
   */
  public static String getTransactionTypeWord(Transaction transaction, Labels labels) {
    return switch (transaction.getTransactionType()) {
      case ACCUMULATE -> labels.typeBuy;
      case REDUCE -> labels.typeSell;
      case DIVIDEND -> isInterestInstrument(transaction.getSecurity()) ? labels.typeInterest : labels.typeDividend;
      case FINANCE_COST -> labels.typeFinanceCost;
      default -> throw new IllegalArgumentException(
          "No receipt support for transaction type " + transaction.getTransactionType());
    };
  }

  /** Checks whether the transaction type is supported by the receipt generation. */
  public static boolean isSupported(TransactionType transactionType) {
    return transactionType == TransactionType.ACCUMULATE || transactionType == TransactionType.REDUCE
        || transactionType == TransactionType.DIVIDEND || transactionType == TransactionType.FINANCE_COST;
  }

  private static boolean isInterestInstrument(Security security) {
    AssetclassType categoryType = security.getAssetClass().getCategoryType();
    return categoryType == AssetclassType.FIXED_INCOME || categoryType == AssetclassType.CONVERTIBLE_BOND
        || categoryType == AssetclassType.MONEY_MARKET;
  }

  /**
   * Immutable per language label set. Every label that starts a value line acts as the import template's line anchor
   * and must stay unique within a receipt (see class Javadoc).
   */
  public static final class Labels {

    public static final Labels DE = new Labels("de", "TRANSAKTIONSBELEG", "Kunde", "Depot", "Konto",
        "Börsentransaktion:", "Referenz:", "Datum", "Uhrzeit", "Ex-Datum", "ISIN:", "Titelwährung", "Anzahl", "Preis",
        "Ausschüttung pro Einheit", "Marchzinsen", "Kommission", "Abgaben und Steuern", "Devisenkurs",
        "Verrechnungswährung", "Zu Ihren Lasten", "Zu Ihren Gunsten", "Gutschrift", "Belastung",
        "Vielen Dank für Ihren Auftrag.", "Erstellt mit Grafioschtrader", "Kauf", "Verkauf", "Dividende", "Zins",
        "Finanzierungskosten");

    public static final Labels EN = new Labels("en", "TRANSACTION RECEIPT", "Customer", "Securities account",
        "Cash account", "Stock exchange transaction:", "Reference:", "Date", "Time", "Ex-date", "ISIN:",
        "Instrument currency", "Quantity", "Price", "Distribution per unit", "Accrued interest", "Commission",
        "Taxes and duties", "Currency exchange rate", "Settlement currency", "Debited to your account",
        "Credited to your account", "Credit", "Charge", "Thank you for your order.", "Created with Grafioschtrader",
        "Buy", "Sell", "Dividend", "Interest", "Financing");

    /** ISO 639-1 language of this label set ('de' or 'en'). */
    public final String language;
    /** Document heading, e.g. "TRANSAKTIONSBELEG". */
    public final String receiptTitle;
    /** Label of the customer (nickname) line in the address block. */
    public final String customer;
    /** Label of the security account line in the address block. */
    public final String securityAccount;
    /** Label of the cash account line in the address block. */
    public final String cashAccount;
    /** Static prefix of the buy/sell receipt title; anchor before the transaction type word. */
    public final String buySellTitlePrefix;
    /** Label before the GT reference number; anchor after the transaction type word. */
    public final String reference;
    /** Anchor before the transaction date. */
    public final String date;
    /** Anchor before the transaction time. */
    public final String time;
    /** Line anchor of the optional ex-dividend date (dividend receipt only). */
    public final String exDate;
    /** Anchor before the ISIN on the security line. */
    public final String isin;
    /** Line anchor of the instrument currency (cin). */
    public final String instrumentCurrency;
    /** Line anchor of the units (units). */
    public final String units;
    /** Line anchor of the price per unit on buy/sell receipts (quotation). */
    public final String price;
    /** Line anchor of the per unit distribution on dividend receipts (quotation). */
    public final String distributionPerUnit;
    /** Line anchor of the optional accrued interest (ac). */
    public final String accruedInterest;
    /** Line anchor of the optional transaction costs (tc1). */
    public final String commission;
    /** Line anchor of the optional taxes (tt1). */
    public final String taxes;
    /** Line anchor of the optional currency exchange rate (cex). */
    public final String exchangeRate;
    /** Line anchor of the cash account currency (cac). */
    public final String settlementCurrency;
    /** Total line anchor of a buy (amount debited). */
    public final String debited;
    /** Total line anchor of a sell (amount credited). */
    public final String credited;
    /** Total line anchor of a dividend/interest receipt. */
    public final String credit;
    /** Total line anchor of a finance cost receipt. */
    public final String charge;
    /** Static closing sentence. */
    public final String thanks;
    /** Static footer identifying the generator. */
    public final String createdWith;
    /** Transaction type words (single word each, see class Javadoc). */
    public final String typeBuy;
    public final String typeSell;
    public final String typeDividend;
    public final String typeInterest;
    public final String typeFinanceCost;

    private Labels(String language, String receiptTitle, String customer, String securityAccount, String cashAccount,
        String buySellTitlePrefix, String reference, String date, String time, String exDate, String isin,
        String instrumentCurrency, String units, String price, String distributionPerUnit, String accruedInterest,
        String commission, String taxes, String exchangeRate, String settlementCurrency, String debited,
        String credited, String credit, String charge, String thanks, String createdWith, String typeBuy,
        String typeSell, String typeDividend, String typeInterest, String typeFinanceCost) {
      this.language = language;
      this.receiptTitle = receiptTitle;
      this.customer = customer;
      this.securityAccount = securityAccount;
      this.cashAccount = cashAccount;
      this.buySellTitlePrefix = buySellTitlePrefix;
      this.reference = reference;
      this.date = date;
      this.time = time;
      this.exDate = exDate;
      this.isin = isin;
      this.instrumentCurrency = instrumentCurrency;
      this.units = units;
      this.price = price;
      this.distributionPerUnit = distributionPerUnit;
      this.accruedInterest = accruedInterest;
      this.commission = commission;
      this.taxes = taxes;
      this.exchangeRate = exchangeRate;
      this.settlementCurrency = settlementCurrency;
      this.debited = debited;
      this.credited = credited;
      this.credit = credit;
      this.charge = charge;
      this.thanks = thanks;
      this.createdWith = createdWith;
      this.typeBuy = typeBuy;
      this.typeSell = typeSell;
      this.typeDividend = typeDividend;
      this.typeInterest = typeInterest;
      this.typeFinanceCost = typeFinanceCost;
    }
  }
}
