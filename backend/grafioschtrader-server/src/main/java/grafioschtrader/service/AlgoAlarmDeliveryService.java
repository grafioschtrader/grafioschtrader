package grafioschtrader.service;

import java.time.*;
import java.util.*;

import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.BaseConstants;
import grafiosch.dto.MailSendForwardDefaultBase;
import grafiosch.entities.*;
import grafiosch.repository.*;
import grafiosch.service.*;
import grafiosch.types.*;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.entities.AlgoMessageAlert;
import grafioschtrader.repository.*;
import grafioschtrader.types.MessageGTComType;

/** Durable per-channel delivery. Database leases fence retries; SMTP is performed outside database transactions. */
@Service
public class AlgoAlarmDeliveryService {
  private final AlgoMessageAlertJpaRepository alarms;
  private final TenantJpaRepository tenants;
  private final AlgoStrategyJpaRepository strategies;
  private final UserJpaRepository users;
  private final TenantAccessJpaRepository access;
  private final MailSettingForwardJpaRepository settings;
  private final MailEntityJpaRepository mailEntities;
  private final SendMailInternalExternalService mail;
  private final MailExternalService smtp;
  private final MessageSource messages;
  private final FeatureConfig features;
  private final TransactionTemplate transaction;
  private Clock clock = Clock.systemUTC();

