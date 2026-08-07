package grafioschtrader.exportcsv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import grafiosch.common.ValueFormatConverter;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.platform.GenericTransactionImportCSV;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.csv.ImportTransactionHelperCsv;
import grafioschtrader.platformimport.csv.TemplateConfigurationAndStateCsv;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TemplateCategory;
import grafioschtrader.types.TemplateFormatType;
import grafioschtrader.types.TransactionType;

/**
 * Round-trip guarantee for the transaction CSV export: every CSV produced by {@link TransactionCsvExportGenerator}
 * must be parseable by the CSV transaction import using the Grafioschtrader import templates in
 * <code>src/test/resources/testdata/import_template/grafioschtrader</code>, and the parsed values must equal the source
 * transaction.
 * Runs without a Spring context or database; any drift between the generator labels ({@link GtCsvExportDefs}) and the
 * template files makes these tests fail. The parse path is the real one: header validation via
 * {@code isValidTemplateForForm}, line filtering via the template's ignore rule and field conversion through
 * {@code GenericTransactionImportCSV.parseDataLine}.
 */
class TransactionCsvExportRoundTripTest {

  private static final String TEMPLATE_DIR = "/testdata/import_template/grafioschtrader/";
  private static final String[] TEMPLATE_FILES = { "csv_base-csv-20000101-de.tmpl", "csv_base-csv-20000101-en.tmpl" };
  private static final List<Locale> LOCALES = List.of(Locale.GERMAN, Locale.ENGLISH);
  private static final int COLUMN_COUNT = 16;
  private static final int ORDER_COLUMN = 1;
  private static final int EXCHANGE_RATE_COLUMN = 14;
  private static final int MARKER_COLUMN = 15;
  private static final LocalDateTime TRANSACTION_TIME = LocalDateTime.of(2026, 3, 9, 14, 5, 0);

  private final TransactionCsvExportGenerator generator = new TransactionCsvExportGenerator();

  @Test
  @DisplayName("Buy of a stock, same currency, with costs and taxes")
  void buyStockRoundTrip() throws Exception {
    for (Locale locale : LOCALES) {
      Transaction t = securityTransaction(1, TransactionType.ACCUMULATE, stock("CH0012032048", "Roche Holding AG"),
          "CHF", 61.0, 35.68, 9.85, 3.26, -2189.59);
      ImportProperties ip = singleRow(generateAndParse(List.of(t), locale));
      assertCommonFields(ip, t);
      assertEquals(9.85, ip.getTc1());
      assertEquals(3.26, ip.getTt1());
      assertEquals(-2189.59, ip.getTa());
      assertEquals("ROG", ip.getSymbol());
      assertNull(ip.getCex());
      assertNull(ip.getAc());
      ImportTransactionPos itp = ImportTransactionPos.createFromImportPropertiesSecurity(List.of(ip));
      assertEquals(61.0, itp.getUnits());
      assertEquals(35.68, itp.getQuotation());
      assertEquals(9.85, itp.getTransactionCost());
      assertEquals(3.26, itp.getTaxCost());
      assertEquals(-2189.59, itp.getCashaccountAmount());
    }
  }

  @Test
  @DisplayName("Sell in a foreign currency with exchange rate and instrument currency")
  void sellForeignCurrencyRoundTrip() throws Exception {
    for (Locale locale : LOCALES) {
      Transaction t = securityTransaction(2, TransactionType.REDUCE, stock("US0378331005", "Apple Inc."), "CHF", 10.0,
          100.0, 5.0, null, 905.45);
      t.getSecurity().setCurrency("USD");
      t.setCurrencyExRate(0.91);
      ImportProperties ip = singleRow(generateAndParse(List.of(t), locale));
      assertCommonFields(ip, t);
      assertEquals("USD", ip.getCin());
      assertEquals(0.91, ip.getCex());
      assertEquals(5.0, ip.getTc1());
      assertNull(ip.getTt1());
      assertEquals(905.45, ip.getTa());
    }
  }

