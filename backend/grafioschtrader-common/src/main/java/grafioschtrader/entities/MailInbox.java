package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;

/**
 * Contains the receiving Messages.
 *  
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = MailInbox.TABNAME)
@DiscriminatorValue("I")
public class MailInbox extends MailInOut implements Serializable {

  public static final String TABNAME = "mail_inbox";

  @Size(min = 2, max = 64)
  @Column(name = "domain_from")
  private String domainFrom;

  @JsonFormat(pattern = GlobalConstants.STANDARD_LOCAL_DATE_TIME)
  @Column(name = "received_time")
  private LocalDateTime receivedTime;

  @Column(name = "has_been_read")
  private boolean hasBeenRead;

  public MailInbox() {
  }

  public MailInbox(@NotNull Integer idUserFrom, Integer idUserTo, String roleName,
      @NotNull @Size(min = 2, max = 96) String subject, @NotNull @Size(min = 2, max = 1024) String message) {
    super(idUserFrom, idUserTo, roleName, subject, message);
  }

  public String getDomainFrom() {
    return domainFrom;
  }

  public void setDomainFrom(String domainFrom) {
    this.domainFrom = domainFrom;
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
