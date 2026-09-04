package grafiosch.gtnet.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageAttemptStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Administrator-facing delivery outcome for one message and target peer. */
@Schema(description = "Per-target delivery outcome of an outgoing GTNet background message.")
public class GTNetMessageAttemptView {

  public final Integer idGtNetMessageAttempt;
  public final Integer idGtNetMessage;
  private final byte messageCode;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  public final LocalDateTime messageTimestamp;

  public final Integer idGtNet;
  public final String targetDomain;
  private final byte attemptStatus;
  public final int tryCount;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  public final LocalDateTime lastAttemptTimestamp;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  public final LocalDateTime sendTimestamp;

  public final String lastError;

  public GTNetMessageAttemptView(Integer idGtNetMessageAttempt, Integer idGtNetMessage, byte messageCode,
      LocalDateTime messageTimestamp, Integer idGtNet, String targetDomain, byte attemptStatus, int tryCount,
      LocalDateTime lastAttemptTimestamp, LocalDateTime sendTimestamp, String lastError) {
    this.idGtNetMessageAttempt = idGtNetMessageAttempt;
    this.idGtNetMessage = idGtNetMessage;
    this.messageCode = messageCode;
    this.messageTimestamp = messageTimestamp;
    this.idGtNet = idGtNet;
    this.targetDomain = targetDomain;
    this.attemptStatus = attemptStatus;
    this.tryCount = tryCount;
    this.lastAttemptTimestamp = lastAttemptTimestamp;
    this.sendTimestamp = sendTimestamp;
    this.lastError = lastError;
  }

  public GNetCoreMessageCode getMessageCode() {
    return GNetCoreMessageCode.getByValue(messageCode);
  }

  public GTNetMessageAttemptStatus getAttemptStatus() {
    return GTNetMessageAttemptStatus.getByValue(attemptStatus);
  }
}
