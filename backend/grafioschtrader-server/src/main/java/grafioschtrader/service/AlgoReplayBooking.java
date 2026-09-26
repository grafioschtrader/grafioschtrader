package grafioschtrader.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.exceptions.TransactionLimitExceededException;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.TransactionType;

/**
 * Turns one replayed order into a portfolio transaction: fill date and price, the account that pays, the money that has
 * to be brought together when no single account can, and the write to the ordinary ledger.
 *
 * <p>
 * {@link AlgoReplayAccounts} decides where an order lives. This class decides how much of it can actually be booked and
 * writes the fill, using the same overdraft and FX conventions as the transaction write path so a choice made here is
 * not refused there.
 * </p>
 */
@Service
public class AlgoReplayBooking {

  /**
   * Funding transfers one run may write. The write path of the portfolio does not police the transaction limit of the
   * tenant - only the REST layer does - so an environment that keeps being short would otherwise grow its ledger
   * without any bound at all. A run that reaches this has a funding problem the moving of money cannot solve.
   */
  static final int MAX_FUNDING_TRANSFERS = 200;

  /** Time of day generated fills are booked at, matching the opening deposits of the environment. */
  private static final int FILL_HOUR = 0;

  /**
   * Rationale of an order the transaction write path refused - an overdrawn cash account, a closed trading period, a
   * sale beyond the units held. The reason itself goes into the details, because it arrives as a resolved message with
   * arguments rather than as a key the trail could translate.
   */
  private static final String RATIONALE_FILL_REJECTED = "REPLAY_FILL_REJECTED";

  /** The refusals the replay raises itself, each of them an NLS key the trail renders as a rationale. */
  private static final Set<String> REPLAY_REFUSALS = Set.of("REPLAY_MARGIN_UNSUPPORTED", "REPLAY_NO_FILL_DATE",
      "REPLAY_NO_FILL_PRICE", "REPLAY_FX_UNAVAILABLE", "REPLAY_NO_ACCOUNT", "REPLAY_NOT_FUNDED",
      "REPLAY_NO_TRADABLE_UNITS", "REPLAY_INSTRUMENT_EXCLUDED");

  /** Rationale of the row that records money moved inside the environment so that an order could be paid for. */
  private static final String RATIONALE_FUNDING_TRANSFER = "REPLAY_FUNDING_TRANSFER";

  /**
   * Rationale of the row stating that none of the priority security accounts of an instrument may trade it on the fill
   * day, so the order was booked on an account chosen automatically. Written once per instrument and run.
   */
  private static final String RATIONALE_PRIORITY_NOT_ELIGIBLE = "REPLAY_PRIORITY_ACCOUNT_NOT_ELIGIBLE";

  private final AlgoReplayCalendar calendar;
  private final TransactionJpaRepository transactions;
  private final CurrencypairJpaRepository currencypairs;
  private final HoldCashaccountBalanceJpaRepository cashHoldings;
  private final GlobalparametersService globalparametersService;
  private final MessageSource messageSource;

  public AlgoReplayBooking(AlgoReplayCalendar calendar, TransactionJpaRepository transactions,
      CurrencypairJpaRepository currencypairs, HoldCashaccountBalanceJpaRepository cashHoldings,
      GlobalparametersService globalparametersService, MessageSource messageSource) {
    this.calendar = calendar;
    this.transactions = transactions;
    this.currencypairs = currencypairs;
    this.cashHoldings = cashHoldings;
    this.globalparametersService = globalparametersService;
    this.messageSource = messageSource;
  }

