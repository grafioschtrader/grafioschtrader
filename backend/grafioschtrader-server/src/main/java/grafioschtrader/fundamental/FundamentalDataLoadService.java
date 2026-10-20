package grafioschtrader.fundamental;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafioschtrader.connector.fundamental.FundamentalAnswer;
import grafioschtrader.connector.fundamental.IFundamentalFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityFundamental;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityFundamentalJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Loads the fundamental base values of the direct equities in scope from every enabled source and stores them graded.
 *
 * <p>
 * Each source runs on its own thread with its own pause, so the slowest rate limit sets the duration rather than the
 * sum of all. A 401 or 403 answer stops that source for the rest of the run, since the key or plan is the cause; any
 * other failure fails that security for that source. Afterwards, per security in one transaction, the rows of every
 * source that gave a usable answer are replaced by that answer, the rows of a source that failed stay as they were, the
 * source checks run with the current last price, and the selection grades the combined rows.
 * </p>
 */
@Service
public class FundamentalDataLoadService {

  private static final Logger log = LoggerFactory.getLogger(FundamentalDataLoadService.class);

  private final List<IFundamentalFeedConnector> connectors;
  private final SecurityJpaRepository securityJpaRepository;
  private final SecurityFundamentalJpaRepository securityFundamentalJpaRepository;
  private final CurrencypairJpaRepository currencypairJpaRepository;
  private final TransactionTemplate transactionTemplate;

  /** Comma-separated sources that may be asked; empty disables every fundamental request. */
  @Value("${gt.fundamental.sources:yahoo,finnhub,eulerpool}")
  private String enabledSources;

