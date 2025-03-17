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
 * Update intraday prices thru the connector for securities or currency pair.
 *
 * @param <S>
 */
public class IntradayThruConnector<S extends Securitycurrency<S>> extends BaseIntradayThru<S> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private final List<IFeedConnector> feedConnectorbeans;
  private final JpaRepository<S, Integer> jpaRepository;
  private final IIntradayEntityAccess<S> intraEntityAccess;

  public IntradayThruConnector(JpaRepository<S, Integer> jpaRepository,
      GlobalparametersService globalparametersService, List<IFeedConnector> feedConnectorbeans,
      IIntradayEntityAccess<S> intraEntityAccess) {
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

  private boolean allowDelayedIntradayUpdate(final S securitycurrency, final IFeedConnector feedConnector,
      final int scIntradayUpdateTimeout) {
    final long lessThenPossible = System.currentTimeMillis() - 1000 * scIntradayUpdateTimeout;
    return securitycurrency.getSLast() == null || securitycurrency.getSTimestamp().getTime()
        + feedConnector.getIntradayDelayedSeconds() * 1000 < lessThenPossible;
  }
}
