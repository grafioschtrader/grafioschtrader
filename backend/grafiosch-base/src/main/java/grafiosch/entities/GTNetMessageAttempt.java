package grafiosch.entities;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * Tracks per-target delivery status for future-oriented GTNet broadcast messages.
 *
 * <p>
 * This entity records which remote GTNet instances should receive a specific broadcast message
 * (such as maintenance announcements or operation discontinuation notices) and whether the
 * message has been successfully delivered to each target.
 * </p>
 *
 * <p>
 * The delivery mechanism is handled by the GTNetFutureMessageDeliveryTask background job which:
 * <ul>
 *   <li>Runs periodically (every 5 hours) and immediately when future-oriented messages are sent</li>
 *   <li>Creates entries for new communication partners whose handshake completed after the message</li>
 *   <li>Delivers pending messages (hasSend = false) to their targets</li>
 *   <li>Handles cancellation logic for maintenance/discontinuation cancellations</li>
 *   <li>Cleans up entries when message dates are in the past</li>
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
    a specific message-to-target delivery, with hasSend indicating whether delivery succeeded.""")
public class GTNetMessageAttempt extends BaseID<Integer> {

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
  @Column(name = "send_timestamp")
  @Temporal(TemporalType.TIMESTAMP)
  private Date sendTimestamp;

  public GTNetMessageAttempt() {
  }

  public GTNetMessageAttempt(Integer idGtNet, Integer idGtNetMessage) {
    this.idGtNet = idGtNet;
    this.idGtNetMessage = idGtNetMessage;
    this.hasSend = false;
  }

  public GTNetMessageAttempt(Integer idGtNet, Integer idGtNetMessage, boolean hasSend, Date sendTimestamp) {
    this.idGtNet = idGtNet;
    this.idGtNetMessage = idGtNetMessage;
    this.hasSend = hasSend;
    this.sendTimestamp = sendTimestamp;
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

  public Date getSendTimestamp() {
    return sendTimestamp;
  }

  public void setSendTimestamp(Date sendTimestamp) {
    this.sendTimestamp = sendTimestamp;
  }

  /**
   * Marks this entry as successfully delivered and records the timestamp.
   */
  public void markAsSent() {
    this.hasSend = true;
    this.sendTimestamp = new Date();
  }

}
