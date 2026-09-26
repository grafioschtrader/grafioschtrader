package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import grafiosch.entities.User;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.entities.*;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.rest.RequestGTMappings;
import grafioschtrader.service.SecurityActionService;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises real MVC binding, repositories and MariaDB in a rollback-only fixture. Authentication is installed on the
 * test thread so MVC requests participate in that same transaction; context authorization has its own REST suite.
 */
@GTIntegrationTestContext
@Transactional
class SimulationOpeningTransactionIntegrationTest {
  private static final String PATH = RequestGTMappings.TRANSACTION_MAP;
  private static final LocalDate DATE = LocalDate.of(2020, 6, 15);
  @PersistenceContext
  private EntityManager em;
  @Autowired
  private WebApplicationContext context;
  @Autowired
  private ObjectMapper mapper;
  @Autowired
  private TransactionJpaRepository transactions;
  @Autowired
  private SecurityActionService actions;
  private MockMvc mvc;
  private Integer tenantId;
  private Cashaccount cash;
  private Cashaccount otherCash;
  private Securityaccount account;
  private Security security;
  private Security successor;

  @Autowired
  private PortfolioJpaRepository portfolios;
  @Autowired
  private CashaccountJpaRepository cashaccounts;
  @Autowired
  private SecurityaccountJpaRepository securityaccounts;

  @BeforeEach
  void fixture() {
    Tenant main = new Tenant("Opening protection parent", "CHF", 0, TenantKindType.MAIN, false);
    em.persist(main);
    Tenant simulation = new Tenant("Opening protection", "CHF", 0, TenantKindType.SIMULATION_COPY, false);
    simulation.setIdParentTenant(main.getId());
    em.persist(simulation);
    tenantId = simulation.getId();
    User user = new User(tenantId);
    user.setIdUser(0);
    user.setLocaleStr("en");
    var authentication = new UsernamePasswordAuthenticationToken("opening", "", List.of());
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    Portfolio portfolio = new Portfolio(tenantId, "Opening protection", "CHF");
    em.persist(portfolio);
    cash = cash("Opening CHF", portfolio);
    otherCash = cash("Other CHF", portfolio);
    account = new Securityaccount("Opening securities", portfolio);
    account.setIdTenant(tenantId);
    account.setLowestTransactionCost(0f);
    account.setTradingPlatformPlan(em.createQuery("SELECT p FROM TradingPlatformPlan p", TradingPlatformPlan.class)
        .setMaxResults(1).getSingleResult());
    em.persist(account);
    List<Security> securities = em.createQuery("""
        SELECT s FROM Security s WHERE s.currency = 'CHF' AND s.isin IS NOT NULL
          AND s.idLinkSecuritycurrency IS NULL AND s.assetClass IS NOT NULL
          AND s.assetClass.specialInvestmentInstrument = 0 ORDER BY s.idSecuritycurrency
        """, Security.class).setMaxResults(2).getResultList();
    assertThat(securities).hasSize(2);
    security = securities.getFirst();
    successor = securities.getLast();
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  @DisplayName("REST cash and security edits cannot remove the persisted opening marker")
  void openingEditIsRejected(boolean withSecurity) throws Exception {
    Transaction opening = booking(withSecurity ? TransactionType.ACCUMULATE : TransactionType.DEPOSIT, cash, true);
    Map<String, Object> body = payload(opening);
    body.put("note", "attempted edit");
    body.put("simulationOpening", false);
    protectedResponse(mvc.perform(put(PATH + (withSecurity ? "/securitytrans" : "/singlecashtrans"))
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))));
    assertUnchanged(opening);
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  @DisplayName("REST deletion keeps both cash and security opening bookings")
  void openingDeleteIsRejected(boolean withSecurity) throws Exception {
    Transaction opening = booking(withSecurity ? TransactionType.ACCUMULATE : TransactionType.DEPOSIT, cash, true);
    protectedResponse(mvc.perform(delete(PATH + "/" + opening.getId())));
    assertUnchanged(opening);
  }

