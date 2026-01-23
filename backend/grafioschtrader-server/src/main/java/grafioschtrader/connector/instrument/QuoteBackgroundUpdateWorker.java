package grafioschtrader.connector.instrument;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.HistoryquoteUpdateLog;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.HistoryquoteUpdateLogJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.StockexchangeJpaRepository;
import grafioschtrader.service.GlobalparametersService;
/**
 * Background worker service responsible for automatically updating historical price quotes
 * for securities across different stock exchanges. This component runs as a long-lived
 * background thread that periodically checks for stock exchanges that require price updates
 * and processes securities associated with those exchanges.
 * 
 * <h3>Update Strategy</h3>
 * <p>
 * The worker employs an intelligent update strategy that:
 * </p>
 * <ul>
 *   <li><strong>Exchange-Based Processing:</strong> Groups securities by stock exchange for efficient processing</li>
 *   <li><strong>Time-Aware Updates:</strong> Only processes exchanges after they have been closed for a minimum time</li>
 *   <li><strong>Weekend Scheduling:</strong> Adjusts sleep periods to skip weekends when markets are typically closed</li>
 *   <li><strong>Configurable Operation:</strong> Can be enabled/disabled via global parameters</li>
 * </ul>
 * 
 * <h3>Processing Logic</h3>
 * <p>
 * The update cycle follows this pattern:
 * </p>
 * <ol>
 *   <li>Query all active stock exchanges (excluding those marked as no market value)</li>
 *   <li>Filter exchanges that have been closed for at least the minimum wait time</li>
 *   <li>Further filter exchanges that may have price updates since last close</li>
 *   <li>Retrieve securities associated with the filtered exchanges</li>
 *   <li>Process price updates for the securities</li>
 *   <li>Sleep for a calculated period before the next cycle</li>
 * </ol>
 * 
 * <h3>Timing and Scheduling</h3>
 * <p>
 * The worker uses adaptive scheduling:
 * </p>
 * <ul>
 *   <li><strong>Dynamic Sleep:</strong> Calculates when the next exchange becomes eligible and sleeps until then</li>
 *   <li><strong>Weekend Handling:</strong> Extended sleep until next Monday when markets reopen</li>
 *   <li><strong>Grace Period:</strong> Waits a configurable time after exchange close before processing</li>
 *   <li><strong>Random Variation:</strong> Adds ±10 minutes to avoid predictable query patterns</li>
 * </ul>
 * 
 * <h3>Configuration</h3>
 * <p>
 * The worker behavior is controlled by:
 * </p>
 * <ul>
 *   <li><code>globalparametersService.getUpdatePriceByStockexchange()</code> - Enables/disables the worker (0 = disabled)</li>
 *   <li><code>GlobalConstants.WAIT_AFTER_SE_CLOSE_FOR_UPDATE_IN_MINUTES</code> - Minimum wait time after exchange close</li>
 * </ul>
 * 
 * <h3>Thread Safety</h3>
 * <p>
 * The class implements proper thread management:
 * </p>
 * <ul>
 *   <li>Uses volatile boolean for thread coordination</li>
 *   <li>Implements DisposableBean for clean shutdown</li>
 *   <li>Handles InterruptedException gracefully</li>
 * </ul>
 */
