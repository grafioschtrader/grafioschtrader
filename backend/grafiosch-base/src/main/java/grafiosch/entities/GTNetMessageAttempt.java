package grafiosch.entities;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.gtnet.GTNetMessageAttemptStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tracks per-target delivery status for future-oriented GTNet broadcast messages.
 *
 * <p>
 * This entity records which remote GTNet instances should receive a specific broadcast message (such as maintenance
 * announcements or operation discontinuation notices) and whether the message has been successfully delivered to each
 * target.
 * </p>
 *
 * <p>
 * The delivery mechanism is handled by the GTNetFutureMessageDeliveryTask background job which:
 * <ul>
 * <li>Runs periodically (every 5 hours) and immediately when future-oriented messages are sent</li>
 * <li>Creates entries for new communication partners whose handshake completed after the message</li>
 * <li>Delivers pending messages (hasSend = false) to their targets</li>
 * <li>Handles cancellation logic for maintenance/discontinuation cancellations</li>
 * <li>Marks unresolved entries expired when message dates are in the past</li>
 * </ul>
 * </p>
 *
 * <p>
 * Message types tracked: GT_NET_MAINTENANCE_ALL_C (24), GT_NET_OPERATION_DISCONTINUED_ALL_C (25),
 * GT_NET_MAINTENANCE_CANCEL_ALL_C (26), GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C (27)
 * </p>
 */
@Entity
@Table(name = GTNetMessageAttempt.TABNAME)
@Schema(description = """
    Tracks per-target delivery status for future-oriented GTNet broadcast messages such as
    maintenance announcements and operation discontinuation notices. Each entry represents
    a specific message-to-target delivery, including retryable and terminal outcomes.""")
public class GTNetMessageAttempt extends BaseID<Integer> {

  private static final int MAX_ERROR_LENGTH = 1000;

  public static final String TABNAME = "gt_net_message_attempt";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net_message_attempt")
  private Integer idGtNetMessageAttempt;

  @Schema(description = """
      Foreign key reference to the target GTNet instance that should receive this message.
      Identifies which remote server is the intended recipient of the broadcast message.""")
  @Column(name = "id_gt_net", nullable = false)
  private Integer idGtNet;

  @Schema(description = """
      Foreign key reference to the GTNetMessage being delivered. Multiple GTNetMessageAttempt
      entries may reference the same message, one for each target that should receive it.
      Note: No JPA mapping is used since few messages map to many targets.""")
  @Column(name = "id_gt_net_message", nullable = false)
  private Integer idGtNetMessage;

  @Schema(description = """
      Indicates whether this message has been successfully delivered to the target.
      False when the entry is created or if delivery failed. True once delivery succeeds.""")
  @Column(name = "has_send", nullable = false)
  private boolean hasSend = false;

  @Schema(description = """
      UTC timestamp when the message was successfully delivered to the target.
      Null until delivery succeeds. Useful for tracking delivery timing and future analysis.""")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  @Column(name = "send_timestamp")
  private LocalDateTime sendTimestamp;

  @Schema(description = "Current per-target delivery outcome.")
  @Column(name = "attempt_status", nullable = false)
  private byte attemptStatus = GTNetMessageAttemptStatus.QUEUED.getValue();

  @Schema(description = "Number of actual HTTP transmissions, including the successful one when delivered.")
  @Column(name = "try_count", nullable = false)
  private int tryCount;

  @Schema(description = "UTC timestamp of the most recent actual HTTP transmission.")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  @Column(name = "last_attempt_timestamp")
  private LocalDateTime lastAttemptTimestamp;

  @Schema(description = "Sanitized diagnostic text from the most recent failed transmission.")
  @Column(name = "last_error", length = MAX_ERROR_LENGTH)
  private String lastError;

  public GTNetMessageAttempt() {
  }

  public GTNetMessageAttempt(Integer idGtNet, Integer idGtNetMessage) {
    this.idGtNet = idGtNet;
    this.idGtNetMessage = idGtNetMessage;
    this.hasSend = false;
  }

