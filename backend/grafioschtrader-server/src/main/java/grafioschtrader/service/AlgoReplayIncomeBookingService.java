package grafioschtrader.service;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.AlgoTradingRepository;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.TransactionType;

/** Books replay income through the ordinary ledger writer, retiring claims only after successful settlement. */
@Service
public class AlgoReplayIncomeBookingService {
  private final AlgoTradingRepository data;
  private final TransactionJpaRepository transactions;
  private final CurrencypairJpaRepository currencypairs;
  private final TransactionTemplate transactionTemplate;

  public AlgoReplayIncomeBookingService(AlgoTradingRepository data, TransactionJpaRepository transactions,
      CurrencypairJpaRepository currencypairs, TransactionTemplate transactionTemplate) {
    this.data = data;
    this.transactions = transactions;
    this.currencypairs = currencypairs;
    this.transactionTemplate = transactionTemplate;
  }

  /** Explicit settlement inputs owned by one replay. */
  public record Context(Integer idRun, Integer idTenant, String currency, AlgoReplayMarketData market) {
  }

  public double pay(Context context, AlgoReplayDividendService.Claim claim, double paymentUnits) {
    return pay(context, claim, paymentUnits, 0).net();
  }

  /** All returned totals use reporting currency; the ledger tax itself uses instrument currency. */
  public record Payment(double gross, double withholding, double net) {
  }

  public Payment pay(Context context, AlgoReplayDividendService.Claim claim, double paymentUnits, double tax) {
    var distribution = claim.distribution();
    LocalDate date = distribution.payDate();
    Security security = distribution.security();
    double units = paymentUnits;
    double securityAmount = convertDividend(context, claim.amount(), distribution.currency(), security.getCurrency(),
        date);
    // Check reporting FX before writing: a payment is only retired after both accounting and reporting can succeed.
    double reportRate = convertDividend(context, 1, claim.cashaccount().getCurrency(), context.currency(), date);
    Transaction payment = new Transaction();
    payment.setIdTenant(context.idTenant());
    payment.setIdSecurityaccount(claim.securityaccount());
    payment.setCashaccount(claim.cashaccount());
    payment.setSecuritycurrency(security);
    payment.setTransactionType(TransactionType.DIVIDEND);
    payment.setUnits(units);
    payment.setQuotation(securityAmount / units);
    payment.setExDate(distribution.exDate());
    payment.setTransactionTime(date.atStartOfDay());
    payment.setTaxCost(tax);
    payment.setTaxableInterest(true);
    payment.setSimulationOpening(false);
    payment.setAlgoFillId(context.idRun() + ":D:" + claim.identity());
    if (!security.getCurrency().equals(claim.cashaccount().getCurrency())) {
      Currencypair pair = dividendPair(security.getCurrency(), claim.cashaccount().getCurrency());
      payment.setIdCurrencypair(pair.getId());
      payment.setCurrencyExRate(dividendRate(context, pair, date));
    }
    payment.setCashaccountAmount(payment.validateSecurityGeneralCashaccountAmount(0));
    Transaction saved = transactionTemplate.execute(_ -> {
      data.lockTenant(context.idTenant());
      transactions.throwWhenTransactionLimitReached(context.idTenant());
      try {
        return transactions.saveOnlyAttributes(payment, null, Set.of());
      } catch (Exception e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
    });
    double grossReport = convertDividend(context, securityAmount, security.getCurrency(),
        claim.cashaccount().getCurrency(), date) * reportRate;
    double taxReport = convertDividend(context, tax, security.getCurrency(), claim.cashaccount().getCurrency(), date)
        * reportRate;
    return new Payment(grossReport, taxReport, saved.getCashaccountAmount() * reportRate);
  }

  private Currencypair dividendPair(String from, String to) {
    Currencypair required = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(from, to);
    Currencypair pair = currencypairs.findByFromCurrencyAndToCurrency(required.getFromCurrency(),
        required.getToCurrency());
    if (pair == null)
      throw new IllegalArgumentException("REPLAY_FX_UNAVAILABLE: " + from + "/" + to);
    return pair;
  }

  private double dividendRate(Context context, Currencypair pair, LocalDate date) {
    Double rate = context.market().close(pair.getId(), date);
    if (rate == null || !Double.isFinite(rate) || rate <= 0)
      throw new IllegalArgumentException("REPLAY_FX_UNAVAILABLE");
    return rate;
  }

  public double convertDividend(Context context, double amount, String from, String to, LocalDate date) {
    if (from.equals(to))
      return amount;
    Currencypair pair = dividendPair(from, to);
    return DataBusinessHelper.divideMultiplyExchangeRate(amount, dividendRate(context, pair, date), from, to);
  }

}
