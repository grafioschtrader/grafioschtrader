package grafioschtrader.task.exec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.MailSendRecv;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.service.SendMailInternalExternalService;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.dto.HoldConsistencyDefect;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.types.MessageGTComType;
import grafioschtrader.types.TaskTypeExtended;
import jakarta.mail.MessagingException;

/**
 * Daily check that the three hold tables still agree with the transactions they are derived from, reporting any drift to
 * the main administrator.
 *
 * <p>
 * The hold tables are maintained incrementally by {@code TransactionJpaRepositoryImpl}. Every write that bypasses that
 * class desynchronises them, and nothing reconciles them on a schedule:
 * {@code REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT} is only enqueued at startup when the task table is empty, i.e. on a
 * database restored from an export. Without this check, drift stays invisible until a performance report looks wrong.
 * </p>
 *
 * <p>
 * <strong>It reports, it does not repair.</strong> A rebuild of a tenant is expensive and replaces data wholesale, so
 * the decision stays with the administrator, who can queue
 * {@code REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT} for the reported tenant from the task administration. Nothing is sent
 * when everything agrees.
 * </p>
 *
 * <p>
 * The row-level detail lives in {@code scripts/check-hold-tables.mjs}; this task only counts, so that the result fits
 * into one mail and stays cheap when there is nothing wrong.
 * </p>
 */
@Component
public class HoldTableConsistencyCheckTask implements ITask {

  /**
   * Absolute tolerance for the cash amount comparisons. It applies to the columns the writer stores unrounded, where it
   * only has to absorb floating point noise. {@code hold_cashaccount_balance.balance} is the exception: it is rounded
   * to the account currency's precision on write, so its query derives its own tolerance of one currency unit from
   * {@code gt.currency.precision} and ignores this value.
   */
  private static final double CASH_TOLERANCE = 0.005;

  /** Relative tolerance for the holdings comparison, absorbing the float error of the split factor product. */
  private static final double HOLDINGS_TOLERANCE = 0.000001;

  /** Message key prefix for the user-facing name of a hold table, completed with the table name. */
  private static final String TABLE_KEY_PREFIX = "gt.hold.consistency.table.";

  /** Message key prefix for the user-facing name of a deviation kind, completed with the kind as the query returns it. */
  private static final String KIND_KEY_PREFIX = "gt.hold.consistency.kind.";

  @Autowired
  private HoldCashaccountBalanceJpaRepository holdCashaccountBalanceJpaRepository;

  @Autowired
  private HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Autowired
  private SendMailInternalExternalService sendMailInternalExternalService;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private MessageSource messageSource;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.HOLD_TABLE_CONSISTENCY_CHECK;
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }

  // getAllowedEntities() is deliberately not overridden: the check always covers every tenant, so it takes no entity.
  // The inherited null excludes the task from TaskDataChangeFormConstraints.taskTypeConfig, which is what makes the
  // create form hide the entity selection. Returning List.of("") would instead register an entity configuration whose
  // only member is the empty entity, and the form would offer an enabled but unusable entity select. Creatability is
  // unaffected — it is governed solely by the task type value against maxUserCreateTask.

  @Scheduled(cron = "${gt.hold.consistency.check}", zone = BaseConstants.TIME_ZONE)
  public void checkHoldTableConsistency() {
    taskDataChangeJpaRepository.save(new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_LOW));
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    Map<String, List<HoldConsistencyDefect>> defectsPerTable = new LinkedHashMap<>();
    defectsPerTable.put("hold_cashaccount_balance",
        holdCashaccountBalanceJpaRepository.countConsistencyDefects(CASH_TOLERANCE));
    defectsPerTable.put("hold_cashaccount_deposit",
        holdCashaccountDepositJpaRepository.countConsistencyDefects(CASH_TOLERANCE));
    defectsPerTable.put("hold_securityaccount_security",
        holdSecurityaccountSecurityJpaRepository.countConsistencyDefects(HOLDINGS_TOLERANCE));

    if (defectsPerTable.values().stream().allMatch(List::isEmpty)) {
      return;
    }
    sendMailToMainAdmin(buildMessage(defectsPerTable));
  }

  /**
   * Renders the findings as one plain text block, grouped by table, truncated to what a
   * {@link MailSendRecv#MAX_TEXT_LENGTH} message can carry.
   *
   * <p>
   * Rendered in the main administrator's language, the same one the subject is resolved in. The table name and the
   * deviation kind arrive from the queries as internal identifiers and are translated here, so that the report does not
   * confront the recipient with database table names and English constants.
   * </p>
   *
   * @param defectsPerTable the findings per hold table, in reporting order
   * @return the message body
   */
  private String buildMessage(Map<String, List<HoldConsistencyDefect>> defectsPerTable) {
    Locale locale = sendMailInternalExternalService.getMainAdminLocale();
    StringBuilder body = new StringBuilder(
        messageSource.getMessage("gt.hold.consistency.intro", null, locale)).append(BaseConstants.NEW_LINE);
    List<String> lines = new ArrayList<>();
    for (Map.Entry<String, List<HoldConsistencyDefect>> entry : defectsPerTable.entrySet()) {
      String tableName = translate(TABLE_KEY_PREFIX, entry.getKey(), locale);
      for (HoldConsistencyDefect defect : entry.getValue()) {
        lines.add(messageSource.getMessage("gt.hold.consistency.defect", new Object[] { tableName,
            defect.getIdTenant(), translate(KIND_KEY_PREFIX, defect.getDefect(), locale), defect.getDefectCount() },
            locale));
      }
    }
    for (String line : lines) {
      if (body.length() + line.length() + BaseConstants.NEW_LINE.length() > MailSendRecv.MAX_TEXT_LENGTH) {
        break;
      }
      body.append(BaseConstants.NEW_LINE).append(line);
    }
    return body.toString();
  }

  /**
   * Translates one internal identifier coming out of the consistency queries.
   *
   * <p>
   * The identifier itself is the fallback: a defect kind added to a query without its message key still produces a
   * readable, if untranslated, report rather than failing the whole task with a missing-key error.
   * </p>
   *
   * @param keyPrefix  {@link #TABLE_KEY_PREFIX} or {@link #KIND_KEY_PREFIX}
   * @param identifier the table name or defect kind as the query returned it
   * @param locale     the recipient's locale
   * @return the translated text, or the identifier itself when no key exists for it
   */
  private String translate(String keyPrefix, String identifier, Locale locale) {
    return messageSource.getMessage(keyPrefix + identifier, null, identifier, locale);
  }

  /**
   * Delivers the report to the main administrator. A mail failure is surfaced as a task failure so that it shows up in
   * the task administration rather than being swallowed.
   *
   * @param message the rendered report
   * @throws TaskBackgroundException when the mail could not be delivered
   */
  private void sendMailToMainAdmin(String message) throws TaskBackgroundException {
    try {
      sendMailInternalExternalService.sendMailToMainAdminInternalOrExternal(BaseConstants.SYSTEM_ID_USER,
          "gt.hold.consistency.subject", null, message, MessageGTComType.MAIN_ADMIN_HOLD_TABLE_INCONSISTENT);
    } catch (MessagingException e) {
      throw new TaskBackgroundException("gt.external.mail.failure", List.of(e.getLocalizedMessage()), false);
    }
  }
}
