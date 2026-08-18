package grafioschtrader.repository;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.common.UserAccessHelper;
import grafiosch.entities.Globalparameters;
import grafiosch.entities.ProposeChangeEntity;
import grafiosch.entities.ProposeChangeField;
import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.repository.ProposeChangeEntityJpaRepository;
import grafiosch.repository.ProposeChangeFieldJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.service.DailyLimitService;
import grafiosch.types.OperationType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.GlobalConstants;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.dto.IMinMaxDateHistoryquote;
import grafioschtrader.dto.SecuritysplitDeleteAndCreateMultiple;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.repository.SecurityJpaRepository.SplitAdjustedHistoryquotes;
import grafioschtrader.repository.SecurityJpaRepository.SplitAdjustedHistoryquotesResult;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.TaskTypeExtended;

public class SecuritysplitJpaRepositoryImpl implements SecuritysplitJpaRepositoryCustom {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private SecurityActionJpaRepository securityActionJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private ProposeChangeEntityJpaRepository proposeChangeEntityJpaRepository;

  @Autowired
  private ProposeChangeFieldJpaRepository proposeChangeFieldJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired(required = false)
  private List<IFeedConnector> feedConnectors = new ArrayList<>();

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private DailyLimitService dailyLimitService;

  @Override
  public Map<Integer, List<Securitysplit>> getSecuritysplitMapByIdTenant(final Integer idTenant) {
    return getMapForList(securitysplitJpaRepository.getByIdTenant(idTenant));
  }

  @Override
  public Map<Integer, List<Securitysplit>> getSecuritysplitMapByIdSecuritycashaccount(
      final Integer idSecuritycashaccount) {
    return getMapForList(securitysplitJpaRepository.getByIdSecuritycashaccount(idSecuritycashaccount));
  }

  @Override
  public Map<Integer, List<Securitysplit>> getSecuritysplitMapByIdWatchlist(final Integer idWatchlist) {
    return getMapForList(securitysplitJpaRepository.getByIdWatchlist(idWatchlist));
  }

  @Override
  public Map<Integer, List<Securitysplit>> getSecuritysplitMapByIdSecuritycurrency(final Integer idSecuritycurrency) {
    return getMapForList(securitysplitJpaRepository.findByIdSecuritycurrencyOrderBySplitDateAsc(idSecuritycurrency));
  }

