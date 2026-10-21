package grafioschtrader.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient.RequestHeadersSpec;

import grafiosch.dto.UserAccessTokenCreateRequest;
import grafiosch.dto.UserAccessTokenCreatedDto;
import grafiosch.entities.UserAccessToken;
import grafiosch.rest.RequestMappings;
import grafiosch.types.AccessTokenScope;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Transaction;
import grafioschtrader.rest.BaseIntegrationTest;
import grafioschtrader.rest.RequestGTMappings;
import grafioschtrader.rest.RestTestHelper;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The MCP server over its real HTTP path: token creation, the separation of the security chains, the tools against the
 * reports of the user interface, ownership checks, the reproducible running sum and the atomic daily budget.
 *
 * <p>
 * It reads the portfolios that Playwright spec 025 created for alledit in {@code grafioschtrader_t}, so it runs inside
 * {@code ResourceTestSuite_50} and fails standalone. alledit has no transactions yet at that point, so the running sum is
 * checked on a series the test books itself. It opens the server through the global parameter {@code gt.mcp.use} for
 * its run and closes it again, and it cleans up the tokens, the bookings and the budget row it writes.
 * </p>
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
public class McpServerIntegrationTest extends BaseIntegrationTest {

  private static final String TOKEN_NAME = "mcp-integration-test";
  private static final String MCP = "/api/mcp";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  /**
   * The year of the bookings of {@link #runningSumIsReproducible()}. No fixture of alledit books in it, and the
   * transactions of alledit are only created by Playwright specs after this suite.
   */
  private static final LocalDate RUNNING_SUM_FIRST_DATE = LocalDate.of(2019, 3, 4);

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  private String token;
  private Integer idUserAlledit;
  private int requestId;
  private final List<Integer> createdTransactionIds = new ArrayList<>();

