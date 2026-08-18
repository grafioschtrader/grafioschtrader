package grafioschtrader.priceupdate.intraday;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;

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
 * <p>This class handles intraday price updates for securities and currency pairs by interfacing with external
 * data providers through configured feed connectors. It provides comprehensive functionality including:
 * <ul>
 * <li><strong>Feed Connector Management</strong>: Locates and validates appropriate connectors based on entity configuration</li>
 * <li><strong>Retry Logic</strong>: Implements sophisticated retry mechanisms with configurable limits and automatic counter management</li>
 * <li><strong>Delayed Update Control</strong>: Respects data provider delay requirements and timeout constraints</li>
 * <li><strong>Secure Link Generation</strong>: Creates download links with proper API key protection and routing</li>
 * <li><strong>Entity-Specific Processing</strong>: Handles both Security and Currencypair entities with appropriate connector methods</li>
 * </ul></p>
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
   * @param jpaRepository           repository for persisting security currency entities
   * @param globalparametersService service for accessing global configuration parameters
   * @param feedConnectorbeans      list of available feed connector implementations
   * @param intraEntityAccess       interface for executing entity-specific intraday updates
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
    final IFeedConnector feedConnector = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
        securitycurrency.getIdConnectorIntra(), IFeedConnector.FeedSupport.FS_INTRA);

    if (feedConnector != null && (securitycurrency.getRetryIntraLoad() < maxIntraRetry || maxIntraRetry == -1)
        && securitycurrency.isActiveForIntradayUpdate(java.time.LocalDate.now())
        && allowDelayedIntradayUpdate(securitycurrency, feedConnector, scIntradayUpdateTimeout)
        && claimIntradayUpdate(securitycurrency, feedConnector, scIntradayUpdateTimeout)) {
      try {
        intraEntityAccess.updateIntraSecurityCurrency(securitycurrency, feedConnector);
        securitycurrency.setRetryIntraLoad((short) 0);
        markGenericEndpointUsed(feedConnector, securitycurrency);
      } catch (final Exception e) {
        log.error("Last price update failed securitycurrency={}", securitycurrency.toString(), e);
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
    }
    return securitycurrency;
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
   * Marks the generic connector endpoint as successfully used if the feed connector is a GenericFeedConnector.
   * Also transfers ownership to system (createdBy=0) if all endpoints of the connector have been used.
   */
  private void markGenericEndpointUsed(IFeedConnector feedConnector, S securitycurrency) {
    if (genericConnectorEndpointJpaRepository != null && feedConnector instanceof GenericFeedConnector gfc) {
      String instrumentType = securitycurrency instanceof Security ? "SECURITY" : "CURRENCY";
      Integer idConnector = gfc.getConnectorDef().getIdGenericConnector();
      int updated = genericConnectorEndpointJpaRepository.markEndpointUsedSuccessfully(
          idConnector, IFeedConnector.FeedSupport.FS_INTRA.name(), instrumentType);
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
