package grafioschtrader.registration;


import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.User;
import grafioschtrader.repository.VerificationTokenJpaRepository;

@Component
public class RegistrationListener implements ApplicationListener<OnRegistrationCompleteEvent> {
  @Autowired
  private VerificationTokenJpaRepository verificationTokenJpaRepository;

  @Autowired
  private MessageSource messages;

  @Autowired
  private JavaMailSender mailSender;

  @Value("${spring.mail.username}")
  private String springMailUsername;

  @Override
  public void onApplicationEvent(final OnRegistrationCompleteEvent event) {
    this.confirmRegistration(event);
  }

  private void confirmRegistration(final OnRegistrationCompleteEvent event) {
    final User user = event.getUser();
    final String token = UUID.randomUUID().toString();
    verificationTokenJpaRepository.createVerificationTokenForUser(user, token);

    final SimpleMailMessage email = constructEmailMessage(event, user, token);
    mailSender.send(email);
  }

  private final SimpleMailMessage constructEmailMessage(final OnRegistrationCompleteEvent event, final User user,
      final String token) {
    final String recipientAddress = user.getUsername();
    final String subject = messages.getMessage("registraion.success.subject", null, user.createAndGetJavaLocale());
    final String confirmationUrl = event.getAppUrl() + "/tokenverify?token=" + token;
    final String message = messages.getMessage("registraion.success.text", null, user.createAndGetJavaLocale());
    final SimpleMailMessage email = new SimpleMailMessage();
    email.setFrom(springMailUsername);
    email.setTo(recipientAddress);
    email.setSubject(subject);
    email.setText(message + " \r\n" + confirmationUrl);

    return email;
  }

}
