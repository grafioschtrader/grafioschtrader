package grafioschtrader.platform.cornertrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import grafiosch.common.ValueFormatConverter;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.csv.TemplateConfigurationAndStateCsv;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
import grafioschtrader.platformimport.pdf.ParseFormInputPDFasTXT;
import grafioschtrader.types.ImportKnownOtherFlags;
import grafioschtrader.types.TransactionType;

/** Tests the Corneronline layouts and third fee through the real parser without a Spring context or database writes. */
class CornerTraderPdfTemplateTest {

  private static final String ROOT = "/cornertrader/";

  @ParameterizedTest
  @CsvSource({ "20260908_kauf,ACCUMULATE,2026-09-08T16:16:39,CA21037X1006,10,2924,CHF,CAD,-17319.80,35,43.87,0.590739",
      "20260729_verkauf,REDUCE,2026-07-29T13:27:45,DK0062498333,550,44.98,EUR,EUR,24682.09,19.80,37.11,",
      "20260805_kauf,ACCUMULATE,2026-08-05T09:52:49,DK0062498333,106,39.39,EUR,EUR,-4184.95,3.34,6.27,",
      "20260528_kauf,ACCUMULATE,2026-05-26T15:36:18,US98978V1035,220,69.30,EUR,EUR,-15281.06,12.20,22.86,",
      "20260306_kauf,ACCUMULATE,2026-03-06T16:20:24,IE00B9M04V95,2580,2.98,CHF,CHF,-7706.70,6.77,11.53,",
      "20260808_dividende,DIVIDEND,2026-08-18T00:00:00,DK0062498333,500,3.75,EUR,DKK,183.12,0,506.25,0.13378437",
      "20260901_dividende,DIVIDEND,2026-09-07T00:00:00,US98978V1035,220,0.53,EUR,USD,70.36,0,34.98,1.160061",
      "20260831_Dividende,DIVIDEND,2026-08-31T00:00:00,IE00B9M04V95,22000,0.0149,CHF,CHF,327.80,0,0," })
  void parsesAllSupportedDocuments(String file, TransactionType type, LocalDateTime time, String isin, double units,
      double quotation, String cashCurrency, String securityCurrency, double amount, double fees, double taxes,
      Double rate) throws Exception {
    String input = resource("doc-as-text/" + file + ".txt");
    String pdfRoot = System.getProperty("cornertrader.pdfRoot");
    if (pdfRoot != null) {
      try (var paths = Files.walk(Path.of(pdfRoot))) {
        Path pdf = paths.filter(p -> p.getFileName().toString().equals(file + ".pdf")).findFirst().orElseThrow();
        try (InputStream stream = Files.newInputStream(pdf)) {
          input = ImportTransactionHelperPdf.transFormPDFToTxt(stream);
        }
      }
    }
    ImportTransactionPos pos = parse(input);
    assertThat(pos.getTransactionType()).isEqualTo(type);
    assertThat(pos.getTransactionTime()).isEqualTo(time);
    assertThat(pos.getIsin()).isEqualTo(isin);
    assertThat(pos.getUnits()).isEqualTo(units);
    assertThat(pos.getQuotation()).isEqualTo(quotation);
    assertThat(pos.getCurrencyAccount()).isEqualTo(cashCurrency);
    assertThat(pos.getCurrencySecurity()).isEqualTo(securityCurrency);
    assertThat(pos.getCurrencyExRate()).isEqualTo(rate);
    assertThat(pos.getField1StringImp()).as("Bank references must not populate the 20-character special-purpose field")
        .isNull();
    assertThat(pos.getTransactionCost() == null ? 0 : pos.getTransactionCost()).isCloseTo(fees, within(0.000001));
    assertThat(pos.getTaxCost() == null ? 0 : pos.getTaxCost()).isCloseTo(taxes, within(0.000001));
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(pos.getCashaccountAmount()).isCloseTo(amount, within(0.000001));
    assertThat(pos.getDiffCashaccountAmount()).isZero();
  }

  @Test
  void longBankReferencesAreNotStoredInTheSpecialPurposeField() throws Exception {
    for (String file : List.of("20260808_dividende", "20260306_kauf")) {
      String input = resource("doc-as-text/" + file + ".txt").replace("EXAMPLE/2026/1", "F4CORACT03/26/00000000/1");
      ImportTransactionPos pos = parse(input);
      assertThat(pos.getField1StringImp()).isNull();
      pos.calcDiffCashaccountAmountWhenPossible();
      assertThat(pos.getDiffCashaccountAmount()).isZero();
    }
  }

  @ParameterizedTest
  @CsvSource({ "true,true,34.98", "true,false,17.49", "false,true,17.49", "false,false,0" })
  void dividendTaxesAreIndependentlyOptional(boolean swissRetention, boolean withholding, double expectedTax)
      throws Exception {
    String input = resource("doc-as-text/20260901_dividende.txt");
    if (!swissRetention) {
      input = input.replaceAll("(?m)^Schweiz\\.Rückh\\.Steu\\..*(?:\\R|$)", "");
    }
    if (!withholding) {
      input = input.replaceAll("(?m)^Verrechnungs-/Quellensteuer.*(?:\\R|$)", "");
    }
    ImportTransactionPos pos = parse(input);
    assertThat(pos.getTaxCost() == null ? 0 : pos.getTaxCost()).isCloseTo(expectedTax, within(0.000001));
  }

