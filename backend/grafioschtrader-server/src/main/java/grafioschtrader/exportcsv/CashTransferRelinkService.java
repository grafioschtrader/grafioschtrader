package grafioschtrader.exportcsv;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import grafiosch.entities.User;
import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.TransactionType;

/**
 * Restores the {@code connectedIdTransaction} pairing between the two sides of a cash account transfer that were
 * imported independently — typically through the per-securities-account CSV files of the transaction export, where a
 * cross-portfolio transfer is split over two files and two import runs.
 *
 * <p>
 * Candidates are the tenant's WITHDRAWAL/DEPOSIT transactions without a connected transaction. They are bucketed by
 * their transaction time truncated to minutes (the CSV wire format's precision; both sides of a transfer carry the same
 * time) and matched in two stages. Stage A pairs same-currency sides with equal absolute amounts. Stage B pairs the
 * remaining cross-currency sides whose implied exchange rate lies within
 * {@link GlobalConstants#ACCEPTESD_PERCENTAGE_EXCHANGE_RATE_DIFF} percent of the currency pair's close of that day —
 * the same tolerance the transfer validation enforces, so a combination outside it could never be saved anyway. A
 * transaction that already has a same-currency counterpart takes no part in stage B. <b>Only one-to-one unambiguous
 * matches are linked</b> — a candidate with several possible counterparts is skipped and reported.
 * </p>
 *
 * <p>
 * The time bucket matters for data exported from a simulation environment: simulated bookings carry only a date, so
 * every transfer of a day falls into the same bucket and the exchange rate check is what separates them.
 * </p>
 *
 * <p>
 * Linking goes through {@code TransactionJpaRepository.updateCreateCashaccountTransfer}, so the full transfer
 * validation runs, both directions are connected and the cash account deposit holdings are adjusted. For a
 * cross-currency pair the currency pair is found or created and set on both sides beforehand; without it the validation
 * would discard the derived exchange rate. Every pair is processed in its own transaction: a validation rejection
 * (overdraft, closed period, exchange rate too far from the close) fails only that pair and is reported in the result.
 * </p>
 */
