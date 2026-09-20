package grafioschtrader.service;

import java.time.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.AlgoMessageAlert;
import grafioschtrader.repository.AlgoMessageAlertJpaRepository;
import grafioschtrader.types.AlgoSignalKind;

/** Records signals in the evaluation transaction. Delivery is independent of observing another crossing. */
@Service
public class AlgoAlarmRecorder {
  private final AlgoMessageAlertJpaRepository repository;

  public AlgoAlarmRecorder(AlgoMessageAlertJpaRepository repository) {
    this.repository = repository;
  }

  /** Every signal that is not one profit taking tranche, and therefore carries no tranche in its identity. */
  @Transactional
  public void record(AlgoAlertScope scope, AlgoSignalKind kind, byte direction, String details, LocalDate day) {
    record(scope, kind, direction, AlgoMessageAlert.NO_TRANCHE, details, day);
  }

  /**
   * Daily uniqueness is enforced by an upsert, without poisoning the surrounding transaction on duplicates.
   *
   * @param scope      the strategy and instrument the signal was produced for
   * @param kind       what kind of signal it is; one component of the daily identity
   * @param direction  1 for a long-side signal, -1 for a short-side one, 0 where direction has no meaning
   * @param trancheKey the profit taking tranche, or {@link AlgoMessageAlert#NO_TRANCHE} for every other signal
   * @param details    the human readable explanation stored with the alarm
   * @param day        the day the signal belongs to; the same identity on the same day is one alarm
   */
  @Transactional
  public void record(AlgoAlertScope scope, AlgoSignalKind kind, byte direction, String trancheKey, String details,
      LocalDate day) {
    repository.recordSignal(scope.idTenant(), scope.strategy().getIdAlgoRuleStrategy(),
        scope.security().getIdSecuritycurrency(), kind.getValue(), direction, trancheKey, details, day,
        LocalDateTime.now(ZoneOffset.UTC), scope.contextName(), scope.security().getName());
  }
}
