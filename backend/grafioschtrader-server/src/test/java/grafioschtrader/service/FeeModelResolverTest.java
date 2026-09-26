package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.User;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@DisplayName("Account commission/custody ownership and independent FX inheritance")
class FeeModelResolverTest {
  @Test
  void comparisonUsesTheInheritedCommissionForAnFxOnlyAccount() {
    var sa = account(FxMarkupEngineTest.flat("0"), FxMarkupEngineTest.COMMISSION);
    var accounts = mock(SecurityaccountJpaRepository.class);
    when(accounts.findByIdSecuritycashAccountAndIdTenant(42, 7)).thenReturn(sa);
    EntityManager manager = mock(EntityManager.class);
    @SuppressWarnings("unchecked")
    TypedQuery<Transaction> query = mock(TypedQuery.class);
    when(manager.createQuery(anyString(), eq(Transaction.class))).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    var tx = new Transaction();
    tx.setIdSecurityaccount(42);
    tx.setIdTransaction(1);
    tx.setUnits(2.0);
    tx.setQuotation(100.0);
    tx.setTransactionCost(10.0);
    tx.setTransactionTime(FxMarkupEngineTest.DATE.atStartOfDay());
    tx.setTransactionType(TransactionType.ACCUMULATE);
    var security = new Security();
    security.setIdSecuritycurrency(3);
    security.setCurrency("CHF");
    tx.setSecuritycurrency(security);
    when(query.getResultList()).thenReturn(List.of(tx));
    var comparison = new FeeModelComparisonService();
    ReflectionTestUtils.setField(comparison, "securityaccountJpaRepository", accounts);
    ReflectionTestUtils.setField(comparison, "entityManager", manager);
    ReflectionTestUtils.setField(comparison, "estimator", new TransactionCostEvalExEstimator());
    var user = mock(User.class);
    when(user.getIdTenant()).thenReturn(7);
    var auth = new UsernamePasswordAuthenticationToken("test", "unused");
    auth.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(auth);
    try {
      var report = comparison.compare(42, false);
      assertThat(report.getComparedCount()).isEqualTo(1);
      assertThat(report.getMeanEstimatedCost()).isEqualTo(10);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  static Securityaccount account(String own, String plan) {
    var sa = new Securityaccount();
    sa.setIdSecuritycashAccount(42);
    sa.setFeeModelYaml(own);
    var p = new TradingPlatformPlan();
    p.setFeeModelYaml(plan);
    sa.setTradingPlatformPlan(p);
    return sa;
  }

  @Test
  void completeResolutionMatrixAndUnsavedOverrideAgree() {
    String commission = FxMarkupEngineTest.COMMISSION;
    String fx = FxMarkupEngineTest.flat("1");
    String zero = FxMarkupEngineTest.flat("0");
    for (String[] row : new String[][] { { null, null, "NONE", "NONE" }, { null, commission, "PLAN", "NONE" },
        { null, commission + fx, "PLAN", "PLAN" }, { commission, commission + fx, "ACCOUNT", "PLAN" },
        { commission + zero, commission + fx, "ACCOUNT", "ACCOUNT" }, { fx, null, "NONE", "ACCOUNT" },
        { fx, commission + zero, "PLAN", "ACCOUNT" } }) {
      var sa = account(row[0], row[1]);
      var resolved = FeeModelResolver.resolve(sa);
      assertThat(resolved.commissionSource().name()).isEqualTo(row[2]);
      assertThat(resolved.fxSource().name()).isEqualTo(row[3]);
      var preview = FeeModelResolver.resolve(account("", row[1]), row[0]);
      assertThat(preview).usingRecursiveComparison().isEqualTo(resolved);
      assertThat(AlgoReplayFees.anyModelActive(List.of(sa))).isEqualTo(!row[2].equals("NONE"));
    }
    assertThat(FeeModelResolver.resolve(account(zero, commission + fx), "").fxSource())
        .isEqualTo(FeeModelResolver.Source.PLAN);
  }

  @Test
  void fxOnlyAccountInheritsCustodyWhileCommissionOverrideReplacesIt() throws Exception {
    String custody;
    try (var input = getClass().getResourceAsStream("/fee-models/custody/postfinance.yaml")) {
      custody = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
    String plan = FxMarkupEngineTest.COMMISSION + custody + FxMarkupEngineTest.flat("1");
    var inherited = FeeModelResolver.resolve(account(FxMarkupEngineTest.flat("0"), plan));
    assertThat(inherited.commissionYaml()).isEqualTo(plan);
    assertThat(CustodyFeeEngine.parse(inherited.commissionYaml())).isNotNull();
    assertThat(
        CustodyFeeEngine.parse(FeeModelResolver.resolve(account(FxMarkupEngineTest.COMMISSION, plan)).commissionYaml()))
            .isNull();
    assertThatThrownBy(() -> FeeModelResolver.resolve(account(FxMarkupEngineTest.flat("0") + custody, plan)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
