package grafiosch.gtnet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNetMessageAttempt;

class GTNetMessageAttemptServiceTest {

  @Test
  void actualFailuresRemainRetryableAndObservable() {
    GTNetMessageAttempt attempt = new GTNetMessageAttempt(2, 7);

    attempt.recordFailedTry("Peer unreachable");

    assertThat(attempt.getAttemptStatus()).isEqualTo(GTNetMessageAttemptStatus.RETRYABLE_FAILURE);
    assertThat(attempt.getTryCount()).isEqualTo(1);
    assertThat(attempt.getLastAttemptTimestamp()).isNotNull();
    assertThat(attempt.getLastError()).isEqualTo("Peer unreachable");
    assertThat(GTNetMessageAttemptService.determineDeliveryStatus(List.of(attempt))).isEqualTo(DeliveryStatus.PENDING);
  }

  @Test
  void skippedHandshakeDoesNotCountAsATry() {
    GTNetMessageAttempt attempt = new GTNetMessageAttempt(2, 7);

    attempt.markWaitingForHandshake();

    assertThat(attempt.getAttemptStatus()).isEqualTo(GTNetMessageAttemptStatus.WAITING_HANDSHAKE);
    assertThat(attempt.getTryCount()).isZero();
    assertThat(attempt.getLastAttemptTimestamp()).isNull();
    assertThat(GTNetMessageAttemptService.determineDeliveryStatus(List.of(attempt))).isEqualTo(DeliveryStatus.PENDING);
  }

  @Test
  void allTerminalFailuresMakeTheMessageFailed() {
    GTNetMessageAttempt retired = new GTNetMessageAttempt(2, 7);
    retired.markPeerOutOfService();
    GTNetMessageAttempt expired = new GTNetMessageAttempt(3, 7);
    expired.markExpired();

    assertThat(GTNetMessageAttemptService.determineDeliveryStatus(List.of(retired, expired)))
        .isEqualTo(DeliveryStatus.FAILED);
  }

  @Test
  void anyDeliveredTargetKeepsTheAggregateDelivered() {
    GTNetMessageAttempt delivered = new GTNetMessageAttempt(2, 7);
    delivered.recordSuccessfulTry();
    GTNetMessageAttempt retired = new GTNetMessageAttempt(3, 7);
    retired.markPeerOutOfService();

    assertThat(GTNetMessageAttemptService.determineDeliveryStatus(List.of(delivered, retired)))
        .isEqualTo(DeliveryStatus.DELIVERED);
    assertThat(delivered.isHasSend()).isTrue();
    assertThat(delivered.getTryCount()).isEqualTo(1);
    assertThat(delivered.getSendTimestamp()).isNotNull();
  }

  @Test
  void aMessageWithoutTargetsRemainsPending() {
    assertThat(GTNetMessageAttemptService.determineDeliveryStatus(List.of())).isEqualTo(DeliveryStatus.PENDING);
  }
}
