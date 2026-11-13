package grafioschtrader.platform.postfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
import grafioschtrader.platformimport.pdf.ParseFormInputPDFasTXT;
import grafioschtrader.types.TransactionType;

/** Verifies Yellowtrade and E-Trading statements on both sides of the May 2016 platform change without a database. */
class PostfinancePdfTemplateTest {

  @ParameterizedTest(name = "{0}")
  @CsvFileSource(resources = "/postfinance/expected.csv", numLinesToSkip = 1)
  void parsesRecordedLayouts(String file, TransactionType type, LocalDate date, String isin, double units,
      double quotation, String cashCurrency, String securityCurrency, double taxes, double fees, Double interest,
      double amount, Double difference, Double roundingStep) throws Exception {
    ImportTransactionPos pos = parse(resource(file + ".txt"));
    assertThat(pos.getTransactionType()).isEqualTo(type);
    assertThat(pos.getTransactionTime()).isEqualTo(date.atStartOfDay());
    assertThat(pos.getIsin()).isEqualTo(isin);
    assertThat(pos.getUnits()).isCloseTo(units, within(0.000001));
    assertThat(pos.getQuotation()).isCloseTo(quotation, within(0.000001));
    assertThat(pos.getCurrencyAccount()).isEqualTo(cashCurrency);
    assertThat(pos.getCurrencySecurity()).isEqualTo(securityCurrency);
    assertThat(pos.getCurrencyExRate()).isNull();
    assertThat(pos.getTaxCost() == null ? 0 : pos.getTaxCost()).isCloseTo(taxes, within(0.000001));
    assertThat(pos.getTransactionCost() == null ? 0 : pos.getTransactionCost()).isCloseTo(fees, within(0.000001));
    assertThat(pos.getAccruedInterest()).isEqualTo(interest);
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(pos.getCashaccountAmount()).isCloseTo(amount, within(0.000001));
    assertThat(pos.getDiffCashaccountAmount()).isCloseTo(difference == null ? 0 : difference, within(0.000001));
    assertThat(pos.resolveConfiguredRoundingStep()).isEqualTo(roundingStep);
    assertThat(Math.abs(amount)).as("Keep published examples modest").isLessThanOrEqualTo(45000);
  }

  @Test
  void missingExecutionFeeCannotBeAcceptedAsRounding() throws Exception {
    String input = resource("20140613_Kauf_Anleihe_CHF_CHF.txt").replaceAll("(?m)^SEPARATE AUSFUEHRUNGSGEBUEHR.*\\R",
        "");
    ImportTransactionPos pos = parse(input);
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(pos.getDiffCashaccountAmount()).isEqualTo(2);
    assertThat(pos.resolveConfiguredRoundingStep()).isNull();
  }

  @Test
  void migrationTransferIsNotImportedAsAnotherPurchase() throws Exception {
    assertThat(parser(resource("20160515_Titeltransfer_CHF_CHF.txt")).parseInput()).isNull();
  }

  private ImportTransactionPos parse(String input) throws Exception {
    List<ImportProperties> result = parser(input).parseInput();
    assertThat(result).as("Parsed transaction properties: %s", input).isNotNull().isNotEmpty();
    return ImportTransactionPos.createFromImportPropertiesSecurity(result);
  }

  private ParseFormInputPDFasTXT parser(String input) throws Exception {
    List<ImportTransactionTemplate> templates = new ArrayList<>();
    try (var paths = Files.list(Path.of(getClass().getResource("/postfinance/template").toURI()))) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".tmpl")).sorted().toList()) {
        ImportTransactionTemplate template = new ImportTransactionTemplate();
        template.setTemplateAsTxt(Files.readString(path, StandardCharsets.UTF_8));
        String validSince = path.getFileName().toString().split("-PDF-")[1].substring(0, 8);
        template.setValidSince(LocalDate.parse(validSince, DateTimeFormatter.BASIC_ISO_DATE));
        template.copyPurposeInTextToFieldPurpose();
        templates.add(template);
      }
    }
    return new ParseFormInputPDFasTXT(input,
        ImportTransactionHelperPdf.readTemplates(templates, Locale.forLanguageTag("de-CH")));
  }

  private String resource(String name) throws Exception {
    try (var stream = getClass().getResourceAsStream("/postfinance/" + name)) {
      assertThat(stream).as(name).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
