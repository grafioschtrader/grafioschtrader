package grafioschtrader.udffill.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import grafiosch.types.UDFDataType;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.udffill.ai.ValueProposalVerifier.Discarded;
import grafioschtrader.udffill.ai.ValueProposalVerifier.NotFound;
import grafioschtrader.udffill.ai.ValueProposalVerifier.Proposed;
import grafioschtrader.udffill.ai.ValueProposalVerifier.Retry;
import grafioschtrader.udffill.ai.ValueProposalVerifier.ValueAnswer;
import grafioschtrader.udffill.ai.ValueProposalVerifier.Verification;
import grafioschtrader.udffill.http.FakeHttpTransport;
import grafioschtrader.udffill.http.FakeHttpTransport.Answer;
import grafioschtrader.udffill.http.UDFFillHttp;

/**
 * The verification of value proposals against recorded pages under {@code udffill/pages/}. The real fetcher runs with
 * an injected DNS lookup and a fake transport; no test opens a socket and none calls a model.
 */
class ValueProposalVerifierTest {

  private static final String BASE = "https://www.fund.example/";
  private static final Map<String, String> PAGES = Map.of("ter", "ter-fund-page.html", "larger",
      "ter-larger-number.html", "dist", "ter-other-share-class.html", "de", "ter-german.html");

  private final Security security = new Security("iShares Core MSCI World UCITS ETF USD (Acc)", "USD", null, null, null,
      null, null, "SWDA", "IE00B4L5Y983");

  private static Answer page(String name) {
    try (InputStream in = ValueProposalVerifierTest.class.getResourceAsStream("/udffill/pages/" + name)) {
      return new Answer(200, "text/html; charset=utf-8", null, null, in.readAllBytes());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private final FakeHttpTransport transport = new FakeHttpTransport(uri -> {
    String path = uri.getPath().substring(1);
    if (path.equals("factsheet")) {
      return Answer.redirect("/ter");
    }
    return PAGES.containsKey(path) ? page(PAGES.get(path)) : null;
  });

  private final ValueProposalVerifier verifier = new ValueProposalVerifier(
      new UDFFillHttp(_ -> new InetAddress[] { InetAddress.ofLiteral("93.184.215.14") }, transport, "test"));

  private static UDFMetadataSecurity field(UDFDataType udfDataType, String fieldSize) {
    UDFMetadataSecurity field = new UDFMetadataSecurity();
    field.setUdfDataType(udfDataType);
    field.setFieldSize(fieldSize);
    return field;
  }

  private Verification verify(String value, String page, String excerpt, String asOfDate) {
    return verifier.verify(field(UDFDataType.UDF_Numeric, "10,4"), security,
        new ValueAnswer(value, BASE + page, excerpt, asOfDate));
  }

  @Test
  @DisplayName("A TER page that names the fund and contains the excerpt with the value gives a proposal")
  void terPageMatches() {
    assertThat(verify("0.20", "ter", "Total Expense Ratio 0.20%", "2026-09-30"))
        .isEqualTo(new Proposed("0.2", BASE + "ter", "Total Expense Ratio 0.20%", LocalDate.of(2026, 9, 30)));
  }

  @Test
  @DisplayName("A decimal comma and a no-break space on the page still match; the stored form uses a dot")
  void germanPageMatches() {
    assertThat(verify("0,20", "de", "Laufende Kosten 0,20 % p.a.", "2026-09-30"))
        .isEqualTo(new Proposed("0.2", BASE + "de", "Laufende Kosten 0,20 % p.a.", LocalDate.of(2026, 9, 30)));
  }

  @Test
  @DisplayName("A number that appears only as part of a larger number is not the value")
  void numberOnlyInsideLargerNumber() {
    assertThat(verify("0.2", "larger", "Total Expense Ratio 10.25%", null)).isInstanceOf(Discarded.class);
    assertThat(verify("0.2", "larger", "Portfolio turnover 0.21", null)).isInstanceOf(Discarded.class);
  }

  @Test
  @DisplayName("The page of another share class without the ISIN does not name the security")
  void wrongShareClass() {
    assertThat(verify("0.20", "dist", "Total Expense Ratio 0.20%", null))
        .isEqualTo(new Discarded("The source does not contain the ISIN"));
  }

  @Test
  @DisplayName("An excerpt that is not on the page is discarded")
  void excerptNotOnPage() {
    assertThat(verify("0.20", "ter", "TER 0.20% per year", null))
        .isEqualTo(new Discarded("The excerpt does not occur in the source"));
  }

  @Test
  @DisplayName("NaN and other non-finite values are discarded before any request")
  void nanValue() {
    assertThat(verify("NaN", "ter", "Total Expense Ratio 0.20%", null))
        .isEqualTo(new Discarded("The value is not a finite number"));
    assertThat(transport.getRequests()).isEmpty();
  }

  @Test
  @DisplayName("A model answer of null is not found, not a failed verification")
  void modelAnswerNull() {
    assertThat(verifier.verify(field(UDFDataType.UDF_Numeric, "10,4"), security, null)).isInstanceOf(NotFound.class);
    assertThat(verify(null, "ter", null, null)).isInstanceOf(NotFound.class);
  }

  @Test
  @DisplayName("A transport failure while verifying is retryable, an as-of date not on the page is dropped")
  void transportFailureAndDate() {
    assertThat(verify("0.20", "missing", "Total Expense Ratio 0.20%", null)).isInstanceOf(Retry.class);
    assertThat(verify("0.20", "ter", "Total Expense Ratio 0.20%", "2026-10-01"))
        .isEqualTo(new Proposed("0.2", BASE + "ter", "Total Expense Ratio 0.20%", null));
  }

  @Test
  @DisplayName("A URL value must answer and name the security; the final URL after redirects is stored")
  void urlValue() {
    Verification verification = verifier.verify(field(UDFDataType.UDF_URLString, null), security,
        new ValueAnswer(BASE + "factsheet", BASE + "ter", "ISIN IE00B4L5Y983", null));
    assertThat(verification).isEqualTo(new Proposed(BASE + "ter", BASE + "ter", "ISIN IE00B4L5Y983", null));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(delimiter = ';', nullValues = "null", value = { "0.20;0.2", "'1''234.50';1234.5", "1.234,5;1234.5",
      "1,234.5;1234.5", "-0.0;0", "12;12", "1e3;null", "0.1.2.3;123" })
  @DisplayName("Numbers are canonicalised to plain decimal notation with a dot and without trailing zeros")
  void canonicalNumbers(String raw, String expected) {
    Double number = ValueProposalVerifier.parseNumber(raw);
    assertThat(number == null ? null : ValueProposalVerifier.format(number)).isEqualTo(expected);
  }
}
