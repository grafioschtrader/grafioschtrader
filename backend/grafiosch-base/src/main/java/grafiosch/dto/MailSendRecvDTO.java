package grafiosch.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;

public interface MailSendRecvDTO {
  public Integer getIdMailSendRecv();

  public String getSendRecv();

  public Integer getIdUserFrom();

  public Boolean getHasBeenRead();

  public Integer getIdUserTo();

  public Integer getIdRoleTo();

  public String getSubject();

  public String getMessage();

  public Integer getIdReplyToLocal();

  public Integer getIdReplyToRemote();

  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME)
  public LocalDateTime getSendRecvTime();

  public Integer getIdGtNet();

  public Byte getReplyToRolePrivate();

  public String getRoleNameTo();
}