  /**
   * Builds the transaction of one order and, for a rebalancing order, books it. A mean reversion order is handed back
   * to the caller instead, because it goes through the simulation fill adapter, which is where its signal identity and
   * its remaining budget are checked.
   */
  Transaction book(AlgoReplayState state, Security security, Integer idAlgoStrategy, double units, TransactionType type,
      LocalDate decisionDate) throws Exception {
    if (state.market.tradingExcluded(security))
      throw new IllegalArgumentException("REPLAY_INSTRUMENT_EXCLUDED");
    if (security.isMarginInstrument()) {
      throw new IllegalArgumentException("REPLAY_MARGIN_UNSUPPORTED");
    }
    var instrument = state.inputs.instruments().get(security.getId());
    LocalDate fillDate = calendar
        .nextEligibleClose(security, decisionDate, state.run.getEndDate(),
            instrument == null ? null : instrument.activeFromDate(),
            instrument == null ? null : instrument.activeToDate(), instrument == null ? null : instrument.directBond())
        .filter(date -> instrument == null || !instrument.tradingStopped(date))
        .orElseThrow(() -> new IllegalArgumentException("REPLAY_NO_FILL_DATE"));
    Double price = state.market.close(security.getId(), fillDate);
    if (price == null || !Double.isFinite(price) || price <= 0) {
      throw new IllegalArgumentException("REPLAY_NO_FILL_PRICE");
    }
    // Only a purchase has to be paid for; a sale releases money and may settle anywhere the rules allow.
    double lot = tradableLot(instrument);
    double requestedUnits = tradableUnits(units, lot);
    if (!(requestedUnits > 0)) {
      throw new IllegalArgumentException("REPLAY_NO_TRADABLE_UNITS");
    }
    double bookedUnits = requestedUnits;
    double requiredAmount = type == TransactionType.ACCUMULATE ? bookedUnits * price : 0;
    // The account is chosen on the naked order value. The fee of an account can only be asked once the account is
    // known, so letting it steer the choice would be circular, and the choice ranks accounts by what they can pay,
    // where a commission is a small delta.
    AlgoReplayAccounts.Booking booking = state.accounts.resolve(security, fillDate, requiredAmount,
        funds(state, security));
    if (state.accounts.takePriorityFallback(security)) {
      state.write(AlgoEventType.UNAVAILABLE, decisionDate, idAlgoStrategy, security.getId(), null, null, null, null,
          RATIONALE_PRIORITY_NOT_ELIGIBLE);
    }
    var order = state.costs.order();
    var cost = order.estimate(booking.idSecurityaccount(), security, bookedUnits, price, type, fillDate,
        state.equityNow(), currency(booking.cashaccount()));
    // A strategy order is handed back unsaved and the fill adapter may still refuse it on its signal identity or its
    // remaining budget. Money moved for an order that is then never booked could not be taken back - the run has no
    // compensating step - so only an order this method books itself is funded.
    if (idAlgoStrategy == null && type == TransactionType.ACCUMULATE) {
      booking = fund(state, security, booking, requiredAmount + cost.total(), decisionDate, fillDate, bookedUnits, lot,
          price, order);
      // Funding may move the purchase onto the settlement account of the environment, which can belong to another
      // trading platform plan and therefore charge differently.
      cost = order.estimate(booking.idSecurityaccount(), security, bookedUnits, price, type, fillDate,
          state.equityNow(), currency(booking.cashaccount()));
    }
    if (type == TransactionType.ACCUMULATE) {
      Sized sized = affordable(state, security, booking, bookedUnits, lot, price, type, fillDate, cost, order);
      bookedUnits = sized.units();
      cost = sized.cost();
    }
    state.lastFillWasReduced = bookedUnits + 1e-8 < requestedUnits;
    Transaction fill = new Transaction();
    fill.setIdTenant(state.idTenant());
    fill.setIdSecurityaccount(booking.idSecurityaccount());
    fill.setCashaccount(booking.cashaccount());
    fill.setSecuritycurrency(security);
    fill.setUnits(bookedUnits);
    fill.setQuotation(price);
    fill.setTransactionType(type);
    fill.setTransactionTime(fillDate.atTime(FILL_HOUR, 0));
    fill.setSimulationOpening(false);
    fill.setTransactionCost(cost.fee() > 0 ? cost.fee() : null);
    fill.setTaxCost(cost.tax());
    fill.setAssetInvestmentValue1(cost.accruedInterest() > 0 ? cost.accruedInterest() : null);
    applyExchangeRate(state, fill, security, booking.cashaccount(), fillDate, 0);
    state.pendingTax = cost.diagnostics();
    state.pendingCustodyCredit = cost.custodyCredit();
    fill.setCashaccountAmount(fill.validateSecurityGeneralCashaccountAmount(0));
    if (idAlgoStrategy == null) {
      fill.setAlgoFillId(state.run.getIdSimulationResult() + ":R:" + security.getId() + ":" + fillDate + ":" + type);
      transactions.throwWhenTransactionLimitReached(state.idTenant(), 1);
      Transaction saved = transactions.saveOnlyAttributes(fill, null, Set.of());
      state.fx.committed(saved.getAlgoFillId(), state.pendingFx);
      state.costs.committed(saved);
      if (state.custody != null)
        state.custody.committed(saved, cost.custodyCredit());
      state.taxes.committed(cost.diagnostics(), security.getId(), saved.getIdSecurityaccount(), fillDate,
          saved.getAlgoFillId());
      // Only an accepted order establishes where the instrument lives. Pinning the choice before that would make one
      // underfunded day the permanent home of the instrument.
      state.accounts.remember(security, booking);
      state.roundTrips.add(null, security.getId(), type, saved.getUnits(), saved.getQuotation(),
          saved.getTransactionCost(), saved.getTaxCost(), saved.getAssetInvestmentValue1(), saved.getTransactionDate());
      return saved;
    }
    return fill;
  }

  /**
   * Repays a directly held bond at par on its captured {@code activeToDate}. No broker fee and no trade tax; accrued
   * interest is the generated-coupon amount on that date, which is zero when the date is a coupon date.
   */
  Transaction redeem(AlgoReplayState state, Security security, AlgoReplayInputs.Instrument instrument,
      Integer idSecurityaccount, Cashaccount cashaccount, double units, LocalDate eventDate) throws Exception {
    double par = parPerUnit(instrument);
    double accrued = 0;
    if (instrument != null && "GENERATED".equals(instrument.incomeSource()) && instrument.couponTerms() != null) {
      accrued = new AlgoReplayCouponSchedule(instrument.couponTerms()).accrued(units, instrument.activeToDate());
    }
    return persistTerminal(state, security, idSecurityaccount, cashaccount, units, par, 0, 0, accrued, eventDate,
        state.run.getIdSimulationResult() + ":M:" + security.getId() + ":" + idSecurityaccount + ":" + eventDate);
  }

  /** Validates the historical settlement rate without falling back to live data. */
  boolean hasLiquidationFx(AlgoReplayState state, Security security, Cashaccount cash, LocalDate date) {
    if (cash == null)
      return false;
    if (security.getCurrency().equals(cash.getCurrency()))
      return true;
    var required = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(security.getCurrency(), cash.getCurrency());
    var pair = currencypairs.findByFromCurrencyAndToCurrency(required.getFromCurrency(), required.getToCurrency());
    Double rate = pair == null ? null : state.market.close(pair.getId(), date);
    return rate != null && Double.isFinite(rate) && rate > 0;
  }