  @Test
  @DisplayName("Buy of a bond with accrued interest from assetInvestmentValue1")
  void buyBondWithAccruedInterestRoundTrip() throws Exception {
    for (Locale locale : LOCALES) {
      Transaction t = securityTransaction(3, TransactionType.ACCUMULATE,
          bond("CH0482172415", "GRANDCIT 0.57% 24.06.2024"), "CHF", 100.0, 95.45, 35.0, 14.3, -9595.55);
      t.setAssetInvestmentValue1(0.8);
      ImportProperties ip = singleRow(generateAndParse(List.of(t), locale));
      assertCommonFields(ip, t);
      assertEquals(0.8, ip.getAc());
      assertEquals(35.0, ip.getTc1());
      assertEquals(14.3, ip.getTt1());
      ImportTransactionPos itp = ImportTransactionPos.createFromImportPropertiesSecurity(List.of(ip));
      assertEquals(0.8, itp.getAccruedInterest());
    }
  }

  @Test
  @DisplayName("Foreign currency dividend with taxes and exchange rate")
  void dividendForeignCurrencyRoundTrip() throws Exception {
    for (Locale locale : LOCALES) {
      Transaction t = securityTransaction(4, TransactionType.DIVIDEND,
          stock("NL0009690239", "VanEck Global Real Estate ETF"), "CHF", 500.0, 0.42, null, 31.5, 164.63);
      t.getSecurity().setCurrency("EUR");
      t.setCurrencyExRate(0.9223);
      ImportProperties ip = singleRow(generateAndParse(List.of(t), locale));
      assertCommonFields(ip, t);
      assertEquals("EUR", ip.getCin());
      assertEquals(31.5, ip.getTt1());
      assertEquals(0.9223, ip.getCex());
      assertEquals(164.63, ip.getTa());
    }
  }

  @Test
  @DisplayName("Cash-only singles: FEE, INTEREST_CASHACCOUNT, DEPOSIT, WITHDRAWAL with order 0 and no exchange rate")
  void cashSinglesRoundTrip() throws Exception {
    for (Locale locale : LOCALES) {
      List<Transaction> transactions = List.of(cashTransaction(11, TransactionType.FEE, "CHF", -120.0, 0),
          cashTransaction(12, TransactionType.INTEREST_CASHACCOUNT, "CHF", 12.35, 1),
          cashTransaction(13, TransactionType.DEPOSIT, "CHF", 5000.0, 2),
          cashTransaction(14, TransactionType.WITHDRAWAL, "CHF", -2500.0, 3));
      ParsedCsv parsed = generateAndParse(transactions, locale);
      assertEquals(4, parsed.importProperties.size());
      for (int i = 0; i < 4; i++) {
        ImportProperties ip = parsed.importProperties.get(i);
        Transaction t = transactions.get(i);
        assertEquals(t.getTransactionType(), ip.getTransactionType());
        assertEquals(t.getCashaccountAmount(), ip.getTa());
        assertEquals("CHF", ip.getCac());
        assertEquals(GtCsvExportDefs.ORDER_NONE, ip.getOrder());
        assertNull(ip.getCex());
        assertNull(ip.getIsin());
        ImportTransactionPos itp = ImportTransactionPos.createFromImportPropertiesSuccess(null, null, null, null, ip);
        assertEquals(t.getCashaccountAmount(), itp.getCashaccountAmount());
        assertEquals(t.getTransactionType(), itp.getTransactionType());
      }
    }
  }

  @Test
  @DisplayName("Same-portfolio transfer pairs share a per-day unique nonzero order number and keep the exchange rate")
  void samePortfolioTransferPairRoundTrip() throws Exception {
    for (Locale locale : LOCALES) {
      Transaction w1 = cashTransaction(21, TransactionType.WITHDRAWAL, "CHF", -1000.0, 0);
      Transaction d1 = cashTransaction(22, TransactionType.DEPOSIT, "EUR", 930.0, 0);
      connect(w1, d1);
      w1.setCurrencyExRate(0.93);
      d1.setCurrencyExRate(0.93);
      Transaction w2 = cashTransaction(23, TransactionType.WITHDRAWAL, "CHF", -50.0, 5);
      Transaction d2 = cashTransaction(24, TransactionType.DEPOSIT, "CHF", 50.0, 5);
      connect(w2, d2);
      ParsedCsv parsed = generateAndParse(List.of(w1, d1, w2, d2), locale);
      assertEquals(4, parsed.importProperties.size());
      ImportProperties ipW1 = byType(parsed, TransactionType.WITHDRAWAL, -1000.0);
      ImportProperties ipD1 = byType(parsed, TransactionType.DEPOSIT, 930.0);
      ImportProperties ipW2 = byType(parsed, TransactionType.WITHDRAWAL, -50.0);
      ImportProperties ipD2 = byType(parsed, TransactionType.DEPOSIT, 50.0);
      assertEquals(ipW1.getOrder(), ipD1.getOrder());
      assertNotEquals(GtCsvExportDefs.ORDER_NONE, ipW1.getOrder());
      assertEquals(ipW2.getOrder(), ipD2.getOrder());
      assertNotEquals(ipW1.getOrder(), ipW2.getOrder(), "Order number must be unique within the day");
      assertEquals(0.93, ipW1.getCex());
      assertEquals(0.93, ipD1.getCex());
      assertNull(ipW2.getCex());
    }
  }

