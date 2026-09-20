package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.platform.TransactionImportHelper;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
import grafioschtrader.platformimport.pdf.ParseFormInputPDFasTXT;
import grafioschtrader.types.TransactionType;

/** Replays a DKK payout for a holding traded in EUR through the PDF parser and import preparation. */
@DisplayName("Dividend import converts to cash currency and resolves ISIN independently of holdings")
class DividendImportCashCurrencyTest {
  private final ImportTransactionPosJpaRepositoryImpl repository = new ImportTransactionPosJpaRepositoryImpl();
  private final HoldSecurityaccountSecurityJpaRepository holdings = mock(
      HoldSecurityaccountSecurityJpaRepository.class);
  private final SecurityJpaRepository securities = mock(SecurityJpaRepository.class);
  private final ImportTransactionHead head = new ImportTransactionHead();
  private final Security dkk = security(4131, "DKK");
  private final Security eur = security(4226, "EUR");
  private ImportTransactionPos pos;

  @BeforeEach
  void prepareParsedDividend() throws Exception {
    ReflectionTestUtils.setField(repository, "holdSecurityaccountSecurityJpaRepository", holdings);
    ReflectionTestUtils.setField(repository, "securityJpaRepository", securities);
    Securityaccount account = new Securityaccount();
    account.setIdSecuritycashAccount(28);
    head.setSecurityaccount(account);
    head.setIdTenant(7);
    ImportTransactionTemplate template = new ImportTransactionTemplate();
    template.setTemplateAsTxt(resource("template/paid_dividend_interest-PDF-20260101-de.tmpl"));
    template.setValidSince(LocalDate.of(2026, 1, 1));
    ParseFormInputPDFasTXT parser = new ParseFormInputPDFasTXT(resource("doc-as-text/20260808_dividende.txt"),
        ImportTransactionHelperPdf.readTemplates(List.of(template), Locale.GERMAN));
    pos = ImportTransactionPos.createFromImportPropertiesSecurity(parser.parseInput());
    pos.setIdTenant(7);
    Cashaccount cashaccount = new Cashaccount();
    cashaccount.setCurrency("EUR");
    cashaccount.setIdTenant(7);
    pos.setCashaccount(cashaccount);
    when(securities.findByIsinAndCurrency("DK0062498333", "DKK")).thenReturn(dkk);
    when(securities.findByIsinAndCurrency("DK0062498333", "EUR")).thenReturn(eur);
    TransactionImportHelper.setSecurityToImportWhenPossible(pos, securities);
    repository.setCheckReadyForSingleTransaction(pos);
    assertThat(pos.getSecurity()).isSameAs(dkk);
    assertThat(pos.isReadyForTransaction()).isTrue();
  }

  @Test
  void convertsDividendAndTaxAndResolvesIsinInCashCurrencyWithoutHoldings() {
    repository.addPossibleExchangeRateForDividend(head, pos);
    assertThat(pos.getSecurity()).isSameAs(eur);
    assertThat(pos.getUnits()).isEqualTo(500.0);
    assertThat(pos.getQuotation()).isEqualTo(0.50169139);
    assertThat(pos.getTaxCost()).isEqualTo(67.73);
    assertThat(pos.getCashaccountAmount()).isEqualTo(183.12);
    assertThat(pos.getCurrencySecurity()).isEqualTo("EUR");
    assertThat(pos.getCurrencyExRate()).isNull();
    assertThat(pos.getDiffCashaccountAmount()).isZero();
    assertThat(pos.isReadyForTransaction()).isTrue();
    repository.addPossibleExchangeRateForDividend(head, pos);
    assertThat(pos.getQuotation()).isEqualTo(0.50169139);
    assertThat(pos.getTaxCost()).isEqualTo(67.73);
    verify(securities).findByIsinAndCurrency("DK0062498333", "EUR");
    verifyNoInteractions(holdings);
  }

  @Test
  void convertsBothZoetisTaxesWithInverseDocumentRateAndClearsExchangeRate() throws Exception {
    ImportTransactionTemplate template = new ImportTransactionTemplate();
    template.setTemplateAsTxt(resource("template/paid_dividend_interest-PDF-20260101-de.tmpl"));
    template.setValidSince(LocalDate.of(2026, 1, 1));
    ParseFormInputPDFasTXT parser = new ParseFormInputPDFasTXT(resource("doc-as-text/20260901_dividende.txt"),
        ImportTransactionHelperPdf.readTemplates(List.of(template), Locale.GERMAN));
    ImportTransactionPos zoetis = ImportTransactionPos.createFromImportPropertiesSecurity(parser.parseInput());
    Security usdSecurity = security(5001, "USD");
    Security eurSecurity = security(5002, "EUR");
    usdSecurity.setIsin("US98978V1035");
    eurSecurity.setIsin("US98978V1035");
    when(securities.findByIsinAndCurrency("US98978V1035", "USD")).thenReturn(usdSecurity);
    when(securities.findByIsinAndCurrency("US98978V1035", "EUR")).thenReturn(eurSecurity);
    zoetis.setCashaccount(pos.getCashaccount());
    TransactionImportHelper.setSecurityToImportWhenPossible(zoetis, securities);
    repository.setCheckReadyForSingleTransaction(zoetis);
    repository.addPossibleExchangeRateForDividend(head, zoetis);
    assertThat(zoetis.getSecurity()).isSameAs(eurSecurity);
    assertThat(zoetis.getQuotation()).isEqualTo(0.45687253);
    assertThat(zoetis.getTaxCost()).isEqualTo(30.15);
    assertThat(zoetis.getCashaccountAmount()).isEqualTo(70.36);
    assertThat(zoetis.getCurrencySecurity()).isEqualTo("EUR");
    assertThat(zoetis.getCurrencyExRate()).isNull();
    assertThat(zoetis.getDiffCashaccountAmount()).isZero();
    assertThat(zoetis.isReadyForTransaction()).isTrue();
    verifyNoInteractions(holdings);
  }