  /** The sole exception to the exclusion guard: close a complete imported position or a linked margin lot. */
  Transaction closeExcluded(AlgoReplayState state, AlgoReplayLiquidation.Close close, LocalDate date) throws Exception {
    Security security = close.security();
    if (!state.market.tradingExcluded(security))
      throw new IllegalArgumentException("Opening liquidation requires an excluded instrument");
    Double price = state.market.exactClose(security.getId(), date);
    if (price == null || !Double.isFinite(price) || price <= 0)
      throw new IllegalArgumentException("REPLAY_NO_FILL_PRICE");
    TransactionType type = close.opening() != null && close.opening().getTransactionType() == TransactionType.REDUCE
        ? TransactionType.ACCUMULATE
        : TransactionType.REDUCE;
    double pointValue = close.opening() == null ? 1 : close.opening().getValuePerPoint();
    var cost = state.costs.estimate(close.account(), security, close.units() * pointValue, price, type, date,
        state.equityNow(), currency(close.cash()));
    Transaction fill = new Transaction();
    fill.setIdTenant(state.idTenant());
    fill.setSecuritycurrency(security);
    fill.setIdSecurityaccount(close.account());
    fill.setCashaccount(close.cash());
    fill.setUnits(close.units());
    fill.setQuotation(price);
    fill.setTransactionType(type);
    fill.setTransactionTime(date.atTime(FILL_HOUR, 0));
    fill.setSimulationOpening(false);
    fill.setTransactionCost(cost.fee() > 0 ? cost.fee() : null);
    fill.setTaxCost(cost.tax());
    fill.setAssetInvestmentValue1(cost.accruedInterest() > 0 ? cost.accruedInterest() : null);
    if (close.opening() != null) {
      fill.setConnectedIdTransaction(close.opening().getId());
      fill.setAssetInvestmentValue2(pointValue);
      var captured = state.inputs.instruments().get(security.getId());
      var splits = captured.splits().stream().map(s -> new grafioschtrader.entities.Securitysplit(security.getId(),
          s.date(), s.from(), s.to(), grafioschtrader.types.CreateType.ADD_MODIFIED_USER)).toList();
      fill.setSplitFactorFromBaseTransaction(grafioschtrader.entities.Securitysplit.calcSplitFatorForFromDateAndToDate(
          security.getId(), close.opening().getTransactionDate(), date.plusDays(1),
          java.util.Map.of(security.getId(), splits)).fromToDateFactor);
    }
    applyExchangeRate(state, fill, security, close.cash(), date,
        close.opening() == null ? 0 : close.opening().getQuotation());
    fill.setCashaccountAmount(
        fill.validateSecurityGeneralCashaccountAmount(close.opening() == null ? 0 : close.opening().getQuotation()));
    fill.setAlgoFillId(state.run.getIdSimulationResult() + ":X:" + security.getId() + ":" + close.account() + ":"
        + (close.opening() == null ? "P" : close.opening().getId()) + ":" + date);
    transactions.throwWhenTransactionLimitReached(state.idTenant(), 1);
    Transaction saved = transactions.saveOnlyAttributes(fill, null, Set.of());
    state.fx.committed(saved.getAlgoFillId(), state.pendingFx);
    state.costs.committed(saved);
    if (state.custody != null)
      state.custody.committed(saved, cost.custodyCredit());
    state.taxes.committed(cost.diagnostics(), security.getId(), close.account(), date, saved.getAlgoFillId());
    // Closing a complete opening position ends the lifecycle the round trips continued from the opening day. A margin
    // lot is no such lifecycle.
    if (close.opening() == null) {
      state.roundTrips.add(null, security.getId(), type, saved.getUnits(), saved.getQuotation(),
          saved.getTransactionCost(), saved.getTaxCost(), saved.getAssetInvestmentValue1(), saved.getTransactionDate());
    }
    return saved;
  }

  /**
   * Closes a leftover non-bond position at the last close on or before {@code min(eventDate, to)}, with ordinary fees
   * and taxes.
   */
  Transaction terminalClose(AlgoReplayState state, Security security, Integer idSecurityaccount,
      Cashaccount cashaccount, double units, LocalDate eventDate, LocalDate to) throws Exception {
    LocalDate priceDate = to != null && to.isBefore(eventDate) ? to : eventDate;
    Double price = state.market.close(security.getId(), priceDate);
    if (price == null || !Double.isFinite(price) || price <= 0) {
      throw new IllegalArgumentException("REPLAY_NO_FILL_PRICE");
    }
    var cost = state.costs.estimate(idSecurityaccount, security, units, price, TransactionType.REDUCE, eventDate,
        state.equityNow(), currency(cashaccount), false);
    Transaction saved = persistTerminal(state, security, idSecurityaccount, cashaccount, units, price, cost.fee(),
        cost.tax(), cost.accruedInterest(), eventDate,
        state.run.getIdSimulationResult() + ":T:" + security.getId() + ":" + idSecurityaccount + ":" + eventDate);
    state.costs.committed(saved);
    return saved;
  }

  private Transaction persistTerminal(AlgoReplayState state, Security security, Integer idSecurityaccount,
      Cashaccount cashaccount, double units, double quotation, double fee, double tax, double accrued,
      LocalDate eventDate, String algoFillId) throws Exception {
    Transaction fill = new Transaction();
    fill.setIdTenant(state.idTenant());
    fill.setIdSecurityaccount(idSecurityaccount);
    fill.setCashaccount(cashaccount);
    fill.setSecuritycurrency(security);
    fill.setUnits(round(units));
    fill.setQuotation(quotation);
    fill.setTransactionType(TransactionType.REDUCE);
    fill.setTransactionTime(eventDate.atTime(FILL_HOUR, 0));
    fill.setSimulationOpening(false);
    fill.setTransactionCost(fee > 0 ? fee : null);
    fill.setTaxCost(tax);
    fill.setAssetInvestmentValue1(accrued > 0 ? accrued : null);
    applyExchangeRate(state, fill, security, cashaccount, eventDate, 0);
    fill.setCashaccountAmount(fill.validateSecurityGeneralCashaccountAmount(0));
    fill.setAlgoFillId(algoFillId);
    // Count before writing, as for replay income. There is no tenant lock in the transaction write path, so concurrent
    // requests may slightly exceed the cap.
    transactions.throwWhenTransactionLimitReached(state.idTenant(), 1);
    Transaction saved = transactions.saveOnlyAttributes(fill, null, Set.of());
    state.fx.committed(saved.getAlgoFillId(), state.pendingFx);
    state.accounts.remember(security, new AlgoReplayAccounts.Booking(idSecurityaccount, cashaccount));
    state.roundTrips.add(null, security.getId(), TransactionType.REDUCE, saved.getUnits(), saved.getQuotation(),
        saved.getTransactionCost(), saved.getTaxCost(), saved.getAssetInvestmentValue1(), saved.getTransactionDate());
    return saved;
  }

  /** The settlement currency a fee rule sees; null when the order has no cash account yet. */
  private static String currency(Cashaccount cashaccount) {
    return cashaccount == null ? null : cashaccount.getCurrency();
  }

  static double parPerUnit(AlgoReplayInputs.Instrument instrument) {
    return AlgoReplayCouponSchedule.PAR_PER_UNIT;
  }

