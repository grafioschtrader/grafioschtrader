package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.alerts.AlertConfigAdapter;
import grafioschtrader.entities.*;
import grafioschtrader.repository.AlgoAlertEvaluationStateJpaRepository;
import grafioschtrader.service.AlgoAlertSchedule.Window;

class AlgoAlertEvaluationStateServiceTest {
  private final AlgoAlertEvaluationStateJpaRepository repository = mock(AlgoAlertEvaluationStateJpaRepository.class);
  private final AlgoAlertEvaluationStateService service = new AlgoAlertEvaluationStateService(repository);
  private final LocalDateTime now = LocalDateTime.parse("2026-09-04T12:00:00");
  private final Window window = new Window(null, null, false, null);
  private AlgoAlertScope scope;
  private AlgoAlertEvaluationState state;

  @BeforeEach
  void setup() {
    AlgoStrategy strategy = new AlgoStrategy();
    strategy.setIdAlgoRuleStrategy(2);
    strategy.setAlgoStrategyImplementations(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE);
    Security security = new Security();
    security.setIdSecuritycurrency(3);
    security.setSTimestamp(now);
    scope = new AlgoAlertScope(1, strategy, security, "test", true);
    state = new AlgoAlertEvaluationState();
    state.setConfigFingerprint(AlertConfigAdapter.fingerprint(strategy));
    when(repository.lockPair(1, 2, 3)).thenReturn(Optional.of(state));
  }

  @Test
  void claimSurvivesRestartAndCannotBeClaimedTwice() {
    String token = service.claim(scope, now, 4, window, true, false);
    assertThat(token).isNotBlank();
    assertThat(state.getLastAttempt()).isEqualTo(now);
    AlgoAlertEvaluationStateService restarted = new AlgoAlertEvaluationStateService(repository);
    assertThat(restarted.claim(scope, now.plusMinutes(1), 4, window, true, false)).isNull();
    assertThat(restarted.claim(scope, now.plusHours(4), 4, window, true, false)).isNotEqualTo(token);
    Runnable evaluator = mock(Runnable.class);
    service.finish(scope, token, now.plusHours(4), now, null, evaluator);
    verifyNoInteractions(evaluator);
  }

  @Test
  void failurePreservesSuccessfulObservationAndClearsLease() {
    state.setQuoteTimestamp(now.minusHours(5));
    String token = service.claim(scope, now, 4, window, true, false);
    Runnable evaluator = mock(Runnable.class);
    service.finish(scope, token, now, now, "stale quote", evaluator);
    assertThat(state.getOutcome()).isEqualTo("UNAVAILABLE");
    assertThat(state.getReason()).isEqualTo("stale quote");
    assertThat(state.getQuoteTimestamp()).isEqualTo(now.minusHours(5));
    assertThat(state.getLastSuccess()).isNull();
    assertThat(state.getLeaseUntil()).isNull();
    verifyNoInteractions(evaluator);
    assertThat(service.claim(scope, now.plusMinutes(5), 4, window, true, false)).isNull();
  }

  @Test
  void successRecordsQuoteAndSuppressesSameIntradayObservation() {
    String token = service.claim(scope, now, 4, window, true, false);
    service.finish(scope, token, now, now, null, () -> {
    });
    assertThat(state.getLastSuccess()).isEqualTo(now);
    assertThat(state.getQuoteTimestamp()).isEqualTo(now);
    assertThat(service.claim(scope, now.plusMinutes(1), 4, window, false, true)).isNull();
    scope.security().setSTimestamp(now.plusMinutes(1));
    assertThat(service.claim(scope, now.plusMinutes(1), 4, window, false, true)).isNotNull();
  }

  @Test
  void missingIndicatorDataIsRecordedWithoutSuccess() {
    String token = service.claim(scope, now, 4, window, true, false);
    assertThatThrownBy(() -> service.finish(scope, token, now, now, null, () -> {
      throw new IllegalStateException("insufficient history");
    })).isInstanceOf(IllegalStateException.class);
    service.failed(scope, token, "insufficient history");
    assertThat(state.getOutcome()).isEqualTo("UNAVAILABLE");
    assertThat(state.getReason()).isEqualTo("insufficient history");
    assertThat(state.getLastSuccess()).isNull();
  }
}
