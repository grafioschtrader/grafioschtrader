package grafioschtrader.priceupdate.intraday;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.ThreadHelper;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.DownloadLink;
import grafioschtrader.connector.instrument.generic.GenericFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.repository.GenericConnectorEndpointJpaRepository;
import grafioschtrader.repository.SecurityCurrencypairJpaRepository;
import grafioschtrader.service.GlobalparametersService;

/**
 * Intraday price updater that retrieves real-time market data through external feed connectors.
 *
 * <p>
 * This class handles intraday price updates for securities and currency pairs by interfacing with external data
 * providers through configured feed connectors. It provides comprehensive functionality including:
 * <ul>
 * <li><strong>Feed Connector Management</strong>: Locates and validates appropriate connectors based on entity
 * configuration</li>
 * <li><strong>Retry Logic</strong>: Implements sophisticated retry mechanisms with configurable limits and automatic
 * counter management</li>
 * <li><strong>Delayed Update Control</strong>: Respects data provider delay requirements and timeout constraints</li>
 * <li><strong>Batch Requests</strong>: Asks connectors that support it for several instruments with one request</li>
 * <li><strong>Secure Link Generation</strong>: Creates download links with proper API key protection and routing</li>
 * <li><strong>Entity-Specific Processing</strong>: Handles both Security and Currencypair entities with appropriate
 * connector methods</li>
 * </ul>
 * </p>
 *
 * @param <S> the type of security currency extending Securitycurrency (Security or Currencypair)
 */
