package grafioschtrader.connector.instrument.test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.generic.GenericFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.entities.GenericConnectorDef;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.GenericConnectorDefJpaRepository;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;

@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("test")
public class GenericFeedConnectorTest extends BaseFeedConnectorCheck {

  private final GenericFeedConnector otcxConnector;
  private final GenericFeedConnector gettexConnector;

  @Autowired
  public GenericFeedConnectorTest(GenericConnectorDefJpaRepository defRepo) {
    // Load OTC-X connector (no API key needed)
    GenericFeedConnector otcx = null;
    Optional<GenericConnectorDef> otcxDef = defRepo.findByShortId("otcx");
    if (otcxDef.isPresent() && otcxDef.get().isActivated()) {
      otcx = new GenericFeedConnector(otcxDef.get(), null);
    }
    this.otcxConnector = otcx;

    // Load gettex connector (auto-token: no manual API key needed)
    GenericFeedConnector gettex = null;
    Optional<GenericConnectorDef> gettexDef = defRepo.findByShortId("gettex");
    if (gettexDef.isPresent() && gettexDef.get().isActivated()) {
      gettex = new GenericFeedConnector(gettexDef.get(), null);
    }
    this.gettexConnector = gettex;
  }

  // ======================== OTC-X Tests ========================

  @Test
  void getEodSecurityHistoryTest() {
    assumeTrue(otcxConnector != null, "otcx connector not in DB or not activated — run otx.sql first");
    getEodSecurityHistory(false);
  }

  @Test
  void updateSecurityLastPriceTest() {
    assumeTrue(otcxConnector != null, "otcx connector not in DB or not activated — run otx.sql first");
    updateSecurityLastPriceByHistoricalData();
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return otcxConnector;
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities(HistoricalIntra histroricalIntra) {
    List<SecurityHistoricalDate> historicalDate = new ArrayList<>();
    try {
      // Rigi Bahnen AG — OTC-X uses ISIN as ticker (urlExtend)
      // expectedRows needs verification: set printValuesInsteadOfAssert=true in BaseFeedConnectorCheck to get actual count
      historicalDate.add(new SecurityHistoricalDate("Rigi Bahnen AG", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "CH0016290014", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 4912, "2005-12-05", "2026-02-20"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return historicalDate;
  }

  // ======================== Gettex Tests ========================

  @Test
  void getEodGettexHistoryTest() throws Exception {
    assumeTrue(gettexConnector != null, "gettex connector not in DB or not activated — run gettex.sql first");
    assumeTrue(gettexConnector.isActivated(), "gettex connector not activated");

    SecurityHistoricalDate hd = createGettexXeonSecurity();
    gettexConnector.checkAndClearSecuritycurrencyUrlExtend(hd.security, IFeedConnector.FeedSupport.FS_HISTORY);

    List<Historyquote> historyquotes = gettexConnector.getEodSecurityHistory(hd.security, hd.from, hd.to);

    Assertions.assertThat(historyquotes)
        .as("History quotes should not be null or empty for " + hd.security.getName()).isNotEmpty();

    var firstDate = historyquotes.getFirst().getDate();
    var lastDate = historyquotes.getLast().getDate();

    // Discovery mode: print actual values so expectedRows can be filled in later
    System.out.println(String.format("[gettex] Security: %s, Actual Rows: %d, First Quote Date: %s, Last Quote Date: %s",
        hd.security.getName(), historyquotes.size(), firstDate, lastDate));
  }

  @Test
  void updateGettexLastPriceTest() throws Exception {
    assumeTrue(gettexConnector != null, "gettex connector not in DB or not activated — run gettex.sql first");
    assumeTrue(gettexConnector.isActivated(), "gettex connector not activated");

    SecurityHistoricalDate hd = createGettexXeonSecurity();
    Security security = hd.security;
    // Intraday uses the same RIC as history
    security.setUrlIntraExtend(security.getUrlHistoryExtend());
    security.setUrlHistoryExtend(null);

    gettexConnector.checkAndClearSecuritycurrencyUrlExtend(security, IFeedConnector.FeedSupport.FS_INTRA);
    gettexConnector.updateSecurityLastPrice(security);

    System.out.println(String.format("[gettex] %s URL: %s last:%f open: %f high: %f low: %f timestamp: %tc",
        security.getName(), security.getUrlIntraExtend(), security.getSLast(),
        security.getSOpen(), security.getSHigh(), security.getSLow(), security.getSTimestamp()));

    Assertions.assertThat(security.getSLast())
        .as("Last price for " + security.getName()).isNotNull().isGreaterThan(0.0);
  }

  private SecurityHistoricalDate createGettexXeonSecurity() throws ParseException {
    // Xtrackers II EUR Overnight Rate Swap UCITS ETF 1C — RIC on gettex: XEON.GTX
    return new SecurityHistoricalDate("XEON Xtrackers II EUR Overnight Rate Swap UCITS ETF 1C", "LU0290358497",
        SpecialInvestmentInstruments.ETF, null, "XEON.GTX", "XMUN", GlobalConstants.MC_EUR,
        0, "2015-01-03", "2026-02-20");
  }
}
