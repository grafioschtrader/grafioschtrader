package grafioschtrader.exportcsv;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import grafiosch.entities.User;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.TransactionType;

/**
 * Restores the {@code connectedIdTransaction} pairing between the two sides of a cash account transfer that were
 * imported independently — typically through the per-securities-account CSV files of the transaction export, where a
 * cross-portfolio transfer is split over two files and two import runs.
 *
 * <p>
 * Candidates are the tenant's WITHDRAWAL/DEPOSIT transactions without a connected transaction. They are bucketed by
 * their transaction time truncated to minutes (the CSV wire format's precision; both sides of a transfer carry the
 * same time) and matched pairwise: different cash account, and for a same-currency transfer equal absolute amounts.
 * <b>Only one-to-one unambiguous matches are linked</b> — a candidate with several possible counterparts is skipped
 * and reported. Linking goes through
 * {@code TransactionJpaRepository.updateCreateCashaccountTransfer}, so the full transfer validation runs, the
 * currency pair is found or created, both directions are connected and the cash account deposit holdings are
 * adjusted. Every pair is processed in its own transaction: a validation rejection (overdraft, closed period) fails
 * only that pair and is reported in the result.
 * </p>
 */
@Service
public class CashTransferRelinkService {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  /**
   * Runs the relink over all unconnected withdrawal/deposit transactions of the authenticated tenant.
   *
   * @return the counts of examined, linked, ambiguous and rejected candidates
   */
  public CashTransferRelinkResult relinkCashTransfers() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<Transaction> candidates = transactionJpaRepository.findUnconnectedTransferCandidates(user.getIdTenant());
    MatchResult matchResult = findUnambiguousPairs(candidates);
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
  private Double deriveExchangeRate(Transaction withdrawal, Transaction deposit) {
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
   * Matches the candidates pairwise per minute bucket and accepts only one-to-one unambiguous withdrawal/deposit
   * pairs. Package-private and static so the matching rules are testable without repositories.
   */
  static MatchResult findUnambiguousPairs(List<Transaction> candidates) {
    Map<LocalDateTime, List<Transaction>> minuteBuckets = new LinkedHashMap<>();
    for (Transaction transaction : candidates) {
      minuteBuckets.computeIfAbsent(transaction.getTransactionTime().truncatedTo(ChronoUnit.MINUTES),
          _ -> new ArrayList<>()).add(transaction);
    }
    List<TransferPair> pairs = new ArrayList<>();
    Set<Integer> matchedSomething = new HashSet<>();
    for (List<Transaction> bucket : minuteBuckets.values()) {
      List<Transaction> withdrawals = bucket.stream()
          .filter(t -> t.getTransactionType() == TransactionType.WITHDRAWAL).toList();
      List<Transaction> deposits = bucket.stream().filter(t -> t.getTransactionType() == TransactionType.DEPOSIT)
          .toList();
      for (Transaction withdrawal : withdrawals) {
        List<Transaction> matches = deposits.stream().filter(deposit -> matches(withdrawal, deposit)).toList();
        if (!matches.isEmpty()) {
          matchedSomething.add(withdrawal.getIdTransaction());
          matches.forEach(deposit -> matchedSomething.add(deposit.getIdTransaction()));
        }
        if (matches.size() == 1) {
          Transaction deposit = matches.get(0);
          boolean depositUnambiguous = withdrawals.stream().filter(w -> matches(w, deposit)).count() == 1;
          if (depositUnambiguous) {
            pairs.add(new TransferPair(withdrawal, deposit));
          }
        }
      }
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
   * A withdrawal matches a deposit when it lives in a different cash account and — for a same-currency transfer —
   * carries the same absolute amount. Cross-currency amounts define their own exchange rate, so any amount pair is
   * plausible; the one-to-one guard is the safety net there.
   */
  private static boolean matches(Transaction withdrawal, Transaction deposit) {
    if (withdrawal.getCashaccount().getIdSecuritycashAccount()
        .equals(deposit.getCashaccount().getIdSecuritycashAccount())) {
      return false;
    }
    if (withdrawal.getCashaccount().getCurrency().equals(deposit.getCashaccount().getCurrency())) {
      // Below the smallest currency step, so equal amounts match and everything else does not.
      return Math.abs(Math.abs(withdrawal.getCashaccountAmount()) - Math.abs(deposit.getCashaccountAmount())) < 0.005;
    }
    return withdrawal.getCashaccountAmount() != 0.0 && deposit.getCashaccountAmount() != 0.0;
  }

  /** One accepted withdrawal/deposit pair. */
  record TransferPair(Transaction withdrawal, Transaction deposit) {
  }

  /** Outcome of the pure matching step: the accepted pairs and the count of ambiguous candidates. */
  record MatchResult(List<TransferPair> pairs, int ambiguous) {
  }
}
