package grafioschtrader.exportcsv;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import grafioschtrader.receipt.GtReceiptDefs;
import grafioschtrader.types.TransactionType;

/**
 * Language dependent column headers and transaction type words for the Grafioschtrader transaction CSV export.
 *
 * <p>
 * <b>These labels are a wire format, not UI text.</b> The exported CSV files must be re-importable through the CSV
 * transaction import. The import maps the CSV column headers against the Grafioschtrader import templates
 * <code>csv_base-csv-20000101-de|en.tmpl</code> in
 * <code>grafioschtrader-server/src/test/resources/testdata/import_template</code>. Those templates bind the
 * transaction properties to exactly these header texts and type words. For this reason the labels are deliberately
 * <b>not</b> placed in <code>messages.properties</code>: a harmless looking translation edit there would silently
 * break the column mapping. Any change to a label here requires the same change in the corresponding template files;
 * the round-trip test <code>TransactionCsvExportRoundTripTest</code> fails when generator and templates drift apart.
 * </p>
 *
 * <p>
 * Constraints imposed by the CSV import engine ({@code TemplateConfigurationAndStateCsv} /
 * {@code GenericTransactionImportCSV}):
 * </p>
 * <ul>
 * <li>Header texts must match the template's column mapping byte for byte (only surrounding whitespace is
 * stripped).</li>
 * <li>The transaction type words are whole-field values mapped via the template's {@code transType=} configuration;
 * every word must map to exactly one transaction type.</li>
 * <li>Numbers are always printed with Swiss separators (apostrophe grouping, dot decimal) and the templates declare
 * {@code overRuleSeparators=All<'|.>}, making the import independent of the importing user's locale.</li>
 * <li>Rows sharing the same non-zero {@code Order} value within one calendar day are treated as one logical
 * transaction; the export uses this to pair the two sides of a same-portfolio cash transfer.</li>
 * <li>Margin open/close rows carry {@link #MARGIN_MARKER} in the Marker column; the template's
 * {@code ignoreLineByFieldValue=sf1||MARGIN} makes the import skip them (securityRisk and the close-to-open link
 * cannot be reconstructed). FINANCE_COST rows are exported without the marker: the import links them to the open
 * margin position automatically or fails the single row visibly.</li>
 * </ul>
 */
public final class GtCsvExportDefs {

  /** Date/time format of the Date column; must match {@code dateFormat=dd.MM.yyyy HH:mm} in the templates. */
  public static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  /** Column delimiter; must match {@code delimiterField=;} in the templates. */
  public static final String DELIMITER = ";";

  /**
   * Marker column value of margin open/close rows. The templates declare {@code ignoreLineByFieldValue=sf1||MARGIN},
   * so the import drops such rows before parsing.
   */
  public static final String MARGIN_MARKER = "MARGIN";

  /** Order column value of rows that do not belong to a multi-row group (see GenericTransactionImportCSV). */
  public static final String ORDER_NONE = "0";

  private GtCsvExportDefs() {
  }

  /**
   * Returns the label set for the given locale. German gets the German headers; every other language falls back to
   * English, mirroring the two languages supported by the shipped import templates.
   */
  public static Labels forLocale(Locale locale) {
    return "de".equals(locale.getLanguage()) ? Labels.DE : Labels.EN;
  }

  /**
   * Returns the localized transaction type word written to the Transaction column. Every word maps back to the same
   * transaction type through the template's {@code transType=} configuration.
   *
   * @param transactionType the persisted transaction type
   * @param labels          the language dependent label set
   * @return whole-field value identifying the transaction type
   */
  public static String getTransactionTypeWord(TransactionType transactionType, Labels labels) {
    return switch (transactionType) {
      case ACCUMULATE -> labels.typeBuy;
      case REDUCE -> labels.typeSell;
      case DIVIDEND -> labels.typeDividend;
      case FEE -> labels.typeFee;
      case INTEREST_CASHACCOUNT -> labels.typeInterest;
      case DEPOSIT -> labels.typeDeposit;
      case WITHDRAWAL -> labels.typeWithdrawal;
      case FINANCE_COST -> labels.typeFinanceCost;
      default -> throw new IllegalArgumentException("No CSV export support for transaction type " + transactionType);
    };
  }

  /**
   * Immutable per language label set: the 16 column headers in file order plus the 8 transaction type words. The
   * header texts are the import template's column mapping keys and must stay unique within one label set.
   */
  public static final class Labels {

    public static final Labels DE = new Labels("de", "Datum", "Order", "Transaktion", "Symbol", "Name", "ISIN",
        "Anzahl", "Kurs", "Marchzinsen", "Kosten", "Steuern", "Titelwährung", "Nettobetrag", "Währung", "Wechselkurs",
        "Marker", "Kauf", "Verkauf", "Dividende", "Gebühr", "Zins", "Einzahlung", "Auszahlung", "Finanzierungskosten");

