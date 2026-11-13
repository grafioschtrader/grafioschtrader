package grafioschtrader.platform.migrosbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
import grafioschtrader.platformimport.pdf.ParseFormInputPDFasTXT;
import grafioschtrader.types.TransactionType;

/** Checks Migros Bank taxes and optional bond interest with the real parser, without database access. */
class MigrosBankPdfTemplateTest {

  @ParameterizedTest(name = "{0}")
  @CsvFileSource(resources = "/migros_bank/expected.csv", numLinesToSkip = 1)
  void parsesRecordedLayouts(String file, TransactionType type, LocalDate date, String isin, String valor, double units,
      double quotation, String cashCurrency, String securityCurrency, double taxes, double fees, Double interest,
      double amount, LocalDate exDate, Double rate) throws Exception {
    ImportTransactionPos pos = parse(resource(file + ".txt"));
    assertThat(pos.getTransactionType()).isEqualTo(type);
    assertThat(pos.getTransactionTime()).isEqualTo(date.atStartOfDay());
    assertThat(pos.getIsin()).isEqualTo(isin);
    assertThat(pos.getField1StringImp()).isEqualTo(valor);
    assertThat(pos.getUnits()).isCloseTo(units, within(0.000001));
    assertThat(pos.getQuotation()).isCloseTo(quotation, within(0.000001));
    assertThat(pos.getCurrencyAccount()).isEqualTo(cashCurrency);
    assertThat(pos.getCurrencySecurity()).isEqualTo(securityCurrency);
    assertThat(pos.getCurrencyExRate()).isEqualTo(rate);
    assertThat(pos.getTaxCost() == null ? 0 : pos.getTaxCost()).isCloseTo(taxes, within(0.000001));
    assertThat(pos.getTransactionCost() == null ? 0 : pos.getTransactionCost()).isCloseTo(fees, within(0.000001));
    assertThat(pos.getAccruedInterest()).isEqualTo(interest);
    assertThat(pos.getExDate()).isEqualTo(exDate);
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(pos.getCashaccountAmount()).isCloseTo(amount, within(0.000001));
    assertThat(pos.getDiffCashaccountAmount()).isZero();
    assertThat(Math.abs(amount)).as("Keep published examples modest").isLessThanOrEqualTo(45000);
  }

