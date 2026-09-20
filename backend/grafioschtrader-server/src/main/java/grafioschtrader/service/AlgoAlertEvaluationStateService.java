package grafioschtrader.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.algo.strategy.model.alerts.AlertConfigAdapter;
import grafioschtrader.entities.AlgoAlertEvaluationState;
import grafioschtrader.repository.AlgoAlertEvaluationStateJpaRepository;
import grafioschtrader.service.AlgoAlertSchedule.Window;

/** Short transactions claim work; evaluation commits under the same pair lock, fencing expired lease owners. */
@Service
public class AlgoAlertEvaluationStateService {
  public static class PartialEvaluationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PartialEvaluationException(String reason) {
      super(reason);
    }
  }

  private final AlgoAlertEvaluationStateJpaRepository repository;

  public AlgoAlertEvaluationStateService(AlgoAlertEvaluationStateJpaRepository repository) {
    this.repository = repository;
  }

  /** Atomically rechecks eligibility and reserves a pair before any provider request. */
  @Transactional
  public String claim(AlgoAlertScope scope, LocalDateTime now, int hours, Window window, boolean background,
      boolean intraday) {
    String fingerprint = AlertConfigAdapter.fingerprint(scope.strategy());
    repository.ensurePair(scope.idTenant(), scope.strategy().getIdAlgoRuleStrategy(),
        scope.security().getIdSecuritycurrency(), fingerprint);
    AlgoAlertEvaluationState state = lock(scope);
    if (state.getLeaseUntil() != null && state.getLeaseUntil().isAfter(now))
      return null;
    if (background && !AlgoAlertSchedule.due(state, fingerprint, now, hours, window))
      return null;
    if (intraday && fingerprint.equals(state.getConfigFingerprint()) && state.getQuoteTimestamp() != null
        && scope.security().getSTimestamp() != null
        && !scope.security().getSTimestamp().isAfter(state.getQuoteTimestamp()))
      return null;
    if (!fingerprint.equals(state.getConfigFingerprint())) {
      state.setLastAttempt(null);
      state.setLastSuccess(null);
      state.setQuoteTimestamp(null);
      state.setClosingAttempt(null);
    }
    state.setConfigFingerprint(fingerprint);
    if (background) {
      state.setLastAttempt(now);
      if (window.closing())
        state.setClosingAttempt(window.close());
    }
    String token = UUID.randomUUID().toString();
    state.setLeaseToken(token);
    state.setLeaseUntil(now.plusMinutes(35));
    state.setOutcome("RUNNING");
    state.setReason(null);
    return token;
  }

  /** Commits the outcome while holding the pair lock. External quote loading happens before this transaction. */
  @Transactional
  public void finish(AlgoAlertScope scope, String token, LocalDateTime now, LocalDateTime quote, String unavailable,
      Runnable evaluation) {
    AlgoAlertEvaluationState state = repository
        .lockPair(scope.idTenant(), scope.strategy().getIdAlgoRuleStrategy(), scope.security().getIdSecuritycurrency())
        .orElse(null);
    if (state == null || !token.equals(state.getLeaseToken()) || state.getLeaseUntil() == null
        || !state.getLeaseUntil().isAfter(now))
      return;
    if (unavailable != null) {
      recordUnavailable(state, unavailable);
    } else {
      String partial = null;
      try {
        evaluation.run();
      } catch (PartialEvaluationException e) {
        partial = e.getMessage();
      }
      // Other failures roll back crossing state and recorded alarms together.
      state.setLastSuccess(now);
      state.setQuoteTimestamp(quote);
      state.setOutcome(partial == null ? "EVALUATED" : "PARTIAL");
      state.setReason(partial);
      state.setLeaseToken(null);
      state.setLeaseUntil(null);
    }
  }

  /** Records diagnostics after the failed evaluation transaction has rolled back. */
  @Transactional
  public void failed(AlgoAlertScope scope, String token, String reason) {
    AlgoAlertEvaluationState state = lock(scope);
    if (token.equals(state.getLeaseToken()))
      recordUnavailable(state, reason);
  }

  private void recordUnavailable(AlgoAlertEvaluationState state, String reason) {
    state.setOutcome("UNAVAILABLE");
    String text = reason == null ? "Evaluation failed" : reason;
    state.setReason(text.substring(0, Math.min(text.length(), 1000)));
    state.setLeaseToken(null);
    state.setLeaseUntil(null);
  }

  private AlgoAlertEvaluationState lock(AlgoAlertScope scope) {
    return repository
        .lockPair(scope.idTenant(), scope.strategy().getIdAlgoRuleStrategy(), scope.security().getIdSecuritycurrency())
        .orElseThrow();
  }
}