    public static final Labels EN = new Labels("en", "Date", "Order", "Transaction", "Symbol", "Name", "ISIN",
        "Quantity", "Unit price", "Accrued interest", "Costs", "Taxes", "Instrument currency", "Net Amount",
        "Currency", "Exchange rate", "Marker", "Buy", "Sell", "Dividend", "Fee", "Interest", "Deposit", "Withdrawal",
        "Financing");

    /** ISO 639-1 language of this label set ('de' or 'en'). */
    public final String language;
    /** Header of the transaction date/time column (template property datetime). */
    public final String headerDate;
    /** Header of the order grouping column (template property order). */
    public final String headerOrder;
    /** Header of the transaction type word column (template property transType). */
    public final String headerTransaction;
    /** Header of the ticker symbol column (template property symbol). */
    public final String headerSymbol;
    /** Header of the security name column (template property sn). */
    public final String headerName;
    /** Header of the ISIN column (template property isin). */
    public final String headerIsin;
    /** Header of the units column (template property units). */
    public final String headerQuantity;
    /** Header of the price per unit column (template property quotation). */
    public final String headerUnitPrice;
    /** Header of the accrued interest column (template property ac). */
    public final String headerAccruedInterest;
    /** Header of the transaction cost column (template property tc1). */
    public final String headerCosts;
    /** Header of the tax column (template property tt1). */
    public final String headerTaxes;
    /** Header of the security currency column (template property cin). */
    public final String headerInstrumentCurrency;
    /** Header of the signed cash account amount column (template property ta). */
    public final String headerNetAmount;
    /** Header of the cash account currency column (template property cac). */
    public final String headerCurrency;
    /** Header of the currency exchange rate column (template property cex). */
    public final String headerExchangeRate;
    /** Header of the marker column used to skip margin open/close rows (template property sf1). */
    public final String headerMarker;
    /** Transaction type words (whole-field values, see class Javadoc). */
    public final String typeBuy;
    public final String typeSell;
    public final String typeDividend;
    public final String typeFee;
    public final String typeInterest;
    public final String typeDeposit;
    public final String typeWithdrawal;
    public final String typeFinanceCost;

    private Labels(String language, String headerDate, String headerOrder, String headerTransaction,
        String headerSymbol, String headerName, String headerIsin, String headerQuantity, String headerUnitPrice,
        String headerAccruedInterest, String headerCosts, String headerTaxes, String headerInstrumentCurrency,
        String headerNetAmount, String headerCurrency, String headerExchangeRate, String headerMarker, String typeBuy,
        String typeSell, String typeDividend, String typeFee, String typeInterest, String typeDeposit,
        String typeWithdrawal, String typeFinanceCost) {
      this.language = language;
      this.headerDate = headerDate;
      this.headerOrder = headerOrder;
      this.headerTransaction = headerTransaction;
      this.headerSymbol = headerSymbol;
      this.headerName = headerName;
      this.headerIsin = headerIsin;
      this.headerQuantity = headerQuantity;
      this.headerUnitPrice = headerUnitPrice;
      this.headerAccruedInterest = headerAccruedInterest;
      this.headerCosts = headerCosts;
      this.headerTaxes = headerTaxes;
      this.headerInstrumentCurrency = headerInstrumentCurrency;
      this.headerNetAmount = headerNetAmount;
      this.headerCurrency = headerCurrency;
      this.headerExchangeRate = headerExchangeRate;
      this.headerMarker = headerMarker;
      this.typeBuy = typeBuy;
      this.typeSell = typeSell;
      this.typeDividend = typeDividend;
      this.typeFee = typeFee;
      this.typeInterest = typeInterest;
      this.typeDeposit = typeDeposit;
      this.typeWithdrawal = typeWithdrawal;
      this.typeFinanceCost = typeFinanceCost;
    }

    /** Returns the 16 column headers in file order; single source of the exported column layout. */
    public String[] headersInOrder() {
      return new String[] { headerDate, headerOrder, headerTransaction, headerSymbol, headerName, headerIsin,
          headerQuantity, headerUnitPrice, headerAccruedInterest, headerCosts, headerTaxes, headerInstrumentCurrency,
          headerNetAmount, headerCurrency, headerExchangeRate, headerMarker };
    }
  }

  /** Number format for monetary amounts and rates; identical to the receipt wire format (see GtReceiptDefs). */
  public static DecimalFormat createAmountFormat() {
    return GtReceiptDefs.createAmountFormat();
  }

  /** Number format for units; identical to the receipt wire format (see GtReceiptDefs). */
  public static DecimalFormat createUnitsFormat() {
    return GtReceiptDefs.createUnitsFormat();
  }
}