public class IntradayThruConnector<S extends Securitycurrency<S>> extends BaseIntradayThru<S> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private final List<IFeedConnector> feedConnectorbeans;
  private final SecurityCurrencypairJpaRepository<S> jpaRepository;
  private final IIntradayEntityAccess<S> intraEntityAccess;
  private final GenericConnectorEndpointJpaRepository genericConnectorEndpointJpaRepository;

  /**
   * Constructs an intraday connector-based price updater.
   *
   * @param jpaRepository                         repository for persisting security currency entities
   * @param globalparametersService               service for accessing global configuration parameters
   * @param feedConnectorbeans                    list of available feed connector implementations
   * @param intraEntityAccess                     interface for executing entity-specific intraday updates
   * @param genericConnectorEndpointJpaRepository repository for marking generic endpoints as used (may be null)
   */
  public IntradayThruConnector(SecurityCurrencypairJpaRepository<S> jpaRepository,
      GlobalparametersService globalparametersService, List<IFeedConnector> feedConnectorbeans,
      IIntradayEntityAccess<S> intraEntityAccess,
      GenericConnectorEndpointJpaRepository genericConnectorEndpointJpaRepository) {
    super(globalparametersService);
    this.jpaRepository = jpaRepository;
    this.feedConnectorbeans = feedConnectorbeans;
    this.intraEntityAccess = intraEntityAccess;
    this.genericConnectorEndpointJpaRepository = genericConnectorEndpointJpaRepository;
  }

  @Override
  public S updateLastPriceSecurityCurrency(S securitycurrency, final short maxIntraRetry,
      final int scIntradayUpdateTimeout) {
    final IFeedConnector feedConnector = getIntraConnector(securitycurrency);
    if (isDue(securitycurrency, feedConnector, maxIntraRetry, scIntradayUpdateTimeout)) {
      final IntradayFields previousFields = prepare(securitycurrency);
      boolean updated = false;
      try {
        intraEntityAccess.updateIntraSecurityCurrency(securitycurrency, feedConnector);
        markGenericEndpointUsed(feedConnector, securitycurrency);
        updated = true;
      } catch (final Exception e) {
        log.error("Last price update failed securitycurrency={}", securitycurrency.toString(), e);
      }
      securitycurrency = finish(securitycurrency, previousFields, updated);
    }
    return securitycurrency;
  }

  /**
   * Updates the instruments of a list, asking a connector with {@link IFeedConnector#getIntradayBatchSize()} greater
   * than 1 for several instruments per request. Without such a connector in the list, or with a single instrument, this
   * is exactly the behaviour of the base class. Otherwise the due instruments of each batch connector are claimed first
   * and then grouped into requests of at most its batch size, while all others go through the single-instrument path.
   * Requests run in parallel like the single updates do, and every instrument is still saved in its own transaction.
   * <p>
   * A single instrument must stay on the calling thread. A caller may already hold the lock on its row, and the claim
   * of a worker thread runs on another connection, so it would wait for a lock its own caller never releases.
   *
   * @return the instruments in input order, updated where an update was due
   */
  @Override
  public List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies, final short maxIntraRetry,
      boolean singleThread) {
    if (securtycurrencies.size() <= 1
        || securtycurrencies.stream().noneMatch(sc -> isBatchConnector(getIntraConnector(sc)))) {
      return super.updateLastPriceOfSecuritycurrency(securtycurrencies, maxIntraRetry, singleThread);
    }
    final int scIntradayUpdateTimeout = globalparametersService.getSecurityCurrencyIntradayUpdateTimeout();
    final Map<S, S> updated = Collections.synchronizedMap(new IdentityHashMap<>());
    final List<Runnable> units = new ArrayList<>();
    final List<S> batchCandidates = new ArrayList<>();
    for (S securitycurrency : securtycurrencies) {
      if (isBatchConnector(getIntraConnector(securitycurrency))) {
        batchCandidates.add(securitycurrency);
      } else {
        units.add(() -> updated.put(securitycurrency,
            updateLastPriceSecurityCurrency(securitycurrency, maxIntraRetry, scIntradayUpdateTimeout)));
      }
    }
    // Claiming is a statement per instrument, so it runs in parallel as well; the filter keeps the input order.
    final List<S> due = new ArrayList<>();
    execute(
        () -> due.addAll(stream(batchCandidates, singleThread)
            .filter(sc -> isDue(sc, getIntraConnector(sc), maxIntraRetry, scIntradayUpdateTimeout)).toList()),
        singleThread);
    createBatchUnits(due, updated, units);
    execute(() -> stream(units, singleThread).forEach(Runnable::run), singleThread);
    return securtycurrencies.stream().map(sc -> updated.getOrDefault(sc, sc)).collect(Collectors.toList());
  }

  /**
   * Groups the due instruments by connector and adds one work unit per request of at most the connector's batch size.
   */
  private void createBatchUnits(final List<S> due, final Map<S, S> updated, final List<Runnable> units) {
    final Map<IFeedConnector, List<S>> byConnector = new LinkedHashMap<>();
    due.forEach(sc -> byConnector.computeIfAbsent(getIntraConnector(sc), _ -> new ArrayList<>()).add(sc));
    byConnector.forEach((feedConnector, instruments) -> {
      final int batchSize = feedConnector.getIntradayBatchSize();
      for (int i = 0; i < instruments.size(); i += batchSize) {
        final List<S> batch = new ArrayList<>(instruments.subList(i, Math.min(i + batchSize, instruments.size())));
        units.add(() -> updateBatch(feedConnector, batch, updated));
      }
    });
  }

  /**
   * Asks the connector for all instruments of one batch with a single request. A failed request fails every instrument
   * of the batch; an instrument missing from the answer fails alone.
   */
  private void updateBatch(final IFeedConnector feedConnector, final List<S> batch, final Map<S, S> updated) {
    final List<IntradayFields> previousFields = batch.stream().map(this::prepare).toList();
    final Set<S> failed = Collections.newSetFromMap(new IdentityHashMap<>());
    try {
      failed.addAll(intraEntityAccess.updateIntraSecurityCurrencies(batch, feedConnector));
      failed.forEach(
          sc -> log.warn("Last price missing in batch answer of {}: securitycurrency={}", feedConnector.getID(), sc));
    } catch (final Exception e) {
      log.error("Last price update failed for a batch of {} instruments of {}", batch.size(), feedConnector.getID(), e);
      failed.addAll(batch);
    }
    for (int i = 0; i < batch.size(); i++) {
      final S securitycurrency = batch.get(i);
      updated.put(securitycurrency,
          finish(securitycurrency, previousFields.get(i), !failed.contains(securitycurrency)));
    }
  }

  private void execute(final Runnable task, final boolean singleThread) {
    if (singleThread) {
      task.run();
    } else {
      ThreadHelper.executeForkJoinPool(task, GlobalConstants.FORK_JOIN_POOL_CORE_MULTIPLIER);
    }
  }

  private static <T> Stream<T> stream(final List<T> items, final boolean singleThread) {
    return singleThread ? items.stream() : items.parallelStream();
  }

  private IFeedConnector getIntraConnector(final S securitycurrency) {
    return ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans, securitycurrency.getIdConnectorIntra(),
        IFeedConnector.FeedSupport.FS_INTRA);
  }

  private static boolean isBatchConnector(final IFeedConnector feedConnector) {
    return feedConnector != null && feedConnector.getIntradayBatchSize() > 1;
  }

  /**
   * Decides whether the instrument is asked for a new price now, and if so claims it against concurrent writers.
   */
  private boolean isDue(final S securitycurrency, final IFeedConnector feedConnector, final short maxIntraRetry,
      final int scIntradayUpdateTimeout) {
    return feedConnector != null && (securitycurrency.getRetryIntraLoad() < maxIntraRetry || maxIntraRetry == -1)
        && securitycurrency.isActiveForIntradayUpdate(java.time.LocalDate.now())
        && allowDelayedIntradayUpdate(securitycurrency, feedConnector, scIntradayUpdateTimeout)
        && claimIntradayUpdate(securitycurrency, feedConnector, scIntradayUpdateTimeout);
  }

  /**
   * Remembers the intraday state before the connector is asked and clears the fields describing the running session.
   * Every connector writes only the fields it happens to parse, so a field it does not deliver would otherwise keep the
   * value of the connector used before it, forever. Clearing them first makes the saved row show exactly what this
   * connector delivered.
   */
  private IntradayFields prepare(final S securitycurrency) {
    final IntradayFields previousFields = IntradayFields.of(securitycurrency);
    clearDerivedIntradayFields(securitycurrency);
    return previousFields;
  }

  /**
   * Sets the retry counter according to the outcome and saves the instrument in its own transaction.
   *
   * @param updated true if the connector delivered a price for this instrument
   * @return the saved instrument, or the unsaved one if the save failed
   */
  private S finish(S securitycurrency, final IntradayFields previousFields, final boolean updated) {
    if (updated) {
      securitycurrency.setRetryIntraLoad((short) 0);
    } else {
      // Some connectors set the timestamp before they parse the price. Without restoring the previous state a failed
      // fetch would persist a fresh timestamp next to an old price, and the instrument would look up to date.
      previousFields.restore(securitycurrency);
      securitycurrency.setRetryIntraLoad((short) (securitycurrency.getRetryIntraLoad() + 1));
    }
    try {
      securitycurrency = jpaRepository.save(securitycurrency);
    } catch (final ConcurrencyFailureException ex) {
      // The last price is non critical and is refreshed within minutes, so a lost race is not worth a stack trace.
      // Losing it means another writer is updating the very same row right now, either holding the row lock
      // (CannotAcquireLockException) or having bumped the version first (ObjectOptimisticLockingFailureException).
      log.warn("Intraday price save skipped, row was concurrently updated: securitycurrency={}", securitycurrency);
    } catch (final Exception ex) {
      log.error("Save failed for securitycurrency={}", securitycurrency, ex);
    }
    return securitycurrency;
  }

  /**
   * Removes the intraday fields that describe the running session, keeping last price and timestamp. They are restored
   * or overwritten immediately afterwards, so the instrument is never left without a price.
   *
   * @param securitycurrency the instrument whose session data is dropped
   */
  private void clearDerivedIntradayFields(final S securitycurrency) {
    securitycurrency.setSOpen(null);
    securitycurrency.setSHigh(null);
    securitycurrency.setSLow(null);
    securitycurrency.setSPrevClose(null);
    securitycurrency.setSChangePercentage(null);
    if (securitycurrency instanceof Security security) {
      security.setSVolume(null);
    }
  }

  /**
   * The intraday state of one instrument before its connector was asked, so that a failed request leaves no trace.
   */
  private record IntradayFields(Double sLast, LocalDateTime sTimestamp, Double sOpen, Double sHigh, Double sLow,
      Double sPrevClose, Double sChangePercentage, Long sVolume) {

    static <S extends Securitycurrency<S>> IntradayFields of(final S securitycurrency) {
      return new IntradayFields(securitycurrency.getSLast(), securitycurrency.getSTimestamp(),
          securitycurrency.getSOpen(), securitycurrency.getSHigh(), securitycurrency.getSLow(),
          securitycurrency.getSPrevClose(), securitycurrency.getSChangePercentage(),
          securitycurrency instanceof Security security ? security.getSVolume() : null);
    }

    <S extends Securitycurrency<S>> void restore(final S securitycurrency) {
      securitycurrency.setSLast(sLast);
      securitycurrency.setSTimestamp(sTimestamp);
      securitycurrency.setSOpen(sOpen);
      securitycurrency.setSHigh(sHigh);
      securitycurrency.setSLow(sLow);
      securitycurrency.setSPrevClose(sPrevClose);
      securitycurrency.setSChangePercentage(sChangePercentage);
      if (securitycurrency instanceof Security security) {
        security.setSVolume(sVolume);
      }
    }
  }

  /**
   * Claims the intraday update of this instrument against concurrent writers.
   *
   * <p>
   * {@link #allowDelayedIntradayUpdate} decides purely on the in-memory entity, so two concurrent requests for the same
   * instrument both pass it and both write the row. This claim resolves that race in the database: only the caller
   * whose conditional UPDATE affected a row proceeds to contact the data provider and save. It runs after the in-memory
   * pre-filter, so the extra statement is only issued when an update actually looks due.
   * </p>
   *
   * @param securitycurrency        the instrument to claim
   * @param feedConnector           the connector supplying the provider specific delay
   * @param scIntradayUpdateTimeout minimum interval between two updates in seconds
   * @return true if this caller won the claim and should perform the update
   */
  private boolean claimIntradayUpdate(final S securitycurrency, final IFeedConnector feedConnector,
      final int scIntradayUpdateTimeout) {
    if (securitycurrency.getIdSecuritycurrency() == null) {
      // A not yet persisted instrument has no row that a second writer could contend for, so there is nothing to claim.
      // This happens while a newly created security or currency pair is initially filled.
      return true;
    }
    final LocalDateTime now = LocalDateTime.now();
    final LocalDateTime threshold = now
        .minusSeconds((long) scIntradayUpdateTimeout + feedConnector.getIntradayDelayedSeconds());
    return jpaRepository.claimIntradayUpdate(securitycurrency.getIdSecuritycurrency(), now, threshold) > 0;
  }

  @Override
  public String getSecuritycurrencyIntraDownloadLinkAsUrlStr(S securitycurrency) {
    final IFeedConnector feedConnector = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
        securitycurrency.getIdConnectorIntra(), IFeedConnector.FeedSupport.FS_INTRA);
    if (feedConnector != null) {
      return feedConnector.isDownloadLinkCreatedLazy().contains(DownloadLink.DL_LAZY_INTRA) ? LINK_DOWNLOAD_LAZY
          : createDownloadLink(securitycurrency, feedConnector);
    }
    return null;
  }

  @Override
  public String createDownloadLink(S securitycurrency, IFeedConnector feedConnector) {
    if (ConnectorHelper.canAccessConnectorApiKey(feedConnector) && (feedConnector != null
        && !feedConnector.isDownloadLinkCreatedLazy().contains(DownloadLink.DL_INTRA_FORCE_BACKEND))) {
      if (securitycurrency instanceof Security) {
        return (feedConnector == null) ? null
            : feedConnector.getSecurityIntradayDownloadLink((Security) securitycurrency);
      } else {
        return (feedConnector == null) ? null
            : feedConnector.getCurrencypairIntradayDownloadLink((Currencypair) securitycurrency);
      }
    } else {
      // The content of the request is created by this backend.
      return getDownlinkWithApiKey(securitycurrency, true);
    }
  }

  /**
   * Marks the generic connector endpoint as successfully used if the feed connector is a GenericFeedConnector. Also
   * transfers ownership to system (createdBy=0) if all endpoints of the connector have been used.
   */
  private void markGenericEndpointUsed(IFeedConnector feedConnector, S securitycurrency) {
    if (genericConnectorEndpointJpaRepository != null && feedConnector instanceof GenericFeedConnector gfc) {
      String instrumentType = securitycurrency instanceof Security ? "SECURITY" : "CURRENCY";
      Integer idConnector = gfc.getConnectorDef().getIdGenericConnector();
      int updated = genericConnectorEndpointJpaRepository.markEndpointUsedSuccessfully(idConnector,
          IFeedConnector.FeedSupport.FS_INTRA.name(), instrumentType);
      if (updated > 0) {
        genericConnectorEndpointJpaRepository.transferOwnershipIfAllEndpointsUsed(idConnector);
      }
    }
  }

  /**
   * Determines if a delayed intraday update should be allowed based on timing constraints and data provider delays.
   *
   * <p>
   * This method implements sophisticated timing logic that considers:
   * <ul>
   * <li><strong>Initial Update Check</strong>: Allows update if no previous price exists (sLast == null)</li>
   * <li><strong>Delay Calculation</strong>: Combines entity's last timestamp, connector's intradayDelayedSeconds, and
   * timeout parameter</li>
   * <li><strong>Timing Formula</strong>: Update allowed when: lastTimestamp + connectorDelay + timeout <
   * currentTime</li>
   * </ul>
   * </p>
   *
   * <p>
   * This ensures compliance with data provider delay requirements while preventing excessive update frequency that
   * could impact system performance or violate rate limits.
   * </p>
   *
   * @param securitycurrency        the security currency to check for update eligibility
   * @param feedConnector           the feed connector providing delay configuration (getIntradayDelayedSeconds())
   * @param scIntradayUpdateTimeout additional timeout in seconds defining minimum update intervals
   * @return true if the delayed update should proceed, false if timing constraints prevent the update
   */
  private boolean allowDelayedIntradayUpdate(final S securitycurrency, final IFeedConnector feedConnector,
      final int scIntradayUpdateTimeout) {
    final long lessThenPossible = System.currentTimeMillis() - 1000L * scIntradayUpdateTimeout;
    return securitycurrency.getSLast() == null
        || securitycurrency.getSTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            + feedConnector.getIntradayDelayedSeconds() * 1000L < lessThenPossible;
  }
}
