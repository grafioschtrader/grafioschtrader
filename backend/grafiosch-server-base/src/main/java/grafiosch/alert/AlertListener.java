package grafiosch.alert;

import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import grafiosch.common.NetworkHelper;
import grafiosch.entities.User;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.service.MailExternalService;
import jakarta.mail.MessagingException;

/**
 * Application event listener for processing and handling system alerts via email.
 * 
 * <p>This component listens for AlertEvent occurrences and sends email notifications
 * to the configured admin user when alerts are triggered. It provides centralized
 * alert management with configurable alert types, internationalized messaging,
 * and bitmap-based alert filtering.</p>
 * 
 * <h3>Alert Processing:</h3>
 * <ul>
 *   <li><strong>Event Listening:</strong> Automatically processes AlertEvent instances</li>
 *   <li><strong>Filtering:</strong> Uses bitmap configuration to determine which alerts to send</li>
 *   <li><strong>Localization:</strong> Sends alerts in admin user's preferred language</li>
 *   <li><strong>Network Context:</strong> Includes source IP address in alert subjects</li>
 * </ul>
 */
@Component
public class AlertListener implements ApplicationListener<AlertEvent> {

  private static final Logger log = LoggerFactory.getLogger(AlertListener.class);

  private final UserJpaRepository userJpaRepository;

  private final GlobalparametersJpaRepository globalparametersJpaRepository;

  private final MessageSource messages;

  /** Email address of the main admin user for alert notifications.  */
  @Value("${gt.main.user.admin.mail}")
  private String mainUserAdminMail;

  @Autowired
  private MailExternalService mailExternalService;

  /**
   * Creates an alert listener with required dependencies.
   * 
   * <p>Uses lazy initialization for repositories to avoid circular dependencies
   * during application startup while ensuring proper alert handling capability.</p>
   * 
   * @param userJpaRepository repository for user data access
   * @param globalparametersJpaRepository repository for global configuration
   * @param messages message source for internationalized content
   */
  public AlertListener(@Lazy UserJpaRepository userJpaRepository,
      @Lazy GlobalparametersJpaRepository globalparametersJpaRepository, MessageSource messages) {
    super();
    this.userJpaRepository = userJpaRepository;
    this.globalparametersJpaRepository = globalparametersJpaRepository;
    this.messages = messages;
  }

  /**
   * Handles AlertEvent by sending email notification to admin user.
   * 
   * <p>Automatically triggered when AlertEvent is published in the application
   * context. Extracts alert type and parameters from the event and delegates
   * to the mail sending logic.</p>
   * 
   * @param event the alert event containing alert type and parameters
   */
  @Override
  public void onApplicationEvent(AlertEvent event) {
    sendMail(event.getAlertType(), new Object[] { event.getMsgParam() });
  }

  /**
   * Sends alert email if admin user exists and alert type is enabled.
   * 
   * <p>
   * Performs comprehensive alert processing including admin user lookup, alert type filtering via bitmap configuration,
   * message localization, and asynchronous email delivery. Only sends alerts that are enabled in the global alert
   * bitmap configuration.
   * </p>
   * 
   * <h3>Processing Steps:</h3>
   * <ul>
   * <li><strong>User Validation:</strong> Verifies admin user exists</li>
   * <li><strong>Alert Filtering:</strong> Checks if alert type is enabled via bitmap</li>
   * <li><strong>Localization:</strong> Formats messages in user's language</li>
   * <li><strong>Network Context:</strong> Includes source IP in subject line</li>
   * <li><strong>Delivery:</strong> Sends email asynchronously</li>
   * </ul>
   * 
   * @param alertType the type of alert to send
   * @param msgParams parameters for message formatting
   */
  public void sendMail(IAlertType alertType, Object[] msgParams) {
    Optional<User> userOpt = userJpaRepository.findByEmail(mainUserAdminMail);
    if (userOpt.isPresent()
        && (globalparametersJpaRepository.getAlertBitmap() & alertType.getValue()) == alertType.getValue()) {
      Locale userLang = userOpt.get().createAndGetJavaLocale();
      String subject = messages.getMessage("alert.mail.subject", new Object[] { "this" }, userLang);
      try {
        subject = messages.getMessage("alert.mail.subject", new Object[] { NetworkHelper.getIpAddressToOutside() },
            userLang);
        mailExternalService.sendSimpleMessageAsync(mainUserAdminMail, subject,
            messages.getMessage(alertType.getName(), msgParams, userLang));
      } catch (NoSuchMessageException | MessagingException e) {
        log.error("Failed to send an email to {} from {} to {}", mainUserAdminMail, subject);
      }
    }
  }

}
