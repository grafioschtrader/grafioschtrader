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

/**
 * Service for sending external email messages using Spring's JavaMailSender. Provides both synchronous and asynchronous
 * email sending capabilities with support for single and multiple recipients.
 */
@Service
public class MailExternalService {

  @Value("${spring.mail.username}")
  private String springMailUsername;

  @Autowired
  private JavaMailSender emailSender;

  /**
   * Sends a simple email message asynchronously to a single recipient. This method executes in a separate thread and
   * returns immediately.
   *
   * @param toEmail the recipient's email address
   * @param subject the email subject line
   * @param message the email message body
   * @throws MessagingException if there is an error creating or sending the message
   */
  @Async
  public void sendSimpleMessageAsync(final String toEmail, final String subject, final String message)
      throws MessagingException {
    sendSimpleMessage(toEmail, subject, message);
  }

  /**
   * Sends a simple email message asynchronously to multiple recipients. This method executes in a separate thread and
   * returns immediately. For multiple recipients, the sender's email is used as the primary recipient and all target
   * emails are added as BCC recipients to protect privacy.
   *
   * @param toEmails array of recipient email addresses
   * @param subject  the email subject line
   * @param message  the email message body
   * @throws MessagingException if there is an error creating or sending the message
   */
  @Async
  public void sendSimpleMessageAsync(final String[] toEmails, final String subject, final String message)
      throws MessagingException {
    sendSimpleMessage(toEmails, subject, message);
  }

  /**
   * Sends a simple email message synchronously to a single recipient.
   *
   * @param toEmail the recipient's email address
   * @param subject the email subject line
   * @param message the email message body
   * @throws MessagingException if there is an error creating or sending the message
   */
  public void sendSimpleMessage(final String toEmail, final String subject, final String message)
      throws MessagingException {
    sendSimpleMessage(new String[] { toEmail }, subject, message);
  }

  /**
   * Sends a simple email message synchronously to one or more recipients. This is the core method that handles the
   * actual email creation and sending.
   * 
   * For single recipients, the email is sent directly to the target address. For multiple recipients, the sender's own
   * email is used as the primary recipient and all target emails are added as BCC recipients to maintain privacy and
   * prevent recipients from seeing each other's email addresses.
   *
   * @param toEmails array of recipient email addresses
   * @param subject  the email subject line
   * @param message  the email message body
   * @throws MessagingException if there is an error creating or sending the message
   */
  public void sendSimpleMessage(final String[] toEmails, final String subject, final String message)
      throws MessagingException {
    final MimeMessage mineMessage = emailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mineMessage, true);
    mineMessage.setSender(new InternetAddress(springMailUsername));
    mineMessage.setFrom(new InternetAddress(springMailUsername));
    if (toEmails.length == 1) {
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