  @Test
  void conversionDoesNotHideACashDifference() {
    pos.setCashaccountAmount(184.12);
    repository.addPossibleExchangeRateForDividend(head, pos);
    assertUnconverted();
  }

  @Test
  void missingTargetSecurityDoesNotRelabelTheDkkSecurity() {
    when(securities.findByIsinAndCurrency("DK0062498333", "EUR")).thenReturn(null);
    repository.addPossibleExchangeRateForDividend(head, pos);
    assertUnconverted();
  }

  @Test
  void buyKeepsItsOriginalInstrumentAndExchangeRate() {
    pos.setTransactionType(TransactionType.ACCUMULATE.getValue());
    repository.addPossibleExchangeRateForDividend(head, pos);
    assertUnconverted();
  }

  @Test
  void costsInAThirdCurrencyNeedAnotherExchangeRate() {
    pos.setCurrencyCost("CHF");
    repository.addPossibleExchangeRateForDividend(head, pos);
    assertUnconverted();
  }

  @Test
  void missingOrInvalidRatesAndBookedPositionsAreNotConverted() {
    for (Double rate : new Double[] { null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY }) {
      pos.setCurrencyExRate(rate);
      repository.addPossibleExchangeRateForDividend(head, pos);
      assertUnconverted();
    }
    pos.setCurrencyExRate(0.13378437);
    pos.setIdTransaction(123);
    repository.addPossibleExchangeRateForDividend(head, pos);
    assertUnconverted();
  }

  @Test
  void costsAlreadyInCashCurrencyAreNotConvertedAgain() {
    pos.setCurrencyCost("EUR");
    pos.setTaxCost(67.73, null, false);
    pos.setTransactionCost(1.25);
    pos.setCashaccountAmount(181.87);
    repository.addPossibleExchangeRateForDividend(head, pos);
    assertThat(pos.getSecurity()).isSameAs(eur);
    assertThat(pos.getTaxCost()).isEqualTo(67.73);
    assertThat(pos.getTransactionCost()).isEqualTo(1.25);
    assertThat(pos.getCurrencyCost()).isEqualTo("EUR");
    assertThat(pos.getDiffCashaccountAmount()).isZero();
  }

  @Test
  void convertsExplicitPayoutCurrencyCostsOnce() {
    pos.setExDate(LocalDate.of(2026, 8, 13));
    pos.setCurrencyCost("DKK");
    repository.addPossibleExchangeRateForDividend(head, pos);
    verifyNoInteractions(holdings);
    assertThat(pos.getTaxCost()).isEqualTo(67.73);
    assertThat(pos.getCurrencyCost()).isEqualTo("EUR");
    assertThat(pos.getDiffCashaccountAmount()).isZero();
  }

  @Test
  void convertsFeesInPayoutCurrencyToCashCurrency() {
    pos.setTransactionCost(10.0);
    pos.setCashaccountAmount(181.78);
    repository.addPossibleExchangeRateForDividend(head, pos);
    assertThat(pos.getSecurity()).isSameAs(eur);
    assertThat(pos.getTransactionCost()).isEqualTo(1.34);
    assertThat(pos.getTaxCost()).isEqualTo(67.73);
    assertThat(pos.getCurrencyExRate()).isNull();
    assertThat(pos.getDiffCashaccountAmount()).isZero();
    verifyNoInteractions(holdings);
  }

  private void assertUnconverted() {
    assertThat(pos.getSecurity()).isSameAs(dkk);
    assertThat(pos.getQuotation()).isEqualTo(3.75);
    assertThat(pos.getTaxCost()).isEqualTo(506.25);
  }

  private Security security(int id, String currency) {
    Security security = new Security();
    security.setIdSecuritycurrency(id);
    security.setIsin("DK0062498333");
    security.setCurrency(currency);
    return security;
  }

  private String resource(String name) throws Exception {
    try (var input = getClass().getResourceAsStream("/cornertrader/" + name)) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
