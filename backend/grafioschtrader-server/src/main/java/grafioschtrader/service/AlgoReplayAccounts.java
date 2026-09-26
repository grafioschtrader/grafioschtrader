package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import grafioschtrader.common.SecurityaccountTradingEligibility;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Transaction;

/**
 * Decides which security account and which cash account a replayed order is booked on, and where the money of the
 * environment has to be brought together when no single account can pay one.
 *
 * <p>
 * A strategy says what to buy, never where to keep it, so a replay has to answer that question itself - and it has to
 * answer it the same way on every run, or two runs of the same configuration would produce different transactions. The
 * rules are therefore ordered and total:
 * </p>
 *
 * <ol>
 * <li>An instrument the environment already holds keeps the accounts its opening position uses. Splitting a position
 * across two accounts mid-run would change what the ledger says about it. This rule wins over a priority.</li>
 * <li>An instrument whose algo node - or, failing that, whose asset class node - names security accounts as priority is
 * booked on the first of them whose trading periods allow it on the settlement day, whether that account can pay for it
 * or not. The priority is the user's statement of where the instrument is cheapest to trade, so the missing money is
 * brought there from the other accounts rather than the order going where the money happens to be. Only when no
 * priority account may trade the instrument that day does the choice fall through to the next rule, and the caller is
 * told so through {@link #takePriorityFallback}.</li>
 * <li>A purchase goes where it can be paid for, among the accounts whose trading periods allow the instrument on the
 * settlement day and that were not closed before it: the first account holding enough on that day, asked in the order
 * the portfolios of the environment are offered it - those that can settle in the currency of the instrument first,
 * each by security account number - and within one portfolio the account in the currency of the instrument, then the
 * one in the currency of the environment, then the rest by account number. A sale needs no funding and takes the first
 * of that same order.</li>
 * <li>When nothing can pay, the account of that order with the most money on it, so the refusal of the write path
 * states what the order was actually short of instead of the whole price of an account that was never funded.</li>
 * </ol>
 *
 * <p>
 * The funding question is what the second rule turns on, and it is the reason an allocation does not stall. An
 * environment is copied with every portfolio of its source, while its money is usually put into one of them, so the
 * lowest numbered portfolio is very often the empty one. Choosing it would hand every purchase of the run to a cash
 * account that may not be overdrawn and holds nothing, and the write path would refuse all of them - a run that trades
 * nothing at all, for a reason that has nothing to do with the strategy under test. Settling in a currency other than
 * the one of the instrument needs no preparatory exchange: the purchase carries its own rate, and the transaction write
 * path converts inside the trade.
 * </p>
 *
 * <p>
 * One order is nevertheless paid by one account, while a rebalancing is sized against the equity of the whole
 * environment. An environment whose cash is spread over several accounts would therefore leave a line unfilled although
 * it holds enough for it, which is why {@link #settlementTarget} names the account such an order is brought together on
 * and {@link #fundingSources} the accounts the money may come from. Moving it is the caller's business; deciding where,
 * so that two runs of the same configuration decide the same, is this class's.
 * </p>
 */
public class AlgoReplayAccounts {

  /** Where one order is booked. */
  public record Booking(Integer idSecurityaccount, Cashaccount cashaccount) {
  }

  /**
   * What a cash account can settle on a day, and what it can pass to another one. Both are answered by the caller
   * rather than here, so the funds and the exchange rates are the ones the transaction write path checks against and
   * the two cannot disagree about whether an order is payable.
   */
  public interface SettlementFunds {
    /**
     * @param idSecurityaccount the candidate broker account whose FX tariff applies
     * @param candidateAmount   the order amount being priced in instrument currency
     * @param cashaccount       the account in question
     * @param date              the settlement day
     * @param currency          the currency of the instrument, which the answer is expressed in so that the accounts of
     *                          an environment are comparable with one another and with the price of the order
     * @return what the account can spend that day, expressed in {@code currency}; {@link Double#POSITIVE_INFINITY} when
     *         it may be overdrawn and the question therefore has no limit, {@link Double#NEGATIVE_INFINITY} when it
     *         cannot settle in that currency at all because the day has no exchange rate
     */
    double availableFor(Integer idSecurityaccount, Cashaccount cashaccount, LocalDate date, String currency,
        double candidateAmount);

