package grafioschtrader.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
import grafioschtrader.platformimport.pdf.ParseFormInputPDFasTXT;
import grafioschtrader.platformimport.pdf.TemplateConfigurationPDFasTXT;
import grafioschtrader.receipt.TransactionReceiptPdfGenerator.ReceiptContext;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.TemplateCategory;
import grafioschtrader.types.TemplateFormatType;
import grafioschtrader.types.TransactionType;

/**
 * Round-trip guarantee for the transaction receipt feature: every receipt PDF produced by
 * {@link TransactionReceiptPdfGenerator} must be parseable by the PDF transaction import using the Grafioschtrader
 * import templates in <code>src/test/resources/testdata/import_template</code>, and the parsed values must equal the
 * source transaction. Runs without a Spring context or database; any drift between the generator labels
 * ({@link GtReceiptDefs}) and the template files makes these tests fail.
 */
class TransactionReceiptRoundTripTest {

  private static final String TEMPLATE_DIR = "/testdata/import_template/";
  private static final String[] TEMPLATE_FILES = { "buy_sell_instrument-pdf-20000101-de.tmpl",
      "buy_sell_instrument-pdf-20000101-en.tmpl", "paid_dividend_interest-pdf-20000101-de.tmpl",
      "paid_dividend_interest-pdf-20000101-en.tmpl", "finance_cost-pdf-20000101-de.tmpl",
      "finance_cost-pdf-20000101-en.tmpl" };

  private static final ReceiptContext CONTEXT = new ReceiptContext("hugo", "Main depot", "Cash CHF");
  private static final LocalDateTime TRANSACTION_TIME = LocalDateTime.of(2026, 3, 9, 14, 5, 33);

  private final TransactionReceiptPdfGenerator generator = new TransactionReceiptPdfGenerator();

  @Test
  @DisplayName("Buy of a stock, same currency, with commission and taxes")
  void buyStockRoundTrip() throws Exception {
    for (Locale locale : List.of(Locale.GERMAN, Locale.ENGLISH)) {
      Transaction t = securityTransaction(TransactionType.ACCUMULATE, stock("CH0012032048", "Roche Holding AG"),
          "CHF", 61.0, 35.68, 9.85, 3.26, -2189.59);
      ImportProperties ip = generateAndParse(t, locale);
      assertCommonFields(ip, t, locale);
      assertEquals(9.85, ip.getTc1());
      assertEquals(3.26, ip.getTt1());
      assertEquals(2189.59, ip.getTa());
      assertNull(ip.getCex());
      assertNull(ip.getAc());
    }
  }

  @Test
  @DisplayName("Sell in a foreign currency with exchange rate")
  void sellForeignCurrencyRoundTrip() throws Exception {
    for (Locale locale : List.of(Locale.GERMAN, Locale.ENGLISH)) {
      Transaction t = securityTransaction(TransactionType.REDUCE, stock("US0378331005", "Apple Inc."), "CHF", 10.0,
          100.0, 5.0, null, 905.45);
      t.getSecurity().setCurrency("USD");
      t.setCurrencyExRate(0.91);
      ImportProperties ip = generateAndParse(t, locale);
      assertCommonFields(ip, t, locale);
      assertEquals("USD", ip.getCin());
      assertEquals(5.0, ip.getTc1());
      assertNull(ip.getTt1());
      assertEquals(0.91, ip.getCex());
      assertEquals(905.45, ip.getTa());
    }
  }

  @Test
  @DisplayName("Buy of a bond with accrued interest")
  void buyBondWithAccruedInterestRoundTrip() throws Exception {
    for (Locale locale : List.of(Locale.GERMAN, Locale.ENGLISH)) {
      Transaction t = securityTransaction(TransactionType.ACCUMULATE,
          bond("CH0482172415", "GRANDCIT 0.57% 24.06.2024"), "CHF", 100.0, 95.45, 35.0, 14.3, -9595.1);
      t.setAssetInvestmentValue1(0.8);
      ImportProperties ip = generateAndParse(t, locale);
      assertCommonFields(ip, t, locale);
      assertEquals(0.8, ip.getAc());
      assertEquals(35.0, ip.getTc1());
      assertEquals(14.3, ip.getTt1());
      assertEquals(9595.1, ip.getTa());
    }
  }

  @Test
  @DisplayName("Foreign currency dividend with withholding tax, exchange rate and ex-date")
  void dividendForeignCurrencyRoundTrip() throws Exception {
    for (Locale locale : List.of(Locale.GERMAN, Locale.ENGLISH)) {
      Transaction t = securityTransaction(TransactionType.DIVIDEND,
          stock("NL0009690239", "VanEck Global Real Estate ETF"), "CHF", 500.0, 0.42, null, 31.5, 164.63);
      t.getSecurity().setCurrency("EUR");
      t.setCurrencyExRate(0.9223);
      t.setExDate(LocalDate.of(2026, 6, 3));
      ImportProperties ip = generateAndParse(t, locale);
      assertCommonFields(ip, t, locale);
      assertEquals("EUR", ip.getCin());
      assertEquals(31.5, ip.getTt1());
      assertEquals(0.9223, ip.getCex());
      assertEquals(164.63, ip.getTa());
      assertEquals(LocalDate.of(2026, 6, 3), ip.getExdiv());
    }
  }