  @Test
  @DisplayName("Cross-portfolio transfer sides in separate files: order 0 and empty exchange rate on both")
  void crossPortfolioTransferPairRoundTrip() throws Exception {
    for (Locale locale : LOCALES) {
      Transaction w = cashTransaction(31, TransactionType.WITHDRAWAL, "CHF", -1000.0, 0);
      Transaction d = cashTransaction(32, TransactionType.DEPOSIT, "EUR", 930.0, 0);
      connect(w, d);
      w.setCurrencyExRate(0.93);
      d.setCurrencyExRate(0.93);
      for (Transaction t : List.of(w, d)) {
        ParsedCsv parsed = generateAndParse(List.of(t), Set.of(t.getIdTransaction()), locale);
        ImportProperties ip = singleRow(parsed);
        assertEquals(GtCsvExportDefs.ORDER_NONE, ip.getOrder());
        assertNull(ip.getCex(), "Unconnected transfer side must not carry an exchange rate");
        assertEquals("", parsed.rawRows.get(0)[EXCHANGE_RATE_COLUMN]);
      }
    }
  }

  @Test
  @DisplayName("Margin open/close rows carry the MARGIN marker and are skipped by the template's ignore rule")
  void marginOpenCloseSkipped() throws Exception {
    for (Locale locale : LOCALES) {
      Transaction open = securityTransaction(41, TransactionType.ACCUMULATE, marginCfd("DE0007164600", "DAX CFD"),
          "EUR", 10.0, 50.0, null, null, -500.0);
      Transaction close = securityTransaction(42, TransactionType.REDUCE, marginCfd("DE0007164600", "DAX CFD"), "EUR",
          10.0, 55.0, null, null, 550.0);
      close.setConnectedIdTransaction(41);
      ParsedCsv parsed = generateAndParse(List.of(open, close), locale);
      assertEquals(2, parsed.rawRows.size());
      assertEquals(0, parsed.importProperties.size(), "Margin open/close rows must be skipped by the import");
      for (String[] row : parsed.rawRows) {
        assertEquals(GtCsvExportDefs.MARGIN_MARKER, row[MARKER_COLUMN]);
        assertEquals(GtCsvExportDefs.ORDER_NONE, row[ORDER_COLUMN],
            "Margin close-to-open link must not be treated as a transfer pair");
      }
    }
  }

  @Test
  @DisplayName("FINANCE_COST is exported without marker and round-trips units (days) and quotation (daily cost)")
  void financeCostRoundTrip() throws Exception {
    for (Locale locale : LOCALES) {
      Transaction t = securityTransaction(51, TransactionType.FINANCE_COST, marginCfd("DE0007164600", "DAX CFD"),
          "EUR", 3.0, 1.25, null, null, -3.75);
      ParsedCsv parsed = generateAndParse(List.of(t), locale);
      ImportProperties ip = singleRow(parsed);
      assertEquals("", parsed.rawRows.get(0)[MARKER_COLUMN], "FINANCE_COST must stay re-importable");
      assertEquals(TransactionType.FINANCE_COST, ip.getTransactionType());
      assertEquals(3.0, ip.getUnits());
      assertEquals(1.25, ip.getQuotation());
      assertEquals(-3.75, ip.getTa());
      ImportTransactionPos itp = ImportTransactionPos.createFromImportPropertiesSecurity(List.of(ip));
      assertEquals(3.0, itp.getUnits());
      assertEquals(1.25, itp.getQuotation());
    }
  }

  @Test
  @DisplayName("Seconds of the transaction time are truncated to the minute by the wire format")
  void secondsTruncatedToMinute() throws Exception {
    for (Locale locale : LOCALES) {
      Transaction t = cashTransaction(61, TransactionType.DEPOSIT, "CHF", 100.0, 0);
      t.setTransactionTime(LocalDateTime.of(2026, 3, 9, 14, 5, 33));
      ImportProperties ip = singleRow(generateAndParse(List.of(t), locale));
      assertEquals(LocalDateTime.of(2026, 3, 9, 14, 5, 0), ip.getDatetime());
    }
  }

