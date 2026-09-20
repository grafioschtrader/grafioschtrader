package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.Globalparameters;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.SimulationSourceRepository;
import grafioschtrader.service.AlgoReplayDividendService.Claim;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.TransactionType;

@DisplayName("Replay dividend entitlement and payment accounting")
class AlgoReplayDividendServiceTest {
  private static final LocalDate OPENING = LocalDate.of(2020, 6, 1);
  private static final LocalDate EX = LocalDate.of(2020, 6, 5);
  private static final LocalDate END = LocalDate.of(2020, 7, 31);
  private final DividendJpaRepository repository = mock(DividendJpaRepository.class);
  private final SecuritysplitJpaRepository splits = mock(SecuritysplitJpaRepository.class);
  private final SimulationSourceRepository source = mock(SimulationSourceRepository.class);
  private final Security security = mock(Security.class);
  private final Cashaccount cash = mock(Cashaccount.class);
  private final List<Transaction> ledger = new ArrayList<>();
  private AlgoReplayDividendService service;

  @BeforeEach
  void setup() {
    when(security.getId()).thenReturn(1);
    when(security.getCurrency()).thenReturn("USD");
    when(security.getName()).thenReturn("Dividend stock");
    when(cash.getId()).thenReturn(3);
    when(cash.getCurrency()).thenReturn("USD");
    when(source.cashaccounts(7)).thenReturn(List.of(cash));
    when(source.transactions(eq(7), any())).thenAnswer(_ -> ledger);
    when(splits.findByIdSecuritycurrencyOrderBySplitDateAsc(1)).thenReturn(List.of());
    service = new AlgoReplayDividendService(repository, splits, source);
  }

  private void trade(LocalDate date, double units, TransactionType type) {
    Transaction transaction = new Transaction();
    transaction.setSecuritycurrency(security);
    transaction.setIdSecurityaccount(2);
    transaction.setCashaccount(cash);
    transaction.setTransactionTime(date.atStartOfDay());
    transaction.setTransactionType(type);
    transaction.setUnits(units);
    ledger.add(transaction);
  }

  private Dividend dividend(LocalDate ex, LocalDate pay, Double amount) {
    Dividend dividend = new Dividend();
    dividend.setIdDividend(10);
    dividend.setIdSecuritycurrency(1);
    dividend.setExDate(ex);
    dividend.setPayDate(pay);
    dividend.setAmount(amount);
    dividend.setCurrency("USD");
    return dividend;
  }

  private AlgoReplayDividendService.Session open(Dividend dividend, int delay) {
    when(repository.findByIdSecuritycurrencyOrderByExDateAsc(1)).thenReturn(List.of(dividend));
    return service.open(7, List.of(security), "CHF", OPENING, END, delay);
  }

  @Test
  @DisplayName("Only pre-ex holdings qualify, and a sale before payment preserves the claim")
  void entitlementSurvivesSaleAndExDatePurchasesDoNotQualify() throws Exception {
    trade(OPENING, 100, TransactionType.ACCUMULATE);
    trade(EX.minusDays(1), 20, TransactionType.REDUCE);
    trade(EX, 30, TransactionType.ACCUMULATE);
    trade(EX.plusDays(1), 110, TransactionType.REDUCE);
    var session = open(dividend(EX, EX.plusDays(16), 2.0), 16);
    List<AlgoEventType> events = new ArrayList<>();
    session.processThrough(EX, _ -> {
      throw new AssertionError("not yet spendable");
    }, (type, _) -> events.add(type));
    assertThat(session.receivables(EX)).containsEntry("USD", 160.0);
    assertThat(session.paidTotal()).isZero();
    List<Claim> payments = new ArrayList<>();
    session.processThrough(END, claim -> {
      payments.add(claim);
      return claim.amount() * 0.9;
    }, (type, _) -> events.add(type));
    assertThat(payments).singleElement().satisfies(claim -> assertThat(claim.units()).isEqualTo(80));
    assertThat(session.paidTotal()).isEqualTo(144);
    assertThat(session.receivables(END)).isEmpty();
    assertThat(session.receivables(EX)).containsEntry("USD", 160.0);
    assertThat(events).containsExactly(AlgoEventType.DIVIDEND_ENTITLEMENT, AlgoEventType.DIVIDEND_PAYMENT);
    session.processThrough(END, _ -> {
      throw new AssertionError("duplicate payment");
    }, (_, _) -> {
    });
  }

