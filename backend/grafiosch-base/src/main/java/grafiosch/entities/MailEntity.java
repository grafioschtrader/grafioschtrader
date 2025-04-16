package grafiosch.entities;

import java.time.LocalDate;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonSetter;

import grafiosch.types.IMessageComType;
import grafiosch.types.MessageComType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 */
@Schema(description="""
 Certain messages are created by the system. For example, if a client still
 has an open position on an inactive instrument. It must be excluded that such
 a message is sent more than once.""")
@Entity
@Table(name = MailEntity.TABNAME)
@EntityListeners(AuditingEntityListener.class)
public class MailEntity extends BaseID<Integer> {

  public static final String TABNAME = "mail_entity";

  public static final EnumRegistry<Byte, IMessageComType> MESSAGE_COM_TYPES_REGISTRY = new EnumRegistry<>(
      MessageComType.values());

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_mail_entity")
  private Integer idMailEntity;

  @Schema(description = "With which message was the information noted. It only used for internal mail")
  @Column(name = "id_mail_send_recv")
  private Integer idMailSendRecv;

  @Schema(description = "The type of the message which will be sent to the private email")
  @Basic(optional = false)
  @Column(name = "message_com_type")
  @NotNull
  private byte messageComType;

  @Schema(description = """
      Avoiding multiple creation of the same message by system monitoring.
      For example, the id of the instrument or the name of the connector is stored in it.""")
  @Column(name = "id_entity")
  private Integer idEntity;

  @Schema(description = """
      Avoid that the same message with the same entity is entered several times.""")
  @Column(name = "mark_date")
  private LocalDate markDate;

  @CreatedDate
  @Column(name = "creation_date")
  private LocalDate creationDate;

  public MailEntity(IMessageComType messageComType, Integer idEntity, LocalDate markDate) {
    super();
    this.messageComType = messageComType.getValue();
    this.idEntity = idEntity;
    this.markDate = markDate;
  }

  public Integer getIdMailEntity() {
    return idMailEntity;
  }

  public void setIdMailEntity(Integer idMailEntity) {
    this.idMailEntity = idMailEntity;
  }

  public Integer getIdMailSendRecv() {
    return idMailSendRecv;
  }

  public void setIdMailSendRecv(Integer idMailSendRecv) {
    this.idMailSendRecv = idMailSendRecv;
  }

  public IMessageComType getMessageComType() {
    return MESSAGE_COM_TYPES_REGISTRY.getTypeByValue(messageComType);
  }

  public void setMessageComType(IMessageComType messageComType) {
    this.messageComType = messageComType.getValue();
  }

  //Change the setter to accept a String from the JSON payload.
  @JsonSetter("messageComType")
  public void setMessageComType(String messageComTypeName) {
    IMessageComType messageComType = MESSAGE_COM_TYPES_REGISTRY.getTypeByName(messageComTypeName);
    if (messageComType == null) {
      throw new IllegalArgumentException("Unknown message type: " + messageComTypeName);
    }
    this.messageComType = messageComType.getValue();
  }

  public void setMessageComType(byte messageComType) {
    this.messageComType = messageComType;
  }

  public Integer getIdEntity() {
    return idEntity;
  }

  public void setIdEntity(Integer idEntity) {
    this.idEntity = idEntity;
  }

  public LocalDate getMarkDate() {
    return markDate;
  }

  public void setMarkDate(LocalDate markDate) {
    this.markDate = markDate;
  }

  public LocalDate getCreationDate() {
    return creationDate;
  }

  public void setCreationDate(LocalDate creationDate) {
    this.creationDate = creationDate;
  }

  @Override
  public Integer getId() {
    return idMailEntity;
  }

}
