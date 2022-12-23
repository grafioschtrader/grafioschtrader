package grafioschtrader.entities;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = MailSendbox.TABNAME)
@DiscriminatorValue("S")
public class MailSendbox extends MailInOut {

  public static final String TABNAME = "mail_sendbox";

  @Size(min = 2, max = 64)
  @Column(name = "domain_to")
  private String domainTo;

  @JsonFormat(pattern = GlobalConstants.STANDARD_LOCAL_DATE_TIME)
  @Column(name = "send_time")
  private LocalDateTime sendTime;

  public MailSendbox() {
  }

  public MailSendbox(@NotNull Integer idUserFrom, Integer idUserTo, String roleName,
      @NotNull @Size(min = 2, max = 96) String subject, @NotNull @Size(min = 2, max = 1024) String message) {
    super(idUserFrom, idUserTo, roleName, subject, message);
  }

  public String getDomainTo() {
    return domainTo;
  }

  public void setDomainTo(String domainTo) {
    this.domainTo = domainTo;
  }

  public LocalDateTime getSendTime() {
    return sendTime;
  }

  public void setSendTime(LocalDateTime sendTime) {
    this.sendTime = sendTime;
  }

}