  /**
   * Brings the money of the environment together on one account when no single one can pay a purchase, and answers
   * where the purchase is then booked.
   *
   * <p>
   * A rebalancing is sized against the equity of the whole environment while one order is paid by one cash account, so
   * an environment whose cash sits in three accounts can be unable to execute an allocation it has the money for. The
   * holder of such a portfolio would move money between the accounts, and so does this: only when nothing can pay
   * outright, only what is missing, and only onto accounts that are never drained again, so the cash of a run cannot
   * travel back and forth between two days.
   * </p>
   *
   * <p>
   * The transfers are dated on the <b>decision day</b>, not on the fill day, and that is not cosmetic. The overdraft
   * guard of the write path takes {@code min(balance before the day, lowest balance from the day on)}, so money that
   * arrives on the day of the purchase raises the second term but not the first and would not pay for anything. The
   * decision day always lies between the opening of the environment and the fill - the run visits only days after the
   * opening, and an order fills at the next close after its decision - so it is the one day a transfer can use.
   * </p>
   *
   * <p>
   * The security account of the booking never changes here, only the cash account within its portfolio. That is what
   * makes a security account priority work: the order stays at the broker the user named, and the money of the other
   * brokers is moved to it. A strategy order is never funded, so a priority account that cannot pay one refuses it with
   * {@code REPLAY_NOT_FUNDED} rather than sending it to a broker the user did not choose.
   * </p>
   *
   * <p>
   * An ordinary transfer refusal is reported against the order but does not fail it: the original booking is returned,
   * and the purchase is refused or reduced afterwards on its own merits rather than surfacing as an overdraft of an
   * account the reader cannot connect to the order. A transaction-limit refusal propagates and stops the replay.
   * </p>
   *
   * @param state          the running replay
   * @param security       the instrument being bought, recorded with every transfer
   * @param booking        where the order would be settled without any funding
   * @param requiredAmount what the order costs, in the currency of the instrument
   * @param decisionDate   the day the transfers are booked on
   * @param fillDate       the day the purchase is booked on
   * @return where to book the purchase - the funded account when money was moved, else the original booking, whose
   *         account is the one holding the most and therefore states the true shortfall when it is refused
   */
  private AlgoReplayAccounts.Booking fund(AlgoReplayState state, Security security, AlgoReplayAccounts.Booking booking,
      double requiredAmount, LocalDate decisionDate, LocalDate fillDate, double units, double lot, double price,
      AlgoReplayCosts.Order order) {
    if (availableFor(state, booking.idSecurityaccount(), booking.cashaccount(), fillDate, security.getCurrency(),
        requiredAmount, mic(state, security)) >= requiredAmount) {
      return booking;
    }
    try {
      // An instrument whose home is pinned has to be funded there; only a fresh one may be steered to the account of
      // the environment currency, which is the direction the exchange rates of the tenant exist in.
      Cashaccount target = state.accounts.isEstablished(security) ? booking.cashaccount()
          : state.accounts.settlementTarget(booking);
      if (state.fx.modelled() && !state.accounts.isEstablished(security)
          && !target.getId().equals(booking.cashaccount().getId())) {
        var direct = fundingPlan(state, security, booking, booking.cashaccount(), units, lot, price, decisionDate,
            fillDate, order);
        var fallback = fundingPlan(state, security, booking, target, units, lot, price, decisionDate, fillDate, order);
        if (direct.markup() || fallback.markup()) {
          FundingPlan chosen = betterPlan(direct, fallback, units);
          boolean moved = false;
          for (Transfer transfer : chosen.transfers())
            moved |= executeTransfer(state, security, transfer) > 0;
          if (moved)
            state.accounts.rememberFundingTarget(chosen.target(), decisionDate);
          return new AlgoReplayAccounts.Booking(booking.idSecurityaccount(), chosen.target());
        }
      }
      Double purchaseRate = purchaseRate(state, booking.idSecurityaccount(), security.getCurrency(),
          target.getCurrency(), fillDate, requiredAmount, mic(state, security));
      Double held = spendable(target, fillDate);
      if (purchaseRate == null || held == null) {
        // No rate means the purchase itself will refuse; an overdrawable target needs nothing brought to it.
        return purchaseRate == null ? booking : new AlgoReplayAccounts.Booking(booking.idSecurityaccount(), target);
      }
      int precision = globalparametersService.getPrecisionForCurrency(target.getCurrency());
      double minorUnit = Math.pow(10, -precision);
      // One minor unit on top, so the rounding the write path applies to the order cannot leave it a cent short.
      double missing = ceil(requiredAmount * purchaseRate - held, precision) + minorUnit;
      if (missing > 0) {
        bringTogether(state, booking.idSecurityaccount(), security, target, missing, precision, decisionDate);
      }
      // The account of the environment currency is the one money can be moved to, but it is not automatically the one
      // with the most on it. Where nothing could be brought to it, the order stays where the choice had put it, so
      // re-targeting can only ever improve what the order is able to pay.
      return availableFor(state, booking.idSecurityaccount(), target, fillDate, security.getCurrency(), requiredAmount,
          mic(state, security)) >= availableFor(state, booking.idSecurityaccount(), booking.cashaccount(), fillDate,
              security.getCurrency(), requiredAmount, mic(state, security))
                  ? new AlgoReplayAccounts.Booking(booking.idSecurityaccount(), target)
                  : booking;
    } catch (TransactionLimitExceededException | AlgoReplayFx.Failure e) {
      throw e;
    } catch (Exception e) {
      state.write(AlgoEventType.UNAVAILABLE, decisionDate, null, security.getId(), null, null, null, null,
          rationaleOf(e), detailsOf(e, state.locale));
      return booking;
    }
  }

  private record FundingPlan(Cashaccount target, List<Transfer> transfers, double available, double cost,
      boolean markup) {
  }

