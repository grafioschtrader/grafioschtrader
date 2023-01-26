package grafioschtrader.entities;

import static jakarta.persistence.InheritanceType.JOINED;

import grafioschtrader.types.MessageComType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Base class for mailing system. Mail also supports M2M on GT.
 * Attention the ID's of the role must be the same on all GT instances.
 * systems.
 *
 */
@Entity
@Table(name = MailInOut.TABNAME)
@Inheritance(strategy = JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
public abstract class MailInOut extends BaseID {

  public static final String TABNAME = "mail_in_out";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_mail_inout")
  private Integer idMailInOut;

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
  
  @Schema(description = "Chaining of messages with idMailInOut")
  @Column(name = "reply_to")
  private Integer replyTo;
  
  @Schema(description = "The subject of this message")
  @NotNull
  @Size(min = 2, max = 96)
  @Column(name = "subject")
  private String subject;

  @Transient
  String roleNameTo;

  @Schema(description = "The message text from this message")
  @NotNull
  @Size(min = 2, max = 1024)
  @Column(name = "message")
  private String message;

  public MailInOut() {
  }

  public MailInOut(Integer idUserFrom, Integer idUserTo, String roleNameTo, String subject, String message) {
    this.idUserFrom = idUserFrom;
    this.idUserTo = idUserTo;
    this.roleNameTo = roleNameTo;
    this.subject = subject;
    this.message = message;
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

  public Integer getIdMailInOut() {
    return idMailInOut;
  }

  public String getRoleNameTo() {
    return roleNameTo;
  }

  public void setRoleNameTo(String roleNameTo) {
    this.roleNameTo = roleNameTo;
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
  
  public Integer getIdGtNet() {
    return idGtNet;
  }

  public void setIdGtNet(Integer idGtNet) {
    this.idGtNet = idGtNet;
  }

  public Integer getReplyTo() {
    return replyTo;
  }

  public void setReplyTo(Integer replyTo) {
    this.replyTo = replyTo;
  }

  @Override
  public Integer getId() {
    return null;
  }

}
