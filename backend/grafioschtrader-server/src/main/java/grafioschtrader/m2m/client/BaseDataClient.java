package grafioschtrader.m2m.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.msg.ApplicationInfo;
import grafioschtrader.m2m.rest.GTNetM2MResource;
import grafioschtrader.repository.GTNetJpaRepositoryImpl;
import grafioschtrader.rest.RequestGTMappings;
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
 *
 * @see GTNetM2MResource for the receiving endpoint
 * @see GTNetJpaRepositoryImpl#sendMessage for usage context
 */
@Service
public class BaseDataClient {

  private static final Logger log = LoggerFactory.getLogger(BaseDataClient.class);

  /**
   * Result wrapper for M2M message sending operations.
   * Indicates whether the target server is reachable and provides the response if successful.
   */
  public record SendResult(boolean serverReachable, MessageEnvelope response, boolean serverBusy) {

    public static SendResult success(MessageEnvelope response) {
      return new SendResult(true, response, response != null && response.serverBusy);
    }

    public static SendResult unreachable() {
      return new SendResult(false, null, false);
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
        .uri(uriBuilder -> uriBuilder.path(RequestGTMappings.ACTUATOR_MAP + "/info").build()).retrieve()
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
          .uri(uriBuilder -> uriBuilder.path(RequestGTMappings.GTNET_M2M_MAP).build())
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON);

      if (tokenRemote != null) {
        requestSpec = requestSpec.header(GTNetM2MResource.AUTHORIZATION_HEADER, tokenRemote);
      }

      MessageEnvelope response = requestSpec
          .body(Mono.just(messageEnvelope), MessageEnvelope.class)
          .retrieve()
          .bodyToMono(MessageEnvelope.class)
          .block();

      return SendResult.success(response);
    } catch (WebClientRequestException e) {
      // Connection error - server is unreachable
      log.warn("GTNet server unreachable at {}: {}", targetDomain, e.getMessage());
      return SendResult.unreachable();
    } catch (WebClientResponseException e) {
      // Server responded with an error status (4xx, 5xx) - server is reachable but returned error
      log.warn("GTNet server at {} returned error status {}: {}", targetDomain, e.getStatusCode(), e.getMessage());
      return SendResult.success(null);
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