  @Override
  @Transactional
  @Modifying
  public List<Securitysplit> deleteAndCreateMultiple(SecuritysplitDeleteAndCreateMultiple sdacm) {
    int maxSplits = globalparametersService.getMaxInstrumentSplits();
    if (sdacm.getSecuritysplits().length > maxSplits) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.max.instrument.splits.exceeded",
          new Object[] { maxSplits });
    }
    final BiPredicate<Securitysplit, Securitysplit> splitCompare = (ss1,
        ss2) -> ss1.getSplitDate().equals(ss2.getSplitDate()) && ss1.getFromFactor().equals(ss2.getFromFactor())
            && ss1.getToFactor().equals(ss2.getToFactor());

    List<Securitysplit> securitysplitsExisting = securitysplitJpaRepository
        .findByIdSecuritycurrencyOrderBySplitDateAsc(sdacm.idSecuritycurrency);
    if (!DataBusinessHelper.compareCollectionsUnSorted(Arrays.asList(sdacm.getSecuritysplits()), securitysplitsExisting,
        splitCompare)) {
      // Security split has changed
      Optional<Security> securityOpt = securityJpaRepository.findById(sdacm.idSecuritycurrency);
      if (securityOpt.isPresent()) {
        return changeSecuritySplit(securityOpt.get(), sdacm, securitysplitsExisting);
      }
    }
    return securitysplitsExisting;
  }

  private List<Securitysplit> changeSecuritySplit(Security security, SecuritysplitDeleteAndCreateMultiple sdacm,
      List<Securitysplit> securitysplitsExisting) {

    boolean reloadHistoricalData = false;
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, security)) {
      // User can edit direct or edit a proposed edit

      for (final Securitysplit securitysplit : sdacm.getSecuritysplits()) {
        securitysplit.setIdSecuritycurrency(sdacm.idSecuritycurrency);
        securitysplit.setIdSecuritysplit(null);
        if (!security.isDerivedInstrument() && security.getFullLoadTimestamp() != null
            && !securitysplit.getSplitDate().isBefore(security.getFullLoadTimestamp().toLocalDate())) {
          // Date of last full load of history quote is older then youngest split date - >
          // Quotes must be reloaded
          reloadHistoricalData = true;
        }
      }
      securitysplitJpaRepository.deleteByIdSecuritycurrency(sdacm.idSecuritycurrency);
      if (security.canHaveSplitConnector()) {
        securitysplitsExisting = securitysplitJpaRepository
            .saveAll(new ArrayList<>(Arrays.asList(sdacm.getSecuritysplits())));

        if (reloadHistoricalData) {
          securityJpaRepository.reloadAsyncFullHistoryquote(security);
        }
      } else {
        securitysplitsExisting = new ArrayList<>();
      }
      // Adjust holdings
      taskDataChangeJpaRepository
          .save(new TaskDataChange(TaskTypeExtended.HOLDINGS_SECURITY_REBUILD, TaskDataExecPriority.PRIO_NORMAL,
              LocalDateTime.now(), security.getIdSecuritycurrency(), Security.class.getSimpleName()));
    } else {
      // User can't change splits directly if another user created the security ->
      // create a proposal change. This branch does not pass through UpdateCreate, so it books the daily budget of the
      // instrument itself: one unit of Security, the same unit UpdateCreate.checkProposeChangeAndSave charges for a
      // proposal on a Security. Without it the propose tables grow unbounded - propose_change_entity has no unique key
      // and the branch never mutates the splits, so compareCollectionsUnSorted never short-circuits a replay.
      dailyLimitService.check(user, Security.class.getSimpleName(), 1);
      final ProposeChangeEntity proposeChangeEntityNew = proposeChangeEntityJpaRepository.save(new ProposeChangeEntity(
          security.getClass().getSimpleName(), security.getId(), security.getCreatedBy(), sdacm.getNoteRequest()));
      proposeChangeFieldJpaRepository.save(new ProposeChangeField(Security.SPLIT_ARRAY,
          SerializationUtils.serialize(sdacm.getSecuritysplits()), proposeChangeEntityNew.getIdProposeRequest()));
      dailyLimitService.log(user.getIdUser(), Security.class.getSimpleName(), OperationType.ADD, 1);
    }
    return securitysplitsExisting;

  }

  private Map<Integer, List<Securitysplit>> getMapForList(final List<Securitysplit> securitysplits) {
    return securitysplits.stream()
        .collect(Collectors.groupingBy(Securitysplit::getIdSecuritycurrency, Collectors.toList()));
  }

  @Override
  public List<String> loadAllSplitDataFromConnectorForSecurity(Security security, LocalDate requestedSplitdate) {
    List<String> errorMessages = new ArrayList<>();
    if (globalparametersService.getMaxSplitRetry() > security.getRetrySplitLoad()) {
      short retrySplitLoad = security.getRetrySplitLoad();
      try {
        IFeedConnector connector = ConnectorHelper.getConnectorByConnectorId(feedConnectors,
            security.getIdConnectorSplit(), IFeedConnector.FeedSupport.FS_SPLIT);
        List<Securitysplit> securitysplitsRead = connector.getSplitHistory(security,
            LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY), getSplitToDate());
        updateSplitData(security, securitysplitsRead, requestedSplitdate);
        retrySplitLoad = 0;
      } catch (ParseException pe) {
        log.error(pe.getMessage() + "Offset: " + pe.getErrorOffset(), pe);
        errorMessages.add(pe.getMessage());
      } catch (final Exception ex) {
        retrySplitLoad++;
        log.error(ex.getMessage() + " " + security, ex);
        errorMessages.add(ex.getMessage());
      }
      security.setRetrySplitLoad(retrySplitLoad);
      securityJpaRepository.save(security);
    }
    return errorMessages;
  }

  private LocalDate getSplitToDate() {
    Optional<Globalparameters> gpLastAppend = globalparametersService
        .getGlobalparametersByProperty(GlobalParamKeyDefault.GLOB_KEY_YOUNGEST_SPLIT_APPEND_DATE);
    return gpLastAppend.isPresent() ? gpLastAppend.get().getPropertyDate() : LocalDate.now().minusDays(1);
  }

  /**
   * Reads the splits through the connector and deletes the previous automatically created splits.
   *
   * @param security
   * @param securitysplitsRead The splits read from the persistence.
   * @param requestedSplitdate The date of this split is the most recent and comes from the split calendar. It can be
   *                           Null if it does not come from a split calendar.
   * @throws Exception
   */
  private void updateSplitData(Security security, List<Securitysplit> securitysplitsRead, LocalDate requestedSplitdate)
      throws Exception {
    securitysplitsRead = removeSplitsOfPredecessor(security, securitysplitsRead);
    securitysplitJpaRepository.deleteByIdSecuritycurrencyAndCreateType(security.getIdSecuritycurrency(),
        CreateType.CONNECTOR_CREATED.getValue());
    List<Securitysplit> existingSplits = securitysplitJpaRepository
        .findByIdSecuritycurrencyOrderBySplitDateAsc(security.getIdSecuritycurrency());
    List<Securitysplit> createdSplits = DividendSplitsHelper.updateDividendSplitData(security, securitysplitsRead,
        existingSplits, this.securitysplitJpaRepository);
    Optional<LocalDate> youngestSplitDate = createdSplits.stream().map(Securitysplit::getSplitDate).max(LocalDate::compareTo);

    if (requestedSplitdate == null
        || !youngestSplitDate.isEmpty() && youngestSplitDate.get().isEqual(requestedSplitdate)) {
      // The requested split could be read via the connector or there is no requested
      // split.
      historicalDataUpdateWhenAdjusted(security, securitysplitsRead, youngestSplitDate, !createdSplits.isEmpty(),
          requestedSplitdate != null);
    } else {
      // The expected split is not yet mapped via the connector.
      if (ChronoUnit.DAYS.between(requestedSplitdate, LocalDate.now())
          <= GlobalConstants.MAX_DAYS_FOR_SECURITY_IS_REFLECTING_SPLIT) {
        var taskDataChange = new TaskDataChange(TaskTypeExtended.SECURITY_SPLIT_UPDATE_FOR_SECURITY,
            TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusDays(1), security.getIdSecuritycurrency(),
            Security.class.getSimpleName());
        taskDataChange.setOldValueString(requestedSplitdate.toString());
        taskDataChangeJpaRepository.save(taskDataChange);
      }
    }
  }

  /**
   * Drops the connector-read splits that belong to the predecessor of an ISIN change.
   * <p>
   * The successor of a {@link grafioschtrader.entities.SecurityAction} normally keeps the ticker symbol of the old
   * instrument, so the split connector reports the very corporate event that triggered the ISIN change — and every
   * older one — for the new security as well. Its own price history only starts at the change, therefore a split dated
   * at or before its oldest quote never shaped that history; keeping it would convert the transactions generated by the
   * ISIN change a second time, on top of the From/To factor of the action.
   * </p>
   * <p>
   * The filter is restricted to successors of an ISIN change on purpose. For an ordinary security whose connector
   * delivers a shorter history than the transactions of a tenant reach back, an old split still applies correctly.
   * </p>
   *
   * @param security           the security the splits were read for
   * @param securitysplitsRead the splits delivered by the connector
   * @return the splits to keep, unchanged when the security is not the successor of an ISIN change or has no history
   *         yet
   */
  private List<Securitysplit> removeSplitsOfPredecessor(Security security, List<Securitysplit> securitysplitsRead) {
    if (securitysplitsRead.isEmpty()
        || !securityActionJpaRepository.existsBySecurityNew_IdSecuritycurrency(security.getIdSecuritycurrency())) {
      return securitysplitsRead;
    }
    LocalDate oldestQuoteDate = historyquoteJpaRepository
        .getMinMaxDateByIdSecuritycurrencyIds(List.of(security.getIdSecuritycurrency())).stream().findFirst()
        .map(IMinMaxDateHistoryquote::getMinDate).orElse(null);
    return oldestQuoteDate == null ? securitysplitsRead
        : securitysplitsRead.stream().filter(ss -> ss.getSplitDate().isAfter(oldestQuoteDate)).toList();
  }

  @Override
  public void historicalDataUpdateWhenAdjusted(Security security, List<Securitysplit> securitysplits,
      Optional<LocalDate> youngestSplitDate, boolean requireHoldingBuild, boolean originSplitCalendar) throws Exception {
    SplitAdjustedHistoryquotesResult sahr = securityJpaRepository.isLatestSplitHistoryquotePossibleAdjusted(security,
        securitysplits);

    log.info("SplitAdjust: {}, Full load timestamp: {}, Next attempt: {}", sahr.sah, security.getFullLoadTimestamp(),
        sahr.addDaysForNextAttempt);
    if (sahr.sah == SplitAdjustedHistoryquotes.ADJUSTED_NOT_LOADED
        || (!youngestSplitDate.isEmpty() && !originSplitCalendar && security.getFullLoadTimestamp() != null
            && youngestSplitDate.get().isAfter(security.getFullLoadTimestamp().toLocalDate()))) {
      // The historical price data must be reloaded if the most recent split date is
      // more recent than the last complete load of this historical data.
      if(youngestSplitDate.isEmpty()) {
        log.info("Full load timestamp: {}", security.getFullLoadTimestamp());
      } else {
        log.info("Youngest Split-Date: {}, Full load timestamp: {}", youngestSplitDate.get(),
          security.getFullLoadTimestamp());
      }
      securityJpaRepository.reloadAsyncFullHistoryquote(security);
      if (requireHoldingBuild) {
        taskDataChangeJpaRepository
            .save(new TaskDataChange(TaskTypeExtended.HOLDINGS_SECURITY_REBUILD, TaskDataExecPriority.PRIO_NORMAL,
                LocalDateTime.now(), security.getIdSecuritycurrency(), Security.class.getSimpleName()));
      }
    } else if (security.getFullLoadTimestamp() != null && sahr.addDaysForNextAttempt != null) {
      // The historical price data does not yet reflect the split, so repeat the
      // process in the future.
      taskDataChangeJpaRepository
          .save(new TaskDataChange(TaskTypeExtended.CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES,
              TaskDataExecPriority.PRIO_LOW, LocalDateTime.now().plusDays(sahr.addDaysForNextAttempt),
              security.getIdSecuritycurrency(), Security.class.getSimpleName()));
    }
  }

}
