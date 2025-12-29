package grafioschtrader.entities;

import java.util.Date;

import grafiosch.entities.BaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * Records individual transmission attempts for outgoing GTNet messages.
 *
 * <p>
 * When a message requires multiple transmission attempts (as defined by {@code repeatSendAsMany}
 * in GTNetModelHelper.GTNetMsgRequest, each attempt is recorded in this entity.
 * This provides an audit trail for debugging transmission failures and analyzing retry patterns.
 * </p>
 *
 * <p>
 * The retry mechanism is implemented via a background task that:
 * <ul>
 *   <li>Queries messages where attempts &lt; repeatSendAsMany and last attempt failed</li>
 *   <li>Applies exponential backoff based on attempt number</li>
 *   <li>Creates a new GTNetMessageAttempt record for each transmission attempt</li>
 *   <li>Marks message as permanently failed when attempts exhausted</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = GTNetMessageAttempt.TABNAME)
@Schema(description = """
    Records individual transmission attempts for outgoing GTNet messages. Each attempt captures
    the timestamp, success status, HTTP response code, and any error details. Enables retry
    tracking and failure analysis for messages configured with repeatSendAsMany > 1.""")
public class GTNetMessageAttempt extends BaseID<Integer> {

  public static final String TABNAME = "gt_net_message_attempt";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net_message_attempt")
  private Integer idGtNetMessageAttempt;

  @Schema(description = """
      Reference to the GTNetMessage being transmitted. Multiple attempts may exist for a single
      message when retries are configured. The message's repeatSendAsMany determines the maximum
      number of attempts allowed.""")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "id_gt_net_message", nullable = false)
  private GTNetMessage gtNetMessage;

  @Schema(description = """
      Sequential attempt number starting from 1. Used to track progress through the retry cycle
      and calculate exponential backoff delays. When attemptNumber reaches the message type's
      repeatSendAsMany limit, no further attempts are made.""")
  @Column(name = "attempt_number", nullable = false)
  private byte attemptNumber;

  @Schema(description = """
      UTC timestamp when this transmission attempt was made. Used for calculating retry delays
      and analyzing transmission patterns. All timestamps use UTC for consistent ordering.""")
  @Column(name = "attempt_timestamp", nullable = false)
  @Temporal(TemporalType.TIMESTAMP)
  private Date attemptTimestamp;

  @Schema(description = """
      Indicates whether this attempt successfully delivered the message. True if the remote
      endpoint acknowledged receipt (HTTP 2xx response). False for network errors, timeouts,
      or HTTP error responses.""")
  @Column(name = "success", nullable = false)
  private boolean success;

  @Schema(description = """
      HTTP status code returned by the remote endpoint, if any. Null for network-level failures
      where no HTTP response was received (e.g., connection timeout, DNS failure). Common values:
      200 (success), 401 (auth failure), 500 (remote error), 503 (unavailable).""")
  @Column(name = "http_status")
  private Integer httpStatus;

  @Schema(description = """
      Detailed error information for failed attempts. Contains exception messages, timeout details,
      or HTTP error response bodies. Truncated to 512 characters. Null for successful attempts.""")
  @Column(name = "error_message", length = 512)
  private String errorMessage;

  public GTNetMessageAttempt() {
  }

  public GTNetMessageAttempt(GTNetMessage gtNetMessage, byte attemptNumber, Date attemptTimestamp,
      boolean success, Integer httpStatus, String errorMessage) {
    this.gtNetMessage = gtNetMessage;
    this.attemptNumber = attemptNumber;
    this.attemptTimestamp = attemptTimestamp;
    this.success = success;
    this.httpStatus = httpStatus;
    setErrorMessage(errorMessage);
  }

  public Integer getIdGtNetMessageAttempt() {
    return idGtNetMessageAttempt;
  }

  public void setIdGtNetMessageAttempt(Integer idGtNetMessageAttempt) {
    this.idGtNetMessageAttempt = idGtNetMessageAttempt;
  }

  public GTNetMessage getGtNetMessage() {
    return gtNetMessage;
  }

  public void setGtNetMessage(GTNetMessage gtNetMessage) {
    this.gtNetMessage = gtNetMessage;
  }

  public byte getAttemptNumber() {
    return attemptNumber;
  }

  public void setAttemptNumber(byte attemptNumber) {
    this.attemptNumber = attemptNumber;
  }

  public Date getAttemptTimestamp() {
    return attemptTimestamp;
  }

  public void setAttemptTimestamp(Date attemptTimestamp) {
    this.attemptTimestamp = attemptTimestamp;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public Integer getHttpStatus() {
    return httpStatus;
  }

  public void setHttpStatus(Integer httpStatus) {
    this.httpStatus = httpStatus;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage != null && errorMessage.length() > 512
        ? errorMessage.substring(0, 512)
        : errorMessage;
  }

  @Override
  public Integer getId() {
    return idGtNetMessageAttempt;
  }

}
