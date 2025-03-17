package grafiosch.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@Service
public class MailExternalService {


  @Value("${spring.mail.username}")
  private String springMailUsername;

  @Autowired
  private JavaMailSender emailSender;

  @Async
  public void sendSimpleMessageAsync(final String toEmail, final String subject, final String message)
      throws MessagingException {
    sendSimpleMessage(toEmail, subject, message);
  }


  @Async
  public void sendSimpleMessageAsync(final String[] toEmails, final String subject, final String message)
      throws MessagingException {
    sendSimpleMessage(toEmails, subject, message);
  }


  public void sendSimpleMessage(final String toEmail, final String subject, final String message)
      throws MessagingException {
   sendSimpleMessage(new String[] {toEmail}, subject, message);
  }


  public void sendSimpleMessage(final String[] toEmails, final String subject, final String message)
      throws MessagingException {
    final MimeMessage mineMessage = emailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mineMessage, true);
    mineMessage.setSender(new InternetAddress(springMailUsername));
    mineMessage.setFrom(new InternetAddress(springMailUsername));
    if(toEmails.length == 1) {
      helper.setTo(toEmails);
    } else {
      helper.setTo(springMailUsername);
      helper.setBcc(toEmails);
    }
    helper.setSubject(subject);
    helper.setText(message);
    emailSender.send(mineMessage);
  }
}
