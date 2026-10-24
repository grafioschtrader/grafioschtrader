package grafioschtrader.connector.instrument.divvydiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.sun.net.httpserver.HttpServer;

import grafiosch.entities.ConnectorApiKey;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector.FeedIdentifier;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.fred.FredFeedConnector;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.reportviews.securitycurrency.SecurityDataProviderUrls;
import grafioschtrader.repository.GenericConnectorDefJpaRepository;
import grafioschtrader.repository.SecurityJpaRepositoryImpl;
import grafioschtrader.service.FeedConnectorApiKeyService;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.DistributionFrequency;

/** Exercises the real HTTP requests and parser without provider access, secrets or a Spring/database context. */
class DivvyDiaryConnectorHttpTest {
  private static final LocalDate FROM = LocalDate.parse("2000-01-01");
  private static final LocalDate TO = LocalDate.parse("2025-12-31");
  private static final String DIVIDENDS = """
      {"dividendFrequency":"quarterly","dividends":[
        {"exDate":"2024-11-08","payDate":"2024-11-14","amount":0.25,"currency":"USD"},
        {"exDate":"2024-08-12","payDate":"2024-08-15","amount":0.25,"currency":"USD"}],
       "splits":[{"date":"invalid","ratioFrom":0}]}
      """;

  private HttpServer server;
  private DivvyDiaryConnector connector;
  private FeedConnectorApiKeyService keyService;
  private ConnectorApiKey storedKey;
  private final List<String> requestKeys = new CopyOnWriteArrayList<>();
  private volatile String body = DIVIDENDS;
  private volatile int status = 200;
  private volatile String location;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/symbols/", exchange -> {
      String key = exchange.getRequestHeaders().getFirst("X-API-Key");
      requestKeys.add(key == null ? "<absent>" : key);
      if (location != null) {
        exchange.getResponseHeaders().add("Location", location);
      }
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      try (var output = exchange.getResponseBody()) {
        output.write(bytes);
      }
    });
    server.start();
    connector = new DivvyDiaryConnector() {
      @Override
      public String getDividendHistoricalDownloadLink(Security security) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/symbols/" + security.getIsin();
      }
    };
    ConnectorApiKeyJpaRepository repository = mock(ConnectorApiKeyJpaRepository.class);
    when(repository.findById("divvydiary")).thenAnswer(_ -> Optional.ofNullable(storedKey));
    ReflectionTestUtils.setField(connector, "connectorApiKeyJpaRepository", repository);
    keyService = new FeedConnectorApiKeyService();
    ReflectionTestUtils.setField(keyService, "feedConnectorbeans", List.of(connector));
    GenericConnectorDefJpaRepository genericRepository = mock(GenericConnectorDefJpaRepository.class);
    when(genericRepository.findByActivatedTrue()).thenReturn(List.of());
    ReflectionTestUtils.setField(keyService, "genericConnectorDefJpaRepository", genericRepository);
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Public dividend links stay accessible and removing a key does not break configured split links")
  void downloadLinksAndUnavailableSelection() {
    User user = mock(User.class);
    when(user.getRoles()).thenReturn(List.of());
    var authentication = new UsernamePasswordAuthenticationToken("reader", "unused");
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    assertThat(ConnectorHelper.canAccessConnectorApiKey(new FredFeedConnector())).isFalse();
    SecurityJpaRepositoryImpl repository = new SecurityJpaRepositoryImpl();
    ReflectionTestUtils.setField(repository, "feedConnectorbeans", List.of(connector));
    Security security = security();
    security.setIdConnectorDividend(connector.getID());
    security.setIdConnectorSplit(connector.getID());
    var urls = new SecurityDataProviderUrls();
    setKey("test-split-key");
    repository.setDividendDownloadLink(security, urls);
    repository.setSplitDownloadLink(security, urls);
    assertThat(urls.getDividendUrl()).isEqualTo(connector.getDividendHistoricalDownloadLink(security));
    assertThat(urls.getSplitUrl()).isEqualTo(connector.getSplitHistoricalDownloadLink(security));
    setKey(null);
    repository.setDividendDownloadLink(security, urls);
    repository.setSplitDownloadLink(security, urls);
    assertThat(urls.getDividendUrl()).isEqualTo(connector.getDividendHistoricalDownloadLink(security));
    assertThat(urls.getSplitUrl()).isNull();
    security.setIdConnectorDividend(null);
    assertThatThrownBy(() -> repository.checkAndClearSecuritycurrencyConnectors(security))
        .isInstanceOf(DataViolationException.class);
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  @DisplayName("Both dividend methods stay anonymous even with a configured split key and ignore split payloads")
  void dividendsNeverSendKey(boolean withKey) throws Exception {
    if (withKey) {
      setKey("test-split-key");
    }
    var dividends = connector.getDividendHistory(security(), LocalDate.parse("2024-11-08"));
    var result = connector.getDividendHistoryWithFrequency(security(), LocalDate.parse("2024-11-08"));
    assertThat(dividends).hasSize(1);
    assertThat(dividends.getFirst().getAmount()).isEqualTo(0.25);
    assertThat(dividends.getFirst().getPayDate()).isEqualTo(LocalDate.parse("2024-11-14"));
    assertThat(result.dividends()).hasSize(1);
    assertThat(result.providerFrequency()).isEqualTo(DistributionFrequency.DF_QUARTERLY);
    assertThat(requestKeys).containsExactly("<absent>", "<absent>");
  }

  @Test
  @DisplayName("Register DivvyDiary without subscription tiers and apply key addition, replacement and deletion")
  void keyLifecycle() throws Exception {
    assertThat(keyService.getFeedSubscriptionType().get("divvydiary").subscriptionTypes).isEmpty();
    assertAvailability(false);
    assertThat(connector.getSubscriptionType()).isNull();
    assertThatThrownBy(() -> connector.getSplitHistory(security(), FROM, TO)).isInstanceOf(IOException.class)
        .hasMessageContaining("API key");
    assertThat(requestKeys).isEmpty();
    body = "{\"splits\":[]}";
    setKey("test-key-one");
    assertAvailability(true);
    connector.getSplitHistory(security(), FROM, TO);
    setKey("test-key-two");
    connector.getSplitHistory(security(), FROM, TO);
    setKey(null);
    assertAvailability(false);
    assertThatThrownBy(() -> connector.getSplitHistory(security(), FROM, TO)).isInstanceOf(IOException.class);
    body = DIVIDENDS;
    connector.getDividendHistory(security(), FROM);
    assertThat(requestKeys).containsExactly("test-key-one", "test-key-two", "<absent>");
  }

  @Test
  @DisplayName("Map recorded Nvidia 10:1 and 3:2 splits to GT factors and include both date boundaries")
  void nvidiaSplits() throws Exception {
    setKey("test-split-key");
    // Split entries observed from /symbols/US67066G1040; unrelated response fields omitted.
    body = """
        {"splits":[
          {"date":"2024-06-10","ratioFrom":10,"ratioTo":1,"isin":"US67066G1040"},
          {"date":"2021-07-20","ratioFrom":4,"ratioTo":1,"isin":"US67066G1040"},
          {"date":"2007-09-11","ratioFrom":3,"ratioTo":2,"isin":"US67066G1040"},
          {"date":"2006-04-07","ratioFrom":2,"ratioTo":1,"isin":"US67066G1040"}]}
        """;
    var splits = connector.getSplitHistory(security(), LocalDate.parse("2007-09-11"), LocalDate.parse("2024-06-10"));
    assertThat(splits).extracting(Securitysplit::getSplitDate).containsExactly(LocalDate.parse("2007-09-11"),
        LocalDate.parse("2021-07-20"), LocalDate.parse("2024-06-10"));
    assertThat(splits).extracting(Securitysplit::getFromFactor).containsExactly(2, 1, 1);
    assertThat(splits).extracting(Securitysplit::getToFactor).containsExactly(3, 4, 10);
    assertThat(splits).extracting(Securitysplit::getFactor).containsExactly(1.5, 4.0, 10.0);
    assertThat(splits).allSatisfy(split -> {
      assertThat(split.getIdSecuritycurrency()).isEqualTo(123);
      assertThat(split.getCreateType()).isEqualTo(CreateType.CONNECTOR_CREATED);
    });
    assertThat(connector.getSplitHistory(security(), FROM, LocalDate.parse("2007-09-10")))
        .extracting(Securitysplit::getSplitDate).containsExactly(LocalDate.parse("2006-04-07"));
  }

  @Test
  @DisplayName("Map Apple's recorded 4:1 split and a synthetic 1:10 reverse split without inverting their effects")
  void appleAndReverseSplit() throws Exception {
    setKey("test-split-key");
    body = """
        {"splits":[{"date":"2020-08-31","ratioFrom":4,"ratioTo":1,"isin":"US0378331005"}]}
        """;
    Security apple = security();
    apple.setIsin("US0378331005");
    assertThat(connector.getSplitHistory(apple, FROM, TO)).extracting(Securitysplit::getFactor).containsExactly(4.0);
    body = """
        {"splits":[{"date":"2024-01-01","ratioFrom":1,"ratioTo":10}]}
        """;
    assertThat(connector.getSplitHistory(security(), FROM, TO)).extracting(Securitysplit::getFactor)
        .containsExactly(0.1);
  }

  @ParameterizedTest
  @ValueSource(strings = { "{}", "{\"splits\":null}", "{\"splits\":[]}" })
  @DisplayName("Missing and empty split lists represent no events")
  void emptySplits(String response) throws Exception {
    setKey("test-split-key");
    body = response;
    assertThat(connector.getSplitHistory(security(), FROM, TO)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = { "null", "{}", "{\"date\":\"invalid\",\"ratioFrom\":2,\"ratioTo\":1}",
      "{\"date\":\"2024-01-01\",\"ratioFrom\":0,\"ratioTo\":1}",
      "{\"date\":\"2024-01-01\",\"ratioFrom\":2,\"ratioTo\":-1}",
      "{\"date\":\"2024-01-01\",\"ratioFrom\":2.5,\"ratioTo\":1}",
      "{\"date\":\"2024-01-01\",\"ratioFrom\":100000000,\"ratioTo\":1}" })
  @DisplayName("Reject malformed split events instead of importing an incorrect ratio")
  void invalidSplits(String event) {
    setKey("test-split-key");
    body = "{\"splits\":[" + event + "]}";
    assertThatThrownBy(() -> connector.getSplitHistory(security(), FROM, TO)).isInstanceOf(Exception.class);
  }

  @ParameterizedTest
  @ValueSource(ints = { 401, 403, 404, 429 })
  @DisplayName("HTTP errors fail both feeds without an anonymous retry of authenticated split requests")
  void httpErrors(int responseStatus) {
    setKey("test-split-key");
    status = responseStatus;
    assertThatThrownBy(() -> connector.getSplitHistory(security(), FROM, TO)).isInstanceOf(IOException.class)
        .hasMessageContaining("HTTP " + status).hasMessageNotContaining("test-split-key");
    assertThatThrownBy(() -> connector.getDividendHistory(security(), FROM)).isInstanceOf(IOException.class)
        .hasMessageContaining("HTTP " + status);
    assertThat(requestKeys).containsExactly("test-split-key", "<absent>");
  }

  @ParameterizedTest
  @ValueSource(ints = { 301, 308 })
  @DisplayName("Keep successor ISIN diagnostics and never follow redirects with the API key")
  void movedIsin(int redirectStatus) {
    setKey("test-split-key");
    status = redirectStatus;
    location = "/symbols/US89686D3035";
    assertThatThrownBy(() -> connector.getSplitHistory(security(), FROM, TO)).isInstanceOf(IOException.class)
        .hasMessageContaining("US89686D3035");
    assertThatThrownBy(() -> connector.getDividendHistoryWithFrequency(security(), FROM))
        .isInstanceOf(IOException.class).hasMessageContaining("US89686D3035");
    assertThat(requestKeys).containsExactly("test-split-key", "<absent>");
  }

  private void setKey(String value) {
    storedKey = value == null ? null : mock(ConnectorApiKey.class);
    if (storedKey != null) {
      when(storedKey.getApiKey()).thenReturn(value);
    }
    keyService.resetConnectorApiKey("divvydiary");
  }

  private void assertAvailability(boolean splitsAvailable) {
    assertThat(connector.isActivated()).isTrue();
    assertThat(connector.getSecuritycurrencyFeedSupport()).containsKey(FeedSupport.FS_DIVIDEND);
    assertThat(connector.getSecuritycurrencyFeedSupport(FeedSupport.FS_DIVIDEND))
        .containsExactly(FeedIdentifier.DIVIDEND);
    assertThat(connector.getSecuritycurrencyFeedSupport().containsKey(FeedSupport.FS_SPLIT)).isEqualTo(splitsAvailable);
    if (splitsAvailable) {
      assertThat(connector.getSecuritycurrencyFeedSupport(FeedSupport.FS_SPLIT)).containsExactly(FeedIdentifier.SPLIT);
    } else {
      assertThat(connector.getSecuritycurrencyFeedSupport(FeedSupport.FS_SPLIT)).isNull();
    }
  }

  private static Security security() {
    Security security = new Security();
    security.setIdSecuritycurrency(123);
    security.setIsin("US67066G1040");
    return security;
  }
}