  @Test
  @DisplayName("Interest on a bond uses the interest type word and maps to DIVIDEND")
  void bondInterestRoundTrip() throws Exception {
    for (Locale locale : List.of(Locale.GERMAN, Locale.ENGLISH)) {
      Transaction t = securityTransaction(TransactionType.DIVIDEND, bond("CH0482172415", "GRANDCIT 0.57% 24.06.2024"),
          "CHF", 100.0, 0.57, null, null, 57.0);
      ImportProperties ip = generateAndParse(t, locale);
      assertCommonFields(ip, t, locale);
      assertEquals(57.0, ip.getTa());
      assertNull(ip.getTt1());
      assertNull(ip.getExdiv());
    }
  }

  @Test
  @DisplayName("Finance cost receipt")
  void financeCostRoundTrip() throws Exception {
    for (Locale locale : List.of(Locale.GERMAN, Locale.ENGLISH)) {
      Transaction t = securityTransaction(TransactionType.FINANCE_COST, stock("US0378331005", "Apple Inc."), "CHF",
          null, null, null, null, -12.5);
      ImportProperties ip = generateAndParse(t, locale);
      assertEquals(TransactionType.FINANCE_COST, ip.getTransactionType());
      assertEquals(TRANSACTION_TIME, ip.getDatetime());
      assertEquals("US0378331005", ip.getIsin());
      assertEquals("CHF", ip.getCac());
      assertEquals(12.5, ip.getTa());
    }
  }

  /** Generates the receipt, extracts its text like the import does and parses it against all shipped templates. */
  private ImportProperties generateAndParse(Transaction transaction, Locale locale) throws Exception {
    byte[] pdf = generator.generate(transaction, locale, CONTEXT);
    String extractedText = ImportTransactionHelperPdf.transFormPDFToTxt(new ByteArrayInputStream(pdf));
    Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateMap = ImportTransactionHelperPdf
        .readTemplates(readAllTemplates(), locale);
    ParseFormInputPDFasTXT parseInput = new ParseFormInputPDFasTXT(extractedText, templateMap);
    List<ImportProperties> importPropertiesList = parseInput.parseInput();
    assertNotNull(importPropertiesList,
        "No template matched the generated receipt. Extracted text:" + System.lineSeparator() + extractedText);
    assertEquals(1, importPropertiesList.size());
    ImportTransactionTemplate successTemplate = parseInput.getSuccessTemplate(importPropertiesList);
    assertEquals(GtReceiptDefs.forLocale(locale).language, successTemplate.getTemplateLanguage());
    return importPropertiesList.get(0);
  }

  private void assertCommonFields(ImportProperties ip, Transaction transaction, Locale locale) {
    assertEquals(transaction.getTransactionType(), ip.getTransactionType());
    assertEquals(TRANSACTION_TIME, ip.getDatetime());
    assertEquals(transaction.getSecurity().getIsin(), ip.getIsin());
    assertEquals(transaction.getSecurity().getCurrency(), ip.getCin());
    assertEquals(transaction.getCashaccount().getCurrency(), ip.getCac());
    assertEquals(transaction.getUnits(), ip.getUnits());
    assertEquals(transaction.getQuotation(), ip.getQuotation());
  }

  private Transaction securityTransaction(TransactionType type, Security security, String cashCurrency, Double units,
      Double quotation, Double transactionCost, Double taxCost, double cashaccountAmount) {
    Transaction t = new Transaction();
    t.setIdTransaction(4711);
    t.setTransactionType(type);
    t.setSecuritycurrency(security);
    Cashaccount cashaccount = new Cashaccount();
    cashaccount.setCurrency(cashCurrency);
    cashaccount.setName("Cash " + cashCurrency);
    t.setCashaccount(cashaccount);
    t.setUnits(units);
    t.setQuotation(quotation);
    t.setTransactionCost(transactionCost);
    t.setTaxCost(taxCost);
    t.setCashaccountAmount(cashaccountAmount);
    t.setTransactionTime(TRANSACTION_TIME);
    return t;
  }

  private Security stock(String isin, String name) {
    return security(isin, name, AssetclassType.EQUITIES);
  }

  private Security bond(String isin, String name) {
    return security(isin, name, AssetclassType.FIXED_INCOME);
  }

  private Security security(String isin, String name, AssetclassType categoryType) {
    Security security = new Security();
    security.setIsin(isin);
    security.setName(name);
    security.setCurrency("CHF");
    Assetclass assetclass = new Assetclass();
    assetclass.setCategoryType(categoryType);
    security.setAssetClass(assetclass);
    return security;
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