  @Test
  void omittedFeesAndExchangeRateRemainOptional() throws Exception {
    String input = resource("doc-as-text/20260306_kauf.txt")
        .replaceAll("(?m)^(?:Handelsgebühr|Fremdgebühren|Ausführungsgeb\\.|Stempel).*(?:\\R|$)", "")
        .replace("7.706,70-", "7.688,40-");
    ImportTransactionPos pos = parse(input);
    assertThat(pos.getTransactionCost()).isNull();
    assertThat(pos.getTaxCost()).isNull();
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(pos.getDiffCashaccountAmount()).isZero();
  }

  @Test
  void missingThirdFeeCannotBeHiddenAsRounding() throws Exception {
    String input = resource("doc-as-text/20260306_kauf.txt").replaceAll("(?m)^Ausführungsgeb\\..*(?:\\R|$)", "");
    ImportTransactionPos pos = parse(input);
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(Math.abs(pos.getDiffCashaccountAmount())).isCloseTo(0.77, within(0.000001));
    assertThat(pos.resolveConfiguredRoundingStep()).isNull();
  }

  @Test
  void combinesThreeFeesAndDiscountAcrossRepeatedRows() {
    ImportProperties first = properties();
    first.setTc1(-4.85);
    first.setTc2(-1.15);
    first.setTc3(-0.77);
    first.setReduce(-0.50);
    ImportProperties second = properties();
    second.setTc3(0.25);
    ImportTransactionPos pos = ImportTransactionPos.createFromImportPropertiesSecurity(List.of(first, second));
    assertThat(pos.getTransactionCost()).isCloseTo(6.52, within(0.000001));
    assertThat(pos.getUnits()).isEqualTo(2.0);
    second.setTc3(null);
    assertThat(ImportTransactionPos.createFromImportPropertiesSecurity(List.of(second)).getTransactionCost()).isNull();
    second.setTc3(0.0);
    assertThat(ImportTransactionPos.createFromImportPropertiesSecurity(List.of(second)).getTransactionCost()).isNull();
    first.setTc3(null);
    assertThat(ImportTransactionPos.createFromImportPropertiesSecurity(List.of(first)).getTransactionCost())
        .isCloseTo(5.50, within(0.000001));
  }

  @Test
  void csvRecognizesAndConvertsThirdFee() throws Exception {
    ImportTransactionTemplate entity = new ImportTransactionTemplate();
    entity.setTemplateAsTxt("""
        tc3=Execution fee
        [END]
        templateId=1
        delimiterField=;
        dateFormat=dd.MM.yyyy
        transType=ACCUMULATE|Buy
        overRuleSeparators=All<.|,>
        """);
    TemplateConfigurationAndStateCsv template = new TemplateConfigurationAndStateCsv(entity, Locale.GERMAN, List.of());
    template.parseTemplateAndThrowError(true);
    assertThat(template.isValidTemplateForForm("Execution fee")).isTrue();
    assertThat(template.getColumnPropertyMapping()).containsValue("tc3");
    ImportProperties properties = properties();
    new ValueFormatConverter(template.getDateFormat(), template.getTimeFormat(), template.getThousandSeparators(),
        template.getThousandSeparatorsPattern(), template.getDecimalSeparator(), template.getLocale())
            .convertAndSetValue(properties, "tc3", "0,77", Double.class);
    assertThat(ImportTransactionPos.createFromImportPropertiesSecurity(List.of(properties)).getTransactionCost())
        .isEqualTo(0.77);
  }

  private ImportProperties properties() {
    ImportProperties properties = new ImportProperties(Map.of("Buy", TransactionType.ACCUMULATE),
        EnumSet.noneOf(ImportKnownOtherFlags.class), null);
    properties.setTransType("Buy");
    properties.setCac("CHF");
    properties.setCin("CHF");
    properties.setUnits(1.0);
    properties.setQuotation(10.0);
    return properties;
  }

  private ImportTransactionPos parse(String input) throws Exception {
    List<ImportTransactionTemplate> templates = new ArrayList<>();
    for (String category : List.of("buy_sell_instrument", "paid_dividend_interest")) {
      ImportTransactionTemplate template = new ImportTransactionTemplate();
      template.setTemplateAsTxt(resource("template/" + category + "-PDF-20260101-de.tmpl"));
      template.setValidSince(LocalDate.of(2026, 1, 1));
      template.copyPurposeInTextToFieldPurpose();
      templates.add(template);
    }
    ParseFormInputPDFasTXT parser = new ParseFormInputPDFasTXT(input,
        ImportTransactionHelperPdf.readTemplates(templates, Locale.forLanguageTag("de-CH")));
    List<ImportProperties> result = parser.parseInput();
    assertThat(result).as("Parsed properties for %s", input).isNotNull();
    assertThat(result).hasSize(1);
    return ImportTransactionPos.createFromImportPropertiesSecurity(result);
  }

  private String resource(String name) throws Exception {
    try (InputStream stream = getClass().getResourceAsStream(ROOT + name)) {
      assertThat(stream).as(name).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