  /** Parse result: every data line as raw cells plus the ImportProperties of the lines the import would accept. */
  private record ParsedCsv(List<ImportProperties> importProperties, List<String[]> rawRows) {
  }

  private ParsedCsv generateAndParse(List<Transaction> transactions, Locale locale) throws IOException {
    Set<Integer> ids = new HashSet<>();
    transactions.forEach(t -> ids.add(t.getIdTransaction()));
    return generateAndParse(transactions, ids, locale);
  }

  /**
   * Generates the CSV for one export file and parses it back through the real import path: template header
   * validation, the template's ignore rule and {@code GenericTransactionImportCSV.parseDataLine}.
   */
  private ParsedCsv generateAndParse(List<Transaction> transactions, Set<Integer> idsInFile, Locale locale)
      throws IOException {
    String csv = generator.generate(transactions, idsInFile, locale);
    List<ImportTransactionTemplate> templates = readAllTemplates();
    Map<TemplateConfigurationAndStateCsv, ImportTransactionTemplate> templateMap = ImportTransactionHelperCsv
        .readTemplates(templates, locale);
    String expectedLanguage = GtCsvExportDefs.forLocale(locale).language;
    TemplateConfigurationAndStateCsv template = templateMap.entrySet().stream()
        .filter(e -> e.getValue().getTemplateLanguage().equals(expectedLanguage)).map(Map.Entry::getKey).findFirst()
        .orElseThrow();

    String[] lines = csv.split("\n");
    assertTrue(template.isValidTemplateForForm(lines[0]),
        "Template columns do not match the generated header: " + lines[0]);
    assertEquals(COLUMN_COUNT, template.getColumnPropertyMapping().size());
    assertTrue(template.isOrderSupport());

    ValueFormatConverter valueFormatConverter = new ValueFormatConverter(template.getDateFormat(),
        template.getTimeFormat(), template.getThousandSeparators(), template.getThousandSeparatorsPattern(),
        template.getDecimalSeparator(), template.getLocale());
    TestCsvParser parser = new TestCsvParser(csv, templates);
    List<ImportProperties> importProperties = new ArrayList<>();
    List<String[]> rawRows = new ArrayList<>();
    for (int i = 1; i < lines.length; i++) {
      String[] values = StringUtils.splitByWholeSeparatorPreserveAllTokens(lines[i], template.getDelimiterField());
      assertEquals(COLUMN_COUNT, values.length, "Every data line must have all columns: " + lines[i]);
      rawRows.add(values);
      if (isIgnoredByTemplate(values, template)) {
        continue;
      }
      GenericTransactionImportCSV.ParseLineSuccessError result = parser.parse(i + 1, values, template,
          valueFormatConverter);
      assertTrue(result.hasSuccess(), "Line could not be parsed: " + lines[i] + " error: " + result.errorMessage);
      importProperties.add(result.importProperties);
    }
    return new ParsedCsv(importProperties, rawRows);
  }

  /** Applies the template's ignoreLineByFieldValue rules exactly like GenericTransactionImportCSV does. */
  private boolean isIgnoredByTemplate(String[] values, TemplateConfigurationAndStateCsv template) {
    for (Map.Entry<String, String> entry : template.getIgnoreLineByFieldValueMap().entrySet()) {
      int column = template.getPropertyColumnMapping().get(entry.getKey());
      if (values[column].matches(entry.getValue())) {
        return true;
      }
    }
    return false;
  }

  private ImportProperties singleRow(ParsedCsv parsed) {
    assertEquals(1, parsed.importProperties.size());
    return parsed.importProperties.get(0);
  }

  private ImportProperties byType(ParsedCsv parsed, TransactionType type, double ta) {
    return parsed.importProperties.stream()
        .filter(ip -> ip.getTransactionType() == type && ip.getTa().equals(ta)).findFirst().orElseThrow();
  }

  private void assertCommonFields(ImportProperties ip, Transaction transaction) {
    assertEquals(transaction.getTransactionType(), ip.getTransactionType());
    assertEquals(TRANSACTION_TIME, ip.getDatetime());
    assertEquals(transaction.getSecurity().getIsin(), ip.getIsin());
    assertEquals(transaction.getSecurity().getCurrency(), ip.getCin());
    assertEquals(transaction.getCashaccount().getCurrency(), ip.getCac());
    assertEquals(transaction.getUnits(), ip.getUnits());
    assertEquals(transaction.getQuotation(), ip.getQuotation());
    assertEquals(transaction.getSecurity().getName(), ip.getSn());
  }

