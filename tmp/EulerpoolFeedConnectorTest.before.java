package grafioschtrader.connector.instrument.test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafiosch.entities.ConnectorApiKey;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.eulerpool.EulerpoolFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.DividendCount;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Security;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Live test against Eulerpool. The API key lives in the developer database, which is why this class uses the
 * {@code prod} profile (Flyway is off; the test only reads). Structure follows
 * {@link EodHistoricalDataConnectorTest}.
 */
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
public class EulerpoolFeedConnectorTest extends BaseFeedConnectorCheck {

  private final EulerpoolFeedConnector eulerpoolFeedConnector;
  private final String apiKey;

  @Autowired
  public EulerpoolFeedConnectorTest(EulerpoolFeedConnector eulerpoolFeedConnector,
      ConnectorApiKeyJpaRepository apiKeyRepository) {
    this.eulerpoolFeedConnector = eulerpoolFeedConnector;
    assumeTrue(eulerpoolFeedConnector.isActivated());
    ConnectorApiKey key = apiKeyRepository.findById("eulerpool").orElse(null);
    assumeTrue(key != null);
    String decrypted = null;
    try {
      decrypted = key.getApiKey();
    } catch (Exception e) {
      assumeTrue(false, "Api key of 'eulerpool' cannot be decrypted, JASYPT_ENCRYPTOR_PASSWORD not set?");
    }
    assumeTrue(decrypted != null && !decrypted.isBlank());
    this.apiKey = decrypted;
  }

  // Security price tests
  // =======================================
  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities(HistoricalIntra histroricalIntra) {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("Apple", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "US0378331005", GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 6564, "2000-01-03",
          "2026-02-09"));
      hisoricalDate.add(new SecurityHistoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "US17275R1023", GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 6565, "2000-01-03",
          "2026-02-09"));
      hisoricalDate.add(new SecurityHistoricalDate("Coca-Cola", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "US1912161007", GlobalConstants.STOCK_EX_MIC_NYSE, GlobalConstants.MC_USD, 6565, "2000-01-03", "2026-02-09"));
      hisoricalDate.add(new SecurityHistoricalDate("SAP", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "DE0007164600",
          GlobalConstants.STOCK_EX_MIC_XETRA, GlobalConstants.MC_EUR, 6671, "2000-01-03", "2026-02-09"));
      hisoricalDate.add(new SecurityHistoricalDate("Nestle", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "CH0038863350", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 6629, "2000-01-03", "2026-02-09"));
      hisoricalDate.add(new SecurityHistoricalDate("Geberit", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "CH0030170408", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 6631, "2000-01-03", "2026-02-09"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return eulerpoolFeedConnector;
  }

  // Split and dividend tests
  // =======================================
  @Test
  void getSplitsTest() throws ParseException {
    ConnectorTestHelper.standardSplitTest(eulerpoolFeedConnector, getSymbolMapping());
  }

  @Test
  void getDividendHistoryTest() throws ParseException {
    List<DividendCount> dividendCount = new ArrayList<>();
    dividendCount.add(new DividendCount("Nestlé S.A", ConnectorTestHelper.ISIN_Nestle, "CH0038863350", 24, "2000-01-03",
        "2024-03-31", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    dividendCount.add(new DividendCount("Apple Inc", ConnectorTestHelper.ISIN_Apple, "US0378331005", 47, "2000-01-03",
        "2024-03-31", GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    dividendCount.add(new DividendCount("Walmart Inc", ConnectorTestHelper.ISIN_Walmart, "US9311421039", 98,
        "2000-01-03", "2024-03-31", GlobalConstants.STOCK_EX_MIC_NYSE, GlobalConstants.MC_USD,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT));

    dividendCount.parallelStream().forEach(dc -> {
      List<Dividend> dividends = new ArrayList<>();
      try {
        dividends = eulerpoolFeedConnector.getDividendHistory(dc.security, dc.from).stream()
            .filter(d -> !d.getEventDate().isAfter(dc.to)).toList();
      } catch (final Exception e) {
        e.printStackTrace();
      }
      Assertions.assertThat(dividends.size()).as("Dividends for %s", dc.security.getName()).isEqualTo(dc.expectedRows);
    });
  }

  /**
   * The API key travels in the URL, so an identifier that is illegal in a path must not leak it into the error.
   */
  @Test
  void apiKeyIsNotLeakedOnAnIllegalIdentifier() {
    Security security = new Security();
    security.setName("Illegal identifier");
    security.setUrlHistoryExtend("^GSPC");
    Assertions.assertThatThrownBy(() -> eulerpoolFeedConnector.getEodSecurityHistory(security,
        java.time.LocalDate.parse("2024-01-02"), java.time.LocalDate.parse("2024-03-28"))).hasMessageNotContaining(apiKey)
        .hasMessageContaining("???");
  }

  private Map<String, String> getSymbolMapping() {
    return Map.of("HUBG", "US4433201062", "AAPL", "US0378331005", "NKE", "US6541061031");
  }

}
