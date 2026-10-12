package grafioschtrader.connector.instrument.yahoo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.yahoo.YahooHelper;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.repository.MicProviderMapRepository;

/** Derivation of the Yahoo symbol from the exchange ticker and the Yahoo suffix in {@code mic_provider_map}. */
class YahooUrlExtensionTest {

  private static SecurityBaseDataDto baseData(String ticker, String mic) {
    return new SecurityBaseDataDto("six", null, "CH0012005267", "CHF", mic, null, ticker, null, null, null, null, null,
        null, null, null, null, null);
  }

  @Test
  @DisplayName("Ticker plus suffix of the MIC; only a US MIC without suffix keeps the ticker; an unmapped MIC gives nothing")
  void derive() {
    MicProviderMapRepository repository = mock(MicProviderMapRepository.class);
    when(repository.findByIdProviderMic_IdProvider(YahooHelper.YAHOO))
        .thenReturn(List.of(new MicProviderMap(YahooHelper.YAHOO, "XSWX", "SWX", ".SW"),
            new MicProviderMap(YahooHelper.YAHOO, "XNAS", "NGM", null),
            new MicProviderMap(YahooHelper.YAHOO, "BIVA", "BIV", null)));
    YahooFeedConnectorCOM yahoo = new YahooFeedConnectorCOM();
    ReflectionTestUtils.setField(yahoo, "micProviderMapRepository", repository);

    assertEquals(Optional.of("NOVN.SW"), yahoo.deriveUrlExtension(baseData("NOVN", "XSWX"), FeedSupport.FS_DIVIDEND));
    assertEquals(Optional.of("NOVN.SW"), yahoo.deriveUrlExtension(baseData("NOVN", "XSWX"), FeedSupport.FS_SPLIT));
    assertEquals(Optional.of("AAPL"), yahoo.deriveUrlExtension(baseData("AAPL", "XNAS"), FeedSupport.FS_HISTORY));
    assertTrue(yahoo.deriveUrlExtension(baseData("SAP", "XETR"), FeedSupport.FS_HISTORY).isEmpty());
    assertTrue(yahoo.deriveUrlExtension(baseData(null, "XSWX"), FeedSupport.FS_HISTORY).isEmpty());
    assertTrue(yahoo.deriveUrlExtension(baseData("GMEXICOB", "BIVA"), FeedSupport.FS_SPLIT).isEmpty(),
        "a row without suffix outside NASDAQ and NYSE means an unknown suffix, not none");
  }

  @Test
  @DisplayName("Without the repository, e.g. outside Spring, no symbol is derived")
  void withoutRepository() {
    assertTrue(
        new YahooFeedConnectorCOM().deriveUrlExtension(baseData("NOVN", "XSWX"), FeedSupport.FS_DIVIDEND).isEmpty());
  }
}