@Service
public class CashTransferRelinkService {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  /**
   * Runs the relink over all unconnected withdrawal/deposit transactions of the authenticated tenant.
   *
   * @return the counts of examined, linked, ambiguous and rejected candidates
   */
  public CashTransferRelinkResult relinkCashTransfers() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<Transaction> candidates = transactionJpaRepository.findUnconnectedTransferCandidates(user.getIdTenant());
    MatchResult matchResult = findUnambiguousPairs(candidates, new CachedCloseLookup());
    int linked = 0;
    int failed = 0;
    for (TransferPair pair : matchResult.pairs()) {
      // No surrounding transaction: updateCreateCashaccountTransfer opens its own, so a validation
      // rejection rolls back only this pair.
      try {
        linkPair(pair, user);
        linked++;
      } catch (Exception ex) {
        failed++;
        log.warn("Cash transfer relink rejected for withdrawal {} / deposit {}: {}",
            pair.withdrawal().getIdTransaction(), pair.deposit().getIdTransaction(), ex.getMessage());
      }
    }
    return new CashTransferRelinkResult(candidates.size(), linked, matchResult.ambiguous(), failed);
  }

  private void linkPair(TransferPair pair, User user) {
    Transaction withdrawal = pair.withdrawal();
    Transaction deposit = pair.deposit();
    Currencypair required = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(
        withdrawal.getCashaccount().getCurrency(), deposit.getCashaccount().getCurrency());
    if (required != null) {
      // The transfer validation clears the exchange rate of a transaction without a currency pair.
      Integer idCurrencypair = currencypairJpaRepository
          .findOrCreateCurrencypairByFromAndToCurrency(required.getFromCurrency(), required.getToCurrency(), true)
          .getIdSecuritycurrency();
      withdrawal.setIdCurrencypair(idCurrencypair);
      deposit.setIdCurrencypair(idCurrencypair);
    }
    Double exchangeRate = deriveExchangeRate(withdrawal, deposit);
    withdrawal.setCurrencyExRate(exchangeRate);
    deposit.setCurrencyExRate(exchangeRate);
    CashAccountTransfer existing = new CashAccountTransfer(
        transactionJpaRepository.findByIdTransactionAndIdTenant(withdrawal.getIdTransaction(), user.getIdTenant()),
        transactionJpaRepository.findByIdTransactionAndIdTenant(deposit.getIdTransaction(), user.getIdTenant()));
    transactionJpaRepository.updateCreateCashaccountTransfer(new CashAccountTransfer(withdrawal, deposit), existing);
  }

  /**
   * Derives the exchange rate from the two amounts with the same currency pair orientation the CSV import uses for
   * same-file transfer pairs (accountTransferSamePortfolio), with the withdrawal's transaction cost taken out of the
   * rate. Returns null for a same-currency transfer.
   */
  private static Double deriveExchangeRate(Transaction withdrawal, Transaction deposit) {
    Currencypair currencypair = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(
        withdrawal.getCashaccount().getCurrency(), deposit.getCashaccount().getCurrency());
    if (currencypair == null) {
      return null;
    }
    double withdrawalNet = Math.abs(withdrawal.getCashaccountAmount())
        - (withdrawal.getTransactionCost() != null ? withdrawal.getTransactionCost() : 0.0);
    return deposit.getCashaccount().getCurrency().equals(currencypair.getFromCurrency())
        ? withdrawalNet / Math.abs(deposit.getCashaccountAmount())
        : Math.abs(deposit.getCashaccountAmount()) / withdrawalNet;
  }

  /**
   * Matches the candidates per minute bucket in two stages and accepts only one-to-one unambiguous withdrawal/deposit
   * pairs. Stage A matches same-currency sides by amount; stage B matches the cross-currency sides left over, checking
   * the implied exchange rate against the close delivered by the lookup. Transactions that took part in a stage A
   * match, paired or not, are excluded from stage B, so a same-currency counterpart always wins over a cross-currency
   * one. Package-private and static so the matching rules are testable without repositories.
   *
   * @param candidates the unconnected withdrawal/deposit transactions of one tenant
   * @param lookup     supplies the close of a currency pair on a date; a null close accepts any cross-currency amounts
   * @return the accepted pairs and the number of transactions that had a candidate but no unambiguous one
   */
  static MatchResult findUnambiguousPairs(List<Transaction> candidates, ExpectedRateLookup lookup) {
    Map<LocalDateTime, List<Transaction>> minuteBuckets = new LinkedHashMap<>();
    for (Transaction transaction : candidates) {
      minuteBuckets
          .computeIfAbsent(transaction.getTransactionTime().truncatedTo(ChronoUnit.MINUTES), _ -> new ArrayList<>())
          .add(transaction);
    }
    List<TransferPair> pairs = new ArrayList<>();
    Set<Integer> matchedSomething = new HashSet<>();
    for (List<Transaction> bucket : minuteBuckets.values()) {
      List<Transaction> withdrawals = bucket.stream().filter(t -> t.getTransactionType() == TransactionType.WITHDRAWAL)
          .toList();
      List<Transaction> deposits = bucket.stream().filter(t -> t.getTransactionType() == TransactionType.DEPOSIT)
          .toList();
      Set<Integer> sameCurrencyMatched = new HashSet<>();
      pairs.addAll(
          matchStage(withdrawals, deposits, CashTransferRelinkService::matchesSameCurrency, sameCurrencyMatched));
      matchedSomething.addAll(sameCurrencyMatched);
      pairs.addAll(matchStage(withoutIds(withdrawals, sameCurrencyMatched), withoutIds(deposits, sameCurrencyMatched),
          (w, d) -> matchesCrossCurrency(w, d, lookup), matchedSomething));
    }
    Set<Integer> pairedIds = new HashSet<>();
    pairs.forEach(pair -> {
      pairedIds.add(pair.withdrawal().getIdTransaction());
      pairedIds.add(pair.deposit().getIdTransaction());
    });
    int ambiguous = (int) matchedSomething.stream().filter(id -> !pairedIds.contains(id)).count();
    return new MatchResult(pairs, ambiguous);
  }

  /**
   * Runs one matching stage: a pair is accepted when the withdrawal has exactly one matching deposit and that deposit
   * has exactly one matching withdrawal. Every transaction with at least one match is added to {@code matchedIds}.
   */
  private static List<TransferPair> matchStage(List<Transaction> withdrawals, List<Transaction> deposits,
      BiPredicate<Transaction, Transaction> matcher, Set<Integer> matchedIds) {
    List<TransferPair> pairs = new ArrayList<>();
    for (Transaction withdrawal : withdrawals) {
      List<Transaction> matches = deposits.stream().filter(deposit -> matcher.test(withdrawal, deposit)).toList();
      if (!matches.isEmpty()) {
        matchedIds.add(withdrawal.getIdTransaction());
        matches.forEach(deposit -> matchedIds.add(deposit.getIdTransaction()));
      }
      if (matches.size() == 1) {
        Transaction deposit = matches.get(0);
        if (withdrawals.stream().filter(w -> matcher.test(w, deposit)).count() == 1) {
          pairs.add(new TransferPair(withdrawal, deposit));
        }
      }
    }
    return pairs;
  }

  private static List<Transaction> withoutIds(List<Transaction> transactions, Set<Integer> excludedIds) {
    return transactions.stream().filter(t -> !excludedIds.contains(t.getIdTransaction())).toList();
  }

  /**
   * A same-currency withdrawal matches a deposit on a different cash account with the same absolute amount.
   */
  private static boolean matchesSameCurrency(Transaction withdrawal, Transaction deposit) {
    if (isSameCashaccount(withdrawal, deposit)
        || !withdrawal.getCashaccount().getCurrency().equals(deposit.getCashaccount().getCurrency())) {
      return false;
    }
    // Below the smallest currency step, so equal amounts match and everything else does not.
    return Math.abs(Math.abs(withdrawal.getCashaccountAmount()) - Math.abs(deposit.getCashaccountAmount())) < 0.005;
  }

  /**
   * A cross-currency withdrawal matches a deposit on a different cash account when the exchange rate implied by the two
   * amounts deviates less than {@link GlobalConstants#ACCEPTESD_PERCENTAGE_EXCHANGE_RATE_DIFF} percent from the close
   * of that day. Without a known close any amount pair is plausible; the one-to-one guard is the safety net then.
   */
  private static boolean matchesCrossCurrency(Transaction withdrawal, Transaction deposit, ExpectedRateLookup lookup) {
    if (isSameCashaccount(withdrawal, deposit)
        || withdrawal.getCashaccount().getCurrency().equals(deposit.getCashaccount().getCurrency())
        || withdrawal.getCashaccountAmount() == 0.0 || deposit.getCashaccountAmount() == 0.0) {
      return false;
    }
    Currencypair required = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(
        withdrawal.getCashaccount().getCurrency(), deposit.getCashaccount().getCurrency());
    Double expected = lookup.closeFor(required.getFromCurrency(), required.getToCurrency(),
        withdrawal.getTransactionTime().toLocalDate());
    if (expected == null || expected == 0.0) {
      return true;
    }
    double implied = deriveExchangeRate(withdrawal, deposit);
    return Math.abs(expected - implied) / expected * 100.0 < GlobalConstants.ACCEPTESD_PERCENTAGE_EXCHANGE_RATE_DIFF;
  }

  private static boolean isSameCashaccount(Transaction withdrawal, Transaction deposit) {
    return withdrawal.getCashaccount().getIdSecuritycashAccount()
        .equals(deposit.getCashaccount().getIdSecuritycashAccount());
  }

  /** Supplies the close of the currency pair from/to on a date, or null when it is not known. */
  @FunctionalInterface
  interface ExpectedRateLookup {
    Double closeFor(String fromCurrency, String toCurrency, LocalDate date);
  }

  /**
   * Close lookup for one relink run. Falls back to the inverted close of the reverse pair when only that one exists,
   * and never creates a currency pair — creating happens only when a pair is actually linked.
   */
  private class CachedCloseLookup implements ExpectedRateLookup {
    private final Map<String, Double> cache = new HashMap<>();

    @Override
    public Double closeFor(String fromCurrency, String toCurrency, LocalDate date) {
      String key = fromCurrency + toCurrency + date;
      if (!cache.containsKey(key)) {
        cache.put(key, loadClose(fromCurrency, toCurrency, date));
      }
      return cache.get(key);
    }

    private Double loadClose(String fromCurrency, String toCurrency, LocalDate date) {
      Currencypair currencypair = currencypairJpaRepository.findByFromCurrencyAndToCurrency(fromCurrency, toCurrency);
      if (currencypair != null) {
        return currencypairJpaRepository.getClosePriceForDate(currencypair, date);
      }
      Currencypair reverse = currencypairJpaRepository.findByFromCurrencyAndToCurrency(toCurrency, fromCurrency);
      Double reverseClose = reverse == null ? null : currencypairJpaRepository.getClosePriceForDate(reverse, date);
      return reverseClose == null || reverseClose == 0.0 ? null : 1.0 / reverseClose;
    }
  }

  /** One accepted withdrawal/deposit pair. */
  record TransferPair(Transaction withdrawal, Transaction deposit) {
  }

  /** Outcome of the pure matching step: the accepted pairs and the count of ambiguous candidates. */
  record MatchResult(List<TransferPair> pairs, int ambiguous) {
  }
}
