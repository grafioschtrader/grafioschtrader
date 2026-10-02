package grafioschtrader.platform.saxotrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
import grafioschtrader.platformimport.pdf.ParseFormInputPDFasTXT;
import grafioschtrader.platformimport.pdf.TemplateConfigurationPDFasTXT;
import grafioschtrader.types.TransactionType;

/**
 * Tests the Saxo Bank CH dividend template (Corporate Action Detail Report) through the real parser without a Spring
 * context or database writes. Saxo prints the dividend per unit rounded to two decimal places, so the template relies
 * on quotationDecimals to calculate the exact quotation back from the booked net amount.
 */
class SaxoTraderDividendPdfTemplateTest {

  private static final String ROOT = "/saxo_trader/";
  private static final String USD_CHF = "SaxoTrader_Dividende_Quellensteuer_USD_CHF_01";

  @ParameterizedTest
  @CsvSource({ USD_CHF + ",2026-09-30,2026-09-17,IE00B3RBWM25,340,0.54,0.54336968,USD,CHF,0.833672,46.19,115.51",
      "SaxoTrader_Dividende_Quellensteuer_EUR_EUR_01,2026-09-30,2026-09-17,IE000LX17BP9,3500,0.04,0.0388,EUR,EUR,,"
          + "33.95,101.85",
      "SaxoTrader_Zins_Dividende_USD_CHF_01,2026-01-28,2026-01-15,IE00B5M4WH52,400,1.36,1.36319348,USD,CHF,0.765647,0,"
          + "417.49" })
  void parsesDividendAndReplacesRoundedQuotation(String file, LocalDate payDate, LocalDate exDate, String isin,
      double units, double printedQuotation, double exactQuotation, String securityCurrency, String cashCurrency,
      Double rate, double taxes, double amount) throws Exception {
    ImportTransactionPos pos = parse(resource(file + ".txt"));
    assertThat(pos.getTransactionType()).isEqualTo(TransactionType.DIVIDEND);
    assertThat(pos.getTransactionTime()).isEqualTo(payDate.atStartOfDay());
    assertThat(pos.getExDate()).isEqualTo(exDate);
    assertThat(pos.getIsin()).isEqualTo(isin);
    assertThat(pos.getUnits()).isEqualTo(units);
    assertThat(pos.getQuotation()).isEqualTo(printedQuotation);
    assertThat(pos.getCurrencySecurity()).isEqualTo(securityCurrency);
    assertThat(pos.getCurrencyAccount()).isEqualTo(cashCurrency);
    assertThat(pos.getCurrencyExRate()).isEqualTo(rate);
    assertThat(pos.getTaxCost() == null ? 0 : pos.getTaxCost()).isCloseTo(taxes, within(0.000001));
    assertThat(pos.getCashaccountAmount()).isCloseTo(amount, within(0.000001));

    assertThat(pos.resolveQuotationDecimals()).isEqualTo(2);
    assertThat(pos.replaceRoundedQuotation(2)).isTrue();
    assertThat(pos.getQuotation()).isEqualTo(exactQuotation);
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(pos.getDiffCashaccountAmount()).isZero();
  }

  @ParameterizedTest
  @CsvSource({ "Mär,3", "Mai,5", "Okt,10", "Dez,12" })
  void parsesGermanMonthAbbreviations(String month, int monthValue) throws Exception {
    ImportTransactionPos pos = parse(resource(USD_CHF + ".txt").replace("Sep-2026", month + "-2026"));
    assertThat(pos.getTransactionTime()).isEqualTo(LocalDateTime.of(2026, monthValue, 30, 0, 0));
    assertThat(pos.getExDate()).isEqualTo(LocalDate.of(2026, monthValue, 17));
    assertThat(pos.getTaxCost()).isCloseTo(46.19, within(0.000001));
  }

  @Test
  void rejectsCalculatedQuotationOutsideRoundingInterval() throws Exception {
    ImportTransactionPos pos = parse(resource(USD_CHF + ".txt").replace("0.833672 115.51", "0.833672 125.51"));
    assertThat(pos.replaceRoundedQuotation(2)).isFalse();
    assertThat(pos.getQuotation()).isEqualTo(0.54);
    assertThat(pos.reverseCalculateQuotation()).isGreaterThan(0.545);
    pos.calcDiffCashaccountAmountWhenPossible();
    assertThat(pos.getDiffCashaccountAmount()).isNotZero();
  }

  @ParameterizedTest
  @CsvSource(value = { "9", "-1", "x", "2,EURO=1" }, delimiter = ';')
  void rejectsInvalidQuotationDecimals(String value) throws Exception {
    ImportTransactionTemplate template = template(resource("template/paid_dividend_interest-PDF-20180101-de.tmpl")
        .replace("quotationDecimals=2", "quotationDecimals=" + value));
    assertThatThrownBy(
        () -> new TemplateConfigurationPDFasTXT(template, Locale.GERMAN).parseTemplateAndThrowError(true))
            .isInstanceOf(DataViolationException.class);
  }

  private ImportTransactionPos parse(String input) throws Exception {
    ImportTransactionTemplate template = template(resource("template/paid_dividend_interest-PDF-20180101-de.tmpl"));
    new TemplateConfigurationPDFasTXT(template, Locale.forLanguageTag("de-CH")).parseTemplateAndThrowError(true);
    ParseFormInputPDFasTXT parser = new ParseFormInputPDFasTXT(input,
        ImportTransactionHelperPdf.readTemplates(List.of(template), Locale.forLanguageTag("de-CH")));
    List<ImportProperties> result = parser.parseInput();
    assertThat(result).as("Parsed properties for %s", input).isNotNull().hasSize(1);
    return ImportTransactionPos.createFromImportPropertiesSecurity(result);
  }

  private ImportTransactionTemplate template(String text) {
    ImportTransactionTemplate template = new ImportTransactionTemplate();
    template.setTemplateAsTxt(text);
    template.setValidSince(LocalDate.of(2018, 1, 1));
    template.copyPurposeInTextToFieldPurpose();
    return template;
  }

  private String resource(String name) throws Exception {
    try (InputStream stream = getClass().getResourceAsStream(ROOT + name)) {
      assertThat(stream).as(name).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