    /**
     * What one account could hand to another one on a day. The direction matters twice over: the transfer is booked
     * with the rate of the pair {@code from -> to}, and only that direction of the pair may exist.
     *
     * @param idSecurityaccount the beneficiary broker account whose FX tariff applies
     * @param candidateAmount   the wanted deposit in target currency
     * @param from              the account the money would leave
     * @param to                the account it would arrive on
     * @param date              the day of the transfer
     * @return the amount, expressed in the currency of {@code to}; {@link Double#NEGATIVE_INFINITY} when the transfer
     *         cannot be priced that day and the two accounts are therefore not connectable
     */
    double transferable(Integer idSecurityaccount, Cashaccount from, Cashaccount to, LocalDate date,
        double candidateAmount);
  }

  private final List<Securityaccount> securityaccounts;
  private final List<Cashaccount> cashaccounts;
  private final String tenantCurrency;
  private final Map<Integer, Booking> established = new HashMap<>();
  /** Environment security account ids an instrument is to be traded at, best first, by instrument id. */
  private final Map<Integer, List<Integer>> priorities;
  /** Instruments whose priority accounts could not trade them and that the caller has not reported yet. */
  private final Set<Integer> priorityFallbacks = new HashSet<>();
  /** Instruments whose fallback was reported, so a run states it once per instrument rather than once per order. */
  private final Set<Integer> priorityFallbacksReported = new HashSet<>();
  /**
   * The accounts money was brought together on during {@link #fundedOn}. Within that day they are not asked to give it
   * away again, so the orders of one day cannot push the same money back and forth. The set holds for one decision day
   * only: a sale settles on an account and stays there, so a lock for the whole run would strand every later sale
   * proceeds on an account that was once a funding target, and the purchases of the following checkpoints would stay
   * unfunded although the environment holds the money.
   */
  private final Set<Integer> fundedTargets = new HashSet<>();
  /** The decision day {@link #fundedTargets} belongs to, or null before the first funding transfer. */
  private LocalDate fundedOn;

  /**
   * @param securityaccounts security accounts of the simulation environment
   * @param cashaccounts     cash accounts of the simulation environment
   * @param openingLedger    the transactions the environment opens with, which decide where a held instrument stays
   * @param tenantCurrency   currency of the environment, the fallback settlement currency
   * @param priorities       environment security account ids per instrument id, best first; instruments without a
   *                         priority are absent
   */
  public AlgoReplayAccounts(List<Securityaccount> securityaccounts, List<Cashaccount> cashaccounts,
      List<Transaction> openingLedger, String tenantCurrency, Map<Integer, List<Integer>> priorities) {
    this.priorities = priorities;
    this.securityaccounts = securityaccounts.stream().sorted(Comparator.comparing(Securityaccount::getId)).toList();
    this.cashaccounts = cashaccounts.stream().sorted(Comparator.comparing(Cashaccount::getId)).toList();
    this.tenantCurrency = tenantCurrency;
    Map<Integer, Cashaccount> cashById = new HashMap<>();
    this.cashaccounts.forEach(account -> cashById.put(account.getId(), account));
    for (Transaction transaction : openingLedger) {
      if (transaction.getSecurity() != null && transaction.getIdSecurityaccount() != null
          && transaction.getCashaccount() != null) {
        established.put(transaction.getSecurity().getId(), new Booking(transaction.getIdSecurityaccount(),
            cashById.getOrDefault(transaction.getCashaccount().getId(), transaction.getCashaccount())));
      }
    }
  }

  /**
   * Where to book one order. An instrument whose home is already established answers from there; everything else is
   * decided freshly, because the answer depends on what the candidate accounts hold on that day. Nothing is cached for
   * that reason - a choice becomes the instrument's home only through {@link #remember}, after an order on it was
   * actually accepted.
   *
   * @param security       the instrument to be traded
   * @param settlementDate the day the order settles on
   * @param requiredAmount what the order costs, in the currency of the instrument; zero or less for a sale, which
   *                       releases money rather than needing it
   * @param funds          whether a candidate account can settle the order on that day
   * @return where to book it
   * @throws IllegalArgumentException {@code REPLAY_NO_ACCOUNT} when no security account of the environment may trade
   *                                  the instrument on that day, or none that may belongs to a portfolio with a cash
   *                                  account
   */
  public Booking resolve(Security security, LocalDate settlementDate, double requiredAmount, SettlementFunds funds) {
    Booking known = established.get(security.getId());
    if (known != null) {
      return known;
    }
    Securityaccount preferred = priorityAccount(security, settlementDate);
    if (preferred != null) {
      Booking booking = bestOf(List.of(preferred), security, settlementDate, requiredAmount, funds);
      if (booking != null) {
        return booking;
      }
    }
    return choose(security, settlementDate, requiredAmount, funds);
  }

