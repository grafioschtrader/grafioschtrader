package grafioschtrader.connector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.entities.User;

public class ConnectorHelper {

  /*
   * public static IFeedConnector getConnectorByConnectorId(final
   * List<IFeedConnector> feedConnectors, final Securitycurrency<?>
   * securitycurrency, FeedSupport feedSupport) { for (final IFeedConnector
   * feedConnector : feedConnectors) { if
   * (feedConnector.getID().equals(securitycurrency.getIdConnectorIntra()) &&
   * feedConnector.getSecuritycurrencyFeedSupport(feedSupport) != null) { return
   * feedConnector; } } return null; }
   *
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


  public static boolean canAccessConnectorApiKey(final IFeedConnector feedConnector) {
    // it is not possible to get the security context in other thread!
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return UserAccessHelper.isAdmin(user) && feedConnector instanceof BaseFeedApiKeyConnector
        || !(feedConnector instanceof BaseFeedApiKeyConnector);

  }

  public static String getContentOfHttpRequestAsString(String urlString, boolean addHttpNewlines) throws Exception {
    URL url = new URI(urlString).toURL();
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
      String line;
      StringBuilder responseContent = new StringBuilder();
      while ((line = reader.readLine()) != null) {
        responseContent.append(line);
        if(addHttpNewlines) {
          responseContent.append("<br />");
        }
      }
      return responseContent.toString();
    }
  }

}
