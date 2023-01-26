package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Contains the receiving messages")
@Entity
@Table(name = MailInbox.TABNAME)
@DiscriminatorValue("I")
public class MailInbox extends MailInOut implements Serializable {

  private static final long serialVersionUID = 1L;

  public static final String TABNAME = "mail_inbox";

  @Schema(description = "When was the message received")
  @JsonFormat(pattern = GlobalConstants.STANDARD_LOCAL_DATE_TIME)
  @Column(name = "received_time")
  private LocalDateTime receivedTime;

  @Schema(description = "The message was read")
  @Column(name = "has_been_read")
  private boolean hasBeenRead;

  public MailInbox() {
  }

  public MailInbox(@NotNull Integer idUserFrom, Integer idUserTo, String roleName,
      @NotNull @Size(min = 2, max = 96) String subject, @NotNull @Size(min = 2, max = 1024) String message) {
    super(idUserFrom, idUserTo, roleName, subject, message);
  }
 

  public LocalDateTime getReceivedTime() {
    return receivedTime;
  }

  public void setReceivedTime(LocalDateTime receivedTime) {
    this.receivedTime = receivedTime;
  }

  public boolean isHasBeenRead() {
    return hasBeenRead;
  }

  public void setHasBeenRead(boolean hasBeenRead) {
    this.hasBeenRead = hasBeenRead;
  }

}
