package grafioschtrader.repository;


import grafioschtrader.dto.MailSendForwardDefault;
import grafioschtrader.entities.MailSettingForward;
import grafioschtrader.types.MessageComType;
import jakarta.mail.MessagingException;


public interface MailSettingForwardJpaRepositoryCustom extends BaseRepositoryCustom<MailSettingForward> {
  
  Integer sendMailToMainAdminInternalOrExternal(Integer idUserFrom, String subjectKey, String message,
      MessageComType messageComType) throws MessagingException;
  
  Integer sendMailInternOrExternal(Integer idUserFrom, Integer idUserTo, String subject, String message,
      MessageComType messageComType) throws MessagingException; 
  
  MailSendForwardDefault getMailSendForwardDefault();
}
