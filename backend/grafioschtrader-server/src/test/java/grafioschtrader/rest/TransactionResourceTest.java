package grafioschtrader.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Transaction;
import grafioschtrader.types.TransactionType;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class TransactionResourceTest extends BaseIntegrationTest {

  private static final String FIXTURE = "/testdata/portfolios.json";

  private List<PortfolioFixture> integrationPortfolios;

  @BeforeAll
  void setUp() throws IOException {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    integrationPortfolios = loadIntegrationPortfolios();
  }

  @Test
  @Order(1)
  @DisplayName("Create integration bank account transactions through REST")
  void createBankAccountTransactions() {
    Assertions.assertThat(integrationPortfolios).isNotEmpty();
    for (PortfolioFixture fixture : integrationPortfolios) {
      List<Integer> createdTransactionIds = new ArrayList<>();
      createTransactions(fixture, createdTransactionIds);
      verifyTransactionsThroughRest(fixture.loginNickname, createdTransactionIds);
    }
  }

  private void createTransactions(PortfolioFixture fixture, List<Integer> createdTransactionIds) {
    Map<String, PortfolioContext> portfolios = loadPortfolioContexts(fixture.loginNickname);
    PortfolioContext portfolio = portfolios.get(fixture.name);
    assertNotNull(portfolio, "Portfolio not returned through REST: " + fixture.name);

    for (TransactionFixture transactionFixture : fixture.transactions) {
      if ("single".equals(transactionFixture.kind)) {
        Transaction created = createSingleTransaction(fixture.loginNickname, portfolio, transactionFixture);
        createdTransactionIds.add(created.getIdTransaction());
      } else if ("transfer".equals(transactionFixture.kind)) {
        CashAccountTransfer created = createTransfer(fixture.loginNickname, portfolio, portfolios,
            transactionFixture);
        createdTransactionIds.add(created.getWithdrawalTransaction().getIdTransaction());
        createdTransactionIds.add(created.getDepositTransaction().getIdTransaction());
      } else {
        throw new AssertionError("Unsupported transaction fixture kind: " + transactionFixture.kind);
      }
    }
  }

  private Transaction createSingleTransaction(String loginNickname, PortfolioContext portfolio,
      TransactionFixture fixture) {
    Cashaccount cashaccount = portfolio.cashAccounts.get(fixture.cashAccount);
    assertNotNull(cashaccount, "Cash account not returned through REST: " + fixture.cashAccount);
    TransactionType transactionType = TransactionType.valueOf(fixture.transactionType);
    Transaction request = new Transaction(cashaccount, fixture.amount, transactionType,
        LocalDate.parse(fixture.date).atTime(12, 0));

    Transaction created = authenticatedClient(loginNickname)
        .post()
        .uri(RequestGTMappings.TRANSACTION_MAP + "/singlecashtrans")
        .body(request)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Transaction.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(created);
    Assertions.assertThat(created.getIdTransaction()).isPositive();
    Assertions.assertThat(created.getTransactionType()).isEqualTo(transactionType);
    Assertions.assertThat(created.getCashaccount().getIdSecuritycashAccount())
        .isEqualTo(cashaccount.getIdSecuritycashAccount());
    Assertions.assertThat(created.getCashaccountAmount()).isEqualTo(expectedStoredAmount(fixture.amount,
        transactionType));
    Assertions.assertThat(created.getTransactionTime()).isEqualTo(request.getTransactionTime());
    return created;
  }

  private CashAccountTransfer createTransfer(String loginNickname, PortfolioContext debitPortfolio,
      Map<String, PortfolioContext> portfolios, TransactionFixture fixture) {
    Cashaccount debitCashaccount = debitPortfolio.cashAccounts.get(fixture.debitCashAccount);
    assertNotNull(debitCashaccount, "Cash account not returned through REST: " + fixture.debitCashAccount);
    PortfolioContext creditPortfolio = portfolios.get(fixture.creditPortfolio);
    assertNotNull(creditPortfolio, "Portfolio not returned through REST: " + fixture.creditPortfolio);
    Cashaccount creditCashaccount = creditPortfolio.cashAccounts.get(fixture.creditCashAccount);
    assertNotNull(creditCashaccount, "Cash account not returned through REST: " + fixture.creditCashAccount);

    Transaction withdrawal = new Transaction(debitCashaccount, fixture.expectedDebitAmount,
        TransactionType.WITHDRAWAL, LocalDate.parse(fixture.date).atTime(12, 0));
    Transaction deposit = new Transaction(creditCashaccount, fixture.creditAmount, TransactionType.DEPOSIT,
        withdrawal.getTransactionTime());
    setCurrencyPair(loginNickname, withdrawal, deposit, fixture.exchangeRate);

    CashAccountTransfer created = authenticatedClient(loginNickname)
        .post()
        .uri(RequestGTMappings.TRANSACTION_MAP + "/cashaccounttransfer")
        .body(new CashAccountTransfer(withdrawal, deposit))
        .exchange()
        .expectStatus().isOk()
        .expectBody(CashAccountTransfer.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(created);
    assertTransfer(created, fixture, debitCashaccount, creditCashaccount);
    return created;
  }

  private void setCurrencyPair(String loginNickname, Transaction withdrawal, Transaction deposit,
      Double exchangeRate) {
    String debitCurrency = withdrawal.getCashaccount().getCurrency();
    String creditCurrency = deposit.getCashaccount().getCurrency();
    if (!debitCurrency.equals(creditCurrency)) {
      assertNotNull(exchangeRate, "A cross-currency transfer requires an exchange rate");
      Currencypair currencypair = authenticatedClient(loginNickname)
          .get()
          .uri(RequestGTMappings.CURRENCYPAIR_MAP + "/" + debitCurrency + "/" + creditCurrency)
          .exchange()
          .expectStatus().isOk()
          .expectBody(Currencypair.class)
          .returnResult()
          .getResponseBody();
      assertNotNull(currencypair);
      withdrawal.setIdCurrencypair(currencypair.getIdSecuritycurrency());
      withdrawal.setCurrencyExRate(exchangeRate);
      deposit.setIdCurrencypair(currencypair.getIdSecuritycurrency());
      deposit.setCurrencyExRate(exchangeRate);
    }
  }

  private void assertTransfer(CashAccountTransfer created, TransactionFixture fixture, Cashaccount debitCashaccount,
      Cashaccount creditCashaccount) {
    Transaction withdrawal = created.getWithdrawalTransaction();
    Transaction deposit = created.getDepositTransaction();
    Assertions.assertThat(withdrawal.getIdTransaction()).isPositive();
    Assertions.assertThat(deposit.getIdTransaction()).isPositive();
    Assertions.assertThat(withdrawal.getTransactionType()).isEqualTo(TransactionType.WITHDRAWAL);
    Assertions.assertThat(deposit.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
    Assertions.assertThat(withdrawal.getConnectedIdTransaction()).isEqualTo(deposit.getIdTransaction());
    Assertions.assertThat(deposit.getConnectedIdTransaction()).isEqualTo(withdrawal.getIdTransaction());
    Assertions.assertThat(withdrawal.getCashaccount().getIdSecuritycashAccount())
        .isEqualTo(debitCashaccount.getIdSecuritycashAccount());
    Assertions.assertThat(deposit.getCashaccount().getIdSecuritycashAccount())
        .isEqualTo(creditCashaccount.getIdSecuritycashAccount());
    Assertions.assertThat(withdrawal.getCashaccountAmount()).isEqualTo(-fixture.expectedDebitAmount);
    Assertions.assertThat(deposit.getCashaccountAmount()).isEqualTo(fixture.creditAmount);
    if (fixture.exchangeRate == null) {
      Assertions.assertThat(withdrawal.getCurrencyExRate()).isNull();
    } else {
      Assertions.assertThat(withdrawal.getCurrencyExRate()).isCloseTo(fixture.exchangeRate, Assertions.within(1e-8));
    }
  }

  private double expectedStoredAmount(Double amount, TransactionType transactionType) {
    return switch (transactionType) {
    case WITHDRAWAL, FEE -> -amount;
    default -> amount;
    };
  }

  private Map<String, PortfolioContext> loadPortfolioContexts(String loginNickname) {
    String responseBody = authenticatedClient(loginNickname)
        .get()
        .uri(RequestGTMappings.PORTFOLIO_MAP + "/tenant")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();

    assertNotNull(responseBody);
    Map<String, PortfolioContext> contexts = new HashMap<>();
    for (JsonNode portfolio : parseJson(responseBody)) {
      Map<String, Cashaccount> cashAccounts = new HashMap<>();
      portfolio.path("cashaccountList").forEach(account -> cashAccounts.put(account.path("name").asText(),
          new Cashaccount(account.path("idSecuritycashAccount").asInt(), account.path("currency").asText())));
      contexts.put(portfolio.path("name").asText(), new PortfolioContext(cashAccounts));
    }
    return contexts;
  }

  private void verifyTransactionsThroughRest(String loginNickname, List<Integer> createdTransactionIds) {
    Transaction[] transactions = authenticatedClient(loginNickname)
        .get()
        .uri(RequestGTMappings.TRANSACTION_MAP)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Transaction[].class)
        .returnResult()
        .getResponseBody();

    assertNotNull(transactions);
    Assertions.assertThat(transactions).extracting(Transaction::getIdTransaction)
        .containsAll(createdTransactionIds);
  }

  private JsonNode parseJson(String responseBody) {
    try {
      return new ObjectMapper().readTree(responseBody);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to parse portfolio REST response", e);
    }
  }

  private List<PortfolioFixture> loadIntegrationPortfolios() throws IOException {
    try (InputStream input = TransactionResourceTest.class.getResourceAsStream(FIXTURE)) {
      assertNotNull(input, "Missing fixture " + FIXTURE);
      PortfolioFixtureFile fixtureFile = new ObjectMapper().readValue(input, PortfolioFixtureFile.class);
      return fixtureFile.portfolios.stream()
          .filter(portfolio -> "i".equals(portfolio.e2e) && portfolio.transactions != null
              && !portfolio.transactions.isEmpty())
          .toList();
    }
  }

  private record PortfolioContext(Map<String, Cashaccount> cashAccounts) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PortfolioFixtureFile {
    public List<PortfolioFixture> portfolios;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PortfolioFixture {
    public String loginNickname;
    public String name;
    public List<TransactionFixture> transactions;
    public String e2e;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TransactionFixture {
    public String kind;
    public String transactionType;
    public String date;
    public String cashAccount;
    public Double amount;
    public String debitCashAccount;
    public String creditPortfolio;
    public String creditCashAccount;
    public Double creditAmount;
    public Double exchangeRate;
    public Double expectedDebitAmount;
  }
}