  public FundamentalDataLoadService(List<IFundamentalFeedConnector> connectors,
      SecurityJpaRepository securityJpaRepository, SecurityFundamentalJpaRepository securityFundamentalJpaRepository,
      CurrencypairJpaRepository currencypairJpaRepository, PlatformTransactionManager transactionManager) {
    this.connectors = connectors;
    this.securityJpaRepository = securityJpaRepository;
    this.securityFundamentalJpaRepository = securityFundamentalJpaRepository;
    this.currencypairJpaRepository = currencypairJpaRepository;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /** Outcome of one source in one run, for the task log. */
  public static class SourceSummary {
    public int usable;
    public int failed;
    public int unsupported;
    public LocalDateTime lastUsable;
    public String stopReason;

    @Override
    public String toString() {
      return "usable=" + usable + " failed=" + failed + " unsupported=" + unsupported + " lastUsable=" + lastUsable
          + (stopReason == null ? "" : " stopped=" + stopReason);
    }
  }

  /**
   * Runs the load.
   *
   * @param idSecuritycurrency one security to load, or null for the whole scope
   * @return per asked source its outcome; empty when no source is enabled or available
   * @throws Exception when a source thread fails unexpectedly
   */
  public Map<FundamentalSource, SourceSummary> load(Integer idSecuritycurrency) throws Exception {
    List<IFundamentalFeedConnector> active = activeConnectors();
    if (active.isEmpty()) {
      return Map.of();
    }
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    List<Security> securities;
    if (idSecuritycurrency == null) {
      securityFundamentalJpaRepository.deleteOutOfScope(AssetclassType.EQUITIES.getValue(),
          SpecialInvestmentInstruments.DIRECT_INVESTMENT.getValue(), today);
      securities = securityJpaRepository.findAllById(securityFundamentalJpaRepository.findScopeIds(
          AssetclassType.EQUITIES.getValue(), SpecialInvestmentInstruments.DIRECT_INVESTMENT.getValue(), today));
    } else {
      securities = securityJpaRepository.findById(idSecuritycurrency).map(List::of).orElse(List.of());
    }
    Map<Integer, Map<FundamentalSource, FundamentalAnswer>> answers = new ConcurrentHashMap<>();
    Map<FundamentalSource, SourceSummary> summaries = askSources(active, securities, answers);
    for (Security security : securities) {
      Map<FundamentalSource, FundamentalAnswer> usable = answers.getOrDefault(security.getIdSecuritycurrency(),
          Map.of());
      if (!usable.isEmpty()) {
        try {
          store(security, usable, today);
        } catch (RuntimeException e) {
          // One security that cannot be stored must not cost every other security its new values.
          log.error("Fundamental values of security {} could not be stored", security.getIdSecuritycurrency(), e);
        }
      }
    }
    return summaries;
  }

  /** Connectors that are enabled by {@code gt.fundamental.sources} and available, in the order of that property. */
  private List<IFundamentalFeedConnector> activeConnectors() {
    List<String> names = Arrays.stream(enabledSources == null ? new String[0] : enabledSources.split(","))
        .map(s -> s.trim().toUpperCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList();
    List<IFundamentalFeedConnector> active = new ArrayList<>();
    for (String name : names) {
      connectors.stream().filter(c -> c.getFundamentalSource().name().equals(name)).findFirst()
          .filter(IFundamentalFeedConnector::isFundamentalAvailable).ifPresent(active::add);
    }
    return active;
  }

  private Map<FundamentalSource, SourceSummary> askSources(List<IFundamentalFeedConnector> active,
      List<Security> securities, Map<Integer, Map<FundamentalSource, FundamentalAnswer>> answers) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(active.size());
    try {
      Map<FundamentalSource, Future<SourceSummary>> futures = new LinkedHashMap<>();
      for (IFundamentalFeedConnector connector : active) {
        futures.put(connector.getFundamentalSource(), executor.submit(() -> askSource(connector, securities, answers)));
      }
      Map<FundamentalSource, SourceSummary> summaries = new EnumMap<>(FundamentalSource.class);
      for (Map.Entry<FundamentalSource, Future<SourceSummary>> entry : futures.entrySet()) {
        try {
          summaries.put(entry.getKey(), entry.getValue().get());
        } catch (ExecutionException e) {
          // A source that breaks down must not discard the answers the other sources already delivered.
          log.error("Fundamental source {} aborted", entry.getKey(), e.getCause());
          SourceSummary aborted = new SourceSummary();
          aborted.stopReason = String.valueOf(e.getCause());
          summaries.put(entry.getKey(), aborted);
        }
      }
      return summaries;
    } finally {
      executor.shutdownNow();
    }
  }

  private SourceSummary askSource(IFundamentalFeedConnector connector, List<Security> securities,
      Map<Integer, Map<FundamentalSource, FundamentalAnswer>> answers) throws InterruptedException {
    SourceSummary summary = new SourceSummary();
    FundamentalSource source = connector.getFundamentalSource();
    boolean unexpectedLogged = false;
    for (Security security : securities) {
      FundamentalAnswer answer;
      try {
        if (!connector.supportsFundamentals(security)) {
          summary.unsupported++;
          continue;
        }
        if (summary.stopReason != null) {
          summary.failed++;
          continue;
        }
        answer = connector.loadFundamentals(security);
      } catch (InterruptedException e) {
        throw e;
      } catch (Exception e) {
        if (e instanceof RuntimeException && !unexpectedLogged) {
          // A programming error rather than a network failure: logged once per source with its stack trace.
          log.error("Fundamental source {} failed unexpectedly for security {}", source,
              security.getIdSecuritycurrency(), e);
          unexpectedLogged = true;
        }
        answer = FundamentalAnswer.failed(e.getClass().getSimpleName(), 0);
      }
      if (answer.isUsable()) {
        summary.usable++;
        summary.lastUsable = LocalDateTime.now(ZoneOffset.UTC);
        answers.computeIfAbsent(security.getIdSecuritycurrency(), _ -> new ConcurrentHashMap<>()).put(source, answer);
      } else {
        summary.failed++;
        log.debug("Fundamental source {} failed for security {}: {}", source, security.getIdSecuritycurrency(),
            answer.reason());
        if (answer.stopsSource()) {
          summary.stopReason = answer.reason();
        }
      }
      Thread.sleep(connector.getFundamentalPause());
    }
    return summary;
  }

  /**
   * Replaces the rows of the sources that answered, checks them and grades all rows of the security, in one
   * transaction. The stored rows are read outside of it, so they are detached and can be saved again after the delete.
   */
  private void store(Security security, Map<FundamentalSource, FundamentalAnswer> usable, LocalDate today) {
    Integer id = security.getIdSecuritycurrency();
    List<SecurityFundamental> all = combine(securityFundamentalJpaRepository.findByIdSecuritycurrency(id), usable,
        security.getSLast(), security.getCurrency(), this::rate, today);
    transactionTemplate.executeWithoutResult(_ -> {
      securityFundamentalJpaRepository.deleteByIdSecuritycurrency(id);
      securityFundamentalJpaRepository.saveAll(all);
    });
  }

  /**
   * The rows a security holds after a run: the stored rows of every source without a usable answer, and the rows of
   * each usable answer in place of that source's stored rows, so that a metric the source no longer delivers
   * disappears. The new rows are checked, then all rows are graded.
   *
   * @param stored           the rows stored so far
   * @param usable           the usable answers of this run per source
   * @param lastPrice        the last price of the security
   * @param securityCurrency the currency of the security
   * @param exchangeRate     the currency conversion of the checks
   * @param today            the day of the run
   * @return the checked and graded rows to store
   */
  static List<SecurityFundamental> combine(List<SecurityFundamental> stored,
      Map<FundamentalSource, FundamentalAnswer> usable, Double lastPrice, String securityCurrency,
      FundamentalSourceChecks.ExchangeRate exchangeRate, LocalDate today) {
    List<SecurityFundamental> all = new ArrayList<>(
        stored.stream().filter(r -> !usable.containsKey(r.getSource())).toList());
    List<SecurityFundamental> loaded = new ArrayList<>();
    Map<FundamentalSource, Map<String, Double>> evidence = new EnumMap<>(FundamentalSource.class);
    usable.forEach((source, answer) -> {
      loaded.addAll(answer.rows());
      evidence.put(source, answer.evidence());
    });
    all.addAll(loaded);
    FundamentalSourceChecks.apply(loaded, all, evidence, lastPrice, securityCurrency, exchangeRate);
    FundamentalSelector.select(all, today);
    return all;
  }

  /** Factor from one currency into another with the last price of the GT currency pair, inverted if needed. */
  private Double rate(String from, String to) {
    Currencypair direct = currencypairJpaRepository.findByFromCurrencyAndToCurrency(from, to);
    if (direct != null && direct.getSLast() != null && direct.getSLast() > 0) {
      return direct.getSLast();
    }
    Currencypair reverse = currencypairJpaRepository.findByFromCurrencyAndToCurrency(to, from);
    return reverse != null && reverse.getSLast() != null && reverse.getSLast() > 0 ? 1.0 / reverse.getSLast() : null;
  }
}