  @ParameterizedTest
  @ValueSource(ints = { 1, 16, 32 })
  @DisplayName("Missing payment dates use calendar days, including weekends")
  void fallbackDelay(int days) throws Exception {
    trade(OPENING, 100, TransactionType.ACCUMULATE);
    var session = open(dividend(EX, null, 2.0), days);
    List<Claim> payments = new ArrayList<>();
    session.processThrough(EX.plusDays(days), claim -> {
      payments.add(claim);
      return claim.amount();
    }, (_, _) -> {
    });
    assertThat(payments).singleElement().satisfies(claim -> {
      assertThat(claim.distribution().payDate()).isEqualTo(EX.plusDays(days));
      assertThat(claim.distribution().estimated()).isTrue();
    });
  }

  @Test
  @DisplayName("Provider date wins; unpaid income remains in terminal equity")
  void knownDateAndOutstandingIncome() throws Exception {
    trade(OPENING, 100, TransactionType.ACCUMULATE);
    var session = open(dividend(EX, END.plusDays(3), 2.0), 1);
    session.processThrough(END, _ -> {
      throw new AssertionError("beyond horizon");
    }, (_, _) -> {
    });
    assertThat(session.receivables(END)).containsEntry("USD", 200.0);
    assertThat(session.paidTotal()).isZero();
  }

  @Test
  @DisplayName("A rejected payment never erases the receivable")
  void rejectedPayment() throws Exception {
    trade(OPENING, 100, TransactionType.ACCUMULATE);
    var session = open(dividend(EX, EX.plusDays(1), 2.0), 16);
    session.processThrough(EX, _ -> 0, (_, _) -> {
    });
    assertThatThrownBy(() -> session.processThrough(EX.plusDays(1), _ -> {
      throw new IllegalStateException("missing FX");
    }, (_, _) -> {
    })).hasMessage("missing FX");
    assertThat(session.receivables(EX.plusDays(1))).containsEntry("USD", 200.0);
    assertThat(session.paidTotal()).isZero();
  }

  @Test
  @DisplayName("Splits on the ex-date and before payment preserve the cash entitlement")
  void splitBasis() throws Exception {
    trade(OPENING, 100, TransactionType.ACCUMULATE);
    Securitysplit onEx = mock(Securitysplit.class);
    when(onEx.getSplitDate()).thenReturn(EX);
    when(onEx.getFactor()).thenReturn(2.0);
    Securitysplit beforePay = mock(Securitysplit.class);
    when(beforePay.getSplitDate()).thenReturn(EX.plusDays(3));
    when(beforePay.getFactor()).thenReturn(3.0);
    when(splits.findByIdSecuritycurrencyOrderBySplitDateAsc(1)).thenReturn(List.of(onEx, beforePay));
    Dividend dividend = dividend(EX, EX.plusDays(16), null);
    dividend.setAmountAdjusted(1.0);
    var session = open(dividend, 16);
    session.processThrough(END, claim -> {
      assertThat(claim.units()).isEqualTo(200);
      assertThat(claim.amount()).isEqualTo(600);
      assertThat(session.paymentUnits(claim)).isEqualTo(600);
      return claim.amount();
    }, (_, _) -> {
    });
    assertThat(session.paidTotal()).isEqualTo(600);
  }

  @Test
  @DisplayName("Opening-date entitlements are excluded and a fresh run rebuilds the same income")
  void openingBoundaryAndRepeat() throws Exception {
    trade(OPENING.minusDays(10), 100, TransactionType.ACCUMULATE);
    var excluded = open(dividend(OPENING, EX, 2.0), 16);
    excluded.processThrough(END, _ -> {
      throw new AssertionError("opening claim");
    }, (_, _) -> {
    });
    assertThat(excluded.receivables(END)).isEmpty();
    for (int run = 0; run < 2; run++) {
      var session = open(dividend(EX, EX, 2.0), 16);
      session.processThrough(END, Claim::amount, (_, _) -> {
      });
      assertThat(session.paidTotal()).isEqualTo(200);
    }
  }

  @Test
  @DisplayName("Malformed amounts on held securities fail explicitly")
  void invalidIncome() {
    trade(OPENING, 100, TransactionType.ACCUMULATE);
    var session = open(dividend(EX, EX, Double.NaN), 16);
    assertThatThrownBy(() -> session.processThrough(EX, Claim::amount, (_, _) -> {
    })).hasMessageContaining("REPLAY_DIVIDEND_INVALID");
  }