  /** Prices both the ordered transfers and the purchase without writing or taking a daily funding lock. */
  private FundingPlan fundingPlan(AlgoReplayState state, Security security, AlgoReplayAccounts.Booking booking,
      Cashaccount target, double units, double lot, double price, LocalDate decision, LocalDate fill,
      AlgoReplayCosts.Order order) {
    var targetBooking = new AlgoReplayAccounts.Booking(booking.idSecurityaccount(), target);
    var estimate = order.estimate(booking.idSecurityaccount(), security, units, price, TransactionType.ACCUMULATE, fill,
        state.equityNow(), target.getCurrency());
    double required = units * price + estimate.total();
    Double rate = purchaseRate(state, booking.idSecurityaccount(), security.getCurrency(), target.getCurrency(), fill,
        required, mic(state, security));
    Double balance = spendable(target, fill);
    if (rate == null)
      return new FundingPlan(target, List.of(), Double.NEGATIVE_INFINITY, 0, false);
    double held = balance == null ? Double.POSITIVE_INFINITY : balance;
    int precision = globalparametersService.getPrecisionForCurrency(target.getCurrency());
    double missing = ceil(required * rate - held, precision) + Math.pow(10, -precision);
    List<Transfer> transfers = new ArrayList<>();
    double cost = 0;
    boolean markup = false;
    for (Cashaccount source : state.accounts.fundingSources(target, decision)) {
      if (missing <= 0 || state.fundingTransfers + transfers.size() >= MAX_FUNDING_TRANSFERS)
        break;
      double available = transferable(state, booking.idSecurityaccount(), source, target, decision, missing);
      if (!Double.isFinite(available) || available <= 0)
        continue;
      var transfer = prepareTransfer(state, booking.idSecurityaccount(), source, target, Math.min(missing, available),
          precision, decision);
      if (transfer == null)
        continue;
      transfers.add(transfer);
      missing -= transfer.deposited();
      held += transfer.deposited();
      if (transfer.fx() != null) {
        cost += transfer.fx().reportingCharge();
        markup |= transfer.fx().quote().percent() > 0;
      }
    }
    double coveredUnits = 0;
    if (!security.getCurrency().equals(target.getCurrency()))
      markup |= rate != settlementRate(security.getCurrency(), target.getCurrency(), fill).doubleValue();
    try {
      var sized = affordable(state, security, targetBooking, units, lot, price, TransactionType.ACCUMULATE, fill,
          estimate, order, held);
      coveredUnits = sized.units();
      double finalAmount = coveredUnits * price + sized.cost().total();
      double finalRate = purchaseRate(state, booking.idSecurityaccount(), security.getCurrency(), target.getCurrency(),
          fill, finalAmount, mic(state, security));
      double close = settlementRate(security.getCurrency(), target.getCurrency(), fill);
      markup |= finalRate != close;
      cost += state.fx.reporting(finalAmount * Math.abs(finalRate - close), target.getCurrency(), fill);
    } catch (IllegalArgumentException e) {
      if (!"REPLAY_NOT_FUNDED".equals(e.getMessage()))
        throw e;
    }
    return new FundingPlan(target, List.copyOf(transfers), coveredUnits, cost, markup);
  }

  private static FundingPlan betterPlan(FundingPlan first, FundingPlan second, double required) {
    double firstCoverage = Math.min(required, first.available());
    double secondCoverage = Math.min(required, second.available());
    if (firstCoverage > secondCoverage + 1e-8)
      return first;
    if (secondCoverage > firstCoverage + 1e-8)
      return second;
    return first.cost() <= second.cost() ? first : second;
  }

  /**
   * Funds a compulsory custody charge using the same bounded internal transfers as an order.
   *
   * <p>
   * The shortfall is measured on the day of the charge, the way the overdraft guard reads it, but the transfers are
   * booked on the day before. The guard takes {@code min(balance before the day, lowest balance from the day on)}, so
   * money arriving on the day of the charge would raise only the second term and pay for nothing - the same reason
   * {@link #fund} dates its transfers on the decision day. A charge is never billed before the day after the opening,
   * so the day before never lies ahead of the opening deposits.
   * </p>
   *
   * @param state   the running replay
   * @param account the securities account whose custody charge is being funded
   * @param target  the cash account the charge is booked on
   * @param date    the day of the charge
   * @param amount  the charge, positive, in the currency of the target
   */
  void fundCustody(AlgoReplayState state, Integer account, Cashaccount target, LocalDate date, double amount) {
    double available = availableFor(state, account, target, date, target.getCurrency(), amount, null);
    if (available < amount)
      bringTogether(state, account, null, target, amount - Math.max(0, available),
          globalparametersService.getPrecisionForCurrency(target.getCurrency()), date.minusDays(1));
  }

  /**
   * Draws the missing amount out of the other cash accounts of the environment. Each contributor is asked for no more
   * than it holds, and the walk stops as soon as the shortfall is covered.
   *
   * <p>
   * When the contributors together cannot cover it they are emptied anyway. The order is then cut down to what arrived
   * rather than refused, and money left behind in another currency would only make the next line short as well - an
   * allocation that deploys the whole equity of an environment is short of its last line by whatever the market moved
   * between the day it was sized on and the day it fills.
   * </p>
   *
   * @param state        the running replay
   * @param account      the beneficiary securities account whose FX tariff applies
   * @param security     the instrument the money is being raised for
   * @param target       the account the money is brought together on
   * @param missing      what is still needed, in the currency of the target
   * @param precision    the number of decimals of the currency of the target
   * @param decisionDate the day of the transfers
   */
  private void bringTogether(AlgoReplayState state, Integer account, Security security, Cashaccount target,
      double missing, int precision, LocalDate decisionDate) {
    double remaining = missing;
    boolean moved = false;
    for (Cashaccount source : state.accounts.fundingSources(target, decisionDate)) {
      if (remaining <= 0 || state.fundingTransfers >= MAX_FUNDING_TRANSFERS) {
        break;
      }
      double contributable = transferable(state, account, source, target, decisionDate, remaining);
      if (!Double.isFinite(contributable) || contributable <= 0) {
        continue;
      }
      double deposited = executeTransfer(state, security,
          prepareTransfer(state, account, source, target, Math.min(remaining, contributable), precision, decisionDate));
      remaining -= deposited;
      moved |= deposited > 0;
    }
    if (moved) {
      state.accounts.rememberFundingTarget(target, decisionDate);
    }
  }

  /** Fully priced contribution: executing a chosen plan does not resolve a different tariff or amount. */
  private record Transfer(Cashaccount source, Cashaccount target, LocalDate date, double deposited, double withdrawn,
      Currencypair pair, Double rate, AlgoReplayFx.Conversion fx) {
  }

