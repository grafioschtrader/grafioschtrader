package grafioschtrader.connector.fundamental;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;

/**
 * Symbol rules of the fundamental sources for a stock exchange without MIC, such as the one of private papers. Such a
 * security once threw a NullPointerException in the Finnhub thread, which aborted the whole weekly load.
 */
class FundamentalFeedSupportTest {

  @Test
  @DisplayName("A security on an exchange without MIC is no US listing and has no derived Yahoo symbol")
  void exchangeWithoutMic() {
    Stockexchange privatePapers = new Stockexchange();
    Security security = new Security();
    security.setStockexchange(privatePapers);
    security.setTickerSymbol("PRIV");

    assertThat(FundamentalFeedSupport.isUsListing(security)).isFalse();
    assertThat(FundamentalFeedSupport.derivedYahooSymbol(security)).isNull();
  }

  @Test
  @DisplayName("A NASDAQ ticker is a US listing with the ticker as Yahoo symbol")
  void usListing() {
    Stockexchange nasdaq = new Stockexchange();
    nasdaq.setMic("XNAS");
    Security security = new Security();
    security.setStockexchange(nasdaq);
    security.setTickerSymbol("aapl");

    assertThat(FundamentalFeedSupport.isUsListing(security)).isTrue();
    assertThat(FundamentalFeedSupport.derivedYahooSymbol(security)).isEqualTo("AAPL");
  }
}
