package grafioschtrader.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.common.PropertyOnlyCreation;
import grafioschtrader.types.MessageComType;
import grafioschtrader.types.MessageTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Schema(description = """
    Messages can originate from user or also from monitoring the system.
    Possibly, these messages should be forwarded to the user's email.
    This entity contains the setting which message types are forwarded.""")
@Entity
@Table(name = MailSettingForward.TABNAME)
public class MailSettingForward extends UserBaseID {
  public static final String TABNAME = "mail_setting_forward";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_mail_setting_forward")
  private Integer idMailSettingForward;

  @Schema(description = "The user to whom this setting belongs")
  @Basic(optional = false)
  @Column(name = "id_user")
  @PropertyOnlyCreation
  private Integer idUser;

  @Schema(description = "The type of the message which will be sent to the private email")
  @Basic(optional = false)
  @Column(name = "message_com_type")
  @NotNull
  @PropertyOnlyCreation
  private byte messageComType;


  @Schema(description = "Which mail system is used. Internal, external or both")
  @Basic(optional = false)
  @Column(name = "message_target_type")
  @NotNull
  @PropertyAlwaysUpdatable
  private byte messageTargetType;


  @Schema(description = "Perhaps a message of a certain type to the main administrator should be forwarded to another admin")
  @Column(name = "id_user_redirect")
  @PropertyAlwaysUpdatable
  private Integer idUserRedirect;



  public Integer getIdMailSettingForward() {
    return idMailSettingForward;
  }

  public void setIdMailSettingForward(Integer idMailSettingForward) {
    this.idMailSettingForward = idMailSettingForward;
  }

  @Override
  public Integer getIdUser() {
    return idUser;
  }

  @Override
  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  public MessageComType getMessageComType() {
    return MessageComType.getMessageComTypeByValue(messageComType);
  }

  public void setMessageComType(MessageComType messageComType) {
    this.messageComType = messageComType.getValue();
  }


  public MessageTargetType getMessageTargetType() {
    return MessageTargetType.getMessageTargetTypeByValue(messageTargetType);
  }

  public void setMessageTargetType(MessageTargetType messageTargetType) {
    this.messageTargetType = messageTargetType.getValue();
  }

  public Integer getIdUserRedirect() {
    return idUserRedirect;
  }

  public void setIdUserRedirect(Integer idUserRedirect) {
    this.idUserRedirect = idUserRedirect;
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idMailSettingForward;
  }

}