  /** Requotes every reduced deposit, so the persisted pair uses the tariff of its final amount. */
  private Transfer prepareTransfer(AlgoReplayState state, Integer account, Cashaccount source, Cashaccount target,
      double wanted, int precision, LocalDate date) {
    Double spendable = spendable(source, date);
    if (spendable == null || spendable <= 0)
      return null;
    Currencypair pair = source.getCurrency().equals(target.getCurrency()) ? null
        : currencypairs.findByFromCurrencyAndToCurrency(source.getCurrency(), target.getCurrency());
    Double close = pair == null ? (source.getCurrency().equals(target.getCurrency()) ? 1.0 : null)
        : currencypairs.getHistoricalClosePriceForDate(pair, date);
    if (close == null || !Double.isFinite(close) || close <= 0)
      return null;
    int sourcePrecision = globalparametersService.getPrecisionForCurrency(source.getCurrency());
    double deposited = ceil(wanted, precision);
    double step = Math.pow(10, -precision);
    while (deposited > 0) {
      var quote = pair == null ? null
          : state.fx.quote(account, source.getCurrency(), target.getCurrency(), pair, close, "TRANSFER",
              deposited / close, date, null, exactFxRates());
      double rate = pair == null ? 1 : AlgoReplayFx.effective(pair, close, target.getCurrency(), quote);
      double withdrawn = DataHelper.round(-(deposited / rate), sourcePrecision);
      if (-withdrawn <= spendable) {
        if (withdrawn >= 0)
          return null;
        var fx = pair == null ? null
            : state.fx.conversion(account, source.getCurrency(), target.getCurrency(), "TRANSFER", quote,
                Math.abs(deposited / rate - deposited / close), source.getCurrency(), date);
        return new Transfer(source, target, date, deposited, withdrawn, pair, rate, fx);
      }
      deposited = DataHelper.round(deposited - step, precision);
    }
    return null;
  }

  /** Executes exactly one previously priced contribution through the ordinary transfer writer. */
  private double executeTransfer(AlgoReplayState state, Security security, Transfer plan) {
    if (plan == null)
      return 0;
    LocalDateTime time = plan.date().atTime(FILL_HOUR, 0);
    Transaction withdrawal = new Transaction(plan.source(), plan.withdrawn(), TransactionType.WITHDRAWAL, time);
    Transaction deposit = new Transaction(plan.target(), plan.deposited(), TransactionType.DEPOSIT, time);
    for (Transaction side : List.of(withdrawal, deposit)) {
      side.setIdTenant(state.idTenant());
      side.setSimulationOpening(false);
      if (plan.pair() != null) {
        side.setIdCurrencypair(plan.pair().getId());
        side.setCurrencyExRate(plan.rate());
      }
    }
    transactions.throwWhenTransactionLimitReached(state.idTenant(), 2);
    transactions.updateCreateCashaccountTransfer(new CashAccountTransfer(withdrawal, deposit),
        new CashAccountTransfer());
    state.fundingTransfers++;
    String identity = state.run.getIdSimulationResult() + ":FX:" + plan.source().getId() + ":" + plan.target().getId()
        + ":" + plan.date();
    state.fx.committed(identity, plan.fx());
    state.write(AlgoEventType.FUNDING_TRANSFER, plan.date(), null, security == null ? null : security.getId(), null,
        null, plan.deposited(), plan.target().getCurrency(), RATIONALE_FUNDING_TRANSFER,
        state.fx.details(identity, plan.source().getName() + " (" + plan.source().getCurrency() + ") -> "
            + plan.target().getName() + " (" + plan.target().getCurrency() + ")"));
    return plan.deposited();
  }

  /** How much of an order its cash account can pay for, together with what that order costs. */
  private record Sized(double units, AlgoReplayCosts.Estimate cost) {
  }

  /**
   * How much of an order the account settling it can actually pay for. An allocation that deploys the whole equity of
   * an environment asks its last line for a little more than it has, because the plan sized it at the close of the day
   * it decided on while the order fills at the next one. Cutting the order down to what the money reaches for is what a
   * holder of the portfolio would do, and the next checkpoint corrects the remainder.
   *
   * <p>
   * The transaction cost is part of what has to be paid, and it depends on the size of the order that pays it, so the
   * answer is taken in bounded passes: cut the order against the fee of the full order, ask the fee of the cut order,
   * and repeat if necessary. Every cut is floored to the instrument's tradable lot, so the arithmetic here can never
   * approve a fractional share, a partial bond denomination, or an order the overdraft guard then refuses.
   * </p>
   *
   * @param state    the running replay
   * @param security the instrument being bought
   * @param booking  where the order is settled
   * @param units    the units the order asks for
   * @param price    the fill price, in the currency of the instrument
   * @param type     direction of the order, for the fee model
   * @param fillDate the day the order settles on
   * @param cost     what the full order costs, already resolved for {@code booking}
   * @return the units that can be settled, never more than were asked for, and their cost
   * @throws IllegalArgumentException {@code REPLAY_NOT_FUNDED} when not even one tradable lot is payable
   */
  private Sized affordable(AlgoReplayState state, Security security, AlgoReplayAccounts.Booking booking, double units,
      double lot, double price, TransactionType type, LocalDate fillDate, AlgoReplayCosts.Estimate cost,
      AlgoReplayCosts.Order order) {
    return affordable(state, security, booking, units, lot, price, type, fillDate, cost, order, null);
  }

  /** Virtual cash is used only while comparing funding plans; the same sizing checks run again after settlement. */
  private Sized affordable(AlgoReplayState state, Security security, AlgoReplayAccounts.Booking booking, double units,
      double lot, double price, TransactionType type, LocalDate fillDate, AlgoReplayCosts.Estimate cost,
      AlgoReplayCosts.Order order, Double virtualCash) {
    double candidate = units;
    for (int attempt = 0; attempt < 128 && candidate > 0 && Double.isFinite(candidate); attempt++) {
      double available = availableFor(state, booking.idSecurityaccount(), booking.cashaccount(), fillDate,
          security.getCurrency(), candidate * price + cost.total(), mic(state, security));
      if (virtualCash != null) {
        Double rate = purchaseRate(state, booking.idSecurityaccount(), security.getCurrency(),
            booking.cashaccount().getCurrency(), fillDate, candidate * price + cost.total(), mic(state, security));
        available = rate == null ? Double.NEGATIVE_INFINITY : virtualCash / rate;
      }
      if (available >= candidate * price + cost.total())
        return new Sized(candidate, cost);
      // This is a search heuristic only: no candidate is accepted until its own costs have been evaluated.
      double next = tradableUnits((available - cost.total()) / price, lot);
      if (!(next > 0) || next >= candidate)
        next = tradableUnits(candidate / 2, lot);
      if (!(next > 0) || next >= candidate)
        break;
      candidate = next;
      cost = order.estimate(booking.idSecurityaccount(), security, candidate, price, type, fillDate, state.equityNow(),
          currency(booking.cashaccount()));
    }
    throw new IllegalArgumentException("REPLAY_NOT_FUNDED");
  }

