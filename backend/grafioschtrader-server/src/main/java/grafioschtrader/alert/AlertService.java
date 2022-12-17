package grafioschtrader.alert;

import java.util.Locale;
import java.util.Optional;

import jakarta.mail.MessagingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;

import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.entities.User;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.UserJpaRepository;

@Service
public class AlertService {

  private static final Logger log = LoggerFactory.getLogger(CashAccountTransfer.class);
  
  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private MessageSource messages;

  @Value("${gt.main.user.admin.mail}")
  private String mainUserAdminMail;

  public void sendMail(AlertType alertType, Object msgParam) {
    sendMail(alertType, new Object[] {msgParam});
  }
  
  
  public void sendMail(AlertType alertType, Object[] msgParams) {
    Optional<User> userOpt = userJpaRepository.findByEmail(mainUserAdminMail);
    if (userOpt.isPresent()
        && (globalparametersJpaRepository.getAlertBitmap() & alertType.getValue()) == alertType.getValue()) {
      Locale userLang = userOpt.get().createAndGetJavaLocale();
      String subject = messages.getMessage("alert.mail.subject", null, userLang);
      try {
        userJpaRepository.sendSimpleMessage(mainUserAdminMail, subject,
            messages.getMessage(alertType.name(), msgParams, userLang));
      } catch (NoSuchMessageException | MessagingException e) {
        log.error("Failed to send an email to {} from {} to {}", mainUserAdminMail, subject);
      }
    }
  }

}
