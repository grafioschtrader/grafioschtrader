package grafioschtrader.task.exec;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.MailEntity;
import grafiosch.entities.MailSendRecv;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.MailEntityJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.service.SendMailInternalExternalService;
import grafiosch.task.ITask;
import grafiosch.types.IMessageComType;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.GlobalConstants;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository.CheckSecurityTransIntegrity;
import grafioschtrader.types.MessageGTComType;
import grafioschtrader.types.TaskTypeExtended;
import jakarta.mail.MessagingException;

/**
 * This involves checking whether dividends or interest for positions held are recorded in the transactions. This
 * background job should be carried out daily. It also checks whether there are open positions for an instrument that is
 * no longer traded. There are two different procedures for determining any missing dividend or interest payments.
 */
@Component
public class CheckInactiveSecurityAndDividendeInterestTask implements ITask {

  @Autowired
  private SendMailInternalExternalService sendMailInternalExternalService;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private MailEntityJpaRepository mailEntityJpaRepository;

  @Autowired
  private MessageSource messageSource;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.CHECK_INACTIVE_SECURITY_AND_DIVIDEND_INTEREST;
  }

  @Scheduled(cron = "${gt.check.inactive.dividend}", zone = BaseConstants.TIME_ZONE)
  public void checkInactiveSecurityAndDivInterest() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_LOW);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    processCheckSecurityTransIntegrityList(securityJpaRepository.getHoldingsOfInactiveSecurties(),
        MessageGTComType.USER_SECURITY_HELD_INACTIVE, "gt.holding.inactive.security");
    processCheckSecurityTransIntegrityList(securityJpaRepository.getPossibleMissingDivInterestByFrequency(),
        MessageGTComType.USER_SECURITY_MISSING_DIV_INTEREST, "gt.possible.missing.div.frequency.interest");
    processCheckSecurityTransIntegrityList(
        securityJpaRepository.getPossibleMissingDividentsByDividendTable(GlobalConstants.DIVIDEND_CHECK_DAYS_LOOK_BACK,
            GlobalConstants.DIVIDEND_CHECK_PAY_DATE_TOLERANCE_IN_DAYS),
        MessageGTComType.USER_SECURITY_MISSING_DIV_INTEREST, "gt.possible.missing.div.divtable.interest");
  }

  private void processCheckSecurityTransIntegrityList(List<CheckSecurityTransIntegrity> cstiList,
      IMessageComType messageComType, String msgKey) throws TaskBackgroundException {
    CheckSecurityTransIntegrity lastCsti = null;
    StringBuilder compoundMsg = new StringBuilder();
    Locale locale = null;
    List<MailEntity> mailEntityList = new ArrayList<>();
    List<String> msgException = new ArrayList<>();
    for (CheckSecurityTransIntegrity csti : cstiList) {
      if (lastCsti == null || csti.getIdUser() != lastCsti.getIdUser()) {
        if (lastCsti != null) {
          this.sendMail(msgKey, compoundMsg, mailEntityList, locale, msgException, lastCsti.getIdUser(),
              messageComType);
        }
        locale = Locale.forLanguageTag(csti.getLocaleStr());
        compoundMsg.setLength(0);
        mailEntityList.clear();
      } else {
        if (compoundMsg.length() < MailSendRecv.MAX_TEXT_LENGTH) {
          compoundMsg.append(BaseConstants.NEW_LINE);
        }
      }
      String msgTransformed = messageSource.getMessage(msgKey,
          new Object[] { csti.getName(), csti.getCurrency(),
              csti.getMarkDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)) },
          locale);
      if (compoundMsg.length() + msgTransformed.length() <= MailSendRecv.MAX_TEXT_LENGTH) {
        compoundMsg.append(msgTransformed);
        mailEntityList.add(new MailEntity(messageComType, csti.getIdSecuritycurrency(), csti.getMarkDate()));
      }
      lastCsti = csti;
    }
    if (compoundMsg.length() > 0) {
      this.sendMail(msgKey, compoundMsg, mailEntityList, locale, msgException, lastCsti.getIdUser(), messageComType);
    }
    if (!msgException.isEmpty()) {
      throw new TaskBackgroundException("gt.external.mail.failure", msgException, false);
    }

  }

  private void sendMail(String msgKey, StringBuilder compoundMsg, List<MailEntity> mailEntityList, Locale locale,
      List<String> msgException, Integer idUser, IMessageComType messageComType) {
    try {
      String subject = messageSource.getMessage(msgKey + ".subject", null, locale);
      Integer idMailSendRecv = sendMailInternalExternalService.sendMailInternAndOrExternal(BaseConstants.SYSTEM_ID_USER,
          idUser, subject, compoundMsg.toString(), messageComType);
      if (idMailSendRecv != null) {
        mailEntityList.forEach(me -> me.setIdMailSendRecv(idMailSendRecv));
      }
      mailEntityJpaRepository.saveAll(mailEntityList);
    } catch (MessagingException e) {
      msgException.add(e.getLocalizedMessage());
    }
  }

}
