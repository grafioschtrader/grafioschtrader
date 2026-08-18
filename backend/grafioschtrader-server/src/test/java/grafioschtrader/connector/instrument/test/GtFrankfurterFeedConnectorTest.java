package grafioschtrader.connector.instrument.test;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.frankfurter.GtFrankfurterFeedConnector;
import grafioschtrader.entities.Currencypair;

/**
 * Checks how a self-hosted Frankfurter instance is wired up. Deliberately free of network access: only the effect of
 * the configured root URL is of interest here, the protocol itself is covered by {@link FrankfurterFeedConnectorTest}.
 */
class GtFrankfurterFeedConnectorTest {

  private static final String INSTANCE_URL = "https://frankfurter.example.org";

  @Test
  void isDeactivatedWithoutConfiguredUrlTest() {
    Assertions.assertThat(new GtFrankfurterFeedConnector("").isActivated()).isFalse();
    Assertions.assertThat(new GtFrankfurterFeedConnector("   ").isActivated()).isFalse();
    Assertions.assertThat(new GtFrankfurterFeedConnector(null).isActivated()).isFalse();
  }

  @Test
  void isActivatedWithConfiguredUrlTest() {
    GtFrankfurterFeedConnector connector = new GtFrankfurterFeedConnector(INSTANCE_URL);

    Assertions.assertThat(connector.isActivated()).isTrue();
    Assertions.assertThat(connector.getID()).isEqualTo("gt.datafeed.gtfrankfurter");
    Assertions.assertThat(connector.supportsCurrency()).isTrue();
    Assertions.assertThat(connector.supportsSecurity()).isFalse();
  }

  @Test
  void buildsDownloadLinkAgainstConfiguredInstanceTest() {
    GtFrankfurterFeedConnector connector = new GtFrankfurterFeedConnector(INSTANCE_URL);
    Currencypair currencypair = new Currencypair(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF);

    String downloadLink = connector.getCurrencypairHistoricalDownloadLink(currencypair);

    Assertions.assertThat(downloadLink).startsWith(INSTANCE_URL + "/v2/rates?base=EUR&quotes=CHF&from=");
  }

  /** A configured URL with a trailing slash must not produce a double slash in the request. */
  @Test
  void trailingSlashIsNormalizedTest() {
    Currencypair currencypair = new Currencypair(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF);

    String withSlash = new GtFrankfurterFeedConnector(INSTANCE_URL + "/")
        .getCurrencypairHistoricalDownloadLink(currencypair);
    String withoutSlash = new GtFrankfurterFeedConnector(INSTANCE_URL)
        .getCurrencypairHistoricalDownloadLink(currencypair);

    Assertions.assertThat(withSlash).isEqualTo(withoutSlash);
  }
}
