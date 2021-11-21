package grafioschtrader.repository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.dto.SecuritysplitDeleteAndCreateMultiple;
import grafioschtrader.entities.ProposeChangeEntity;
import grafioschtrader.entities.ProposeChangeField;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.entities.User;
import grafioschtrader.repository.SecurityJpaRepository.SplitAdjustedHistoryquotes;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;

public class SecuritysplitJpaRepositoryImpl implements SecuritysplitJpaRepositoryCustom {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private ProposeChangeEntityJpaRepository proposeChangeEntityJpaRepository;

  @Autowired
  private ProposeChangeFieldJpaRepository proposeChangeFieldJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired(required = false)
  private List<IFeedConnector> feedConnectors = new ArrayList<>();

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
    final BiPredicate<Securitysplit, Securitysplit> splitCompare = (ss1,
        ss2) -> ss1.getSplitDate().equals(ss2.getSplitDate()) && ss1.getFromFactor().equals(ss2.getFromFactor())
            && ss1.getToFactor().equals(ss2.getToFactor());

    List<Securitysplit> securitysplitsExisting = securitysplitJpaRepository
        .findByIdSecuritycurrencyOrderBySplitDateAsc(sdacm.idSecuritycurrency);
    if (!DataHelper.compareCollectionsUnSorted(Arrays.asList(sdacm.getSecuritysplits()), securitysplitsExisting,
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
            && securitysplit.getSplitDate().getTime() >= security.getFullLoadTimestamp().getTime()) {
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
          .save(new TaskDataChange(TaskType.HOLDINGS_SECURITY_REBUILD, TaskDataExecPriority.PRIO_NORMAL,
              LocalDateTime.now(), security.getIdSecuritycurrency(), Security.class.getSimpleName()));

    } else {
      // User can't change splits directly if another user created the security ->
      // create a proposal change
      final ProposeChangeEntity proposeChangeEntityNew = proposeChangeEntityJpaRepository.save(new ProposeChangeEntity(
          security.getClass().getSimpleName(), security.getId(), security.getCreatedBy(), sdacm.getNoteRequest()));
      proposeChangeFieldJpaRepository.save(new ProposeChangeField(Security.SPLIT_ARRAY,
          SerializationUtils.serialize(sdacm.getSecuritysplits()), proposeChangeEntityNew.getIdProposeRequest()));
    }
    return securitysplitsExisting;

  }

  private Map<Integer, List<Securitysplit>> getMapForList(final List<Securitysplit> securitysplits) {
    return securitysplits.stream()
        .collect(Collectors.groupingBy(Securitysplit::getIdSecuritycurrency, Collectors.toList()));
  }

  @Override
  public List<String> loadAllSplitDataFromConnector(Security security, Date requestedSplitdate) {
    List<String> errorMessages = new ArrayList<>();
    short retrySplitLoad = security.getRetrySplitLoad();
    try {
      IFeedConnector connector = ConnectorHelper.getConnectorByConnectorId(feedConnectors,
          security.getIdConnectorSplit(), IFeedConnector.FeedSupport.SPLIT);
      List<Securitysplit> securitysplitsRead = connector.getSplitHistory(security,
          LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY));
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
    return errorMessages;
  }

  private void updateSplitData(Security security, List<Securitysplit> securitysplitsRead, Date requestedSplitdate)
      throws Exception {
    securitysplitJpaRepository.deleteByIdSecuritycurrencyAndCreateType(security.getIdSecuritycurrency(),
        CreateType.CONNECTOR_CREATED.getValue());
    List<Securitysplit> existingSplits = securitysplitJpaRepository
        .findByIdSecuritycurrencyOrderBySplitDateAsc(security.getIdSecuritycurrency());
    List<Securitysplit> createdSplits = DividendSplitsHelper.updateDividendSplitData(security, securitysplitsRead,
        existingSplits, this.securitysplitJpaRepository);
    Optional<Date> maxSplitDate = createdSplits.stream().map(Securitysplit::getSplitDate).max(Date::compareTo);

    if (requestedSplitdate == null
        || !maxSplitDate.isEmpty() && DateHelper.isSameDay(maxSplitDate.get(), requestedSplitdate)) {
      // Expected split was found
      if (securityJpaRepository.isYoungestSplitHistoryquotePossibleAdjusted(security, securitysplitsRead,
          true) == SplitAdjustedHistoryquotes.ADJUSTED) {
        if (!maxSplitDate.isEmpty() && security.getFullLoadTimestamp() != null
            && maxSplitDate.get().after(security.getFullLoadTimestamp())) {
          securityJpaRepository.reloadAsyncFullHistoryquote(security);
        }
        if (!createdSplits.isEmpty()) {
          taskDataChangeJpaRepository
              .save(new TaskDataChange(TaskType.HOLDINGS_SECURITY_REBUILD, TaskDataExecPriority.PRIO_NORMAL,
                  LocalDateTime.now(), security.getIdSecuritycurrency(), Security.class.getSimpleName()));
        }
      } else {
        taskDataChangeJpaRepository.save(
            new TaskDataChange(TaskType.CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES, TaskDataExecPriority.PRIO_LOW,
                LocalDateTime.now().plusDays(1L), security.getIdSecuritycurrency(), Security.class.getSimpleName()));
      }
    } else {
      // Expected Split could not be read
      if (DateHelper.getDateDiff(requestedSplitdate, new Date(),
          TimeUnit.DAYS) <= GlobalConstants.MAX_DAYS_FOR_SECURITY_IS_REFLECTING_SPLIT) {
        var taskDataChange = new TaskDataChange(TaskType.SECURITY_SPLIT_UPDATE_FOR_SECURITY,
            TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusDays(1), security.getIdSecuritycurrency(),
            Security.class.getSimpleName());
        taskDataChange.setOldValueString(
            new SimpleDateFormat(GlobalConstants.SHORT_STANDARD_DATE_FORMAT).format(requestedSplitdate));
        taskDataChangeJpaRepository.save(taskDataChange);
      }

    }

  }

}
