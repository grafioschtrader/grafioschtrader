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

@Component
public class AlertListener implements ApplicationListener<AlertEvent> {

  private static final Logger log = LoggerFactory.getLogger(AlertListener.class);

  private final UserJpaRepository userJpaRepository;

  private final GlobalparametersJpaRepository globalparametersJpaRepository;

  private final MessageSource messages;

  @Value("${gt.main.user.admin.mail}")
  private String mainUserAdminMail;

  @Autowired
  private MailExternalService mailExternalService;

  public AlertListener(@Lazy UserJpaRepository userJpaRepository,
      @Lazy GlobalparametersJpaRepository globalparametersJpaRepository, MessageSource messages) {
    super();
    this.userJpaRepository = userJpaRepository;
    this.globalparametersJpaRepository = globalparametersJpaRepository;
    this.messages = messages;
  }

  @Override
  public void onApplicationEvent(AlertEvent event) {
    sendMail(event.getAlertType(), new Object[] { event.getMsgParam() });
  }

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
