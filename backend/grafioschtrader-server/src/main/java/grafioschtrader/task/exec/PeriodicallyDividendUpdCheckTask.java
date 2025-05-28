package grafioschtrader.task.exec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.common.NetworkHelper;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.UserJpaRepository.IdUserLocale;
import grafiosch.service.SendMailInternalExternalService;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.types.MessageGTComType;
import grafioschtrader.types.TaskTypeExtended;
import jakarta.mail.MessagingException;

/**
 * The following algorithm is used to determine possible missing dividend income
 * in the dividend entity for securities. This is based on the date of the last
 * dividend payment and the periodicity of the expected payments. In addition,
 * the dividend payments of the transactions are also taken into account if the
 * dividend payment is more recent than the date in the dividend entity.
 *
 */
@Component
public class PeriodicallyDividendUpdCheckTask implements ITask {

  @Autowired
  private DividendJpaRepository dividendJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  private SendMailInternalExternalService sendMailInternalExternalService;

  @Autowired
  private MessageSource messageSource;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Scheduled(cron = "${gt.dividend.update.data}", zone = BaseConstants.TIME_ZONE)
  public void periodicallCheckDividendUpdCheck() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_LOW);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.PERIODICALLY_DIVIDEND_UPDATE_CHECK;
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    List<Security> missingConnectorSecurities = dividendJpaRepository.appendThruDividendCalendar();
    missingDividendConnectorButCalendar(missingConnectorSecurities);
    List<String> errorMessages = dividendJpaRepository.periodicallyUpdate();
    if (!errorMessages.isEmpty()) {
      throw new TaskBackgroundException("gt.dividend.connector.failure", errorMessages, false);
    }
  }

  private void missingDividendConnectorButCalendar(List<Security> missingConnectorSecurities)
      throws TaskBackgroundException {
    List<Integer> userIds = missingConnectorSecurities.stream().map(s -> s.getCreatedBy()).distinct()
        .collect(Collectors.toList());
    List<IdUserLocale> idUserLocales = userJpaRepository.findIdUserAndLocaleStrByIdUsers(userIds);

    Map<Integer, List<Security>> groupedMissing = missingConnectorSecurities.stream()
        .collect(Collectors.groupingBy(Security::getCreatedBy, Collectors.collectingAndThen(Collectors.toList(),
            list -> list.stream().sorted(Comparator.comparing(Security::getName)).collect(Collectors.toList()))));
    List<String> msgException = new ArrayList<>();
    createMails(idUserLocales, groupedMissing, msgException);
    if (!msgException.isEmpty()) {
      throw new TaskBackgroundException("gt.external.mail.failure", msgException, false);
    }
  }

  private void createMails(List<IdUserLocale> idUserLocales, Map<Integer, List<Security>> groupedMissing,
      List<String> msgException) {
    for (IdUserLocale idUserLocale : idUserLocales) {
      Locale locale = Locale.forLanguageTag(idUserLocale.getLocaleStr());
      StringBuilder compoundMsg = new StringBuilder();
      List<Security> securities = groupedMissing.get(idUserLocale.getIdUser());
      for (Security security : securities) {
        compoundMsg.append(messageSource.getMessage(
            "gt.dividend.calenar.security", new Object[] { security.getName(),
                messageSource.getMessage("currency", null, locale), security.getCurrency(), security.getIsin() },
            locale));
        compoundMsg.append(BaseConstants.NEW_LINE);
      }
      sendMail(idUserLocale.getIdUser(), locale, compoundMsg.toString(), msgException);
    }
  }

  private void sendMail(Integer idUser, Locale locale, String message, List<String> msgException) {
    String subject = messageSource.getMessage("gt.dividend.calenar.security.subject",
        new Object[] { NetworkHelper.getIpAddressToOutside() }, locale);
    try {
      sendMailInternalExternalService.sendMailInternAndOrExternal(BaseConstants.SYSTEM_ID_USER, idUser, subject,
          message, MessageGTComType.USER_SECURITY_MISSING_CONNECTOR);
    } catch (MessagingException e) {
      msgException.add(e.getLocalizedMessage());
    }
  }
}
