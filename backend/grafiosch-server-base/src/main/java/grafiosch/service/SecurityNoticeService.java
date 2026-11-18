package grafiosch.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import grafiosch.entities.User;

/** Delivers credential mail only after its transaction commits, without making mail availability a prerequisite. */
@Service
public class SecurityNoticeService {
  private static final Logger log = LoggerFactory.getLogger(SecurityNoticeService.class);
  private final ApplicationEventPublisher events;
  private final MessageSource messages;
  private final MailExternalService mail;

  public SecurityNoticeService(ApplicationEventPublisher events, MessageSource messages, MailExternalService mail) {
    this.events = events;
    this.messages = messages;
    this.mail = mail;
  }

  /** Immutable mail content; it never holds a managed user or a security context. */
  public record MailNotice(String email, String subject, String body) {
  }

  /**
   * Announces a change to the sign-in of the user after the current transaction commits.
   *
   * @param user     the affected user, for address and locale
   * @param eventKey message key naming the event; never a secret
   * @param args     arguments of the event text, such as the number of recovery codes left
   */
  public void credentialChanged(User user, String eventKey, Object... args) {
    var locale = user.createAndGetJavaLocale();
    String event = messages.getMessage(eventKey, args.length == 0 ? null : args, locale);
    sendAfterCommit(user.getUsername(), messages.getMessage("security.notice.subject", null, locale),
        messages.getMessage("security.notice.body", new Object[] { event, LocalDateTime.now(ZoneOffset.UTC) + " UTC" },
            locale));
  }

  public void sendAfterCommit(String email, String subject, String body) {
    events.publishEvent(new MailNotice(email, subject, body));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void deliver(MailNotice notice) {
    try {
      mail.sendSimpleMessageAsync(notice.email(), notice.subject(), notice.body());
    } catch (Exception e) {
      log.error("Could not schedule security mail", e);
    }
  }
}
