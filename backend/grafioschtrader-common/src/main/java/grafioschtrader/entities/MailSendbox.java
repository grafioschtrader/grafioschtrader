package grafioschtrader.entities;

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

@Schema(description = "Contains the sent message")
@Entity
@Table(name = MailSendbox.TABNAME)
@DiscriminatorValue("S")
public class MailSendbox extends MailInOut {

  public static final String TABNAME = "mail_sendbox";
 
  @JsonFormat(pattern = GlobalConstants.STANDARD_LOCAL_DATE_TIME)
  @Column(name = "send_time")
  private LocalDateTime sendTime;

  public MailSendbox() {
  }

  public MailSendbox(@NotNull Integer idUserFrom, Integer idUserTo, String roleName,
      @NotNull @Size(min = 2, max = 96) String subject, @NotNull @Size(min = 2, max = 1024) String message) {
    super(idUserFrom, idUserTo, roleName, subject, message);
  }

  public LocalDateTime getSendTime() {
    return sendTime;
  }

  public void setSendTime(LocalDateTime sendTime) {
    this.sendTime = sendTime;
  }

}
