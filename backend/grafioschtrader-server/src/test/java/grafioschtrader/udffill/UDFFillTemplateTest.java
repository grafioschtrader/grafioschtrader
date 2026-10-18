package grafioschtrader.udffill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import grafiosch.types.UDFDataType;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.udffill.UDFFillTemplate.FallThroughReason;
import grafioschtrader.udffill.UDFFillTemplate.TemplateResult;
import grafioschtrader.udffill.UDFFillTemplateException.Reason;

/**
 * Replays the template cases of {@code testdata/udf_fill_template_cases.csv}, taken from the measured fields of the
 * specification, against real securities and the real validation of the field. No mocks, no database.
 */
class UDFFillTemplateTest {

  private static final String CASES = "/testdata/udf_fill_template_cases.csv";

  static Stream<Arguments> templateCases() throws IOException {
    List<Arguments> cases = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(UDFFillTemplateTest.class.getResourceAsStream(CASES), StandardCharsets.UTF_8))) {
      String line;
      boolean header = true;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank() || line.startsWith("#")) {
          continue;
        }
        if (header) {
          header = false;
          continue;
        }
        cases.add(toArguments(line));
      }
    }
    return cases.stream();
  }

  /**
   * Splits a row: the rule may contain the filter separator, so the first five fields and the last one are split off
   * and the rest is the rule.
   */
  private static Arguments toArguments(String line) {
    String[] head = line.split("\\|", 6);
    String rest = head[5];
    int last = rest.lastIndexOf('|');
    String rule = rest.substring(0, last).replace("\\r", "\r").replace("\\n", "\n");
    String expected = rest.substring(last + 1);
    return Arguments.of(head[0], emptyToNull(head[1]), emptyToNull(head[2]), emptyToNull(head[3]), head[4], rule,
        emptyToNull(expected));
  }

  private static String emptyToNull(String value) {
    return value.isEmpty() ? null : value;
  }

  @ParameterizedTest(name = "{0}: {5}")
  @MethodSource("templateCases")
  @DisplayName("A template rule produces the measured value or none")
  void evaluateCase(String name, String isin, String ticker, String mic, String currency, String rule,
      String expected) {
    Stockexchange stockexchange = new Stockexchange();
    stockexchange.setMic(mic);
    Security security = new Security(name, currency, null, stockexchange, null, null, null, ticker, isin);
    UDFMetadataSecurity field = field(rule.contains("://") ? UDFDataType.UDF_URLString : UDFDataType.UDF_String);

    TemplateResult result = UDFFillTemplate.parse(rule).evaluate(UDFFillContext.of(security, null), field);

    assertThat(result.value()).isEqualTo(expected);
  }

  @Test
  @DisplayName("A URL over 254 characters falls through as invalid, the next line produces the value")
  void tooLongUrlFallsThroughAsInvalid() {
    Security security = security("Apple Inc.", "AAPL", "XNAS");
    String rule = "https://long.example/" + "x".repeat(240) + "/{ticker}\nhttps://short.example/{ticker}";

    TemplateResult result = UDFFillTemplate.parse(rule).evaluate(UDFFillContext.of(security, null),
        field(UDFDataType.UDF_URLString));

    assertThat(result.value()).isEqualTo("https://short.example/AAPL");
    assertThat(result.lineNumber()).isEqualTo(2);
    assertThat(result.fallThrough()).singleElement()
        .satisfies(lo -> assertThat(lo.reason()).isEqualTo(FallThroughReason.INVALID));
  }

  @Test
  @DisplayName("A rule whose only candidate fails validation is reported as invalid, not as no line")
  void onlyInvalidCandidate() {
    TemplateResult result = UDFFillTemplate.parse("not a url {ticker}")
        .evaluate(UDFFillContext.of(security("Apple Inc.", "AAPL", "XNAS"), null), field(UDFDataType.UDF_URLString));

    assertThat(result.hasValue()).isFalse();
    assertThat(result.isInvalid()).isTrue();
    assertThat(result.getInvalidMessage()).isNotBlank();
  }

  @Test
  @DisplayName("The yahooSymbol placeholder reads the persisted symbol of the context")
  void yahooSymbol() {
    UDFFillTemplate template = UDFFillTemplate.parse("https://finance.yahoo.com/quote/{yahooSymbol|urlencode}");
    Security security = security("Nestlé SA", "NESN", "XSWX");

    assertThat(template.evaluate(UDFFillContext.of(security, "NESN.SW"), field(UDFDataType.UDF_URLString)).value())
        .isEqualTo("https://finance.yahoo.com/quote/NESN.SW");
    assertThat(template.evaluate(UDFFillContext.of(security, null), field(UDFDataType.UDF_URLString)).hasValue())
        .isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = ';', textBlock = """
      https://a.example/{isni};                         UNKNOWN_PLACEHOLDER;     1
      https://a.example/{isin|lowercase};               UNKNOWN_FILTER;          1
      https://a.example/{isin|lower(x)};                INVALID_FILTER_ARGUMENT; 1
      https://a.example/{isin|slug(a,b)};               INVALID_FILTER_ARGUMENT; 1
      https://a.example/{mic|map(XNAS)};                INVALID_FILTER_ARGUMENT; 1
      https://a.example/{mic|map(XNAS=A,XNAS=B)};       DUPLICATE_MAP_KEY;       1
      [isin=CH] https://a.example/{isin};               UNKNOWN_CONDITION_KEY;   1
      [mic=XNAS,mic=XNYS] https://a.example/{isin};     INVALID_CONDITION;       1
      '[mic=XNAS;mic=XNYS] https://a.example/{isin}';   DUPLICATE_CONDITION_KEY; 1
      [mic=XNAS]https://a.example/{isin};               CONDITION_SPACE_MISSING; 1
      [mic=XNAS https://a.example/{isin};               UNBALANCED_BRACKET;      1
      https://a.example/{isin;                          UNBALANCED_BRACE;        1
      https://a.example/isin};                          UNBALANCED_BRACE;        1
      [mic=XNAS];                                       EMPTY_TEMPLATE;          1
      '# only a comment';                               NO_LINE;                 1
      """)
  @DisplayName("A rule that does not parse is refused with reason and line number")
  void parseErrorOnFirstLine(String rule, Reason reason, int line) {
    assertParseError(rule, reason, line);
  }

  @Test
  @DisplayName("The line number of a parse error counts comment and empty lines")
  void parseErrorLineNumber() {
    assertParseError("# marketbeat\n\nhttps://a.example/{isin}\r\nhttps://b.example/{tickr}",
        Reason.UNKNOWN_PLACEHOLDER, 4);
    assertParseError("https://a.example/{isin}\n[mic=XNAS;currency=USD;mic=XNYS] https://b.example/{isin}",
        Reason.DUPLICATE_CONDITION_KEY, 2);
  }

  @Test
  @DisplayName("The position of a parse error points into its line")
  void parseErrorPosition() {
    assertThatThrownBy(() -> UDFFillTemplate.parse("  https://a.example/{tickr}"))
        .isInstanceOfSatisfying(UDFFillTemplateException.class, e -> assertThat(e.getPosition()).isEqualTo(21));
  }

  private static void assertParseError(String rule, Reason reason, int line) {
    assertThatThrownBy(() -> UDFFillTemplate.parse(rule)).isInstanceOfSatisfying(UDFFillTemplateException.class, e -> {
      assertThat(e.getReason()).isEqualTo(reason);
      assertThat(e.getLine()).isEqualTo(line);
    });
  }

  private static Security security(String name, String ticker, String mic) {
    Stockexchange stockexchange = new Stockexchange();
    stockexchange.setMic(mic);
    return new Security(name, "USD", null, stockexchange, null, null, null, ticker, null);
  }

  private static UDFMetadataSecurity field(UDFDataType udfDataType) {
    UDFMetadataSecurity field = new UDFMetadataSecurity();
    field.setUdfDataType(udfDataType);
    field.setFieldSize(udfDataType == UDFDataType.UDF_String ? "1,254" : null);
    return field;
  }
}
