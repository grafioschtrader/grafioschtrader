package grafioschtrader.connector;

import java.util.List;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;

public class ConnectorHelper {

  /*
  public static IFeedConnector getConnectorByConnectorId(final List<IFeedConnector> feedConnectors,
      final Securitycurrency<?> securitycurrency, FeedSupport feedSupport) {
    for (final IFeedConnector feedConnector : feedConnectors) {
      if (feedConnector.getID().equals(securitycurrency.getIdConnectorIntra())
          && feedConnector.getSecuritycurrencyFeedSupport(feedSupport) != null) {
        return feedConnector;
      }
    }
    return null;
  }
  
  */
  public static IFeedConnector getConnectorByConnectorId(final List<IFeedConnector> feedConnectors,
      final String idConnector, FeedSupport feedSupport) {
    for (final IFeedConnector feedConnector : feedConnectors) {
      if (feedConnector.getID().equals(idConnector)
          && feedConnector.getSecuritycurrencyFeedSupport(feedSupport) != null) {
        return feedConnector;
      }
    }
    return null;
  }

  

}
