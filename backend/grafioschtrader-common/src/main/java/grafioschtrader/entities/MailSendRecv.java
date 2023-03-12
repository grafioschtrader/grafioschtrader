package grafioschtrader.entities;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.types.MessageComType;
import grafioschtrader.types.ReplyToRolePrivateType;
import grafioschtrader.types.SendRecvType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Base class for mailing system. Mail also supports M2M on GT. Attention the
 * ID's of the role must be the same on all GT instances. systems.
 *
 */
@Entity
@Table(name = MailSendRecv.TABNAME)
public class MailSendRecv extends BaseID {

  public static final String TABNAME = "mail_send_recv";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_mail_send_recv ")
  private Integer idMailSendRecv;

  @Column(name = "send_recv ")
  private char sendRecv;

  @Schema(description = "This message was created by this user")
  @NotNull
  @Column(name = "id_user_from")
  private Integer idUserFrom;

  @Schema(description = "A message is intended for a specific other user")
  @Column(name = "id_user_to")
  private Integer idUserTo;

  @Schema(description = "A message can also be addressed to a role")
  @Column(name = "id_role_to")
  private Integer idRoleTo;

  @Schema(description = """
      Avoiding multiple creation of the same message by system monitoring.
      For example, the id of the instrument or the name of the connector is stored in it.""")
  @Column(name = "id_entity")
  private String idEntity;

  @Schema(description = "The type of the message which will be sent to the private email")
  @Basic(optional = false)
  @Column(name = "message_com_type")
  @NotNull
  private byte messageComType;

  @Schema(description = "Exchange of a message beyond this instance")
  @Column(name = "id_gt_net")
  private Integer idGtNet;

  @Schema(description = "The subject of this message")
  @NotNull
  @Size(min = 2, max = 96)
  @Column(name = "subject")
  private String subject;

  @Schema(description = "The message text from this message")
  @NotNull
  @Size(min = 2, max = 1024)
  @Column(name = "message")
  private String message;

  @Schema(description = "Reference to local parent message id.")
  @Column(name = "id_reply_to_local")
  private Integer idReplyToLocal;

  @Schema(description = "Reference to local parent message id.")
  @Column(name = "id_reply_to_remote")
  private Integer idReplyToRemote;

  @Schema(description = "The response can be private or marked as a role response.")
  @Column(name = "reply_to_role_private")
  private Byte replyToRolePrivate;

  @Schema(description = "When was the message received")
  @JsonFormat(pattern = GlobalConstants.STANDARD_LOCAL_DATE_TIME)
  @Column(name = "send_recv_time")
  private LocalDateTime sendRecvTime;

  @Transient
  String roleNameTo;

  @Transient
  boolean hasBeenRead;

  public MailSendRecv() {
  }

  public MailSendRecv(SendRecvType sendRecv, Integer idUserFrom, Integer idUserTo, String roleNameTo, String subject,
      String message, Integer idReplyToLocal, ReplyToRolePrivateType replyToRolePrivate) {
    this.sendRecv = sendRecv.getValue();
    this.idUserFrom = idUserFrom;
    this.idUserTo = idUserTo;
    this.roleNameTo = roleNameTo;
    this.subject = subject;
    this.message = message;
    this.idReplyToLocal = idReplyToLocal;
    this.replyToRolePrivate = replyToRolePrivate.getValue();
  }

  public Integer getIdMailSendRecv() {
    return idMailSendRecv;
  }

  public void setIdMailSendRecv(Integer idMailSendRecv) {
    this.idMailSendRecv = idMailSendRecv;
  }

  public char getSendRecv() {
    return sendRecv;
  }

  public void setSendRecv(char sendRecv) {
    this.sendRecv = sendRecv;
  }

  @JsonIgnore
  public SendRecvType getSendRecvAsType() {
    return SendRecvType.getSendRecvType(sendRecv);
  }

  public void setSendRecvAsType(SendRecvType sendRecv) {
    this.sendRecv = sendRecv.getValue();
  }

  public Integer getIdUserFrom() {
    return idUserFrom;
  }

  public void setIdUserFrom(Integer idUserFrom) {
    this.idUserFrom = idUserFrom;
  }

  public Integer getIdUserTo() {
    return idUserTo;
  }

  public void setIdUserTo(Integer idUserTo) {
    this.idUserTo = idUserTo;
  }

  public Integer getIdRoleTo() {
    return idRoleTo;
  }

  public void setIdRoleTo(Integer idRoleTo) {
    this.idRoleTo = idRoleTo;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Integer getIdReplyToLocal() {
    return idReplyToLocal;
  }

  public void setIdReplyToLocal(Integer idReplyToLocal) {
    this.idReplyToLocal = idReplyToLocal;
  }

  public Integer getIdReplyToRemote() {
    return idReplyToRemote;
  }

  public void setIdReplyToRemote(Integer idReplyToRemote) {
    this.idReplyToRemote = idReplyToRemote;
  }

  public LocalDateTime getSendRecvTime() {
    return sendRecvTime;
  }

  public void setSendRecvTime(LocalDateTime sendRecvTime) {
    this.sendRecvTime = sendRecvTime;
  }

  public String getIdEntity() {
    return idEntity;
  }

  public void setIdEntity(String idEntity) {
    this.idEntity = idEntity;
  }

  public MessageComType getMessageComType() {
    return MessageComType.getMessageComTypeByValue(messageComType);
  }

  public void setMessageComType(MessageComType messageComType) {
    this.messageComType = messageComType.getValue();
  }

  public void setMessageComType(byte messageComType) {
    this.messageComType = messageComType;
  }

  public Integer getIdGtNet() {
    return idGtNet;
  }

  public void setIdGtNet(Integer idGtNet) {
    this.idGtNet = idGtNet;
  }

  public ReplyToRolePrivateType getReplyToRolePrivate() {
    return replyToRolePrivate == null ? ReplyToRolePrivateType.REPLY_NORMAL
        : ReplyToRolePrivateType.getReplyToRolePrivateTypeByValue(replyToRolePrivate);
  }

  public void setReplyToRolePrivate(ReplyToRolePrivateType replyToRolePrivate) {
    this.replyToRolePrivate = replyToRolePrivate.getValue();
  }

  public String getRoleNameTo() {
    return roleNameTo;
  }

  public void setRoleNameTo(String roleNameTo) {
    this.roleNameTo = roleNameTo;
  }

  public boolean isHasBeenRead() {
    return hasBeenRead;
  }

  public void setHasBeenRead(boolean hasBeenRead) {
    this.hasBeenRead = hasBeenRead;
  }

  @Override
  public Integer getId() {
    return idMailSendRecv;
  }

}
