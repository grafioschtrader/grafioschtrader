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

import grafiosch.entities.Globalparameters;
import grafiosch.entities.TaskDataChange;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.GlobalConstants;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.connector.calendar.ISplitCalendarFeedConnector.TickerSecuritysplit;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.StockexchangeJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Service component that monitors split calendars daily and creates TaskDataChange entries for securities that have
 * announced stock splits. Uses name similarity matching to identify securities and avoids direct database insertion to
 * prevent incorrect matches.
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
  private List<ISplitCalendarFeedConnector> splitCalendarFeedConnectors = new ArrayList<>();

  /** Regex pattern to remove common corporate suffixes from company names for matching */
  private static String removeFromNameRegex = " (corp|corp\\.|corporation|inc\\.|inc|llc|ltd adr)$";

  /**
   * Processes split calendar data from the last append date until today. Updates the global parameter with the latest
   * processing date upon completion.
   */
  public void appendSecuritySplitsUntilToday() {
    Optional<Globalparameters> gpLastAppend = globalparametersJpaRepository
        .findById(GlobalParamKeyDefault.GLOB_KEY_YOUNGEST_SPLIT_APPEND_DATE);
    gpLastAppend.ifPresentOrElse(gp -> loadSplitData(gp.getPropertyDate().plusDays(1)),
        () -> loadSplitData(LocalDate.now()));
  }

  /**
   * Loads and processes split data for each trading day from the specified start date to today. Initializes similarity
   * algorithm and sorts connectors by priority before processing.
   * 
   * @param fromDate the starting date for split data loading
   */
  private void loadSplitData(LocalDate fromDate) {
    LocalDate now = LocalDate.now();
    List<TradingDaysPlus> tradingDaysPlusList = tradingDaysPlusJpaRepository
        .findByTradingDateBetweenOrderByTradingDate(fromDate, now);
    String[] countryCodes = stockexchangeJpaRepository.findDistinctCountryCodes();
    SimilarityScore<Double> similarityAlgo = new JaroWinklerSimilarity();
    splitCalendarFeedConnectors.sort(Comparator.comparingInt(ISplitCalendarFeedConnector::getPriority));
    stepThruEveryCalendarDay(tradingDaysPlusList, countryCodes, similarityAlgo);
    Globalparameters globalparameters = new Globalparameters(GlobalParamKeyDefault.GLOB_KEY_YOUNGEST_SPLIT_APPEND_DATE,
        now, true);
    globalparametersJpaRepository.save(globalparameters);
  }

  /**
   * Processes split calendar data for each trading day using available connectors. Retrieves split data for each day
   * and attempts to match securities by ticker and name similarity.
   * 
   * @param tradingDaysPlusList list of trading days to process
   * @param countryCodes        array of country codes for filtering split data
   * @param similarityAlgo      algorithm for comparing company names when exact matches fail
   */
  private void stepThruEveryCalendarDay(List<TradingDaysPlus> tradingDaysPlusList, String[] countryCodes,
      SimilarityScore<Double> similarityAlgo) {
    for (TradingDaysPlus tradingDaysPlus : tradingDaysPlusList) {
      for (ISplitCalendarFeedConnector calendarFeedConnector : splitCalendarFeedConnectors) {
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
  }

  /**
   * Validates security matches by comparing company names using similarity algorithm. Removes securities from the list
   * if name similarity falls below threshold. Uses different similarity thresholds for stocks (0.88) vs other
   * securities (0.95).
   * 
   * @param splitTickerMap map of ticker symbols to split information
   * @param securities     list of securities to validate (modified in place)
   * @param similarityAlgo algorithm for calculating name similarity scores
   */
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

  /**
   * Creates TaskDataChange entries for new security splits that don't already exist. Checks existing splits and pending
   * tasks to avoid duplicates before creating new tasks.
   * 
   * @param splitTickerMap map of ticker symbols to split information from calendar feeds
   * @param securities     list of matched securities to process for split tasks
   */
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
            .findByIdTaskAndIdEntityAndProgressStateType(TaskTypeExtended.SECURITY_SPLIT_UPDATE_FOR_SECURITY.getValue(),
                security.getIdSecuritycurrency(), ProgressStateType.PROG_WAITING.getValue())
            .isEmpty()) {
          var taskDataChange = new TaskDataChange(TaskTypeExtended.SECURITY_SPLIT_UPDATE_FOR_SECURITY,
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