  /**
   * Whether the priority accounts of an instrument could not trade it and the automatic choice was used instead, asked
   * once after {@link #resolve}. Answers true only the first time per instrument, so the trail states it once.
   *
   * @param security the instrument just resolved
   * @return true when the fallback happened and has not been reported yet
   */
  public boolean takePriorityFallback(Security security) {
    return priorityFallbacks.remove(security.getId()) && priorityFallbacksReported.add(security.getId());
  }

  /**
   * The first priority account of the instrument that may trade it on the day, or null when it has no priority or none
   * of them may. The latter is remembered for {@link #takePriorityFallback}.
   */
  private Securityaccount priorityAccount(Security security, LocalDate settlementDate) {
    List<Integer> order = priorities.get(security.getId());
    if (order == null || order.isEmpty()) {
      return null;
    }
    for (Integer id : order) {
      Securityaccount account = securityaccounts.stream().filter(sa -> sa.getId().equals(id)).findFirst().orElse(null);
      if (account != null && tradable(account, security, settlementDate)) {
        return account;
      }
    }
    priorityFallbacks.add(security.getId());
    return null;
  }

  /** Remembers where an instrument was booked, so every later order of the run follows the first one. */
  public void remember(Security security, Booking booking) {
    established.put(security.getId(), booking);
  }

  /**
   * Whether the instrument already has a home. An established booking is not reconsidered, so a shortfall of it has to
   * be brought to the account of its first accepted fill rather than to the one the topology would prefer.
   *
   * @param security the instrument in question
   * @return true when an accepted fill has pinned its accounts
   */
  public boolean isEstablished(Security security) {
    return established.containsKey(security.getId());
  }

  /**
   * The account a shortfall of this booking is brought together on: the account in the currency of the environment of
   * the portfolio the booking settles in, or the account of the booking itself when that portfolio has none.
   *
   * <p>
   * The portfolio is the one the booking already names, so the security account and the cash account of the order stay
   * in the same portfolio. Within it the currency of the environment is preferred, because that is the direction the
   * exchange rates of a tenant exist in: they are kept as <em>foreign currency to tenant currency</em>, so a transfer
   * into the account of the environment currency can be priced while the opposite one often cannot.
   * </p>
   *
   * @param booking where the order would be settled without any funding
   * @return the account to bring the money together on
   */
  public Cashaccount settlementTarget(Booking booking) {
    List<Cashaccount> portfolioAccounts = ofPortfolio(booking.cashaccount().getPortfolio().getId());
    Cashaccount inEnvironmentCurrency = firstInCurrency(portfolioAccounts, tenantCurrency);
    return inEnvironmentCurrency != null ? inEnvironmentCurrency : booking.cashaccount();
  }

  /**
   * The accounts a funding transfer may draw on, in a fixed order: those already in the currency of the target first,
   * because such a transfer needs no exchange rate at all, then those of the portfolio of the target, then the rest,
   * each group by account number. Money may cross a portfolio boundary - the environment is one pot - but staying
   * inside one portfolio is the smaller change to make to it.
   *
   * <p>
   * The order is deliberately structural rather than by amount. Comparing two converted balances would decide the order
   * of two transfers on a floating point difference, and a run has to be reproducible.
   * </p>
   *
   * @param target the account the money is to arrive on
   * @param date   the decision day of the transfers
   * @return the possible contributors, best first, never containing the target or an account already funded that day
   */
  public List<Cashaccount> fundingSources(Cashaccount target, LocalDate date) {
    Integer idPortfolio = target.getPortfolio().getId();
    Set<Integer> funded = date.equals(fundedOn) ? fundedTargets : Set.of();
    return cashaccounts.stream().filter(candidate -> !candidate.getId().equals(target.getId()))
        .filter(candidate -> !funded.contains(candidate.getId()))
        .sorted(Comparator.comparing((Cashaccount candidate) -> !candidate.getCurrency().equals(target.getCurrency()))
            .thenComparing(candidate -> !candidate.getPortfolio().getId().equals(idPortfolio)))
        .toList();
  }

