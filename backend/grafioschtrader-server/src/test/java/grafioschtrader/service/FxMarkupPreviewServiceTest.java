package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.User;
import grafioschtrader.dto.*;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.rest.SecurityaccountResource;

@DisplayName("Read-only FX previews resolve unsaved documents and enforce account ownership")
class FxMarkupPreviewServiceTest {
  private final SecurityaccountJpaRepository accounts = mock(SecurityaccountJpaRepository.class);
  private final TradingPlatformPlanJpaRepository plans = mock(TradingPlatformPlanJpaRepository.class);
  private final CurrencypairJpaRepository pairs = mock(CurrencypairJpaRepository.class);
  private final HistoryquoteJpaRepository quotes = mock(HistoryquoteJpaRepository.class);
  private final FxMarkupPreviewService service = new FxMarkupPreviewService(accounts, plans, pairs, quotes,
      new FxMarkupEngine());

  private FxMarkupPreviewRequest preview(String yaml) {
    return new FxMarkupPreviewRequest(42, 1, yaml, FxMarkupEngineTest.request("USD", "CHF", 100));
  }

  @Test
  void anotherTenantsAccountCannotBeReadByEitherPreview() {
    assertThat(service.account(preview(FxMarkupEngineTest.flat("1")), 7).outcome()).isEqualTo(FxOutcome.INVALID);
    assertThatThrownBy(() -> service.ownedAccount(42, 7)).hasMessageContaining("not owned");
    verify(accounts, times(2)).findByIdSecuritycashAccountAndIdTenant(42, 7);
    verifyNoInteractions(plans, pairs, quotes);
    var resource = new SecurityaccountResource();
    ReflectionTestUtils.setField(resource, "fxMarkupPreviewService", service);
    User user = mock(User.class);
    // The operating tenant decides ownership; the home tenant differs while a simulation or client is switched in.
    when(user.getIdTenant()).thenReturn(7);
    when(user.getActualIdTenant()).thenReturn(99);
    var authentication = new UsernamePasswordAuthenticationToken("test", "unused");
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      var request = new TransactionCostEstimateRequest();
      request.setIdSecurityaccount(42);
      request.setYaml(FxMarkupEngineTest.COMMISSION);
      assertThat(resource.estimateCostFromYaml(request).getBody().getError()).contains("not owned");
      verify(accounts, times(3)).findByIdSecuritycashAccountAndIdTenant(42, 7);
      verify(accounts, never()).findByIdSecuritycashAccountAndIdTenant(42, 99);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void unsavedAccountFxOnlyAndCommissionOnlyResolveIndependently() {
    var sa = FeeModelResolverTest.account(FxMarkupEngineTest.flat("2"),
        FxMarkupEngineTest.COMMISSION + FxMarkupEngineTest.flat("1"));
    when(accounts.findByIdSecuritycashAccountAndIdTenant(42, 7)).thenReturn(sa);
    assertThat(service.account(preview(FxMarkupEngineTest.flat("0")), 7).percent()).isZero();
    assertThat(service.account(preview(FxMarkupEngineTest.COMMISSION), 7).percent()).isEqualTo(1);
    assertThat(service.account(preview(""), 7).percent()).isEqualTo(1);
    assertThat(sa.getFeeModelYaml()).isEqualTo(FxMarkupEngineTest.flat("2"));
    assertThat(new TransactionCostEvalExEstimator()
        .evaluateYaml(FeeModelResolver.resolve(sa, FxMarkupEngineTest.flat("0")).commissionYaml(),
            new TransactionCostEstimateRequest())
        .getEstimatedCost()).isEqualTo(10);
    verifyNoInteractions(plans, pairs, quotes);
  }

  @Test
  void planFallbackDistinguishesNoSectionZeroAndInvalidCurrency() {
    var plan = new TradingPlatformPlan();
    plan.setFeeModelYaml(FxMarkupEngineTest.COMMISSION);
    when(plans.findById(1)).thenReturn(Optional.of(plan));
    assertThat(service.plan(preview(" ")).outcome()).isEqualTo(FxOutcome.NO_SECTION);
    assertThat(service.plan(preview(FxMarkupEngineTest.COMMISSION + FxMarkupEngineTest.flat("0"))).outcome())
        .isEqualTo(FxOutcome.MATCHED);
    var invalid = new FxMarkupPreviewRequest(null, 1, "", FxMarkupEngineTest.request("ZZZ", "CHF", 100));
    assertThat(service.plan(invalid).outcome()).isEqualTo(FxOutcome.INVALID);
    assertThat(plan.getFeeModelYaml()).isEqualTo(FxMarkupEngineTest.COMMISSION);
  }

  @Test
  void historicalTierRateAcceptsEitherStoredDirectionAndMissingCloseIsCoverageGap() throws Exception {
    var sa = FeeModelResolverTest.account(null, FxMarkupEngineTest.COMMISSION + FxMarkupEngineTest.template("migros"));
    when(accounts.findByIdSecuritycashAccountAndIdTenant(42, 7)).thenReturn(sa);
    var pair = new Currencypair("CHF", "USD");
    pair.setIdSecuritycurrency(9);
    when(pairs.findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency("USD", "CHF")).thenReturn(List.of(pair));
    when(quotes.findByIdSecuritycurrencyAndDate(9, FxMarkupEngineTest.DATE)).thenReturn(Optional.empty());
    assertThat(service.account(preview(""), 7).outcome()).isEqualTo(FxOutcome.NO_TIER_RATE);
    var quote = new Historyquote();
    quote.setClose(1.25);
    when(quotes.findByIdSecuritycurrencyAndDate(9, FxMarkupEngineTest.DATE)).thenReturn(Optional.of(quote));
    var request = new FxMarkupPreviewRequest(42, null, "", FxMarkupEngineTest.request("USD", "CHF", 62500));
    assertThat(service.account(request, 7).percent()).isEqualTo(1.5);
    request = new FxMarkupPreviewRequest(42, null, "", FxMarkupEngineTest.request("USD", "CHF", 62500.02));
    assertThat(service.account(request, 7).percent()).isEqualTo(1);
  }
}