  public GTNetMessageAttempt(Integer idGtNet, Integer idGtNetMessage, boolean hasSend, LocalDateTime sendTimestamp) {
    this.idGtNet = idGtNet;
    this.idGtNetMessage = idGtNetMessage;
    this.hasSend = hasSend;
    this.sendTimestamp = sendTimestamp;
    this.attemptStatus = hasSend ? GTNetMessageAttemptStatus.DELIVERED.getValue()
        : GTNetMessageAttemptStatus.QUEUED.getValue();
    this.tryCount = hasSend ? 1 : 0;
    this.lastAttemptTimestamp = hasSend ? sendTimestamp : null;
  }

  @Override
  public Integer getId() {
    return idGtNetMessageAttempt;
  }

  public Integer getIdGtNetMessageAttempt() {
    return idGtNetMessageAttempt;
  }

  public void setIdGtNetMessageAttempt(Integer idGtNetMessageAttempt) {
    this.idGtNetMessageAttempt = idGtNetMessageAttempt;
  }

  public Integer getIdGtNet() {
    return idGtNet;
  }

  public void setIdGtNet(Integer idGtNet) {
    this.idGtNet = idGtNet;
  }

  public Integer getIdGtNetMessage() {
    return idGtNetMessage;
  }

  public void setIdGtNetMessage(Integer idGtNetMessage) {
    this.idGtNetMessage = idGtNetMessage;
  }

  public boolean isHasSend() {
    return hasSend;
  }

  public void setHasSend(boolean hasSend) {
    this.hasSend = hasSend;
  }

  public LocalDateTime getSendTimestamp() {
    return sendTimestamp;
  }

  public void setSendTimestamp(LocalDateTime sendTimestamp) {
    this.sendTimestamp = sendTimestamp;
  }

  public GTNetMessageAttemptStatus getAttemptStatus() {
    return GTNetMessageAttemptStatus.getByValue(attemptStatus);
  }

  public void setAttemptStatus(GTNetMessageAttemptStatus attemptStatus) {
    this.attemptStatus = attemptStatus == null ? GTNetMessageAttemptStatus.QUEUED.getValue() : attemptStatus.getValue();
  }

  public int getTryCount() {
    return tryCount;
  }

  public void setTryCount(int tryCount) {
    this.tryCount = tryCount;
  }

  public LocalDateTime getLastAttemptTimestamp() {
    return lastAttemptTimestamp;
  }

  public void setLastAttemptTimestamp(LocalDateTime lastAttemptTimestamp) {
    this.lastAttemptTimestamp = lastAttemptTimestamp;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = truncate(lastError);
  }

  /**
   * Marks this entry as successfully delivered and records the timestamp.
   */
  public void markAsSent() {
    recordSuccessfulTry();
  }

  /** Records an actual transmission which the peer accepted. */
  public void recordSuccessfulTry() {
    LocalDateTime now = LocalDateTime.now();
    this.tryCount++;
    this.lastAttemptTimestamp = now;
    this.hasSend = true;
    this.sendTimestamp = now;
    this.attemptStatus = GTNetMessageAttemptStatus.DELIVERED.getValue();
    this.lastError = null;
  }

  /** Records an actual transmission which may be retried later. */
  public void recordFailedTry(String error) {
    this.tryCount++;
    this.lastAttemptTimestamp = LocalDateTime.now();
    this.hasSend = false;
    this.attemptStatus = GTNetMessageAttemptStatus.RETRYABLE_FAILURE.getValue();
    this.lastError = truncate(error);
  }

  /** Records that no transmission can start until the handshake is complete. */
  public void markWaitingForHandshake() {
    this.attemptStatus = GTNetMessageAttemptStatus.WAITING_HANDSHAKE.getValue();
  }

  /** Records that the target has permanently left GTNet. */
  public void markPeerOutOfService() {
    this.attemptStatus = GTNetMessageAttemptStatus.PEER_OUT_OF_SERVICE.getValue();
  }

  /** Records that the announcement expired before this target accepted it. */
  public void markExpired() {
    if (!getAttemptStatus().isTerminal()) {
      this.attemptStatus = GTNetMessageAttemptStatus.EXPIRED.getValue();
    }
  }

  private static String truncate(String value) {
    if (value == null || value.length() <= MAX_ERROR_LENGTH) {
      return value;
    }
    return value.substring(0, MAX_ERROR_LENGTH);
  }

}