  public AlgoAlarmDeliveryService(AlgoMessageAlertJpaRepository alarms, TenantJpaRepository tenants,
      AlgoStrategyJpaRepository strategies, UserJpaRepository users, TenantAccessJpaRepository access,
      MailSettingForwardJpaRepository settings, MailEntityJpaRepository mailEntities,
      SendMailInternalExternalService mail, MailExternalService smtp, MessageSource messages, FeatureConfig features,
      PlatformTransactionManager manager) {
    this.alarms = alarms;
    this.tenants = tenants;
    this.strategies = strategies;
    this.users = users;
    this.access = access;
    this.settings = settings;
    this.mailEntities = mailEntities;
    this.mail = mail;
    this.smtp = smtp;
    this.messages = messages;
    this.features = features;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private boolean enabled() {
    return features.isAlgo() && features.isAlert();
  }

  /** Checks only pending transport work, independently of exchange calendars and evaluation intervals. */
  public boolean hasDueDeliveries() {
    return enabled() && !alarms.dueDeliveries(now(), PageRequest.of(0, 1)).isEmpty();
  }

  /** A bounded batch reserves each alarm immediately before sending it. */
  public void deliverPending() {
    if (!enabled())
      return;
    for (Integer id : alarms.dueDeliveries(now(), PageRequest.of(0, 25))) {
      if (!enabled())
        break;
      String token = UUID.randomUUID().toString();
      try {
        AlgoMessageAlert a = transaction.execute(_ -> claim(id, token));
        if (a == null)
          continue;
        if (needsInternal(a) && a.getInternalCompletedAt() == null) {
          transaction.executeWithoutResult(_ -> completeInternal(id, token));
        }
        a = transaction.execute(_ -> authorizedClaim(id, token));
        if (a == null)
          continue;
        if (needsExternal(a) && a.getExternalCompletedAt() == null) {
          smtp.sendSimpleMessage(a.getRecipientEmail(), "GT: " + a.getDeliverySubject(), a.getDeliveryBody());
          transaction.executeWithoutResult(_ -> {
            AlgoMessageAlert locked = locked(id, token);
            if (locked != null)
              locked.setExternalCompletedAt(now());
          });
        }
        transaction.executeWithoutResult(_ -> {
          AlgoMessageAlert locked = locked(id, token);
          if (locked != null) {
            locked.setNotifiedAt(now());
            locked.setDeliveryStatus("DELIVERED");
            clearLease(locked);
            locked.setDeliveryError(null);
            locked.setNextAttemptAt(null);
          }
        });
      } catch (Exception e) {
        transaction.executeWithoutResult(_ -> failed(id, token, e));
      }
    }
  }

  /** Owner resolution never uses a tenant id as a user id and never selects a viewer arbitrarily. */
  User recipient(Integer tenantId) {
    var tenant = tenants.findById(tenantId).orElseThrow(() -> new IllegalStateException("Tenant no longer exists"));
    List<User> owners = users.findByIdTenantAndHomeTenantReadOnlyFalse(tenantId);
    if (owners.size() == 1 && owners.getFirst().isEnabled())
      return owners.getFirst();
    if (!owners.isEmpty())
      throw new IllegalStateException("Tenant has no unambiguous enabled owner");
    Integer creator = tenant.getCreateIdUser();
    if (creator != null && access.findByIdUserAndIdTenant(creator, tenantId)
        .filter(a -> a.getAccessLevel() == TenantAccessLevel.MANAGE).isPresent()) {
      return users.findById(creator).filter(User::isEnabled)
          .orElseThrow(() -> new IllegalStateException("Managing advisor is unavailable"));
    }
    throw new IllegalStateException("No authorized alert recipient");
  }

  private AlgoMessageAlert claim(Integer id, String token) {
    AlgoMessageAlert a = alarms.lockDelivery(id).orElse(null);
    if (a == null || !Set.of("PENDING", "RETRY", "SENDING").contains(a.getDeliveryStatus())
        || a.getDeliveryLeaseUntil() != null && a.getDeliveryLeaseUntil().isAfter(now())
        || a.getNextAttemptAt() != null && a.getNextAttemptAt().isAfter(now()))
      return null;
    a.setDeliveryLeaseToken(token);
    a.setDeliveryLeaseUntil(now().plusMinutes(5));
    a.setDeliveryStatus("SENDING");
    // Reserve durably even when recipient resolution or rendering will fail on the next transaction.
    return a;
  }

  private AlgoMessageAlert authorizedClaim(Integer id, String token) {
    AlgoMessageAlert a = locked(id, token);
    if (a == null)
      return null;
    if (!tenants.existsById(a.getIdTenant()) || !strategies.existsById(a.getIdAlgoStrategy())) {
      a.setDeliveryStatus("CANCELLED");
      clearLease(a);
      return null;
    }
    User owner = recipient(a.getIdTenant());
    if (a.getRecipientUserId() != null && (!a.getRecipientUserId().equals(owner.getIdUser())
        || !Objects.equals(a.getRecipientEmail(), owner.getUsername()))) {
      a.setDeliveryStatus("REVIEW_REQUIRED");
      a.setDeliveryError("Recipient changed; review before retry");
      clearLease(a);
      return null;
    }
    if (a.getRecipientUserId() == null) {
      var setting = settings.findByIdUserAndMessageComType(owner.getIdUser(),
          MessageGTComType.USER_ALGO_ALARM_TRIGGERED.getValue());
      MessageTargetType channels = setting.map(MailSettingForward::getMessageTargetType)
          .orElseGet(() -> MailSendForwardDefaultBase.mailSendForwardDefaultMap
              .get(MessageGTComType.USER_ALGO_ALARM_TRIGGERED).messageTargetDefaultType);
      if (channels == MessageTargetType.NO_MAIL)
        throw new IllegalStateException("No notification channel selected");
      Locale locale = owner.getLocaleStr() == null ? Locale.ENGLISH : owner.createAndGetJavaLocale();
      String subject = messages.getMessage("algo.alarm.subject",
          new Object[] { a.getContextName() == null ? a.getIdAlgoStrategy() : a.getContextName() }, locale);
      String body = messages.getMessage("algo.alarm.mail.body.prefix", null, locale) + "\n"
          + (a.getSecurityName() == null ? a.getIdSecurityCurrency() : a.getSecurityName()) + "\n"
          + a.getAlarmDetails();
      a.setRecipientUserId(owner.getIdUser());
      a.setRecipientEmail(owner.getUsername());
      a.setDeliveryChannels(channels.name());
      a.setDeliverySubject(subject);
      a.setDeliveryBody(mail.renderExternalMessage(BaseConstants.SYSTEM_ID_USER, body, locale));
    }
    return a;
  }

  private void completeInternal(Integer id, String token) {
    AlgoMessageAlert a = authorizedClaim(id, token);
    if (a == null || !needsInternal(a) || a.getInternalCompletedAt() != null)
      return;
    Integer messageId = mail.sendInternalMail(BaseConstants.SYSTEM_ID_USER, a.getRecipientUserId(),
        a.getDeliverySubject(), a.getDeliveryBody());
    MailEntity link = new MailEntity(MessageGTComType.USER_ALGO_ALARM_TRIGGERED, a.getIdAlgoStrategy(),
        a.getAlertDay());
    link.setIdMailSendRecv(messageId);
    mailEntities.save(link);
    a.setInternalMessageId(messageId);
    a.setInternalCompletedAt(now());
  }

  private static boolean needsInternal(AlgoMessageAlert a) {
    return a.getDeliveryChannels() == null || !a.getDeliveryChannels().equals(MessageTargetType.EXTERNAL_MAIL.name());
  }

  private static boolean needsExternal(AlgoMessageAlert a) {
    return !MessageTargetType.INTERNAL_MAIL.name().equals(a.getDeliveryChannels());
  }

  private AlgoMessageAlert locked(Integer id, String token) {
    return alarms.lockDelivery(id).filter(a -> token.equals(a.getDeliveryLeaseToken())
        && a.getDeliveryLeaseUntil() != null && a.getDeliveryLeaseUntil().isAfter(now())).orElse(null);
  }

  private static void clearLease(AlgoMessageAlert a) {
    a.setDeliveryLeaseToken(null);
    a.setDeliveryLeaseUntil(null);
  }

  static long retryMinutes(int attempts) {
    return attempts == 1 ? 5 : attempts == 2 ? 15 : attempts == 3 ? 60 : 360;
  }

  private void failed(Integer id, String token, Exception e) {
    AlgoMessageAlert a = locked(id, token);
    if (a == null)
      return;
    int attempts = a.getDeliveryAttempts() + 1;
    a.setDeliveryAttempts(attempts);
    a.setDeliveryStatus(attempts >= 8 ? "FAILED" : "RETRY");
    a.setNextAttemptAt(attempts >= 8 ? null : now().plusMinutes(retryMinutes(attempts)));
    String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    a.setDeliveryError(error.substring(0, Math.min(error.length(), 1000)));
    clearLease(a);
  }

  /** Explicit review queues one existing alarm, retaining completed channels and never duplicating an active job. */
  public void retry(Integer tenant, Integer id) {
    transaction.executeWithoutResult(_ -> {
      AlgoMessageAlert a = alarms.lockDelivery(id).filter(x -> tenant.equals(x.getIdTenant()))
          .orElseThrow(() -> new grafiosch.exceptions.ResourceNotFoundException(id));
      if (!Set.of("FAILED", "REVIEW_REQUIRED").contains(a.getDeliveryStatus()))
        return;
      User owner = recipient(tenant);
      if (a.getRecipientUserId() != null && !a.getRecipientUserId().equals(owner.getIdUser())) {
        a.setRecipientUserId(null);
        a.setRecipientEmail(null);
        a.setDeliverySubject(null);
        a.setDeliveryBody(null);
      }
      if (a.getRecipientUserId() != null)
        a.setRecipientEmail(owner.getUsername());
      a.setDeliveryStatus("PENDING");
      a.setDeliveryAttempts(0);
      a.setNextAttemptAt(null);
      clearLease(a);
    });
  }
}
