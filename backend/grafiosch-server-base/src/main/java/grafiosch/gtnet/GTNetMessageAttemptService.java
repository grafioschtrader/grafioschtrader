package grafiosch.gtnet;

import java.util.List;

import org.springframework.stereotype.Service;

import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessageAttempt;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.repository.GTNetMessageAttemptJpaRepository;
import grafiosch.repository.GTNetMessageJpaRepository;

/** Persists per-target delivery outcomes and derives the aggregate message status from all targets. */
@Service
public class GTNetMessageAttemptService {

  private final GTNetMessageAttemptJpaRepository attemptRepository;
  private final GTNetMessageJpaRepository messageRepository;

  public GTNetMessageAttemptService(GTNetMessageAttemptJpaRepository attemptRepository,
      GTNetMessageJpaRepository messageRepository) {
    this.attemptRepository = attemptRepository;
    this.messageRepository = messageRepository;
  }

  /** Recomputes the message status from every persisted target, including targets completed in earlier task runs. */
  public void refreshDeliveryStatus(GTNetMessage message) {
    List<GTNetMessageAttempt> attempts = attemptRepository.findByIdGtNetMessage(message.getIdGtNetMessage());
    DeliveryStatus newStatus = determineDeliveryStatus(attempts);
    if (message.getDeliveryStatus() != newStatus) {
      message.setDeliveryStatus(newStatus);
      messageRepository.save(message);
    }
  }

  public static DeliveryStatus determineDeliveryStatus(List<GTNetMessageAttempt> attempts) {
    if (attempts.isEmpty()) {
      return DeliveryStatus.PENDING;
    }
    if (attempts.stream().anyMatch(attempt -> attempt.getAttemptStatus().isDelivered())) {
      return DeliveryStatus.DELIVERED;
    }
    return attempts.stream().allMatch(attempt -> attempt.getAttemptStatus().isTerminal()) ? DeliveryStatus.FAILED
        : DeliveryStatus.PENDING;
  }

  /** Produces bounded administrator-facing diagnostics without storing an unbounded response body. */
  public static String describeFailure(SendResult result) {
    if (result == null) {
      return "No delivery result";
    }
    if (!result.serverReachable()) {
      return withDetail("Peer unreachable", result.errorMessage());
    }
    if (result.httpError()) {
      return withDetail("HTTP " + result.httpStatusCode(), result.errorMessage());
    }
    if (result.response() == null) {
      return "Peer returned an empty response";
    }
    if (result.isRefused()) {
      return "Peer refused the message: " + result.response().errorMsgCode;
    }
    return "Peer did not accept the message";
  }

  public static String describeFailure(Exception exception) {
    return withDetail(exception.getClass().getSimpleName(), exception.getMessage());
  }

  private static String withDetail(String summary, String detail) {
    if (detail == null || detail.isBlank()) {
      return summary;
    }
    return summary + ": " + detail.replace('\r', ' ').replace('\n', ' ');
  }
}