  /**
   * Records that money was brought together on this account, so no later order of the same day drains it again.
   *
   * @param target the account the money arrived on
   * @param date   the decision day of the transfers; a new day discards the targets of the previous one
   */
  public void rememberFundingTarget(Cashaccount target, LocalDate date) {
    if (!date.equals(fundedOn)) {
      fundedTargets.clear();
      fundedOn = date;
    }
    fundedTargets.add(target.getId());
  }

  private Booking choose(Security security, LocalDate settlementDate, double requiredAmount, SettlementFunds funds) {
    Booking booking = bestOf(candidateAccounts(security, settlementDate), security, settlementDate, requiredAmount,
        funds);
    if (booking == null) {
      throw new IllegalArgumentException("REPLAY_NO_ACCOUNT");
    }
    return booking;
  }

  /**
   * The first cash account of the given security accounts, in their order, that can pay the order, else the one of them
   * holding the most, so a refusal states the true shortfall.
   *
   * @return the booking, or null when none of the accounts has a cash account in its portfolio
   */
  private Booking bestOf(List<Securityaccount> candidates, Security security, LocalDate settlementDate,
      double requiredAmount, SettlementFunds funds) {
    Booking richest = null;
    double richestFunds = Double.NEGATIVE_INFINITY;
    for (Securityaccount candidate : candidates) {
      for (Cashaccount cash : settlementOrder(candidate, security)) {
        double available = funds.availableFor(candidate.getId(), cash, settlementDate, security.getCurrency(),
            requiredAmount);
        if (available >= requiredAmount) {
          return new Booking(candidate.getId(), cash);
        }
        if (richest == null || available > richestFunds) {
          richest = new Booking(candidate.getId(), cash);
          richestFunds = available;
        }
      }
    }
    return richest;
  }

  /**
   * The security accounts in the order they are offered the order: those whose portfolio can settle in the currency of
   * the instrument first, each group by account number, so the choice is the same on every run. An account that may not
   * trade the instrument on the day is not offered at all, because the transaction write path would refuse it.
   */
  private List<Securityaccount> candidateAccounts(Security security, LocalDate settlementDate) {
    return securityaccounts.stream().filter(account -> tradable(account, security, settlementDate))
        .sorted(Comparator.comparing((Securityaccount account) -> !hasCurrency(account, security.getCurrency())))
        .toList();
  }

  private static boolean tradable(Securityaccount account, Security security, LocalDate settlementDate) {
    return security.getAssetClass() == null
        || SecurityaccountTradingEligibility.tradable(account, security.getAssetClass(), settlementDate);
  }

  /**
   * The cash accounts of the portfolio of one security account, in the order an order is offered them: the one in the
   * currency of the instrument, then the one in the currency of the environment, then the rest by account number.
   */
  private List<Cashaccount> settlementOrder(Securityaccount account, Security security) {
    List<Cashaccount> portfolioAccounts = ofPortfolio(account.getPortfolio().getId());
    LinkedHashSet<Cashaccount> ordered = new LinkedHashSet<>();
    add(ordered, firstInCurrency(portfolioAccounts, security.getCurrency()));
    add(ordered, firstInCurrency(portfolioAccounts, tenantCurrency));
    ordered.addAll(portfolioAccounts);
    return new ArrayList<>(ordered);
  }

  private List<Cashaccount> ofPortfolio(Integer idPortfolio) {
    return cashaccounts.stream().filter(candidate -> candidate.getPortfolio().getId().equals(idPortfolio)).toList();
  }

  private static void add(LinkedHashSet<Cashaccount> ordered, Cashaccount account) {
    if (account != null) {
      ordered.add(account);
    }
  }

  private static Cashaccount firstInCurrency(List<Cashaccount> candidates, String currency) {
    return candidates.stream().filter(candidate -> candidate.getCurrency().equals(currency)).findFirst().orElse(null);
  }

  private boolean hasCurrency(Securityaccount account, String currency) {
    return cashaccounts.stream()
        .anyMatch(candidate -> candidate.getPortfolio().getId().equals(account.getPortfolio().getId())
            && candidate.getCurrency().equals(currency));
  }

  /** Trading periods and active dates are checked by the transaction write path; this is only the earliest guess. */
  public boolean isBookable(LocalDate date, Security security) {
    return (security.getActiveFromDate() == null || !date.isBefore(security.getActiveFromDate()))
        && (security.getActiveToDate() == null || !date.isAfter(security.getActiveToDate()));
  }
}
