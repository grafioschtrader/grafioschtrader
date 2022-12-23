package grafioschtrader.connector.calendar;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.commons.text.similarity.SimilarityScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.calendar.ICalendarFeedConnector.TickerSecuritysplit;
import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.StockexchangeJpaRepository;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.types.ProgressStateType;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;

/**
 *
 * Checks the split calendar daily and creates a TaskDataChange for the
 * corresponding securities. A direct insertion into the split table is not
 * performed, because the security may have been identified incorrectly. For the
 * identification the security symbol is used and also the name is checked for
 * similarity.
 *
 */
@Component
public class SplitCalendarAppender {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired(required = false)
  private List<ICalendarFeedConnector> calendarFeedConnectors = new ArrayList<>();

  private static String removeFromNameRegex = " (corp|corp\\.|corporation|inc\\.|inc|llc)$";

  public void appendSecuritySplitsUntilToday() {
    Optional<Globalparameters> gpLastAppend = globalparametersJpaRepository
        .findById(Globalparameters.GLOB_KEY_YOUNGES_SPLIT_APPEND_DATE);
    gpLastAppend.ifPresentOrElse(gp -> loadSplitData(gp.getPropertyDate().plusDays(1)),
        () -> loadSplitData(LocalDate.now()));
  }

  private void loadSplitData(LocalDate forDate) {
    LocalDate now = LocalDate.now();
    List<TradingDaysPlus> tradingDaysPlusList = tradingDaysPlusJpaRepository
        .findByTradingDateBetweenOrderByTradingDate(forDate, now);
    String[] countryCodes = stockexchangeJpaRepository.findDistinctCountryCodes();
    SimilarityScore<Double> similarityAlgo = new JaroWinklerSimilarity();
    calendarFeedConnectors.sort(Comparator.comparingInt(ICalendarFeedConnector::getPriority));

    for (TradingDaysPlus tradingDaysPlus : tradingDaysPlusList) {
      for (ICalendarFeedConnector calendarFeedConnector : calendarFeedConnectors) {
        try {
          Map<String, TickerSecuritysplit> splitTickerMap = calendarFeedConnector
              .getCalendarSplitForSingleDay(tradingDaysPlus.getTradingDate(), countryCodes);
          List<Security> securities = securityJpaRepository
              .findByTickerSymbolInOrderByIdSecuritycurrency(splitTickerMap.keySet());
          matchSecurityNameWithSplitNameOthweiseRemoveIt(splitTickerMap, securities, similarityAlgo);
          proposeSecuritsplitOverTaskDataChange(splitTickerMap, securities);
        } catch (Exception ex) {
          log.error(ex.getMessage(), ex);
        }
      }
    }
    Globalparameters globalparameters = new Globalparameters(Globalparameters.GLOB_KEY_YOUNGES_SPLIT_APPEND_DATE, now,
        true);
    globalparametersJpaRepository.save(globalparameters);
  }

  private void matchSecurityNameWithSplitNameOthweiseRemoveIt(Map<String, TickerSecuritysplit> splitTickerMap,
      List<Security> securities, SimilarityScore<Double> similarityAlgo) {
    securities.removeIf(s -> {
      TickerSecuritysplit tss = splitTickerMap.get(s.getTickerSymbol());

      String name1 = s.getName().toLowerCase();
      String name2 = tss.companyName.toLowerCase();
      if (s.isStockAndDirectInvestment()) {
        name1 = name1.replaceFirst(removeFromNameRegex, "").strip();
        name2 = name2.replaceFirst(removeFromNameRegex, "").strip();
      }
      Double similarity = similarityAlgo.apply(name1, name2);
      boolean matchName = s.isStockAndDirectInvestment() && similarity > 0.88 || similarity > 0.95;
      if (log.isInfoEnabled() && !matchName) {
        log.info(
            "Found ticker {} Security split name '{}' does not match with security name '{}' with a similarity of {}",
            s.getTickerSymbol(), tss.companyName.toLowerCase(), s.getName().toLowerCase(), similarity);
      }
      return !matchName;
    });
  }

  private void proposeSecuritsplitOverTaskDataChange(Map<String, TickerSecuritysplit> splitTickerMap,
      List<Security> securities) {
    int ssi = 0;
    Set<Integer> idSecuritiesSet = securities.stream().map(Security::getIdSecuritycurrency).collect(Collectors.toSet());
    List<Securitysplit> securitysplits = securitysplitJpaRepository
        .findByIdSecuritycurrencyInOrderByIdSecuritycurrencyAscSplitDateAsc(idSecuritiesSet);

    for (Security security : securities) {
      TickerSecuritysplit tss = splitTickerMap.get(security.getTickerSymbol());
      Securitysplit securitysplit = null;
      while (ssi < securitysplits.size()) {
        securitysplit = securitysplits.get(ssi);
        if (security.getIdSecuritycurrency() >= securitysplit.getIdSecuritycurrency()
            && !tss.securitysplit.getSplitDate().equals(securitysplit.getSplitDate())) {
          // Split may already exists
          ssi++;
        } else {
          break;
        }
      }
      if (securitysplit == null || !(security.getIdSecuritycurrency().equals(securitysplit.getIdSecuritycurrency())
          && tss.securitysplit.getSplitDate().equals(securitysplit.getSplitDate()))) {
        if (taskDataChangeJpaRepository
            .findByIdTaskAndIdEntityAndProgressStateType(TaskType.SECURITY_SPLIT_UPDATE_FOR_SECURITY.getValue(),
                security.getIdSecuritycurrency(), ProgressStateType.PROG_WAITING.getValue())
            .isEmpty()) {
          var taskDataChange = new TaskDataChange(TaskType.SECURITY_SPLIT_UPDATE_FOR_SECURITY,
              TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusMinutes(2), security.getIdSecuritycurrency(),
              Security.class.getSimpleName());
          taskDataChange.setOldValueString(new SimpleDateFormat(GlobalConstants.SHORT_STANDARD_DATE_FORMAT)
              .format(tss.securitysplit.getSplitDate()));
          taskDataChangeJpaRepository.save(taskDataChange);
        }
      }
    }
  }

}