@Component
public class QuoteBackgroundUpdateWorker
    implements DisposableBean, Runnable, ApplicationListener<ApplicationReadyEvent> {

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private HistoryquoteUpdateLogJpaRepository historyquoteUpdateLogJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /** The background thread that runs the update loop */
  private Thread backgroundThread;
  
  /** Volatile flag controlling the main update loop. Set to false to signal thread shutdown.  */
  private volatile boolean runningLoop;

  /**
   * Constructs a new QuoteBackgroundUpdateWorker and initializes the background thread.
   * The thread is created but not started until the application is ready.
   */
  QuoteBackgroundUpdateWorker() {
    backgroundThread = new Thread(this);
  }

  /**
   * Application event handler that starts the background update worker when the application is fully initialized.
   * The worker only starts if price updates are enabled in the global configuration.
   * 
   * @param event the ApplicationReadyEvent indicating the application has finished starting up
   */
  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    if (globalparametersService.getUpdatePriceByStockexchange() != 0) {
      log.info("Historical price quote updater was started");
      runningLoop = true;
      backgroundThread.start();
    }
  }

  /**
   * Main execution loop for the background update worker.
   * Continuously processes stock exchanges for price updates until the runningLoop flag is set to false.
   * 
   * <p>
   * The loop performs the following steps:
   * </p>
   * <ol>
   *   <li>Retrieve all active stock exchanges</li>
   *   <li>Filter exchanges eligible for updates based on timing criteria</li>
   *   <li>Process price updates for securities on filtered exchanges</li>
   *   <li>Sleep for a calculated period before the next iteration</li>
   * </ol>
   * 
   * <p>
   * The loop handles InterruptedException gracefully and will exit cleanly
   * when the thread is interrupted during shutdown.
   * </p>
   */
  @Override
  public void run() {
    while (runningLoop) {
      try {
        List<Stockexchange> stockexchanges = stockexchangeJpaRepository.findByNoMarketValueFalse();
        List<Stockexchange> stockexchangesUpd = stockexchanges.stream()
            .filter(se -> se.getClosedMinuntes() >= GlobalConstants.WAIT_AFTER_SE_CLOSE_FOR_UPDATE_IN_MINUTES
                && se.mayHavePriceUpdateSinceLastClose())
            .toList();
        updatePriceForStockexchange(stockexchangesUpd);

        TimeUnit.MINUTES.sleep(getCalculatedSleepTimeInMinutes(stockexchanges));
      } catch (InterruptedException ie) {
        log.info("Backgroud thread was interrupted, Failed to complete operation");
      }
    }
  }

  /**
   * Processes price updates for securities associated with the specified stock exchanges.
   * Retrieves all securities that need historical quote updates for the given exchanges
   * and logs information about the update process to both the application log and the database.
   *
   * <p>
   * This method performs the following:
   * </p>
   * <ul>
   *   <li>Creates database log entries for each exchange before starting the update</li>
   *   <li>Calls the security repository to fetch and update historical quotes</li>
   *   <li>Groups updated securities by exchange to track per-exchange statistics</li>
   *   <li>Updates database log entries with final counts and success/failure status</li>
   * </ul>
   *
   * @param stockexchanges list of stock exchanges that are eligible for price updates
   */
  private void updatePriceForStockexchange(List<Stockexchange> stockexchanges) {
    if (stockexchanges.isEmpty()) {
      return;
    }

    // Create log entries for each exchange before starting update
    Map<Integer, HistoryquoteUpdateLog> logEntries = new HashMap<>();
    for (Stockexchange stockexchange : stockexchanges) {
      HistoryquoteUpdateLog logEntry = new HistoryquoteUpdateLog(
          stockexchange.getIdStockexchange(),
          stockexchange.getClosedMinuntes(),
          0 // securities count will be updated after the query
      );
      logEntry = historyquoteUpdateLogJpaRepository.save(logEntry);
      logEntries.put(stockexchange.getIdStockexchange(), logEntry);
    }

    try {
      List<Security> securities = securityJpaRepository
          .catchAllUpSecurityHistoryquote(stockexchanges.stream().map(Stockexchange::getIdStockexchange).toList());

      // Group securities by exchange to get per-exchange counts
      Map<Integer, Long> securitiesPerExchange = securities.stream()
          .collect(Collectors.groupingBy(
              s -> s.getStockexchange().getIdStockexchange(),
              Collectors.counting()));

      // Update log entries with results
      for (Stockexchange stockexchange : stockexchanges) {
        HistoryquoteUpdateLog logEntry = logEntries.get(stockexchange.getIdStockexchange());
        int securitiesCount = securitiesPerExchange.getOrDefault(stockexchange.getIdStockexchange(), 0L).intValue();
        logEntry.setSecuritiesCount(securitiesCount);
        logEntry.markSuccess(securitiesCount);
        historyquoteUpdateLogJpaRepository.save(logEntry);

        log.info("Exchange {}: time since close: {} min, securities updated: {}, index-update: {}",
            stockexchange.getName(), stockexchange.getClosedMinuntes(), securitiesCount,
            getIndexOfStockexchange(stockexchange));
      }
    } catch (Exception e) {
      // Mark all log entries as failed
      for (HistoryquoteUpdateLog logEntry : logEntries.values()) {
        String errorMessage = e.getMessage();
        if (errorMessage != null && errorMessage.length() > 500) {
          errorMessage = errorMessage.substring(0, 500);
        }
        logEntry.markFailed(errorMessage);
        historyquoteUpdateLogJpaRepository.save(logEntry);
      }
      log.error("Failed to update historical prices for exchanges: {}",
          stockexchanges.stream().map(Stockexchange::getName).toList(), e);
    }
  }

  /**
   * Retrieves the latest historical quote date for a stock exchange's index security.
   * This information is used to track when the exchange's index was last updated
   * and can help determine if fresh market data is available.
   * 
   * @param stockexchange the stock exchange to check for index update information
   * @return the date of the most recent historical quote for the exchange's index security,
   *         or null if no index security is configured or no quotes are available
   */
  private Date getIndexOfStockexchange(Stockexchange stockexchange) {
    return stockexchange.getIdIndexUpdCalendar() == null ? null
        : historyquoteJpaRepository.getMaxDateByIdSecurity(stockexchange.getIdIndexUpdCalendar());
  }

  /**
   * Calculates the optimal sleep time in minutes before the next update cycle.
   * Uses intelligent scheduling that adapts to exchange closing times and weekends.
   *
   * <p>
   * Sleep time calculation strategy:
   * </p>
   * <ul>
   *   <li><strong>Weekdays:</strong> Calculates when the next exchange becomes eligible
   *       (close time + {@link GlobalConstants#WAIT_AFTER_SE_CLOSE_FOR_UPDATE_IN_MINUTES})
   *       and sleeps until that time</li>
   *   <li><strong>Weekends:</strong> Sleep until next Monday (markets typically closed)</li>
   *   <li><strong>Random variation:</strong> Adds ±10 minutes to avoid predictable query patterns</li>
   * </ul>
   *
   * <p>
   * An exchange becomes eligible for update when:
   * </p>
   * <ol>
   *   <li>Current time is at least {@link GlobalConstants#WAIT_AFTER_SE_CLOSE_FOR_UPDATE_IN_MINUTES}
   *       after the exchange's close time</li>
   *   <li>The exchange may have new price data since its last update
   *       ({@link Stockexchange#mayHavePriceUpdateSinceLastClose()})</li>
   * </ol>
   *
   * @param stockexchanges list of all active stock exchanges to consider for scheduling
   * @return the number of minutes to sleep before the next update cycle (minimum 5 minutes)
   */
  private long getCalculatedSleepTimeInMinutes(List<Stockexchange> stockexchanges) {
    Instant now = Instant.now();
    Random random = new Random();

    // Check if it's weekend - if so, sleep until Monday
    DayOfWeek dayOfWeekNow = now.atOffset(ZoneOffset.UTC).getDayOfWeek();
    if (DayOfWeek.SATURDAY == dayOfWeekNow || DayOfWeek.SUNDAY == dayOfWeekNow) {
      Instant nextMonday = LocalDate.now(ZoneOffset.UTC)
          .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
          .atStartOfDay()
          .toInstant(ZoneOffset.UTC);
      long sleepMinutes = Duration.between(now, nextMonday).toMinutes();
      int randomVariation = random.nextInt(21) - 10;
      sleepMinutes += randomVariation;
      log.info("Weekend detected, sleeping {} minutes until Monday (including {}min random variation)",
          sleepMinutes, randomVariation);
      return sleepMinutes;
    }

    // Find the next exchange eligibility time
    Instant nextEligibleTime = null;
    String nextExchangeName = null;

    for (Stockexchange se : stockexchanges) {
      // Skip exchanges that don't have complete timing info
      if (se.getTimeZone() == null || se.getTimeClose() == null) {
        continue;
      }

      try {
        ZoneId exchangeZone = ZoneId.of(se.getTimeZone());
        LocalDateTime nowInExchangeZone = LocalDateTime.now(exchangeZone);
        LocalDateTime closeTimeToday = LocalDateTime.of(nowInExchangeZone.toLocalDate(), se.getTimeClose());

        // Calculate eligibility time: close time + wait period
        LocalDateTime eligibleTime = closeTimeToday
            .plusMinutes(GlobalConstants.WAIT_AFTER_SE_CLOSE_FOR_UPDATE_IN_MINUTES);
        Instant eligibleInstant = eligibleTime.atZone(exchangeZone).toInstant();

        // If eligibility time is in the past, check if already updated or calculate for tomorrow
        if (eligibleInstant.isBefore(now) || eligibleInstant.equals(now)) {
          if (!se.mayHavePriceUpdateSinceLastClose()) {
            // Already updated today, calculate for tomorrow's eligibility
            eligibleTime = closeTimeToday.plusDays(1)
                .plusMinutes(GlobalConstants.WAIT_AFTER_SE_CLOSE_FOR_UPDATE_IN_MINUTES);
            eligibleInstant = eligibleTime.atZone(exchangeZone).toInstant();
          } else {
            // Exchange is currently eligible (will be processed in this cycle), skip
            continue;
          }
        }

        // Track the soonest eligibility time
        if (nextEligibleTime == null || eligibleInstant.isBefore(nextEligibleTime)) {
          nextEligibleTime = eligibleInstant;
          nextExchangeName = se.getName();
        }
      } catch (Exception e) {
        log.warn("Error calculating eligibility for exchange {}: {}", se.getName(), e.getMessage());
      }
    }

    if (nextEligibleTime != null) {
      long sleepMinutes = Duration.between(now, nextEligibleTime).toMinutes();
      int randomVariation = random.nextInt(21) - 10;
      sleepMinutes += randomVariation;

      // Ensure minimum sleep time (avoid busy-waiting)
      sleepMinutes = Math.max(sleepMinutes, 5);

      log.info("Next exchange '{}' eligible in {} minutes (including {}min random variation)",
          nextExchangeName, sleepMinutes, randomVariation);
      return sleepMinutes;
    }

    // Fallback: 1 hour with random variation if no exchanges need updates
    int randomVariation = random.nextInt(21) - 10;
    long sleepMinutes = 60 + randomVariation;
    log.info("No upcoming exchange eligibility found, sleeping {} minutes (including {}min random variation)",
        sleepMinutes, randomVariation);
    return sleepMinutes;
  }

  /**
   * Cleanup method called during Spring application shutdown.
   * Signals the background thread to stop its execution loop, allowing for graceful shutdown.
   * 
   * <p>
   * This method is automatically called by Spring when the application context is being destroyed,
   * ensuring that the background thread does not continue running after the application shuts down.
   * </p>
   */
  @Override
  public void destroy() {
    runningLoop = false;
  }

}