  /**
   * Smallest quantity a replay may trade. GT stores a directly held bond as nominal divided by 100, so its security
   * denomination has to be converted to that same unit basis. Other instruments trade in whole units.
   */
  static double tradableLot(AlgoReplayInputs.Instrument instrument) {
    if (instrument != null && instrument.directBond() && instrument.denomination() != null
        && instrument.denomination() > 0) {
      return instrument.denomination() / 100.0;
    }
    return 1.0;
  }

  /** Floors an absolute requested quantity to a complete tradable lot. */
  static double tradableUnits(double units, double lot) {
    if (!Double.isFinite(units) || !Double.isFinite(lot) || lot <= 0) {
      return 0;
    }
    double absolute = Math.abs(units);
    double lots = Math.floor(absolute / lot + 1e-10);
    return round(lots * lot);
  }

  /** Rounds away from zero, so a shortfall is never understated by the rounding meant to make it payable. */
  private static double ceil(double amount, int precision) {
    double factor = Math.pow(10, precision);
    return Math.ceil(amount * factor) / factor;
  }

  /**
   * What a cash account can spend on a day, read exactly the way the transaction write path reads it, so an account
   * chosen here is not refused there for a reason the choice did not consider. An account that may be overdrawn -
   * {@code borrowingRate} is set - has no limit at all.
   *
   * <p>
   * The answer is expressed in the currency of the instrument rather than in the one of the account, converted at the
   * rate the fill itself would carry, because that is the rate the order is booked at and because the accounts of an
   * environment are only comparable with one another that way. Where that rate does not exist the account answers that
   * it can settle nothing: the order would be refused there with {@code REPLAY_FX_UNAVAILABLE}, so any other account is
   * the better answer, and where there is none the choice hands the order back to it and the refusal is reported.
   * </p>
   *
   * @param cashaccount the account in question
   * @param date        the settlement day
   * @param currency    the currency of the instrument
   * @return the spendable amount in the currency of the instrument, infinite where the account may be overdrawn and
   *         negative infinite where it has no rate to settle that currency with on that day
   */
  double availableFor(AlgoReplayState state, Integer account, Cashaccount cashaccount, LocalDate date, String currency,
      double candidateAmount, String mic) {
    Double spendable = spendable(cashaccount, date);
    if (spendable == null) {
      return Double.POSITIVE_INFINITY;
    }
    Double rate = purchaseRate(state, account, currency, cashaccount.getCurrency(), date, candidateAmount, mic);
    return rate == null ? Double.NEGATIVE_INFINITY : spendable / rate;
  }

  /**
   * What a cash account can spend on a day, in its own currency, read exactly the way
   * {@code TransactionJpaRepositoryImpl.checkOverdraftAllowed} reads it - the balance before the day and the lowest it
   * reaches from the day on, whichever is smaller - so an account chosen here is not refused there for a reason the
   * choice did not consider.
   *
   * <p>
   * That the balance <em>before</em> the day is part of it is the reason a funding transfer has to be dated earlier
   * than the order it pays for: money arriving on the day of the order raises only the second term, and the smaller
   * first one still decides.
   * </p>
   *
   * @param cashaccount the account in question
   * @param date        the day in question
   * @return the spendable amount in the currency of the account, or null when the account may be overdrawn and the
   *         question therefore has no answer
   */
  private Double spendable(Cashaccount cashaccount, LocalDate date) {
    if (cashaccount.getBorrowingRate() != null) {
      return null;
    }
    Double before = cashHoldings.getBalanceBeforeDate(cashaccount.getId(), date);
    double balanceBefore = before != null ? before : 0.0;
    Double minFrom = cashHoldings.getMinBalanceFromDate(cashaccount.getId(), date);
    return Math.min(balanceBefore, minFrom != null ? minFrom : balanceBefore);
  }

  /**
   * The rate one amount in the currency of an instrument is settled at on an account in another currency, in the
   * direction {@link Transaction#validateSecurityGeneralCashaccountAmount} multiplies by.
   *
   * @param from the currency of the instrument
   * @param to   the currency of the settling cash account
   * @param date the settlement day
   * @return 1 when the currencies are the same, the closing rate of the pair, or null when no usable rate exists
   */
  private Double settlementRate(String from, String to, LocalDate date) {
    if (from.equals(to)) {
      return 1.0;
    }
    Currencypair required = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(from, to);
    Currencypair pair = currencypairs.findByFromCurrencyAndToCurrency(required.getFromCurrency(),
        required.getToCurrency());
    Double rate = pair == null ? null : currencypairs.getHistoricalClosePriceForDate(pair, date);
    return rate != null && Double.isFinite(rate) && rate > 0 ? rate : null;
  }

  /**
   * What one account could hand to another one, in the currency of the receiving account. The rate is the one of the
   * transfer direction, not of the order, and only that direction of the pair may exist - which is why an account that
   * cannot be priced towards the target answers that it can pass nothing rather than being asked to try.
   *
   * @param from the account the money would leave
   * @param to   the account it would arrive on
   * @param date the day of the transfer
   * @return the amount in the currency of {@code to}, or negative infinity when the transfer cannot be priced
   */
  double transferable(AlgoReplayState state, Integer account, Cashaccount from, Cashaccount to, LocalDate date,
      double candidateAmount) {
    Double close = settlementRate(from.getCurrency(), to.getCurrency(), date);
    Double spendable = spendable(from, date);
    if (close == null)
      return Double.NEGATIVE_INFINITY;
    if (spendable == null || spendable <= 0)
      return 0;
    if (from.getCurrency().equals(to.getCurrency()))
      return spendable;
    Currencypair pair = currencypairs.findByFromCurrencyAndToCurrency(from.getCurrency(), to.getCurrency());
    var quote = state.fx.quote(account, from.getCurrency(), to.getCurrency(), pair, close, "TRANSFER",
        Math.min(candidateAmount / close, spendable), date, null, exactFxRates());
    return spendable * AlgoReplayFx.effective(pair, close, to.getCurrency(), quote);
  }

