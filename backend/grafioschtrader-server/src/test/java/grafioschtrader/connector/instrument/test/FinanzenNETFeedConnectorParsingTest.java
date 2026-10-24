package grafioschtrader.connector.instrument.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import grafioschtrader.connector.instrument.finanzennet.FinanzenNETFeedConnector;
import grafioschtrader.entities.Currencypair;

class FinanzenNETFeedConnectorParsingTest {

  @ParameterizedTest
  @DisplayName("Read complete quotes with and without thousands separators")
  @CsvSource(delimiter = '|', value = { "68.137,76 CHF -1188,60 -1,71 % | 68137.76 | 69326.36 | -1.71",
      "68137,76 CHF -1188,60 -1,71 % | 68137.76 | 69326.36 | -1.71",
      "68137,76 CHF -188,60 -0,28 % | 68137.76 | 68326.36 | -0.28",
      "68.137,76 CHF -1.188,60 -1,71 % | 68137.76 | 69326.36 | -1.71",
      "68.137,76 CHF +1188,60 +1,78 % | 68137.76 | 66949.16 | 1.78",
      "1,0809 EUR +0,0014 +0,13 % | 1.0809 | 1.0795 | 0.13",
      "68.137,76 CHF ±0,00 ±0,00 % | 68137.76 | 68137.76 | 0.0" })
  void parsesSnapshot(String snapshot, double expectedLast, double expectedOpen, double expectedChange)
      throws Exception {
    var connector = new FinanzenNETFeedConnector() {
      @Override
      protected void initalizeHttpClient(String domain) {
        // Recorded snapshot only; constructor must not contact the provider.
      }

      @Override
      protected Document getDoc(String url) {
        return Jsoup.parse("<div class=\"snapshot__values\">" + snapshot + "</div>");
      }
    };
    var currencypair = new Currencypair();
    currencypair.setUrlIntraExtend("bitcoin-franken-kurs");

    connector.updateCurrencyPairLastPrice(currencypair);

    assertEquals(expectedLast, currencypair.getSLast(), 0.000001);
    assertEquals(expectedOpen, currencypair.getSOpen(), 0.000001);
    assertEquals(expectedChange, currencypair.getSChangePercentage(), 0.000001);
  }
}