  private void connect(Transaction withdrawal, Transaction deposit) {
    withdrawal.setConnectedIdTransaction(deposit.getIdTransaction());
    deposit.setConnectedIdTransaction(withdrawal.getIdTransaction());
  }

  private Transaction securityTransaction(int idTransaction, TransactionType type, Security security,
      String cashCurrency, Double units, Double quotation, Double transactionCost, Double taxCost,
      double cashaccountAmount) {
    Transaction t = cashTransaction(idTransaction, type, cashCurrency, cashaccountAmount, 0);
    t.setSecuritycurrency(security);
    t.setUnits(units);
    t.setQuotation(quotation);
    t.setTransactionCost(transactionCost);
    t.setTaxCost(taxCost);
    return t;
  }

  private Transaction cashTransaction(int idTransaction, TransactionType type, String currency,
      double cashaccountAmount, int minuteOffset) {
    Transaction t = new Transaction();
    t.setIdTransaction(idTransaction);
    t.setTransactionType(type);
    Cashaccount cashaccount = new Cashaccount();
    cashaccount.setCurrency(currency);
    cashaccount.setName("Cash " + currency);
    t.setCashaccount(cashaccount);
    t.setCashaccountAmount(cashaccountAmount);
    t.setTransactionTime(TRANSACTION_TIME.plusMinutes(minuteOffset));
    return t;
  }

  private Security stock(String isin, String name) {
    return security(isin, name, AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
  }

  private Security bond(String isin, String name) {
    return security(isin, name, AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
  }

  private Security marginCfd(String isin, String name) {
    return security(isin, name, AssetclassType.EQUITIES, SpecialInvestmentInstruments.CFD);
  }

  private Security security(String isin, String name, AssetclassType categoryType,
      SpecialInvestmentInstruments specialInvestmentInstrument) {
    Security security = new Security();
    security.setIsin(isin);
    security.setName(name);
    security.setCurrency("CHF");
    security.setTickerSymbol("ROG");
    Assetclass assetclass = new Assetclass();
    assetclass.setCategoryType(categoryType);
    assetclass.setSpecialInvestmentInstrument(specialInvestmentInstrument);
    security.setAssetClass(assetclass);
    return security;
  }

  /** Exposes the protected real parse method of the CSV importer for the round trip. */
  private static class TestCsvParser extends GenericTransactionImportCSV {
    TestCsvParser(String csv, List<ImportTransactionTemplate> templates) {
      super(new ImportTransactionHead(),
          new MockMultipartFile("file", "gt_transactions.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)),
          templates);
    }

    ParseLineSuccessError parse(int lineNumber, String[] values, TemplateConfigurationAndStateCsv template,
        ValueFormatConverter valueFormatConverter) {
      return parseDataLine(lineNumber, values, template, valueFormatConverter);
    }
  }

  /** Builds the template entities from the shipped .tmpl files exactly like the template file upload does. */
  private List<ImportTransactionTemplate> readAllTemplates() throws IOException {
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(GlobalConstants.SHORT_STANDARD_DATE_FORMAT);
    List<ImportTransactionTemplate> templates = new ArrayList<>();
    for (String fileName : TEMPLATE_FILES) {
      String[] fileNameParts = fileName.replaceFirst("\\.tmpl$", "").split("-");
      ImportTransactionTemplate itt = new ImportTransactionTemplate(
          TemplateCategory.valueOf(fileNameParts[0].toUpperCase()),
          TemplateFormatType.valueOf(fileNameParts[1].toUpperCase()), fileNameParts[3].toLowerCase());
      itt.setValidSince(LocalDate.parse(fileNameParts[2], dateFormat));
      try (InputStream is = getClass().getResourceAsStream(TEMPLATE_DIR + fileName)) {
        assertNotNull(is, "Template file not found: " + fileName);
        itt.setTemplateAsTxt(new String(is.readAllBytes(), StandardCharsets.UTF_8));
      }
      assertEquals(true, itt.copyPurposeInTextToFieldPurpose(), "templatePurpose= line missing in " + fileName);
      templates.add(itt);
    }
    return templates;
  }
}
