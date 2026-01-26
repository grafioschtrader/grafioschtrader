package grafiosch.m2m.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.gtnet.model.msg.ApplicationInfo;
import grafiosch.rest.RequestMappings;
import io.netty.resolver.ResolvedAddressTypes;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * HTTP client for machine-to-machine (M2M) communication with remote GTNet instances.
 *
 * Uses Spring WebClient with Reactor Netty for non-blocking HTTP calls (though currently used
 * in blocking mode via .block()). Handles two types of requests:
 * <ul>
 *   <li><b>Actuator Info</b>: GET to /actuator/info for liveness checks and metadata retrieval</li>
 *   <li><b>GTNet Messages</b>: POST to /m2m/gtnet for all GTNet protocol messages</li>
 * </ul>
 *
 * <h3>Authentication</h3>
 * M2M messages include the remote-supplied token in the Authorization header. This token was
 * exchanged during the handshake and must be validated by the receiving endpoint.
 *
 * <h3>Network Configuration</h3>
 * The client is configured to prefer IPv6 addresses, which may need adjustment depending on
 * deployment environments.
 *
 * <h3>Error Handling</h3>
 * Currently no explicit error handling - WebClient exceptions will propagate to callers.
 * Future improvements should include retry logic and proper error classification.
 */
@Service
public class BaseDataClient {

  private static final Logger log = LoggerFactory.getLogger(BaseDataClient.class);

  /** Standard HTTP Authorization header name. */
  public static final String AUTHORIZATION_HEADER = "Authorization";

  /**
   * Result wrapper for M2M message sending operations.
   * Distinguishes between successful delivery, HTTP errors, and network failures.
   */
  public record SendResult(boolean serverReachable, boolean httpError, int httpStatusCode,
      MessageEnvelope response, boolean serverBusy) {

    /**
     * Message was successfully delivered and a response was received.
     */
    public static SendResult success(MessageEnvelope response) {
      return new SendResult(true, false, 200, response, response != null && response.serverBusy);
    }

    /**
     * Server is unreachable due to network/connection error.
     */
    public static SendResult unreachable() {
      return new SendResult(false, false, 0, null, false);
    }

    /**
     * Server responded with an HTTP error status (4xx, 5xx).
     */
    public static SendResult httpError(int statusCode) {
      return new SendResult(true, true, statusCode, null, false);
    }

    /**
     * Returns true if the message was successfully delivered (server reachable and no HTTP error).
     */
    public boolean isDelivered() {
      return serverReachable && !httpError && response != null;
    }

    /**
     * Returns true if delivery failed (unreachable or HTTP error).
     */
    public boolean isFailed() {
      return !serverReachable || httpError;
    }
  }

  /**
   * Retrieves application metadata from a remote instance's actuator endpoint.
   *
   * Used during GTNet entry creation/update to verify the remote is reachable and running
   * Grafioschtrader. Also used to detect if the domain URL refers to the local machine.
   *
   * @param domainName the base URL of the remote instance (e.g., "https://example.com:8080")
   * @return application info including name, version, and user capacity
   * @throws WebClientResponseException if the remote returns an error status
   * @throws WebClientRequestException if the remote is unreachable
   */
  public ApplicationInfo getActuatorInfo(String domainName) {
    return getWebClientForDomain(domainName).get()
        .uri(uriBuilder -> uriBuilder.path(RequestMappings.ACTUATOR_MAP + "/info").build()).retrieve()
        .bodyToMono(ApplicationInfo.class).block();
  }

  /**
   * Sends a GTNet message to a remote instance.
   *
   * POSTs the message envelope to the remote's M2M endpoint with the authentication token
   * in the Authorization header. The remote validates the token against its stored tokenThis
   * for the sender's domain.
   *
   * @param tokenRemote the authentication token received from the remote during handshake
   * @param targetDomain the base URL of the remote instance
   * @param messageEnvelope the message to send
   * @return the response envelope from the remote
   * @throws WebClientResponseException if the remote returns an error (e.g., 401 for invalid token)
   * @throws WebClientRequestException if the remote is unreachable
   */
  public MessageEnvelope sendToMsg(String tokenRemote, String targetDomain, MessageEnvelope messageEnvelope) {
    return sendToMsgWithStatus(tokenRemote, targetDomain, messageEnvelope).response();
  }

  /**
   * Sends a GTNet message to a remote instance and returns detailed status information.
   *
   * This method wraps the HTTP call with error handling to distinguish between:
   * - Server reachable and responded (serverReachable=true, response contains data)
   * - Server unreachable due to network/connection error (serverReachable=false)
   *
   * @param tokenRemote the authentication token received from the remote during handshake
   * @param targetDomain the base URL of the remote instance
   * @param messageEnvelope the message to send
   * @return SendResult containing reachability status, response, and remote server's busy flag
   */
  public SendResult sendToMsgWithStatus(String tokenRemote, String targetDomain, MessageEnvelope messageEnvelope) {
    try {
      WebClient.RequestBodySpec requestSpec = getWebClientForDomain(targetDomain).post()
          .uri(uriBuilder -> uriBuilder.path(RequestMappings.GTNET_M2M_MAP).build())
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON);

      if (tokenRemote != null) {
        requestSpec = requestSpec.header(AUTHORIZATION_HEADER, tokenRemote);
      }

      MessageEnvelope response = requestSpec
          .body(Mono.just(messageEnvelope), MessageEnvelope.class)
          .retrieve()
          .bodyToMono(MessageEnvelope.class)
          .block();
      log.info("GTNet server reached at {}", targetDomain);
      return SendResult.success(response);
    } catch (WebClientRequestException e) {
      // Connection error - server is unreachable
      log.warn("GTNet server unreachable at {}: {}", targetDomain, e.getMessage());
      return SendResult.unreachable();
    } catch (WebClientResponseException e) {
      // Server responded with an error status (4xx, 5xx) - server is reachable but returned error
      log.warn("GTNet server at {} returned error status {}: {}", targetDomain, e.getStatusCode(), e.getMessage());
      return SendResult.httpError(e.getStatusCode().value());
    }
  }

  /**
   * Creates a WebClient configured for the specified domain.
   *
   * Configuration includes:
   * <ul>
   *   <li>IPv6 preferred address resolution (may need adjustment for IPv4-only environments)</li>
   *   <li>Base URL set to the target domain</li>
   * </ul>
   *
   * Note: Each call creates a new WebClient instance. For high-volume scenarios, consider
   * caching clients per domain.
   *
   * @param domainName the base URL for the WebClient
   * @return configured WebClient instance
   */
  private WebClient getWebClientForDomain(String domainName) {
    HttpClient httpClient = HttpClient.create().resolver(spec -> {
      spec.resolvedAddressTypes(ResolvedAddressTypes.IPV6_PREFERRED);
      spec.disableRecursionDesired(false);
    });

    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).baseUrl(domainName).build();
  }

}
