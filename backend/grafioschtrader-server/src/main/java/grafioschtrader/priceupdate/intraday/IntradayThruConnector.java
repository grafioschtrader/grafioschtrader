package grafioschtrader.priceupdate.intraday;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.DownloadLink;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
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
  private final JpaRepository<S, Integer> jpaRepository;
  private final IIntradayEntityAccess<S> intraEntityAccess;

  /**
   * Constructs an intraday connector-based price updater.
   * 
   * @param jpaRepository           repository for persisting security currency entities
   * @param globalparametersService service for accessing global configuration parameters
   * @param feedConnectorbeans      list of available feed connector implementations
   * @param intraEntityAccess       interface for executing entity-specific intraday updates
   */
  public IntradayThruConnector(JpaRepository<S, Integer> jpaRepository, GlobalparametersService globalparametersService,
      List<IFeedConnector> feedConnectorbeans, IIntradayEntityAccess<S> intraEntityAccess) {
    super(globalparametersService);
    this.jpaRepository = jpaRepository;
    this.feedConnectorbeans = feedConnectorbeans;
    this.intraEntityAccess = intraEntityAccess;
  }

  @Override
  public S updateLastPriceSecurityCurrency(S securitycurrency, final short maxIntraRetry,
      final int scIntradayUpdateTimeout) {
    final IFeedConnector feedConnector = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
        securitycurrency.getIdConnectorIntra(), IFeedConnector.FeedSupport.FS_INTRA);

    Date now = new Date();
    if (feedConnector != null && (securitycurrency.getRetryIntraLoad() < maxIntraRetry || maxIntraRetry == -1)
        && securitycurrency.isActiveForIntradayUpdate(now)
        && allowDelayedIntradayUpdate(securitycurrency, feedConnector, scIntradayUpdateTimeout)) {
      try {
        intraEntityAccess.updateIntraSecurityCurrency(securitycurrency, feedConnector);
        securitycurrency.setRetryIntraLoad((short) 0);
      } catch (final Exception e) {
        log.error("Last price update failed securitycurrency={}", securitycurrency.toString(), e);
        securitycurrency.setRetryIntraLoad((short) (securitycurrency.getRetryIntraLoad() + 1));
      }
      securitycurrency = jpaRepository.save(securitycurrency);
    }
    return securitycurrency;
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
    final long lessThenPossible = System.currentTimeMillis() - 1000 * scIntradayUpdateTimeout;
    return securitycurrency.getSLast() == null || securitycurrency.getSTimestamp().getTime()
        + feedConnector.getIntradayDelayedSeconds() * 1000 < lessThenPossible;
  }
}