  @BeforeAll
  void setUp() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    idUserAlledit = RestTestHelper.getUserByNickname(RestTestHelper.ALLEDIT).idUser;
    deleteTestTokens();
    McpTestSwitch.open(jdbcTemplate, entityManagerFactory, null);
  }

  @AfterAll
  void tearDown() {
    deleteBookedTransactions();
    McpTestSwitch.close(jdbcTemplate, entityManagerFactory);
    deleteTestTokens();
    jdbcTemplate.update("DELETE FROM user_entity_change_count WHERE id_user = ? AND entity_name = 'McpToolCall'",
        idUserAlledit);
  }

  @Test
  @Order(1)
  @DisplayName("A created token comes back once with no-store and is stored only as its hash")
  void createToken() {
    EntityExchangeResult<UserAccessTokenCreatedDto> result = authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestMappings.USER_ACCESS_TOKEN_MAP)
        .body(new UserAccessTokenCreateRequest(TOKEN_NAME, AccessTokenScope.READ, 30)).exchange().expectStatus().isOk()
        .expectBody(UserAccessTokenCreatedDto.class).returnResult();
    assertThat(result.getResponseHeaders().getCacheControl()).contains("no-store");
    token = result.getResponseBody().plainToken();
    assertThat(token).startsWith(UserAccessToken.TOKEN_PREFIX).hasSize(48);
    Map<String, Object> row = jdbcTemplate.queryForMap(
        "SELECT token_hash, token_prefix FROM user_access_token WHERE id_user = ? AND token_name = ?", idUserAlledit,
        TOKEN_NAME);
    assertThat(row.get("token_hash")).isEqualTo(UserAccessToken.hashToken(token));
    assertThat(row.get("token_prefix")).isEqualTo(token.substring(0, 12));
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_access_token WHERE token_hash = ?", Integer.class,
        token)).isZero();
  }

  @Test
  @Order(2)
  @DisplayName("Tokens and JWTs never cross the two security chains, and a foreign Origin is refused")
  void chainSeparation() {
    restTestClient.post().uri(MCP).contentType(MediaType.APPLICATION_JSON).body(rpc("tools/list", Map.of())).exchange()
        .expectStatus().isUnauthorized();
    String jwt = RestTestHelper.getUserByNickname(RestTestHelper.ALLEDIT).authToken;
    restTestClient.post().uri(MCP).header("x-auth-token", jwt).contentType(MediaType.APPLICATION_JSON)
        .body(rpc("tools/list", Map.of())).exchange().expectStatus().isUnauthorized();
    int status = restTestClient.get().uri(RequestGTMappings.PORTFOLIO_MAP + "/tenant")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange().returnResult(String.class).getStatus().value();
    assertThat(status).isIn(401, 403);
    mcpRequest("tools/list", Map.of()).header(HttpHeaders.ORIGIN, "http://evil.example").exchange().expectStatus()
        .isForbidden();
    restTestClient.post().uri("/mcp").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).body(rpc("tools/list", Map.of())).exchange().expectStatus()
        .is4xxClientError();
  }

  @Test
  @Order(3)
  @DisplayName("tools/list names exactly the eight reading tools")
  void toolsList() {
    JsonNode result = call("tools/list", Map.of()).get("result");
    List<String> names = new ArrayList<>();
    result.get("tools").forEach(t -> names.add(t.get("name").asString()));
    assertThat(names).containsExactlyInAnyOrder("list_portfolios", "account_summary", "holdings", "period_performance",
        "dividends", "security_transactions", "cashaccount_transactions", "find_instrument");
  }

  @Test
  @Order(4)
  @DisplayName("holdings equal the currency grouping of SecurityaccountResource, at the client's day")
  void holdingsMatchUserInterface() throws Exception {
    String zone = "Pacific/Kiritimati";
    JsonNode holdings = toolWithZone("holdings", Map.of("limit", 500), zone);
    assertThat(holdings.get("asOfDate").asString()).isEqualTo(LocalDate.now(ZoneId.of(zone)).toString());
    String untilDate = holdings.get("asOfDate").asString();
    String ui = authenticatedClient(RestTestHelper.ALLEDIT).get()
        .uri(RequestGTMappings.SECURITYACCOUNT_MAP
            + "/tenantsecurityaccountsummary/currency?includeClosedPosition=false&untilDate=" + untilDate)
        .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
    JsonNode grand = JSON.readTree(ui);
    JsonNode totals = holdings.get("totalsOfAllPositions");
    assertThat(totals.get("valueMC").asDouble()).isEqualTo(grand.get("grandAccountValueSecurityMC").asDouble());
    assertThat(totals.get("gainLossCurrencyMC").asDouble()).isEqualTo(grand.get("grandGainLossCurrencyMC").asDouble());
    int uiPositions = 0;
    for (JsonNode group : grand.get("securityPositionGroupSummaryList")) {
      uiPositions += group.get("securityPositionSummaryList").size();
    }
    assertThat(holdings.get("page").get("totalCount").asInt()).isEqualTo(uiPositions);
  }

  @Test
  @Order(5)
  @DisplayName("An id of another tenant gives NOT_OWNED instead of an empty report")
  void foreignIdsAreNotOwned() {
    Integer foreignPortfolio = jdbcTemplate.queryForObject(
        "SELECT MIN(p.id_portfolio) FROM portfolio p JOIN user u ON u.id_tenant = p.id_tenant WHERE u.nickname = ?",
        Integer.class, RestTestHelper.USER);
    Integer foreignCashaccount = jdbcTemplate.queryForObject("""
        SELECT MIN(c.id_securitycash_account) FROM cashaccount c JOIN securitycashaccount s
          ON s.id_securitycash_account = c.id_securitycash_account JOIN portfolio p ON p.id_portfolio = s.id_portfolio
          JOIN user u ON u.id_tenant = p.id_tenant WHERE u.nickname = ?""", Integer.class, RestTestHelper.USER);
    assertError(toolResult("holdings", Map.of("idPortfolio", foreignPortfolio)), "NOT_OWNED");
    assertError(toolResult("cashaccount_transactions", Map.of("idCashaccount", foreignCashaccount, "year", 2024)),
        "NOT_OWNED");
    assertError(toolResult("dividends", Map.of("idsCashaccount", List.of(foreignCashaccount))), "NOT_OWNED");
    assertError(toolResult("find_instrument", Map.of("text", "a")), "INVALID_ARGUMENT");
  }

  @Test
  @Order(6)
  @DisplayName("runningSumOfSelection is the chronological sum of the whole selection, on every page")
  void runningSumIsReproducible() {
    Integer idCashaccount = jdbcTemplate.queryForObject("""
        SELECT MIN(c.id_securitycash_account) FROM cashaccount c JOIN securitycashaccount s
          ON s.id_securitycash_account = c.id_securitycash_account JOIN portfolio p ON p.id_portfolio = s.id_portfolio
          WHERE p.id_tenant = (SELECT id_tenant FROM user WHERE id_user = ?) AND c.currency = 'CHF'""", Integer.class,
        idUserAlledit);
    assertThat(idCashaccount).as("a CHF cash account of the portfolios Playwright spec 025 created").isNotNull();
    bookRunningSumSeries(idCashaccount);
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM transaction WHERE id_cash_account = ? AND YEAR(transaction_time) = ?", Integer.class,
        idCashaccount, RUNNING_SUM_FIRST_DATE.getYear());
    Map<String, Object> args = Map.of("idCashaccount", idCashaccount, "year", RUNNING_SUM_FIRST_DATE.getYear(),
        "limit", 3);
    List<JsonNode> rows = readAllPages("cashaccount_transactions", args);
    assertThat(rows).hasSize(count);
    double sum = 0;
    for (JsonNode row : rows) {
      sum += row.get("cashaccountAmount").asDouble();
      assertThat(row.get("runningSumOfSelection").asDouble()).isCloseTo(sum, offset(0.01));
    }
    assertThat(readAllPages("cashaccount_transactions", args).toString()).isEqualTo(rows.toString());
  }

  @Test
  @Order(7)
  @DisplayName("Parallel calls at the last free budget unit give exactly one success and never exceed the limit")
  void budgetIsAtomic() throws Exception {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      // Several rounds, because a race or a lock conflict does not show on every run
      for (int round = 0; round < 5; round++) {
        jdbcTemplate.update("DELETE FROM user_entity_change_count WHERE id_user = ? AND entity_name = 'McpToolCall'",
            idUserAlledit);
        jdbcTemplate.update("""
            INSERT INTO user_entity_change_count (id_user, date, entity_name, count_insert, count_update, count_delete)
            VALUES (?, ?, 'McpToolCall', 499, 0, 0)""", idUserAlledit, today);
        List<Callable<JsonNode>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
          calls.add(() -> toolResult("list_portfolios", Map.of()));
        }
        int successes = 0;
        for (Future<JsonNode> future : pool.invokeAll(calls)) {
          JsonNode result = future.get();
          if (result.get("isError").asBoolean()) {
            assertError(result, "QUOTA_EXCEEDED");
          } else {
            successes++;
          }
        }
        assertThat(successes).as("round %d", round).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT count_insert FROM user_entity_change_count
            WHERE id_user = ? AND date = ? AND entity_name = 'McpToolCall'""", Integer.class, idUserAlledit, today))
            .as("round %d", round).isEqualTo(500);
      }
    } finally {
      pool.shutdown();
      jdbcTemplate.update("DELETE FROM user_entity_change_count WHERE id_user = ? AND entity_name = 'McpToolCall'",
          idUserAlledit);
    }
  }

  @Test
  @Order(8)
  @DisplayName("A revoked token is rejected with 401")
  void revokedTokenIsRejected() {
    Integer id = jdbcTemplate.queryForObject(
        "SELECT id_user_access_token FROM user_access_token WHERE id_user = ? AND token_name = ?", Integer.class,
        idUserAlledit, TOKEN_NAME);
    authenticatedClient(RestTestHelper.ALLEDIT).delete().uri(RequestMappings.USER_ACCESS_TOKEN_MAP + "/" + id)
        .exchange().expectStatus().isNoContent();
    mcpRequest("tools/list", Map.of()).exchange().expectStatus().isUnauthorized();
  }

  @Test
  @Order(9)
  @DisplayName("While the administrator keeps the server closed, /api/mcp answers 404 and no token can be created")
  void closedServer() {
    McpTestSwitch.close(jdbcTemplate, entityManagerFactory);
    restTestClient.post().uri(MCP).contentType(MediaType.APPLICATION_JSON).body(rpc("tools/list", Map.of())).exchange()
        .expectStatus().isNotFound();
    authenticatedClient(RestTestHelper.ALLEDIT).post().uri(RequestMappings.USER_ACCESS_TOKEN_MAP)
        .body(new UserAccessTokenCreateRequest(TOKEN_NAME + "-closed", AccessTokenScope.READ, 30)).exchange()
        .expectStatus().isBadRequest();
    // Listing stays possible, so a user can still revoke what was created before the server was closed
    authenticatedClient(RestTestHelper.ALLEDIT).get().uri(RequestMappings.USER_ACCESS_TOKEN_MAP).exchange()
        .expectStatus().isOk();
  }

  // -----------------------------------------------------------------------------------------------------------------

  /**
   * Books seven cash transactions, more than two pages of three. Two pairs share their timestamp, so the order and with
   * it the running sum depend on the id as tie-break. The rows are removed again in {@link #tearDown()}.
   */
  private void bookRunningSumSeries(Integer idCashaccount) {
    LocalDateTime first = LocalDateTime.of(RUNNING_SUM_FIRST_DATE, LocalTime.NOON);
    book(idCashaccount, TransactionType.DEPOSIT, 1000.0, first);
    book(idCashaccount, TransactionType.INTEREST_CASHACCOUNT, 12.35, first);
    book(idCashaccount, TransactionType.FEE, 7.5, first.plusDays(30));
    book(idCashaccount, TransactionType.DEPOSIT, 250.0, first.plusDays(61));
    book(idCashaccount, TransactionType.FEE, 3.25, first.plusDays(61));
    book(idCashaccount, TransactionType.INTEREST_CASHACCOUNT, 4.1, first.plusDays(92));
    book(idCashaccount, TransactionType.DEPOSIT, 80.0, first.plusDays(122));
  }

  private void book(Integer idCashaccount, TransactionType transactionType, double amount, LocalDateTime time) {
    Transaction created = authenticatedClient(RestTestHelper.ALLEDIT).post()
        .uri(RequestGTMappings.TRANSACTION_MAP + "/singlecashtrans")
        .body(new Transaction(new Cashaccount(idCashaccount, "CHF"), amount, transactionType, time)).exchange()
        .expectStatus().isOk().expectBody(Transaction.class).returnResult().getResponseBody();
    createdTransactionIds.add(created.getIdTransaction());
  }

  private void deleteBookedTransactions() {
    createdTransactionIds.reversed()
        .forEach(id -> authenticatedClient(RestTestHelper.ALLEDIT).delete()
            .uri(RequestGTMappings.TRANSACTION_MAP + "/" + id).exchange().expectStatus().isNoContent());
    createdTransactionIds.clear();
  }

  private List<JsonNode> readAllPages(String tool, Map<String, Object> args) {
    List<JsonNode> rows = new ArrayList<>();
    Integer offset = 0;
    while (offset != null) {
      Map<String, Object> pageArgs = new HashMap<>(args);
      pageArgs.put("offset", offset);
      JsonNode result = tool(tool, pageArgs);
      result.get("transactions").forEach(rows::add);
      JsonNode next = result.get("page").get("nextOffset");
      offset = next == null || next.isNull() ? null : next.asInt();
    }
    return rows;
  }

  private void assertError(JsonNode toolResult, String code) {
    assertThat(toolResult.get("isError").asBoolean()).isTrue();
    JsonNode content = JSON.readTree(toolResult.get("content").get(0).get("text").asString());
    assertThat(content.get("code").asString()).isEqualTo(code);
  }

  private JsonNode tool(String name, Map<String, Object> args) {
    JsonNode result = toolResult(name, args);
    assertThat(result.get("isError").asBoolean()).as(result.toString()).isFalse();
    return JSON.readTree(result.get("content").get(0).get("text").asString());
  }

  private JsonNode toolWithZone(String name, Map<String, Object> args, String zone) {
    String body = mcpRequest("tools/call", Map.of("name", name, "arguments", args)).header("x-time-zone", zone)
        .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
    JsonNode result = JSON.readTree(body).get("result");
    assertThat(result.get("isError").asBoolean()).as(result.toString()).isFalse();
    return JSON.readTree(result.get("content").get(0).get("text").asString());
  }

  private JsonNode toolResult(String name, Map<String, Object> args) {
    return call("tools/call", Map.of("name", name, "arguments", args)).get("result");
  }

  private JsonNode call(String method, Map<String, Object> params) {
    String body = mcpRequest(method, params).exchange().expectStatus().isOk().expectBody(String.class).returnResult()
        .getResponseBody();
    return JSON.readTree(body);
  }

  private RequestHeadersSpec<?> mcpRequest(String method, Map<String, Object> params) {
    return restTestClient.post().uri(MCP).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
        .body(rpc(method, params));
  }

  private synchronized String rpc(String method, Map<String, Object> params) {
    return JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", ++requestId, "method", method, "params", params));
  }

  /**
   * Removes the token of an earlier run and today's create/revoke counter, so that the class can be run any number of
   * times a day without reaching the daily budget of its user.
   */
  private void deleteTestTokens() {
    jdbcTemplate.update("DELETE FROM user_access_token WHERE token_name = ?", TOKEN_NAME);
    jdbcTemplate.update("DELETE FROM user_entity_change_count WHERE id_user = ? AND entity_name = 'UserAccessToken'",
        idUserAlledit);
  }
}
