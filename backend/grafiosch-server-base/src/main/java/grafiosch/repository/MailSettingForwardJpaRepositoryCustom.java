package grafiosch.repository;

import grafiosch.dto.MailSendForwardDefaultBase;
import grafiosch.entities.MailSettingForward;

public interface MailSettingForwardJpaRepositoryCustom extends BaseRepositoryCustom<MailSettingForward> {

  MailSendForwardDefaultBase getMailSendForwardDefault();
}