  @Test
  void etfStampDutyIsNotAccruedInterest() throws Exception {
    ImportTransactionPos pos = parse(resource("Migros_Bank_Kauf_CHF_CHF_02.txt"));
    assertThat(pos.getTransactionType()).isEqualTo(TransactionType.ACCUMULATE);
    assertThat(pos.getTransactionTime()).isEqualTo(LocalDateTime.of(2022, 8, 29, 0, 0));
    assertThat(pos.getIsin()).isEqualTo("IE00B95PGT31");
    assertThat(pos.getUnits()).isEqualTo(900);
    assertThat(pos.getQuotation()).isEqualTo(27.66);
    assertThat(pos.getCurrencyAccount()).isEqualTo("CHF");
    assertThat(pos.getTaxCost()).isCloseTo(37.35, within(0.000001));
    assertThat(pos.getTransactionCost()).isCloseTo(45.45, within(0.000001));
    assertThat(pos.getAccruedInterest()).isNull();
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(pos.getCashaccountAmount()).isCloseTo(-24976.80, within(0.000001));
    assertThat(pos.getDiffCashaccountAmount()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = { "CHF", "EUR" })
  void bondInterestIsReadFromTheInterestLine(String currency) throws Exception {
    String input = resource("20221007_Kauf_Anleihe_CHF_CHF.txt").replace("CHF", currency);
    ImportTransactionPos pos = parse(input);
    assertThat(pos.getUnits()).isEqualTo(100);
    assertThat(pos.getQuotation()).isEqualTo(95.84);
    assertThat(pos.getAccruedInterest()).isCloseTo(49.15, within(0.000001));
    assertThat(pos.getTaxCost()).isCloseTo(7.20, within(0.000001));
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(pos.getCashaccountAmount()).isCloseTo(-9684.05, within(0.000001));
    assertThat(pos.getDiffCashaccountAmount()).isZero();
  }

  @Test
  void interestDoesNotDependOnThePreviousLine() throws Exception {
    String input = resource("20221007_Kauf_Anleihe_CHF_CHF.txt").replace("1.00 % Zins vom",
        "Zusätzliche Information\n1.00 % Zins vom");
    ImportTransactionPos pos = parse(input);
    assertThat(pos.getUnits()).isEqualTo(100);
    assertThat(pos.getAccruedInterest()).isEqualTo(49.15);
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(pos.getDiffCashaccountAmount()).isZero();
  }

  @Test
  void saleWithoutInterestKeepsFeesAndTaxesSeparate() throws Exception {
    String input = resource("Migros_Bank_Kauf_CHF_CHF_02.txt").replace("Kauf", "Verkauf")
        .replace("-24'894.00", "24'894.00").replace("-24'976.80", "24'811.20");
    ImportTransactionPos pos = parse(input);
    assertThat(pos.getTransactionType()).isEqualTo(TransactionType.REDUCE);
    assertThat(pos.getAccruedInterest()).isNull();
    assertThat(pos.getTaxCost()).isEqualTo(37.35);
    assertThat(pos.getTransactionCost()).isEqualTo(45.45);
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(pos.getCashaccountAmount()).isEqualTo(24811.20);
    assertThat(pos.getDiffCashaccountAmount()).isZero();
  }

  @ParameterizedTest
  @CsvSource({ "true,true,37.50,87.50", "true,false,18.75,106.25", "false,true,18.75,106.25", "false,false,0,125" })
  void dividendTaxesAreIndependentlyOptional(boolean withholding, boolean additionalRetention, double expectedTax,
      double expectedNet) throws Exception {
    String input = resource("20240903_Dividende_USD_USD.txt");
    if (!withholding) {
      input = input.replaceAll("(?m)^15% Nicht rückforderbare Steuern.*\\R", "");
    }
    if (!additionalRetention) {
      input = input.replaceAll("(?m)^15% zusätzlicher Steuerrückbehalt USA.*\\R", "");
    }
    input = input.replace("Netto USD 87.50", "Netto USD " + expectedNet);
    ImportTransactionPos pos = parse(input);
    assertThat(pos.getTaxCost() == null ? 0 : pos.getTaxCost()).isEqualTo(expectedTax);
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(pos.getCashaccountAmount()).isEqualTo(expectedNet);
    assertThat(pos.getDiffCashaccountAmount()).isZero();
  }

  private ImportTransactionPos parse(String input) throws Exception {
    List<ImportTransactionTemplate> templates = new ArrayList<>();
    try (var paths = Files.list(Path.of(getClass().getResource("/migros_bank/template").toURI()))) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".tmpl")).sorted().toList()) {
        ImportTransactionTemplate template = new ImportTransactionTemplate();
        template.setTemplateAsTxt(Files.readString(path, StandardCharsets.UTF_8));
        String validSince = path.getFileName().toString().split("-PDF-")[1].substring(0, 8);
        template.setValidSince(LocalDate.parse(validSince, java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        template.copyPurposeInTextToFieldPurpose();
        templates.add(template);
      }
    }
    ParseFormInputPDFasTXT parser = new ParseFormInputPDFasTXT(input,
        ImportTransactionHelperPdf.readTemplates(templates, Locale.forLanguageTag("de-CH")));
    List<ImportProperties> result = parser.parseInput();
    assertThat(result).isNotNull().isNotEmpty();
    return ImportTransactionPos.createFromImportPropertiesSecurity(result);
  }

  private String resource(String name) throws Exception {
    try (var stream = getClass().getResourceAsStream("/migros_bank/" + name)) {
      assertThat(stream).as(name).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
