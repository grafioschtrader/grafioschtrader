package grafioschtrader.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.Assertions;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Guards the invariant of GitHub issue #219 point 4: the incremental replay of the two cash hold tables and their full
 * rebuild must produce the same numbers.
 *
 * <p>
 * Both tables are maintained by two paths. {@code REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT} accumulates the whole history
 * from zero, while every transaction write replays only the tail of one cash account, seeded from the stored values of
 * the youngest surviving row. As long as nothing is rounded on write, that seed is the exact running total the rebuild
 * would hold at the same point and the two paths agree. Rounding any amount column re-enters the accumulator through
 * the seed and offsets the whole remainder of the series against a rebuild, invisibly, because the offset stays below
 * the currency precision.
 * </p>
 *
 * <p>
 * The booked amounts therefore carry deliberate sub-cent fractions on a CHF account, which is what makes a rounded
 * write observable at all. {@code Transaction.validateCashaccountAmount} accepts them: for DEPOSIT,
 * INTEREST_CASHACCOUNT and FEE the calculated amount is the booked amount, so no comparison at the currency precision
 * takes place.
 * </p>
 *
 * <p>
 * The test needs the portfolio and the cash account that {@code PortfolioResourceTest} creates, so it runs inside
 * {@code ResourceTestSuite_50} and fails standalone. It books in its own date window in 2019, well before the fixture
 * transactions, and removes what it booked afterwards, so it can be repeated against the same database.
 * </p>
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class HoldCashaccountReplayRebuildTest extends BaseIntegrationTest {

  /** Holds ROLE_ADMIN, so the series of writes below is not stopped by the daily CUD limit. */
  private static final String NICKNAME = "admin";
  private static final String PORTFOLIO_NAME = "Migros Bank";
  private static final String CASHACCOUNT_NAME = "Migros CHF";

  /** Own window, far before the fixture transactions, so no other test's rows are read or removed. */
  private static final LocalDate FIRST_DATE = LocalDate.of(2019, 3, 4);

  private static final String BALANCE_SQL = """
      SELECT from_hold_date, to_hold_date, withdrawl_deposit, interest_cashaccount, fee, finance_cost,
             accumulate_reduce, dividend, balance
        FROM hold_cashaccount_balance WHERE id_securitycash_account = ?1 ORDER BY from_hold_date""";

  private static final String DEPOSIT_SQL = """
      SELECT from_hold_date, to_hold_date, deposit, deposit_portfolio_currency, deposit_tenant_currency
        FROM hold_cashaccount_deposit WHERE id_securitycash_account = ?1 ORDER BY from_hold_date""";

  @Autowired
  private HoldCashaccountBalanceJpaRepository holdCashaccountBalanceJpaRepository;

  @Autowired
  private HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @PersistenceContext
  private EntityManager entityManager;

  private Integer idTenant;
  private Integer idCashaccount;
  private final List<Integer> createdTransactionIds = new ArrayList<>();

  @BeforeAll
  void setUp() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    resolveCashaccount();
  }

  @AfterAll
  void tearDown() {
    createdTransactionIds.reversed().forEach(this::deleteTransaction);
    createdTransactionIds.clear();
    rebuild();
  }

  @Test
  @Order(1)
  @DisplayName("Cross-currency transfer replay and rebuild agree")
  void crossCurrencyTransferReplayMatchesRebuild() {
    List<List<Object>> balanceAfterReplay = readRows(BALANCE_SQL);
    List<List<Object>> depositAfterReplay = readRows(DEPOSIT_SQL);
    Assertions.assertThat(depositAfterReplay).as("the transfer fixture produced deposit hold rows").isNotEmpty();

    rebuild();

    Assertions.assertThat(readRows(BALANCE_SQL)).as("cash balance after transfer replay vs. after rebuild")
        .isEqualTo(balanceAfterReplay);
    Assertions.assertThat(readRows(DEPOSIT_SQL)).as("cash deposit after transfer replay vs. after rebuild")
        .isEqualTo(depositAfterReplay);
  }

  @Test
  @Order(2)
  @DisplayName("Replay and rebuild agree on hold_cashaccount_balance and hold_cashaccount_deposit")
  void replayMatchesRebuild() {
    bookSeries();
    updateMiddleTransaction();

    List<List<Object>> balanceAfterReplay = readRows(BALANCE_SQL);
    List<List<Object>> depositAfterReplay = readRows(DEPOSIT_SQL);
    Assertions.assertThat(balanceAfterReplay).as("the booked series produced hold rows").isNotEmpty();

    rebuild();

    // Exact equality, not isCloseTo: every amount is stored unrounded, both paths add the same daily aggregates in the
    // same order, and the replay seed is the value the rebuild computed. Anything else means a value was rounded on the
    // way into the table and read back as a seed.
    Assertions.assertThat(readRows(BALANCE_SQL)).as("hold_cashaccount_balance after replay vs. after rebuild")
        .isEqualTo(balanceAfterReplay);
    Assertions.assertThat(readRows(DEPOSIT_SQL)).as("hold_cashaccount_deposit after replay vs. after rebuild")
        .isEqualTo(depositAfterReplay);
  }

  /**
   * Books deposits, interest and fees whose amounts carry more decimals than the CHF precision of two, so that a
   * rounded write would differ measurably from the unrounded running total.
   */
  private void bookSeries() {
    create(TransactionType.DEPOSIT, 10000.004, FIRST_DATE);
    create(TransactionType.INTEREST_CASHACCOUNT, 12.3456, FIRST_DATE.plusDays(20));
    create(TransactionType.FEE, 7.0025, FIRST_DATE.plusDays(41));
    create(TransactionType.DEPOSIT, 250.00751, FIRST_DATE.plusDays(63));
    create(TransactionType.INTEREST_CASHACCOUNT, 3.33333, FIRST_DATE.plusDays(84));
  }

  /**
   * Changes the amount of the third booking. This is the case the invariant is about: the replay starts in the middle
   * of the series and seeds its accumulator from the row before it, rather than from zero.
   */
  private void updateMiddleTransaction() {
    Transaction toUpdate = readTransaction(createdTransactionIds.get(2));
    toUpdate.setCashaccountAmount(9.0075);
    Transaction updated = authenticatedClient(NICKNAME).put()
        .uri(RequestGTMappings.TRANSACTION_MAP + "/singlecashtrans").body(toUpdate).exchange().expectStatus().isOk()
        .expectBody(Transaction.class).returnResult().getResponseBody();
    assertNotNull(updated);
  }

  private void create(TransactionType transactionType, double amount, LocalDate date) {
    Transaction request = new Transaction(new Cashaccount(idCashaccount, "CHF"), amount, transactionType,
        LocalDateTime.of(date, LocalTime.NOON));
    Transaction created = authenticatedClient(NICKNAME).post()
        .uri(RequestGTMappings.TRANSACTION_MAP + "/singlecashtrans").body(request).exchange().expectStatus().isOk()
        .expectBody(Transaction.class).returnResult().getResponseBody();
    assertNotNull(created);
    createdTransactionIds.add(created.getIdTransaction());
  }

  private Transaction readTransaction(Integer idTransaction) {
    Transaction transaction = authenticatedClient(NICKNAME).get()
        .uri(RequestGTMappings.TRANSACTION_MAP + "/" + idTransaction).exchange().expectStatus().isOk()
        .expectBody(Transaction.class).returnResult().getResponseBody();
    assertNotNull(transaction);
    return transaction;
  }

  private void deleteTransaction(Integer idTransaction) {
    authenticatedClient(NICKNAME).delete().uri(RequestGTMappings.TRANSACTION_MAP + "/" + idTransaction).exchange()
        .expectStatus().isNoContent();
  }

  private void rebuild() {
    holdCashaccountBalanceJpaRepository.createCashaccountBalanceEntireByTenant(idTenant);
    holdCashaccountDepositJpaRepository.createCashaccountDepositTimeFrameByTenant(idTenant);
  }

  /**
   * Reads the hold rows of the cash account column by column with a native query. The entities cannot be compared
   * directly: their embedded key exposes no getter for the hold date, and entity equality would ignore exactly the
   * amount columns that are the subject of the assertion.
   *
   * @param sql the projection to read, taking the cash account id as its only parameter
   * @return one list of column values per row, ordered by from_hold_date
   */
  @SuppressWarnings("unchecked")
  private List<List<Object>> readRows(String sql) {
    entityManager.clear();
    return ((List<Object[]>) entityManager.createNativeQuery(sql).setParameter(1, idCashaccount).getResultList())
        .stream().map(Arrays::asList).toList();
  }

  private void resolveCashaccount() {
    String responseBody = authenticatedClient(NICKNAME).get().uri(RequestGTMappings.PORTFOLIO_MAP + "/tenant")
        .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
    assertNotNull(responseBody);
    for (JsonNode portfolio : parseJson(responseBody)) {
      if (!PORTFOLIO_NAME.equals(portfolio.path("name").asText())) {
        continue;
      }
      for (JsonNode account : portfolio.path("cashaccountList")) {
        if (CASHACCOUNT_NAME.equals(account.path("name").asText())) {
          idTenant = portfolio.path("idTenant").asInt();
          idCashaccount = account.path("idSecuritycashAccount").asInt();
        }
      }
    }
    assertNotNull(idCashaccount, "Cash account not returned through REST: " + PORTFOLIO_NAME + "/" + CASHACCOUNT_NAME
        + ". This test runs inside ResourceTestSuite_50 and fails standalone.");
    Assertions.assertThat(idTenant).isPositive();
  }

  private JsonNode parseJson(String responseBody) {
    try {
      return new ObjectMapper().readTree(responseBody);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to parse portfolio REST response", e);
    }
  }

}