  @ParameterizedTest
  @ValueSource(strings = { "cashaccount", "securityaccount", "portfolio" })
  @DisplayName("Deleting opening-ledger parents returns a translated error and preserves their bookings")
  void openingParentDeleteIsRejected(String entity) throws Exception {
    Transaction opening = booking(TransactionType.ACCUMULATE, cash, true);
    Integer portfolioId = cash.getPortfolio().getId();
    Integer id = switch (entity) {
    case "cashaccount" -> cash.getId();
    case "securityaccount" -> account.getId();
    default -> portfolioId;
    };
    mvc.perform(delete("/api/" + entity + "/" + id)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.className").value("SingleNativeMsgError")).andExpect(jsonPath("$.error.message").value(
            "This account or portfolio contains opening transactions and cannot be deleted. Create a new simulation environment to change the opening balance."));
    assertUnchanged(opening);
    assertThat(em.find(Portfolio.class, portfolioId)).isNotNull();
    assertThat(em.find(Cashaccount.class, cash.getId())).isNotNull();
    assertThat(em.find(Securityaccount.class, account.getId())).isNotNull();
  }

  @Test
  @DisplayName("The portfolio guard also finds a securities account whose booking uses cash in another portfolio")
  void openingPortfolioGuardChecksBothAccountReferences() throws Exception {
    Portfolio securitiesPortfolio = new Portfolio(tenantId, "Opening securities", "CHF");
    em.persist(securitiesPortfolio);
    account.setPortfolio(securitiesPortfolio);
    Transaction opening = booking(TransactionType.ACCUMULATE, cash, true);
    assertThat(transactions.hasOpeningTransactionsInPortfolio(cash.getPortfolio().getId(), tenantId)).isTrue();
    assertThat(transactions.hasOpeningTransactionsInPortfolio(securitiesPortfolio.getId(), tenantId)).isTrue();
    mvc.perform(delete("/api/portfolio/" + securitiesPortfolio.getId())).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.className").value("SingleNativeMsgError"));
    assertUnchanged(opening);
  }

  @Test
  @DisplayName("Parent guards do not reveal another tenant's opening ledger or block empty accounts")
  void openingParentGuardIsTenantScoped() {
    booking(TransactionType.ACCUMULATE, cash, true);
    Integer foreignTenant = cash.getPortfolio().getIdTenant() + 1000000;
    assertThat(cashaccounts.delEntityWithTenant(cash.getId(), foreignTenant)).isZero();
    assertThat(securityaccounts.delEntityWithTenant(account.getId(), foreignTenant)).isZero();
    assertThat(portfolios.delEntityWithTenant(cash.getPortfolio().getId(), foreignTenant)).isZero();
    assertThat(cashaccounts.delEntityWithTenant(otherCash.getId(), tenantId)).isEqualTo(1);
    Portfolio empty = new Portfolio(tenantId, "Empty", "CHF");
    em.persist(empty);
    assertThat(portfolios.delEntityWithTenant(empty.getId(), tenantId)).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  @DisplayName("A protected side prevents editing or deleting the entire cash transfer")
  void transferChecksBothStoredSides(boolean openingWithdrawal) throws Exception {
    Transaction withdrawal = booking(TransactionType.WITHDRAWAL, cash, openingWithdrawal);
    Transaction deposit = booking(TransactionType.DEPOSIT, otherCash, !openingWithdrawal);
    withdrawal.setConnectedIdTransaction(deposit.getId());
    deposit.setConnectedIdTransaction(withdrawal.getId());
    em.flush();
    Map<String, Object> body = Map.of("withdrawalTransaction", payload(withdrawal), "depositTransaction",
        payload(deposit));
    protectedResponse(mvc.perform(post(PATH + "/cashaccounttransfer").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(body))));
    protectedResponse(mvc.perform(delete(PATH + "/" + (openingWithdrawal ? deposit.getId() : withdrawal.getId()))));
    assertUnchanged(withdrawal);
    assertUnchanged(deposit);
  }

  @Test
  @DisplayName("Converting a single opening deposit to a transfer is refused before creating its counterpart")
  void openingCannotBecomeTransfer() throws Exception {
    Transaction opening = booking(TransactionType.DEPOSIT, otherCash, true);
    Map<String, Object> withdrawal = new LinkedHashMap<>(payload(opening));
    withdrawal.remove("idTransaction");
    withdrawal.put("transactionType", "WITHDRAWAL");
    withdrawal.put("cashaccount", Map.of("idSecuritycashAccount", cash.getId()));
    withdrawal.put("cashaccountAmount", -100.0);
    protectedResponse(
        mvc.perform(post(PATH + "/cashaccounttransfer").contentType(MediaType.APPLICATION_JSON).content(mapper
            .writeValueAsString(Map.of("withdrawalTransaction", withdrawal, "depositTransaction", payload(opening))))));
    assertThat(transactions.countByIdTenant(tenantId)).isEqualTo(1);
    assertUnchanged(opening);
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  @DisplayName("Ordinary REST edits retain all internal replay metadata")
  void replayMetadataSurvivesEdit(boolean withSecurity) throws Exception {
    Transaction replay = booking(withSecurity ? TransactionType.ACCUMULATE : TransactionType.DEPOSIT, cash, false);
    Map<String, Object> body = payload(replay);
    body.put("note", "allowed edit");
    body.put("simulationOpening", true);
    body.put("algoFillId", "forged");
    mvc.perform(put(PATH + (withSecurity ? "/securitytrans" : "/singlecashtrans"))
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().isOk());
    em.flush();
    em.clear();
    Transaction saved = em.find(Transaction.class, replay.getId());
    assertThat(saved.getNote()).isEqualTo("allowed edit");
    assertThat(saved.isSimulationOpening()).isFalse();
    assertMetadata(saved, replay);
    mvc.perform(delete(PATH + "/" + saved.getId())).andExpect(status().isNoContent());
    assertThat(em.find(Transaction.class, saved.getId())).isNull();
  }

  @Test
  @DisplayName("Cash transfer edits preserve independent replay metadata on both sides")
  void transferMetadataSurvivesEdit() throws Exception {
    Transaction funding = booking(TransactionType.DEPOSIT, cash, false);
    funding.setCashaccountAmount(1000.0);
    Transaction withdrawal = booking(TransactionType.WITHDRAWAL, cash, false);
    Transaction deposit = booking(TransactionType.DEPOSIT, otherCash, false);
    withdrawal.setConnectedIdTransaction(deposit.getId());
    deposit.setConnectedIdTransaction(withdrawal.getId());
    em.flush();
    mvc.perform(post(PATH + "/cashaccounttransfer").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(
            Map.of("withdrawalTransaction", payload(withdrawal), "depositTransaction", payload(deposit)))))
        .andExpect(status().isOk());
    em.flush();
    em.clear();
    assertMetadata(em.find(Transaction.class, withdrawal.getId()), withdrawal);
    assertMetadata(em.find(Transaction.class, deposit.getId()), deposit);
  }

  @Test
  @DisplayName("The taxable-interest shortcut cannot modify an opening dividend")
  void taxableInterestIsProtected() throws Exception {
    Transaction opening = booking(TransactionType.DIVIDEND, cash, true);
    protectedResponse(mvc.perform(put(PATH + "/" + opening.getId() + "/taxableinterest/true")));
    assertUnchanged(opening);
    assertThat(em.find(Transaction.class, opening.getId()).isTaxableInterest()).isFalse();
  }

  @Test
  @DisplayName("Tax enrichment skips opening dividends while updating matching ordinary dividends")
  void exDateEnrichmentSkipsOpening() throws Exception {
    Transaction opening = booking(TransactionType.DIVIDEND, cash, true);
    Transaction ordinary = booking(TransactionType.DIVIDEND, cash, false);
    taxPayment();
    mvc.perform(put(PATH + "/exdatefromtaxdata/2020")).andExpect(status().isOk());
    em.flush();
    em.clear();
    assertThat(em.find(Transaction.class, opening.getId()).getExDate()).isNull();
    assertThat(em.find(Transaction.class, ordinary.getId()).getExDate()).isEqualTo(DATE.minusDays(2));
  }

  @Test
  @DisplayName("ISIN changes that would reassign the opening ledger are rejected before writing")
  void securityActionIsProtected() {
    Transaction opening = booking(TransactionType.ACCUMULATE, cash, true);
    SecurityAction action = securityAction();
    assertThatThrownBy(() -> actions.applySecurityAction(action.getId())).isInstanceOfSatisfying(
        GeneralNotTranslatedWithArgumentsException.class,
        exception -> assertThat(exception.getMessageKey()).isEqualTo("gt.simulation.opening.protected"));
    assertUnchanged(opening);
    assertThat(em.createQuery("SELECT a FROM SecurityActionApplication a WHERE a.idTenant = ?1")
        .setParameter(1, tenantId).getResultList()).isEmpty();
  }

  @Test
  @DisplayName("Native reassignment and reversal leave opening transactions untouched")
  void nativeSecurityUpdatesExcludeOpening() {
    Transaction opening = booking(TransactionType.ACCUMULATE, cash, true);
    Transaction ordinary = booking(TransactionType.ACCUMULATE, cash, false);
    SecurityActionApplication application = new SecurityActionApplication();
    application.setSecurityAction(securityAction());
    application.setIdTenant(tenantId);
    application.setAppliedTime(DATE.atStartOfDay());
    em.persist(application);
    em.flush();
    assertThat(transactions.reassignTransactionsToNewSecurity(tenantId, security.getId(), successor.getId(),
        application.getId(), DATE.minusDays(1))).isEqualTo(1);
    em.clear();
    assertThat(em.find(Transaction.class, opening.getId()).getSecurity().getId()).isEqualTo(security.getId());
    assertThat(em.find(Transaction.class, ordinary.getId()).getSecurity().getId()).isEqualTo(successor.getId());
    // A legacy tagged opening row must also survive reversal.
    em.find(Transaction.class, opening.getId()).setIdSecurityActionApp(application.getId());
    em.flush();
    assertThat(transactions.revertReassignedTransactions(security.getId(), application.getId())).isEqualTo(1);
    em.clear();
    assertThat(em.find(Transaction.class, opening.getId()).getIdSecurityActionApp()).isEqualTo(application.getId());
    assertThat(em.find(Transaction.class, ordinary.getId()).getIdSecurityActionApp()).isNull();
  }

  @Test
  @DisplayName("Transfer relinking does not report protected opening pairs as rejected")
  void relinkingSkipsOpeningPairs() throws Exception {
    Transaction withdrawal = booking(TransactionType.WITHDRAWAL, cash, true);
    Transaction deposit = booking(TransactionType.DEPOSIT, otherCash, true);
    mvc.perform(post(PATH + "/connecttransfers")).andExpect(status().isOk()).andExpect(jsonPath("$.checked").value(0))
        .andExpect(jsonPath("$.failed").value(0)).andExpect(jsonPath("$.linkedPairs").value(0));
    assertUnchanged(withdrawal);
    assertUnchanged(deposit);
  }

  @Test
  @DisplayName("Import writes consult the saved opening flag even without a supplied pre-image")
  void importCannotBypassProtection() {
    Transaction opening = booking(TransactionType.DEPOSIT, cash, true);
    Transaction incoming = new Transaction(cash, 200.0, TransactionType.DEPOSIT, DATE.atStartOfDay());
    incoming.setIdTenant(tenantId);
    incoming.setIdTransaction(opening.getId());
    assertThatThrownBy(() -> transactions.saveOnlyAttributesFormImport(incoming, null))
        .isInstanceOf(GeneralNotTranslatedWithArgumentsException.class);
    assertUnchanged(opening);
  }

  private Cashaccount cash(String name, Portfolio portfolio) {
    Cashaccount result = new Cashaccount(name, 0.0, "CHF", portfolio);
    result.setIdTenant(tenantId);
    result.setBorrowingRate(0.0);
    em.persist(result);
    return result;
  }

  private Transaction booking(TransactionType type, Cashaccount cashaccount, boolean opening) {
    double amount = type == TransactionType.WITHDRAWAL || type == TransactionType.ACCUMULATE ? -100.0 : 100.0;
    Transaction result = new Transaction(cashaccount, amount, type, DATE.atTime(12, 0));
    result.setIdTenant(tenantId);
    result.setSimulationOpening(opening);
    result.setTaxableInterest(false);
    if (type == TransactionType.ACCUMULATE || type == TransactionType.DIVIDEND) {
      result.setSecuritycurrency(security);
      result.setIdSecurityaccount(account.getId());
      result.setUnits(10.0);
      result.setQuotation(10.0);
    }
    em.persist(result);
    result.setAlgoFillId("fill-" + result.getId());
    result.setAlgoSignalId("signal-" + result.getId());
    result.setAlgoTrancheTargets("{\"tranche\":10}");
    em.flush();
    return result;
  }

  private Map<String, Object> payload(Transaction transaction) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("idTransaction", transaction.getId());
    body.put("transactionType", transaction.getTransactionType().name());
    body.put("transactionTime", "2020-06-15T12:00:00Z");
    body.put("cashaccountAmount", transaction.getCashaccountAmount());
    body.put("cashaccount", Map.of("idSecuritycashAccount", transaction.getCashaccount().getId()));
    body.put("connectedIdTransaction", transaction.getConnectedIdTransaction());
    if (transaction.getSecurity() != null) {
      body.put("security", transaction.getSecurity());
      body.put("idSecurityaccount", account.getId());
      body.put("units", 10.0);
      body.put("quotation", 10.0);
    }
    return body;
  }

  private void protectedResponse(ResultActions response) throws Exception {
    response.andExpect(status().isBadRequest());
    assertThat(response.andReturn().getResponse().getContentAsString()).contains("Opening transactions cannot");
  }

  private void assertUnchanged(Transaction original) {
    em.clear();
    Transaction saved = em.find(Transaction.class, original.getId());
    assertThat(saved).isNotNull();
    assertThat(saved.getCashaccountAmount()).isEqualTo(original.getCashaccountAmount());
    assertThat(saved.getNote()).isNull();
    assertThat(saved.getConnectedIdTransaction()).isEqualTo(original.getConnectedIdTransaction());
    assertMetadata(saved, original);
  }

  private void assertMetadata(Transaction saved, Transaction original) {
    assertThat(saved.isSimulationOpening()).isEqualTo(original.isSimulationOpening());
    assertThat(saved.getAlgoFillId()).isEqualTo("fill-" + original.getId());
    assertThat(saved.getAlgoSignalId()).isEqualTo("signal-" + original.getId());
    assertThat(saved.getAlgoTrancheTargets()).isEqualTo("{\"tranche\":10}");
  }

  private SecurityAction securityAction() {
    SecurityAction action = new SecurityAction();
    action.setSecurityOld(security);
    action.setSecurityNew(successor);
    action.setIsinOld(security.getIsin());
    action.setIsinNew(successor.getIsin());
    action.setActionDate(DATE.minusDays(1));
    action.setCreatedBy(0);
    em.persist(action);
    em.flush();
    return action;
  }

  private void taxPayment() {
    TaxCountry country = em.createQuery("SELECT c FROM TaxCountry c WHERE c.countryCode = 'CH'", TaxCountry.class)
        .getSingleResult();
    List<TaxYear> years = em
        .createQuery("SELECT y FROM TaxYear y WHERE y.taxCountry = ?1 AND y.taxYear = 2020", TaxYear.class)
        .setParameter(1, country).getResultList();
    TaxYear year = years.isEmpty() ? new TaxYear() : years.getFirst();
    if (years.isEmpty()) {
      year.setTaxCountry(country);
      year.setTaxYear((short) 2020);
      em.persist(year);
    }
    TaxUpload upload = new TaxUpload();
    upload.setTaxYear(year);
    upload.setFileName("opening-protection.xml");
    upload.setFilePath("opening-protection.xml");
    em.persist(upload);
    IctaxSecurityTaxData data = new IctaxSecurityTaxData();
    data.setIdTaxUpload(upload.getIdTaxUpload());
    data.setIsin(security.getIsin());
    em.persist(data);
    IctaxPayment payment = new IctaxPayment();
    payment.setIctaxSecurityTaxData(data);
    payment.setExDate(DATE.minusDays(2));
    payment.setPaymentDate(DATE);
    em.persist(payment);
    em.flush();
    em.clear();
  }
}
