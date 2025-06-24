package grafioschtrader.connector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.common.UserAccessHelper;
import grafiosch.entities.User;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;

/**
 * Utility class providing static helper methods for feed connector operations
 * and management. This class serves as a central location for common connector
 * tasks including connector lookup, access control validation, and HTTP content
 * retrieval functionality.
 */
public class ConnectorHelper {


  /**
   * Locates a feed connector by its unique identifier that supports the specified feed type.
   * This method searches through a list of available connectors to find one that matches
   * both the connector ID and supports the requested feed support type.
   * 
   * <p>
   * The search criteria require:
   * </p>
   * <ul>
   *   <li>Exact match on connector ID (case-sensitive)</li>
   *   <li>Non-null feed support for the specified type</li>
   * </ul>
   * 
   * <p>
   * This method is commonly used when the system needs to route data requests
   * to a specific connector based on user configuration or automatic selection.
   * </p>
   * 
   * @param feedConnectors the list of available feed connectors to search through
   * @param idConnector the unique identifier of the connector to find
   * @param feedSupport the type of feed support required (e.g., FS_HISTORY, FS_INTRA, FS_DIVIDEND)
   * @return the matching connector if found, or null if no connector matches both criteria
   * 
   * @see IFeedConnector#getID()
   * @see IFeedConnector#getSecuritycurrencyFeedSupport(FeedSupport)
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

  /**
   * Determines whether the current authenticated user has permission to access
   * the specified feed connector's configuration and API key information.
   * 
   * <p>
   * The access control logic follows these rules:
   * </p>
   * <ul>
   *   <li><strong>API Key Connectors:</strong> Require administrator privileges due to sensitive API key data</li>
   *   <li><strong>Regular Connectors:</strong> Accessible to all authenticated users</li>
   * </ul>
   * 
   * <p>
   * This method is essential for protecting sensitive API key information while
   * allowing general users to configure and use public data connectors.
   * </p>
   * 
   * 
   * @param feedConnector the connector to check access permissions for
   * @return true if the current user can access this connector, false otherwise
   */
  public static boolean canAccessConnectorApiKey(final IFeedConnector feedConnector) {
    // it is not possible to get the security context in other thread!
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return UserAccessHelper.isAdmin(user) && feedConnector instanceof BaseFeedApiKeyConnector
        || !(feedConnector instanceof BaseFeedApiKeyConnector);
  }

  /**
   * Retrieves the content of a web page as a string via HTTP GET request.
   * This utility method provides a simple way to fetch content from web URLs
   * for display purposes, particularly in frontend interfaces.
   * 
   * <p>
   * The method supports two output formats:
   * </p>
   * <ul>
   *   <li><strong>Plain Text:</strong> Returns content with original line breaks</li>
   *   <li><strong>HTML-Friendly:</strong> Replaces line breaks with &lt;br /&gt; tags for web display</li>
   * </ul>
   * 
   * <p>
   * Common use cases include:
   * </p>
   * <ul>
   *   <li>Displaying raw data provider responses in administrative interfaces</li>
   *   <li>Fetching and showing API documentation or status pages</li>
   *   <li>Retrieving sample data for connector testing and validation</li>
   * </ul>
   * 
   * <p>
   * <strong>Security Note:</strong> This method makes direct HTTP requests to
   * external URLs. Ensure that URLs are validated and trusted before calling
   * this method to prevent SSRF (Server-Side Request Forgery) attacks.
   * </p>
   * 
   * @param urlString the URL to retrieve content from (must be a valid HTTP/HTTPS URL)
   * @param addHttpNewlines if true, replaces line breaks with HTML &lt;br /&gt; tags;
   *                        if false, preserves original line breaks
   * @return the content of the web page as a string
   */
  public static String getContentOfHttpRequestAsString(String urlString, boolean addHttpNewlines) throws Exception {
    URL url = new URI(urlString).toURL();
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
      String line;
      StringBuilder responseContent = new StringBuilder();
      while ((line = reader.readLine()) != null) {
        responseContent.append(line);
        if (addHttpNewlines) {
          responseContent.append("<br />");
        }
      }
      return responseContent.toString();
    }
  }

}