  @Test
  @DisplayName("Entitlements retain separate custody and settlement accounts")
  void accountAttribution() throws Exception {
    Cashaccount other = mock(Cashaccount.class);
    when(other.getId()).thenReturn(4);
    when(other.getCurrency()).thenReturn("USD");
    when(source.cashaccounts(7)).thenReturn(List.of(cash, other));
    trade(OPENING, 100, TransactionType.ACCUMULATE);
    trade(OPENING, 60, TransactionType.ACCUMULATE);
    ledger.getLast().setCashaccount(other);
    ledger.getLast().setIdSecurityaccount(5);
    var session = open(dividend(EX, EX, 2.0), 16);
    List<Claim> paid = new ArrayList<>();
    session.processThrough(EX, claim -> {
      paid.add(claim);
      return claim.amount();
    }, (_, _) -> {
    });
    assertThat(paid).hasSize(2);
    assertThat(paid.getFirst().securityaccount()).isEqualTo(2);
    assertThat(paid.getFirst().cashaccount().getId()).isEqualTo(3);
    assertThat(paid.getLast().securityaccount()).isEqualTo(5);
    assertThat(paid.getLast().cashaccount().getId()).isEqualTo(4);
    assertThat(session.paidTotal()).isEqualTo(320);
  }

  @Test
  @DisplayName("Foreign receivables contribute equity but no cash; missing FX marks the valuation unavailable")
  void receivableValuation() {
    AlgoHistoricalValuationService valuation = new AlgoHistoricalValuationService();
    var tenants = mock(grafioschtrader.repository.TenantJpaRepository.class);
    var tenant = mock(grafioschtrader.entities.Tenant.class);
    when(tenant.getCurrency()).thenReturn("CHF");
    when(tenants.findById(7)).thenReturn(java.util.Optional.of(tenant));
    var currencies = mock(grafioschtrader.repository.CurrencypairJpaRepository.class);
    var pair = mock(grafioschtrader.entities.Currencypair.class);
    when(pair.getId()).thenReturn(9);
    when(currencies.findByFromCurrencyAndToCurrency("USD", "CHF")).thenReturn(pair);
    ReflectionTestUtils.setField(valuation, "source", source);
    ReflectionTestUtils.setField(valuation, "tenants", tenants);
    ReflectionTestUtils.setField(valuation, "currencies", currencies);
    ReflectionTestUtils.setField(valuation, "balances",
        mock(grafioschtrader.repository.HoldCashaccountBalanceJpaRepository.class));
    ReflectionTestUtils.setField(valuation, "holdings",
        mock(grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository.class));
    var market = new AlgoHistoricalValuationService.ClosingPrices() {
      @Override
      public Double close(Integer id, LocalDate date) {
        return 0.9;
      }

      @Override
      public Map<String, Double> receivables(LocalDate date) {
        return Map.of("USD", 200.0);
      }
    };
    var snapshot = valuation.value(7, EX, market);
    assertThat(snapshot.equity()).isEqualTo(180);
    assertThat(snapshot.cashBalances()).containsEntry(3, 0.0);
    assertThat(snapshot.errors()).isEmpty();
    when(currencies.findByFromCurrencyAndToCurrency("USD", "CHF")).thenReturn(null);
    var missing = valuation.value(7, EX, market);
    assertThat(missing.errors()).contains("USD/CHF");
    assertThatThrownBy(missing::requireAvailable).isInstanceOf(grafiosch.exceptions.DataViolationException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = { 0, 1, 16, 32, 33 })
  @DisplayName("Administrator bounds are enforced even for externally corrupted values")
  void configurationBounds(int value) {
    GlobalparametersService parameters = new GlobalparametersService();
    GlobalparametersJpaRepository parametersRepository = mock(GlobalparametersJpaRepository.class);
    ReflectionTestUtils.setField(parameters, "globalparametersJpaRepository", parametersRepository);
    Globalparameters parameter = mock(Globalparameters.class);
    when(parameter.getPropertyInt()).thenReturn(value);
    when(parametersRepository.findById(GlobalParamKeyDefault.GLOB_KEY_SIMULATION_DIVIDEND_PAYMENT_DELAY_DAYS))
        .thenReturn(java.util.Optional.of(parameter));
    assertThat(parameters.getSimulationDividendPaymentDelayDays()).isEqualTo(value < 1 || value > 32 ? 16 : value);
    when(parametersRepository.findById(GlobalParamKeyDefault.GLOB_KEY_SIMULATION_DIVIDEND_PAYMENT_DELAY_DAYS))
        .thenReturn(java.util.Optional.empty());
    assertThat(parameters.getSimulationDividendPaymentDelayDays()).isEqualTo(16);
  }
}
