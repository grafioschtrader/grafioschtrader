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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Assetclass;
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
import grafioschtrader.types.CreateType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

@DisplayName("Replay dividend entitlement and payment accounting")
class AlgoReplayDividendServiceTest {
  private static final LocalDate OPENING = LocalDate.of(2020, 6, 1);
  private static final LocalDate EX = LocalDate.of(2020, 6, 5);
  private static final LocalDate END = LocalDate.of(2020, 7, 31);
  private final DividendJpaRepository repository = mock(DividendJpaRepository.class);
  private final SecuritysplitJpaRepository splits = mock(SecuritysplitJpaRepository.class);
  private final SimulationSourceRepository source = mock(SimulationSourceRepository.class);
  private final Security security = new Security();
  private final Cashaccount cash = new Cashaccount();
  private final List<Transaction> ledger = new ArrayList<>();
  private AlgoReplayDividendService service;

  @BeforeEach
  void setup() {
    security.setIdSecuritycurrency(1);
    security.setCurrency("USD");
    security.setName("Dividend stock");
    security.setLeverageFactor(1);
    Assetclass assetclass = new Assetclass();
    assetclass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    security.setAssetClass(assetclass);
    cash.setIdSecuritycashAccount(3);
    cash.setCurrency("USD");
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

  @Test
  @DisplayName("Provider dates override the calendar-day fallback; unpaid income remains a receivable")
  void knownDateAndOutstandingIncome() throws Exception {
    trade(OPENING, 100, TransactionType.ACCUMULATE);
    var session = open(dividend(EX, END.plusDays(3), 2.0), 1);
    session.processThrough(END, _ -> {
      throw new AssertionError("beyond horizon");
    }, (_, _) -> {
    });
    assertThat(session.receivables(END)).containsEntry("USD", 200.0);
    assertThat(session.paidTotal()).isZero();

    var estimated = open(dividend(EX, null, 2.0), 16);
    estimated.processThrough(EX.plusDays(15), _ -> {
      throw new AssertionError("before estimated payment date");
    }, (_, _) -> {
    });
    assertThat(estimated.receivables(EX.plusDays(15))).containsEntry("USD", 200.0);
    List<Claim> payments = new ArrayList<>();
    estimated.processThrough(EX.plusDays(16), claim -> {
      payments.add(claim);
      return claim.amount();
    }, (_, _) -> {
    });
    assertThat(payments).singleElement().satisfies(claim -> {
      assertThat(claim.distribution().payDate()).isEqualTo(EX.plusDays(16));
      assertThat(claim.distribution().estimated()).isTrue();
    });
    assertThat(estimated.paidTotal()).isEqualTo(200.0);
    assertThat(estimated.receivables(EX.plusDays(16))).isEmpty();
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
    Securitysplit onEx = new Securitysplit(1, EX, 1, 2, CreateType.ADD_MODIFIED_USER);
    Securitysplit beforePay = new Securitysplit(1, EX.plusDays(3), 1, 3, CreateType.ADD_MODIFIED_USER);
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

}
