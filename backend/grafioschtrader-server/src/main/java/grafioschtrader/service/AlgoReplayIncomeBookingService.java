package grafioschtrader.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.AlgoTradingRepository;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.TransactionType;

/** Books replay income through the ordinary ledger writer, retiring claims only after successful settlement. */
@Service
public class AlgoReplayIncomeBookingService {
  /** A payment date is moved to a trading day at most this far ahead; beyond it the ledger writer reports the gap. */
  private static final int MAX_BOOKING_SHIFT_DAYS = 14;
  private final AlgoTradingRepository data;
  private final TransactionJpaRepository transactions;
  private final CurrencypairJpaRepository currencypairs;
  private final TransactionTemplate transactionTemplate;
  private final TradingDaysPlusJpaRepository tradingDaysPlus;

  public AlgoReplayIncomeBookingService(AlgoTradingRepository data, TransactionJpaRepository transactions,
      CurrencypairJpaRepository currencypairs, TransactionTemplate transactionTemplate,
      TradingDaysPlusJpaRepository tradingDaysPlus) {
    this.data = data;
    this.transactions = transactions;
    this.currencypairs = currencypairs;
    this.transactionTemplate = transactionTemplate;
    this.tradingDaysPlus = tradingDaysPlus;
  }

  /** Explicit settlement inputs owned by one replay. */
  public record Context(Integer idRun, Integer idTenant, String currency, AlgoReplayMarketData market,
      AlgoReplayFx fx) {
    public Context(Integer idRun, Integer idTenant, String currency, AlgoReplayMarketData market) {
      this(idRun, idTenant, currency, market, null);
    }
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
    payment.setTransactionTime(bookingDate(date).atStartOfDay());
    payment.setTaxCost(tax);
    payment.setTaxableInterest(true);
    payment.setSimulationOpening(false);
    payment.setAlgoFillId(context.idRun() + ":D:" + claim.identity());
    AlgoReplayFx.Conversion conversion = null;
    if (!security.getCurrency().equals(claim.cashaccount().getCurrency())) {
      Currencypair pair = dividendPair(security.getCurrency(), claim.cashaccount().getCurrency());
      payment.setIdCurrencypair(pair.getId());
      double close = dividendRate(context, pair, date);
      double rate = close;
      if (context.fx() != null) {
        double net = securityAmount - tax;
        String pay = net >= 0 ? security.getCurrency() : claim.cashaccount().getCurrency();
        String receive = net >= 0 ? claim.cashaccount().getCurrency() : security.getCurrency();
        double mid = DataBusinessHelper.divideMultiplyExchangeRate(net, close, security.getCurrency(),
            claim.cashaccount().getCurrency());
        var quote = context.fx().quote(claim.securityaccount(), pay, receive, pair, close, "INCOME",
            Math.abs(net >= 0 ? net : mid), date,
            security.getStockexchange() == null ? null : security.getStockexchange().getMic());
        rate = AlgoReplayFx.effective(pair, close, receive, quote);
        double actual = DataBusinessHelper.divideMultiplyExchangeRate(net, rate, security.getCurrency(),
            claim.cashaccount().getCurrency());
        conversion = context.fx().conversion(claim.securityaccount(), pay, receive, "INCOME", quote,
            Math.abs(actual - mid), claim.cashaccount().getCurrency(), date);
      }
      payment.setCurrencyExRate(rate);
    }
    payment.setCashaccountAmount(payment.validateSecurityGeneralCashaccountAmount(0));
    Transaction saved = transactionTemplate.execute(_ -> {
      data.lockTenant(context.idTenant());
      transactions.throwWhenTransactionLimitReached(context.idTenant(), 1);
      try {
        return transactions.saveOnlyAttributes(payment, null, Set.of());
      } catch (Exception e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
    });
    if (context.fx() != null)
      context.fx().committed(payment.getAlgoFillId(), conversion);
    double grossReport = convertDividend(context, securityAmount, security.getCurrency(),
        claim.cashaccount().getCurrency(), date) * reportRate;
    double taxReport = convertDividend(context, tax, security.getCurrency(), claim.cashaccount().getCurrency(), date)
        * reportRate;
    return new Payment(grossReport, taxReport, saved.getCashaccountAmount() * reportRate);
  }

  /**
   * The day the payment enters the ledger. The transaction writer accepts a security transaction only on a possible
   * trading day, while a payment date may fall on a weekend or a worldwide holiday: a provider pay date on a Saturday,
   * an estimated date of ex-date plus delay, or a generated coupon date. Such a payment is credited on the next trading
   * day, as a bank does. The claim keeps its payment date, so the trail, the receivables and the exchange rate stay on
   * the economic date.
   *
   * @param payDate the payment date of the distribution
   * @return the payment date itself when it is a trading day, otherwise the next trading day; the payment date when no
   *         trading day follows within {@link #MAX_BOOKING_SHIFT_DAYS}
   */
  private LocalDate bookingDate(LocalDate payDate) {
    List<TradingDaysPlus> days = tradingDaysPlus.findByTradingDateBetweenOrderByTradingDate(payDate,
        payDate.plusDays(MAX_BOOKING_SHIFT_DAYS));
    return days.isEmpty() ? payDate : days.getFirst().getTradingDate();
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
