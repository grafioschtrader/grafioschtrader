package grafioschtrader.entities;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.types.MessageComType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User can set per type if this message will be forwarded to his EMail.")
public class MailSettingForward extends BaseID {
  public static final String TABNAME = "mail_setting_forward";
  
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_mail_setting_forward")
  private Integer idMailSettingForward;
  
  @Basic(optional = false)
  @Column(name = "id_user")
  private Integer idUser;
  
  @Schema(description = "The type of message")
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
