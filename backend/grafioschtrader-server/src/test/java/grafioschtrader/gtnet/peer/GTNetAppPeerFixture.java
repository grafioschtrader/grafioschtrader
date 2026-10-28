package grafioschtrader.gtnet.peer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import grafiosch.test.gtnet.GTNetPeerTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The instruments the payload suite exchanges, read from testdata/gtnet_peer_instruments.json.
 *
 * Rows are addressed by natural key - ISIN plus currency, or the two currencies of a pair - because the ids differ
 * between grafioschtrader_t and grafioschtrader_t1. The file lives next to the other application fixtures and never in
 * testdata/generated, which is wiped and rebuilt from the production database.
 */
final class GTNetAppPeerFixture {

  private static final String RESOURCE = "/testdata/gtnet_peer_instruments.json";

  /**
   * The administrator frontend/e2e/gtnet-app/global.setup.ts registers on both application peers. It differs from the
   * library topology's admin@test.local because registration grants ROLE_ADMIN only to g.main.user.admin.mail, which
   * application-e2e-gtnet.properties sets to this address.
   */
  private static final String ADMIN_EMAIL = "gtnetadmin@test.local";

  private final JsonNode root;

  private GTNetAppPeerFixture(JsonNode root) {
    this.root = root;
  }

  /**
   * Logs in as the administrator of an application peer.
   *
   * @param peer the backend URL of the peer
   * @return the JWT for the x-auth-token header
   */
  static String loginAdmin(URI peer) throws IOException, InterruptedException {
    return GTNetPeerTestSupport.login(peer, ADMIN_EMAIL);
  }

  /**
   * Sets how the own entry of a peer accepts requests for one exchange kind, through the same PUT the setup dialog
   * uses. The kind is resolved by name through exchangeKindTypes, because the REST answer carries entityKind as a
   * number; comparing it with the name silently matched nothing and left the setting unchanged.
   *
   * @param peer          the backend URL of the peer whose own entry is changed
   * @param jwt           an administrator token of that peer
   * @param kindName      the exchange kind, for example LAST_PRICE or SECURITY_METADATA
   * @param acceptRequest the accept mode, for example AC_OPEN, AC_PUSH_OPEN or AC_CLOSED
   */
  static void setOwnAcceptRequest(URI peer, String jwt, String kindName, String acceptRequest) throws Exception {
    JsonNode state = GTNetPeerTestSupport.readGTNet(peer, jwt);
    int kindValue = -1;
    for (JsonNode type : state.path("exchangeKindTypes")) {
      if (kindName.equals(type.path("name").asString())) {
        kindValue = type.path("value").asInt();
      }
    }
    if (kindValue < 0) {
      throw new IllegalStateException("Exchange kind " + kindName + " is not registered at " + peer);
    }
    int ownId = state.path("gtNetMyEntryId").asInt();
    for (JsonNode entry : state.path("gtNetList")) {
      if (entry.path("idGtNet").asInt() == ownId) {
        ObjectNode ownEntry = ((ObjectNode) entry).deepCopy();
        boolean found = false;
        for (JsonNode entity : ownEntry.path("gtNetEntities")) {
          if (entity.path("entityKind").asInt() == kindValue) {
            ((ObjectNode) entity).put("acceptRequest", acceptRequest);
            found = true;
          }
        }
        if (!found) {
          throw new IllegalStateException("Own entry of " + peer + " has no " + kindName + " entity");
        }
        var response = GTNetPeerTestSupport.putApi(peer, "/api/gtnet", jwt, ownEntry.toString());
        if (response.statusCode() < 200 || response.statusCode() > 299) {
          throw new IllegalStateException("Saving the own entry of " + peer + " failed: " + response.body());
        }
        return;
      }
    }
    throw new IllegalStateException(peer + " has no own entry");
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
      securities.add(new Instrument(node.path("isin").asString(), node.path("currency").asString(),
          node.path("tickerSymbol").asString(null)));
    }
    return securities;
  }

  /** @return currency pairs as fromCurrency/toCurrency tuples, in fixture order */
  List<CurrencyPair> currencypairs() {
    List<CurrencyPair> pairs = new ArrayList<>();
    for (JsonNode node : root.path("currencypairs")) {
      pairs.add(new CurrencyPair(node.path("fromCurrency").asString(), node.path("toCurrency").asString()));
    }
    return pairs;
  }

  Instrument unknownSecurity() {
    JsonNode node = root.path("unknownSecurity");
    return new Instrument(node.path("isin").asString(), node.path("currency").asString(), null);
  }

  /** @return how many instruments a request must carry to exceed the max_limit the peer bootstrap wrote */
  int maxLimitProbeCount() {
    return root.path("maxLimitProbeCount").asInt();
  }

  LocalDate historyFromDate() {
    return LocalDate.parse(root.path("historyquoteRange").path("fromDate").asString());
  }

  LocalDate historyToDate() {
    return LocalDate.parse(root.path("historyquoteRange").path("toDate").asString());
  }

  record Instrument(String isin, String currency, String tickerSymbol) {
  }

  record CurrencyPair(String fromCurrency, String toCurrency) {
  }
}