  /** A bound adapter prevents concurrent runs from sharing their FX models through this singleton. */
  AlgoReplayAccounts.SettlementFunds funds(AlgoReplayState state, Security security) {
    return new AlgoReplayAccounts.SettlementFunds() {
      public double availableFor(Integer account, Cashaccount cash, LocalDate date, String currency, double amount) {
        return AlgoReplayBooking.this.availableFor(state, account, cash, date, currency, amount, mic(state, security));
      }

      public double transferable(Integer account, Cashaccount from, Cashaccount to, LocalDate date, double amount) {
        return AlgoReplayBooking.this.transferable(state, account, from, to, date, amount);
      }
    };
  }

  /** Mid conversion for tariff tiers and reporting, accepting either stored direction without live prices. */
  FxMarkupEngine.TierRate fxRates(AlgoReplayMarketData market) {
    return fxRates((pair, date) -> market.close(pair.getId(), date));
  }

  private FxMarkupEngine.TierRate exactFxRates() {
    return fxRates(currencypairs::getHistoricalClosePriceForDate);
  }

  private FxMarkupEngine.TierRate fxRates(java.util.function.BiFunction<Currencypair, LocalDate, Double> closes) {
    return (from, to, date) -> {
      if (from.equals(to))
        return 1.0;
      var pair = currencypairs.findByFromCurrencyAndToCurrency(from, to);
      boolean inverse = pair == null;
      if (inverse)
        pair = currencypairs.findByFromCurrencyAndToCurrency(to, from);
      Double rate = pair == null ? null : closes.apply(pair, date);
      return rate == null || !Double.isFinite(rate) || rate <= 0 ? null : inverse ? 1 / rate : rate;
    };
  }

  private static String mic(AlgoReplayState state, Security security) {
    var instrument = state.inputs.instruments().get(security.getId());
    return instrument == null ? null : instrument.mic();
  }

  private Double purchaseRate(AlgoReplayState state, Integer account, String instrument, String cash, LocalDate date,
      double amount, String mic) {
    Double close = settlementRate(instrument, cash, date);
    if (close == null || instrument.equals(cash))
      return close;
    var pair = currencypairs.findByFromCurrencyAndToCurrency(instrument, cash);
    var quote = state.fx.quote(account, cash, instrument, pair, close, "TRADE", Math.abs(amount) * close, date, mic,
        exactFxRates());
    return AlgoReplayFx.effective(pair, close, instrument, quote);
  }

  /** Costs are already attached, so tariff tiers see the entire net amount converted by the ledger. */
  private void applyExchangeRate(AlgoReplayState state, Transaction fill, Security security, Cashaccount cashaccount,
      LocalDate fillDate, double buyQuotation) {
    state.pendingFx = null;
    if (security.getCurrency().equals(cashaccount.getCurrency()))
      return;
    Currencypair required = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(security.getCurrency(),
        cashaccount.getCurrency());
    Currencypair pair = currencypairs.findByFromCurrencyAndToCurrency(required.getFromCurrency(),
        required.getToCurrency());
    Double close = pair == null ? null : currencypairs.getHistoricalClosePriceForDate(pair, fillDate);
    if (close == null || !Double.isFinite(close) || close <= 0)
      throw new IllegalArgumentException("REPLAY_FX_UNAVAILABLE");
    double net = fill.calculateSecurityTransactionAmountWithoutExchangeRate(buyQuotation);
    String pay = net < 0 ? cashaccount.getCurrency() : security.getCurrency();
    String receive = net < 0 ? security.getCurrency() : cashaccount.getCurrency();
    double midCash = DataBusinessHelper.divideMultiplyExchangeRate(net, close, security.getCurrency(),
        cashaccount.getCurrency());
    var input = state.inputs.instruments().get(security.getId());
    var quote = state.fx.quote(fill.getIdSecurityaccount(), pay, receive, pair, close, "TRADE",
        Math.abs(net < 0 ? midCash : net), fillDate, input == null ? null : input.mic(), exactFxRates());
    double rate = AlgoReplayFx.effective(pair, close, receive, quote);
    fill.setIdCurrencypair(pair.getIdSecuritycurrency());
    fill.setCurrencyExRate(rate);
    double actual = DataBusinessHelper.divideMultiplyExchangeRate(net, rate, security.getCurrency(),
        cashaccount.getCurrency());
    state.pendingFx = state.fx.conversion(fill.getIdSecurityaccount(), pay, receive, "TRADE", quote,
        Math.abs(actual - midCash), cashaccount.getCurrency(), fillDate);
  }

  private static double round(double units) {
    return DataHelper.round(units, BaseConstants.FID_MAX_FRACTION_DIGITS);
  }

  /** A refusal the replay raised itself is an NLS key; anything else is one key with the reason in the details. */
  static String rationaleOf(Exception e) {
    return isReplayRefusal(e) ? e.getMessage() : RATIONALE_FILL_REJECTED;
  }

  String detailsOf(Exception e, Locale locale) {
    if (isReplayRefusal(e)) {
      return null;
    }
    return e instanceof DataViolationException violation ? describe(violation, locale) : message(e);
  }

  /**
   * Whether the exception is one of the refusals the order construction raises itself. The refusal of the write path
   * carries no message at all - a {@link DataViolationException} keeps its reasons in its violation list - so the null
   * has to be answered here: the immutable set rejects a null probe with a {@code NullPointerException} instead of
   * returning false, and that one would leave the catch clause and end the whole run.
   *
   * @param e the refusal the order was rejected with
   * @return true when the message is one of the replay's own keys
   */
  private static boolean isReplayRefusal(Exception e) {
    return e.getMessage() != null && REPLAY_REFUSALS.contains(e.getMessage());
  }

  /**
   * Resolves the violations of a refused evaluation into readable text for the details of a trail row. Such a message
   * carries arguments the client cannot fill, so it is resolved here rather than kept as a key, in the language of the
   * user the run belongs to.
   *
   * @param e      the refusal raised by the rebalancing
   * @param locale the language of the user the run belongs to
   * @return the resolved messages, or the bare keys where a text is missing
   */
  String describe(DataViolationException e, Locale locale) {
    return e.getDataViolation().stream()
        .map(v -> messageSource.getMessage(v.getMessageKey(), v.getData(), v.getMessageKey(), locale))
        .collect(Collectors.joining(", "));
  }

  private static String message(Exception e) {
    return truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 500);
  }

  private static String truncate(String value, int length) {
    return value == null || value.length() <= length ? value : value.substring(0, length);
  }
}
