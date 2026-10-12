package grafioschtrader.connector.instrument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.comdirect.ComdirectFeedConnector;
import grafioschtrader.connector.instrument.finnhub.FinnhubConnector;
import grafioschtrader.connector.instrument.stockdata.StockDataFeedConnector;
import grafioschtrader.connector.instrument.twelvedata.TwelvedataFeedConnector;
import grafioschtrader.dto.SecurityBaseDataDto;

/**
 * Connectors without a search of their own that build their URL extension from what another provider found: the US
 * key-based connectors from the exchange ticker, comdirect from the notation of a Consorsbank or onvista listing.
 */
class ForeignProviderUrlExtensionTest {

  private static SecurityBaseDataDto baseData(String providerId, String providerInstrumentId, String mic,
      String ticker, Map<String, String> attributes) {
    return new SecurityBaseDataDto(providerId, providerInstrumentId, "US0378331005", "USD", mic, null, ticker, null,
        null, null, null, null, null, null, null, null, attributes);
  }

  @Test
  @DisplayName("US ticker on NASDAQ and NYSE; share classes with a dot; other exchanges and odd tickers give nothing")
  void usTicker() {
    assertEquals(Optional.of("AAPL"),
        FeedConnectorHelper.deriveUsTicker(baseData("yahoo", "AAPL", "XNAS", "aapl", Map.of())));
    assertEquals(Optional.of("BRK.B"),
        FeedConnectorHelper.deriveUsTicker(baseData("yahoo", "BRK-B", "XNYS", "BRK-B", Map.of())));
    assertTrue(FeedConnectorHelper.deriveUsTicker(baseData("onvista", null, "XFRA", "APC", Map.of())).isEmpty());
    assertTrue(FeedConnectorHelper.deriveUsTicker(baseData("six", null, "XNYS", "^DJI", Map.of())).isEmpty());
    assertTrue(FeedConnectorHelper.deriveUsTicker(baseData("six", null, "XNYS", null, Map.of())).isEmpty());

    SecurityBaseDataDto apple = baseData("yahoo", "AAPL", "XNAS", "AAPL", Map.of());
    for (IFeedConnector connector : new IFeedConnector[] { new FinnhubConnector(), new TwelvedataFeedConnector(),
        new StockDataFeedConnector() }) {
      assertEquals(Optional.of("AAPL"), connector.deriveUrlExtension(apple, FeedSupport.FS_HISTORY),
          connector.getReadableName());
    }
  }

  @Test
  @DisplayName("comdirect: notation of a Consorsbank or onvista listing plus the path of the instrument type")
  void comdirect() {
    ComdirectFeedConnector comdirect = new ComdirectFeedConnector();
    assertEquals(Optional.of("aktien/detail/uebersicht.html?ID_NOTATION=39491761"),
        comdirect.deriveUrlExtension(baseData("consorsbank", "_39491761,MUN", "XMUN", "ADS", Map.of("type", "STO")),
            FeedSupport.FS_INTRA));
    assertEquals(Optional.of("etfs/detail/uebersicht.html?ID_NOTATION=48184822"),
        comdirect.deriveUrlExtension(baseData("consorsbank", "_48184822,SWX", "XSWX", null, Map.of("type", "ETF")),
            FeedSupport.FS_INTRA));
    assertEquals(Optional.of("anleihen/detail/uebersicht.html?ID_NOTATION=449635241"),
        comdirect.deriveUrlExtension(baseData("onvista", "BOND/265058343/eod_history?idNotation=449635241", "XFRA",
            "A3LUB5", Map.of("type", "BOND")), FeedSupport.FS_INTRA));
    assertEquals(Optional.of("fonds/detail/uebersicht.html?ID_NOTATION=3227521"),
        comdirect.deriveUrlExtension(baseData("onvista", "FUND/1/eod_history?idNotation=3227521", "XFRA", null,
            Map.of("type", "FUND")), FeedSupport.FS_INTRA));
    assertTrue(comdirect.deriveUrlExtension(baseData("yahoo", "AAPL", "XNAS", "AAPL", Map.of("type", "EQUITY")),
        FeedSupport.FS_INTRA).isEmpty(), "Yahoo has no notation");
    assertTrue(comdirect.deriveUrlExtension(baseData("consorsbank", "_1,GER", "XETR", null, Map.of("type", "CER")),
        FeedSupport.FS_INTRA).isEmpty(), "a type without comdirect path");
    assertTrue(comdirect.deriveUrlExtension(baseData("consorsbank", "_1,GER", "XETR", null, Map.of()),
        FeedSupport.FS_INTRA).isEmpty(), "master data without type");
  }
}
