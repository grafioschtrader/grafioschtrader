package grafioschtrader.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.types.MessageComType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;

@Schema(description = """
    Messages can originate from user or also from monitoring the system. 
    Possibly, these messages should be forwarded to the user's email.  
    This entity contains the setting which message types are forwarded.""")
public class MailSettingForward extends BaseID {
  public static final String TABNAME = "mail_setting_forward";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_mail_setting_forward")
  private Integer idMailSettingForward;

  @Schema(description = "The user to whom this setting belongs")
  @Basic(optional = false)
  @Column(name = "id_user")
  private Integer idUser;

  @Schema(description = "The type of the message which will be sent to the private email")
  @Basic(optional = false)
  @Column(name = "message_com_type")
  @NotNull
  private byte messageComType;

  public Integer getIdMailSettingForward() {
    return idMailSettingForward;
  }

  public void setIdMailSettingForward(Integer idMailSettingForward) {
    this.idMailSettingForward = idMailSettingForward;
  }

  public Integer getIdUser() {
    return idUser;
  }

  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  public MessageComType getMessageComType() {
    return MessageComType.getMessageComTypeByValue(messageComType);
  }

  public void setMessageComType(MessageComType messageComType) {
    this.messageComType = messageComType.getValue();
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idMailSettingForward;
  }

}
