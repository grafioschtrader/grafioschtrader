package grafiosch.entities;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

/**
 * One can be addressed according to a role. This means that they cannot be
 * physically deleted by a user of this role. The corresponding message is
 * marked as deleted and thus hidden from the user. It is also marked as read.
 */
@Entity
public class MailSendRecvReadDel {

  @EmbeddedId
  private MailSendRecvReadDelKey msrrdk;

  /**
   * The message has been read by the recipient
   */
  @Column(name = "has_been_read")
  private boolean hasBeenRead;

  /**
   * The message has been marked as deleted by the recipient
   */
  @Column(name = "mark_hide_del")
  private boolean markHideDel;

  public MailSendRecvReadDel() {
  }

  public MailSendRecvReadDel(MailSendRecvReadDelKey mailSendRecvReadDelKey) {
    this.msrrdk = mailSendRecvReadDelKey;
  }

  public MailSendRecvReadDelKey getMsrrdk() {
    return msrrdk;
  }

  public void setMsrrdk(MailSendRecvReadDelKey msrrdk) {
    this.msrrdk = msrrdk;
  }

  public boolean isHasBeenRead() {
    return hasBeenRead;
  }

  public void setHasBeenRead(boolean hasBeenRead) {
    this.hasBeenRead = hasBeenRead;
  }

  public boolean isMarkHideDel() {
    return markHideDel;
  }

  public void setMarkHideDel(boolean markHideDel) {
    this.markHideDel = markHideDel;
  }

  /**
   * The key consists of the ID of the mail and the user.
   */
  @Embeddable
  public static class MailSendRecvReadDelKey {

    public MailSendRecvReadDelKey() {
    }

    public MailSendRecvReadDelKey(Integer idMailSendRecv, Integer idUser) {
      this.idMailSendRecv = idMailSendRecv;
      this.idUser = idUser;
    }

    @Column(name = "id_mail_send_recv ")
    private Integer idMailSendRecv;

    @Column(name = "id_user")
    private Integer idUser;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      MailSendRecvReadDelKey that = (MailSendRecvReadDelKey) o;
      return Objects.equals(idMailSendRecv, that.idMailSendRecv) && Objects.equals(idUser, that.idUser);
    }

    @Override
    public int hashCode() {
      return Objects.hash(idMailSendRecv, idUser);
    }

  }
}
