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

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.MailEntity;
import grafioschtrader.entities.MailSendRecv;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.MailEntityJpaRepository;
import grafioschtrader.repository.MailSettingForwardJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository.CheckSecurityTransIntegrity;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.MessageComType;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;
import jakarta.mail.MessagingException;

@Component
public class CheckInactiveSecurityAndDividendeInterest implements ITask {

  @Autowired
  private MailSettingForwardJpaRepository mailSettingForwardJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private MailEntityJpaRepository mailEntityJpaRepository;

  @Autowired
  private MessageSource messageSource;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.CHECK_INACTIVE_SECURITY_AND_DIVIDEND_INTEREST;
  }

  @Scheduled(cron = "${gt.check.inactive.dividend}", zone = GlobalConstants.TIME_ZONE)
  public void checkInactiveSecurityAndDivInterest() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_LOW);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    processCheckSecurityTransIntegrityList(securityJpaRepository.getHoldingsOfInactiveSecurties(),
        MessageComType.USER_SECURITY_HELD_INACTIVE, "gt.holding.inactive.security");
    processCheckSecurityTransIntegrityList(securityJpaRepository.getPossibleMissingDivInterest(),
        MessageComType.USER_SECURITY_MISSING_DIV_INTEREST, "gt.possible.missing.div.interest");
  }

  private void processCheckSecurityTransIntegrityList(List<CheckSecurityTransIntegrity> cstiList,
      MessageComType messageComType, String msg) throws TaskBackgroundException {
    CheckSecurityTransIntegrity lastCsti = null;
    StringBuilder compoundMsg = new StringBuilder();
    Locale locale = null;
    List<MailEntity> mailEntityList = new ArrayList<>();
    List<String> msgException = new ArrayList<>();
    for (CheckSecurityTransIntegrity csti : cstiList) {
      if (lastCsti == null || csti.getIdUser() != lastCsti.getIdUser()) {
        if (lastCsti != null) {
          this.sendMail(msg, compoundMsg, mailEntityList, locale, msgException, lastCsti.getIdUser());
        }
        locale = Locale.forLanguageTag(csti.getLocaleStr());
        compoundMsg.setLength(0);
        mailEntityList.clear();
      } else {
        if (compoundMsg.length() < MailSendRecv.MAX_TEXT_LENGTH) {
          compoundMsg.append(GlobalConstants.NEW_LINE);
        }
      }
      String msgTransformed = messageSource.getMessage(msg,
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
      this.sendMail(msg, compoundMsg, mailEntityList, locale, msgException, lastCsti.getIdUser());
    }
    if (!msgException.isEmpty()) {
      throw new TaskBackgroundException("gt.external.mail.failure", msgException, false);
    }

  }

  private void sendMail(String msg, StringBuilder compoundMsg, List<MailEntity> mailEntityList, Locale locale,
      List<String> msgException, Integer idUser) {
    try {
      String subject = messageSource.getMessage(msg + ".subject", null, locale);
      Integer idMailSendRecv = mailSettingForwardJpaRepository.sendMailInternOrExternal(0, idUser, subject,
          compoundMsg.toString(), MessageComType.USER_SECURITY_MISSING_DIV_INTEREST);
      if (idMailSendRecv != null) {
        mailEntityList.forEach(me -> me.setIdMailSendRecv(idMailSendRecv));
      }
      mailEntityJpaRepository.saveAll(mailEntityList);
    } catch (MessagingException e) {
      msgException.add(e.getLocalizedMessage());
    }
  }

}
