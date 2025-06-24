package grafiosch.repository;

import grafiosch.dto.MailSendForwardDefaultBase;
import grafiosch.entities.MailSettingForward;

/**
 * Custom repository interface for MailSettingForward entities providing specialized
 * operations beyond standard JPA repository methods.
 */
public interface MailSettingForwardJpaRepositoryCustom extends BaseRepositoryCustom<MailSettingForward> {

  /**
   * Retrieves the default mail forwarding configuration settings.
   * 
   * <p>This method returns the system-wide default forwarding preferences that
   * are applied when users haven't configured specific forwarding rules for
   * particular message types. These defaults determine whether messages are
   * delivered internally, externally via email, or both.
   * 
   * <p>The default settings serve as fallback configuration and ensure that
   * all message types have defined delivery behavior even for users who haven't
   * customized their forwarding preferences.
   * 
   * @return a MailSendForwardDefaultBase containing the default forwarding configuration
   */
  MailSendForwardDefaultBase getMailSendForwardDefault();
}
