package grafioschtrader.connector.instrument;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.StockexchangeJpaRepository;

@Component
public class QuoteBackgroundUpdateWorker
    implements DisposableBean, Runnable, ApplicationListener<ApplicationReadyEvent> {

  @Autowired
  private StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private Thread backgroundThread;
  private volatile boolean runningLoop;

  QuoteBackgroundUpdateWorker() {
    backgroundThread = new Thread(this);
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    if (globalparametersJpaRepository.getUpdatePriceByStockexchange() != 0) {
      log.info("Historical price quote updater was started");
      runningLoop = true;
      backgroundThread.start();
    }
  }

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

        TimeUnit.HOURS.sleep(getCalculatedSleepTimeInHours());
      } catch (InterruptedException ie) {
        log.info("Backgroud thread was interrupted, Failed to complete operation");
      }
    }
  }

  private void updatePriceForStockexchange(List<Stockexchange> stockexchanges) {
    List<Security> securities = securityJpaRepository
        .catchAllUpSecurityHistoryquote(stockexchanges.stream().map(Stockexchange::getIdStockexchange).toList());
    for (Stockexchange stockexchange : stockexchanges) {
      log.info("Namen {}, time since close: {}, number of securties {}, stock-Index-upd: {}", stockexchange.getName(),
          stockexchange.getClosedMinuntes(), securities.size(), getIndexOfStockexchange(stockexchange));
    }

  }

  private Date getIndexOfStockexchange(Stockexchange stockexchange) {
    return stockexchange.getIdIndexUpdCalendar() == null ? null
        : historyquoteJpaRepository.getMaxDateByIdSecurity(stockexchange.getIdIndexUpdCalendar());
  }

  /**
   * Sleep during
   * 
   * @return
   */
  private long getCalculatedSleepTimeInHours() {
    long sleepHours = 1;
    Instant now = Instant.now();
    DayOfWeek dayOfWeekNow = now
        .plus(GlobalConstants.WAIT_AFTER_SE_CLOSE_FOR_UPDATE_IN_MINUTES * -1, ChronoUnit.MINUTES)
        .atOffset(ZoneOffset.UTC).getDayOfWeek();
    if (DayOfWeek.SATURDAY == dayOfWeekNow || DayOfWeek.SUNDAY == dayOfWeekNow) {
      Instant nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay()
          .toInstant(ZoneOffset.UTC);

      sleepHours = Duration.between(now, nextMonday).toHours();
    }
    log.info("Sleep hours {}", sleepHours);
    return sleepHours;

  }

  @Override
  public void destroy() {
    runningLoop = false;
  }

}
