package grafioschtrader.connector.instrument.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.text.ParseException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafiosch.entities.ConnectorApiKey;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.eulerpool.EulerpoolFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.DividendCount;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SplitCount;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Live test against Eulerpool. The API key lives in the developer database, which is why this class uses the
 * {@code prod} profile (Flyway is off; the test only reads). Fixtures use ISINs because ticker identifiers can return
 * different series. Each instrument is reported separately, and provider failures propagate to JUnit.
 */
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
public class EulerpoolFeedConnectorTest {

  // Eulerpool returns these isolated Saturday rows in the ISIN series. Pin the exact dates so additional anomalies
  // still fail the test; the connector currently preserves the provider's dates.
  private static final Map<String, List<LocalDate>> KNOWN_WEEKEND_DATES = Map.of("US17275R1023",
      List.of(LocalDate.of(2016, 2, 27)), "US1912161007", List.of(LocalDate.of(2020, 11, 14)));

  // Both Swiss series contain zero closes on these dates even though their other OHLC values are positive.
  private static final Map<String, List<LocalDate>> KNOWN_ZERO_CLOSE_DATES = Map.of("CH0038863350",
      List.of(LocalDate.of(2021, 1, 4), LocalDate.of(2021, 1, 12)), "CH0030170408",
      List.of(LocalDate.of(2021, 1, 4), LocalDate.of(2021, 1, 12)));

  private final EulerpoolFeedConnector eulerpoolFeedConnector;
  private final ConnectorApiKeyJpaRepository apiKeyRepository;
  private String apiKey;

  @Autowired
  public EulerpoolFeedConnectorTest(EulerpoolFeedConnector eulerpoolFeedConnector,
      ConnectorApiKeyJpaRepository apiKeyRepository) {
    this.eulerpoolFeedConnector = eulerpoolFeedConnector;
    this.apiKeyRepository = apiKeyRepository;
  }

  @BeforeEach
  void requireApiKey() {
    assumeTrue(eulerpoolFeedConnector.isActivated(), "Eulerpool connector is not configured");
    ConnectorApiKey key = apiKeyRepository.findById("eulerpool").orElse(null);
    assumeTrue(key != null, "Eulerpool API key is missing");
    String decrypted = null;
    try {
      decrypted = key.getApiKey();
    } catch (Exception _) {
      assumeTrue(false, "Api key of 'eulerpool' cannot be decrypted, JASYPT_ENCRYPTOR_PASSWORD not set?");
    }
    assumeTrue(decrypted != null && !decrypted.isBlank(), "Eulerpool API key is blank");
    this.apiKey = decrypted;
  }

  // Security price tests
  // =======================================
  @ParameterizedTest(name = "Last price: {0}")
  @MethodSource("historicalSecurities")
  void updateSecurityLastPriceTest(SecurityHistoricalDate fixture) throws Exception {
    Security security = fixture.security;
    security.setUrlIntraExtend(security.getUrlHistoryExtend());
    security.setUrlHistoryExtend(null);
    eulerpoolFeedConnector.checkAndClearSecuritycurrencyUrlExtend(security, FeedSupport.FS_INTRA);
    eulerpoolFeedConnector.updateSecurityLastPrice(security);
    Assertions.assertThat(security.getSLast()).isNotNull().isPositive();
    Assertions.assertThat(security.getSTimestamp()).isNotNull();
  }

  @ParameterizedTest(name = "History: {0}")
  @MethodSource("historicalSecurities")
  void getEodSecurityHistoryTest(SecurityHistoricalDate fixture) throws Exception {
    eulerpoolFeedConnector.checkAndClearSecuritycurrencyUrlExtend(fixture.security, FeedSupport.FS_HISTORY);
    List<Historyquote> quotes = eulerpoolFeedConnector.getEodSecurityHistory(fixture.security, fixture.from,
        fixture.to);
    Assertions.assertThat(quotes).hasSize(fixture.expectedRows);
    Assertions.assertThat(quotes).extracting(Historyquote::getDate).doesNotHaveDuplicates().isSorted();
    Assertions.assertThat(quotes.getFirst().getDate()).isEqualTo(fixture.from);
    Assertions.assertThat(quotes.getLast().getDate()).isEqualTo(fixture.to);
    Assertions.assertThat(quotes).allSatisfy(quote -> {
      Assertions.assertThat(quote.getDate()).isBetween(fixture.from, fixture.to);
      Assertions.assertThat(quote.getClose()).isFinite().isNotNegative();
    });
    List<LocalDate> weekendDates = quotes.stream().map(Historyquote::getDate)
        .filter(date -> date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY).toList();
    Assertions.assertThat(weekendDates)
        .containsExactlyElementsOf(KNOWN_WEEKEND_DATES.getOrDefault(fixture.security.getUrlHistoryExtend(), List.of()));
    Assertions.assertThat(quotes.stream().filter(quote -> quote.getClose() == 0.0).map(Historyquote::getDate).toList())
        .containsExactlyElementsOf(
            KNOWN_ZERO_CLOSE_DATES.getOrDefault(fixture.security.getUrlHistoryExtend(), List.of()));
  }

