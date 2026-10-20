package grafioschtrader.mcp.oauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import grafioschtrader.udffill.http.UDFFillHttp;
import grafioschtrader.udffill.http.UDFFillHttp.FetchResult;

/**
 * Loads the Client ID Metadata Document of an OAuth client. The default goes through the guarded public fetch of the
 * user-defined field filling: HTTPS on port 443 only, no private, loopback or link-local address, bounded size and
 * time. A document must be served directly; a redirect to another URL is refused, because the document's
 * {@code client_id} has to equal the URL it identifies.
 */
@Component
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class ClientMetadataFetcher {

  private final UDFFillHttp udfFillHttp;

  public ClientMetadataFetcher(UDFFillHttp udfFillHttp) {
    this.udfFillHttp = udfFillHttp;
  }

  /**
   * @param clientId the HTTPS URL of the document
   * @return the document
   * @throws Exception when the URL is refused, not reachable or does not answer with a document of its own
   */
  public String fetch(String clientId) throws Exception {
    FetchResult result = udfFillHttp.fetch(clientId);
    if (!result.isSuccess() || !clientId.equals(result.finalUrl())) {
      throw new IllegalStateException("status " + result.status() + " from " + result.finalUrl());
    }
    return result.body();
  }
}
