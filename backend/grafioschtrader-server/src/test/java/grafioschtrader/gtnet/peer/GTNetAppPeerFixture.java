package grafioschtrader.gtnet.peer;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import grafiosch.test.gtnet.GTNetPeerTestSupport;
import tools.jackson.databind.JsonNode;

/**
 * The instruments the payload suite exchanges, read from testdata/gtnet_peer_instruments.json.
 *
 * Rows are addressed by natural key - ISIN plus currency, or the two currencies of a pair - because the ids differ
 * between grafioschtrader_t and grafioschtrader_t1. The file lives next to the other application fixtures and never in
 * testdata/generated, which is wiped and rebuilt from the production database.
 */
final class GTNetAppPeerFixture {

  private static final String RESOURCE = "/testdata/gtnet_peer_instruments.json";

  private final JsonNode root;

  private GTNetAppPeerFixture(JsonNode root) {
    this.root = root;
  }

  static GTNetAppPeerFixture load() {
    try (InputStream in = GTNetAppPeerFixture.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Fixture " + RESOURCE + " is missing");
      }
      return new GTNetAppPeerFixture(GTNetPeerTestSupport.JSON.readTree(in));
    } catch (Exception e) {
      throw new IllegalStateException("Fixture " + RESOURCE + " could not be read", e);
    }
  }

  /** @return securities as isin/currency/tickerSymbol triples, in fixture order */
  List<Instrument> securities() {
    List<Instrument> securities = new ArrayList<>();
    for (JsonNode node : root.path("securities")) {
      securities.add(new Instrument(node.path("isin").asText(), node.path("currency").asText(),
          node.path("tickerSymbol").asText(null)));
    }
    return securities;
  }

  /** @return currency pairs as fromCurrency/toCurrency tuples, in fixture order */
  List<CurrencyPair> currencypairs() {
    List<CurrencyPair> pairs = new ArrayList<>();
    for (JsonNode node : root.path("currencypairs")) {
      pairs.add(new CurrencyPair(node.path("fromCurrency").asText(), node.path("toCurrency").asText()));
    }
    return pairs;
  }

  Instrument unknownSecurity() {
    JsonNode node = root.path("unknownSecurity");
    return new Instrument(node.path("isin").asText(), node.path("currency").asText(), null);
  }

  /** @return how many instruments a request must carry to exceed the max_limit the peer bootstrap wrote */
  int maxLimitProbeCount() {
    return root.path("maxLimitProbeCount").asInt();
  }

  LocalDate historyFromDate() {
    return LocalDate.parse(root.path("historyquoteRange").path("fromDate").asText());
  }

  LocalDate historyToDate() {
    return LocalDate.parse(root.path("historyquoteRange").path("toDate").asText());
  }

  record Instrument(String isin, String currency, String tickerSymbol) {
  }

  record CurrencyPair(String fromCurrency, String toCurrency) {
  }
}