  static Stream<Named<SecurityHistoricalDate>> historicalSecurities() throws ParseException {
    List<SecurityHistoricalDate> historicalDates = new ArrayList<>();
    historicalDates.add(new SecurityHistoricalDate("Apple", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
        "US0378331005", GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 6564, "2000-01-03", "2026-02-09"));
    historicalDates.add(new SecurityHistoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
        "US17275R1023", GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 6565, "2000-01-03", "2026-02-09"));
    historicalDates.add(new SecurityHistoricalDate("Coca-Cola", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
        "US1912161007", GlobalConstants.STOCK_EX_MIC_NYSE, GlobalConstants.MC_USD, 6565, "2000-01-03", "2026-02-09"));
    historicalDates.add(new SecurityHistoricalDate("SAP", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
        "DE0007164600", GlobalConstants.STOCK_EX_MIC_XETRA, GlobalConstants.MC_EUR, 6671, "2000-01-03", "2026-02-09"));
    historicalDates.add(new SecurityHistoricalDate("Nestle", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
        "CH0038863350", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 6629, "2000-01-03", "2026-02-09"));
    historicalDates.add(new SecurityHistoricalDate("Geberit", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
        "CH0030170408", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 6631, "2000-01-03", "2026-02-09"));
    return historicalDates.stream().map(fixture -> Named.of(fixture.security.getName(), fixture));
  }

  // Split and dividend tests
  // =======================================
  @ParameterizedTest(name = "Splits: {0}")
  @MethodSource("splitSecurities")
  void getSplitsTest(SplitCount fixture) throws Exception {
    List<Securitysplit> splits = eulerpoolFeedConnector.getSplitHistory(fixture.security, fixture.from, fixture.to);
    Assertions.assertThat(splits).hasSize(fixture.expectedRows);
    Assertions.assertThat(splits).extracting(Securitysplit::getSplitDate).doesNotHaveDuplicates();
    Assertions.assertThat(splits).allSatisfy(split -> {
      Assertions.assertThat(split.getSplitDate()).isBetween(fixture.from, fixture.to);
      Assertions.assertThat(split.getFromFactor()).isPositive();
      Assertions.assertThat(split.getToFactor()).isPositive();
    });
  }

  static Stream<Named<SplitCount>> splitSecurities() throws ParseException {
    return Stream
        .of(new SplitCount("Hub Group Inc", "US4433201062", 3, "2000-01-03", "2024-01-31"),
            new SplitCount("Apple Inc", "US0378331005", 3, "2000-01-03", "2014-06-09"),
            new SplitCount("Apple Inc", "US0378331005", 4, "2000-01-03", "2023-06-09"),
            new SplitCount("NIKE", "US6541061031", 3, "2007-04-03", "2023-06-09"),
            new SplitCount("NIKE", "US6541061031", 3, "2000-01-03", "2023-06-09"))
        .map(fixture -> Named.of(fixture.security.getName() + " " + fixture.from + " to " + fixture.to, fixture));
  }

  @ParameterizedTest(name = "Dividends: {0}")
  @MethodSource("dividendSecurities")
  void getDividendHistoryTest(DividendCount fixture) throws Exception {
    List<Dividend> dividends = eulerpoolFeedConnector.getDividendHistory(fixture.security, fixture.from).stream()
        .filter(dividend -> !dividend.getEventDate().isAfter(fixture.to)).toList();
    Assertions.assertThat(dividends).hasSize(fixture.expectedRows);
    Assertions.assertThat(dividends).allSatisfy(dividend -> {
      Assertions.assertThat(dividend.getEventDate()).isBetween(fixture.from, fixture.to);
      Assertions.assertThat(dividend.getAmountAdjusted()).isPositive();
      Assertions.assertThat(dividend.getCurrency()).isNotNull().isEqualTo(fixture.security.getCurrency());
    });
  }

  static Stream<Named<DividendCount>> dividendSecurities() throws ParseException {
    List<DividendCount> dividendCount = new ArrayList<>();
    dividendCount.add(
        new DividendCount("Nestlé S.A", ConnectorTestHelper.ISIN_Nestle, "CH0038863350", 24, "2000-01-03", "2024-03-31",
            GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    dividendCount.add(new DividendCount("Apple Inc", ConnectorTestHelper.ISIN_Apple, "US0378331005", 47, "2000-01-03",
        "2024-03-31", GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    dividendCount.add(new DividendCount("Walmart Inc", ConnectorTestHelper.ISIN_Walmart, "US9311421039", 98,
        "2000-01-03", "2024-03-31", GlobalConstants.STOCK_EX_MIC_NYSE, GlobalConstants.MC_USD,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT));

    return dividendCount.stream().map(fixture -> Named.of(fixture.security.getName(), fixture));
  }

  /**
   * The connector URL-encodes the caret, but Eulerpool does not support indices. Its error must mask the token.
   */
  @Test
  @DisplayName("An unsupported index returns an error with a masked API key")
  void apiKeyIsNotLeakedOnAnUnsupportedIdentifier() {
    Security security = new Security();
    security.setName("Unsupported index");
    security.setUrlHistoryExtend("^GSPC");
    IOException exception = assertThrows(IOException.class, () -> eulerpoolFeedConnector.getEodSecurityHistory(security,
        LocalDate.parse("2024-01-02"), LocalDate.parse("2024-03-28")));
    // A boolean assertion keeps the real key out of JUnit's expected/actual failure output.
    assertFalse(exception.getMessage().contains(apiKey), "Error message contains the Eulerpool API key");
    Assertions.assertThat(exception).hasMessageContaining("token=???");
  }

}
