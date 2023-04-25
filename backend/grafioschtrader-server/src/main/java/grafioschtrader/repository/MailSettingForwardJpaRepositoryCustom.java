package grafioschtrader.repository;


import grafioschtrader.dto.MailSendForwardDefault;
import grafioschtrader.entities.MailSettingForward;


public interface MailSettingForwardJpaRepositoryCustom extends BaseRepositoryCustom<MailSettingForward> {
  
  MailSendForwardDefault getMailSendForwardDefault();
}
