package grafioschtrader.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.AlgoMessageAlertJpaRepository;
import grafioschtrader.types.AlgoSignalKind;

/** Every signal type, including rebalancing and mean reversion, rechecks monitoring before persistence. */
class AlgoMonitoringRecorderTest {
  @ParameterizedTest
  @EnumSource(AlgoSignalKind.class)
  void checksCurrentEligibilityEvenForAPreviouslyActiveScope(AlgoSignalKind kind) {
    var alarms = mock(AlgoMessageAlertJpaRepository.class);
    var monitoring = mock(AlgoMonitoringService.class);
    var recorder = new AlgoAlarmRecorder(alarms);
    ReflectionTestUtils.setField(recorder, "monitoring", monitoring);
    AlgoStrategy strategy = new AlgoStrategy();
    strategy.setIdAlgoRuleStrategy(2);
    Security security = new Security();
    security.setIdSecuritycurrency(3);
    security.setName("Instrument");
    var scope = new AlgoAlertScope(1, strategy, security, "Hierarchy", true);
    var day = LocalDate.of(2026, 9, 21);
    recorder.record(scope, kind, (byte) 1, "details", day);
    verifyNoInteractions(alarms);
    when(monitoring.permitsAlert(1, 2)).thenReturn(true);
    recorder.record(scope, kind, (byte) 1, "details", day);
    verify(alarms).recordSignal(eq(1), eq(2), eq(3), eq(kind.getValue()), eq((byte) 1), anyString(), eq("details"),
        eq(day), any(), eq("Hierarchy"), eq("Instrument"));
  }
}
