package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.GTNet;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.GTNetWithMessages;
import grafioschtrader.gtnet.model.MsgRequest;

/**
 * Custom repository interface for complex GTNet operations not covered by Spring Data JPA.
 *
 * Provides three main capabilities:
 * <ul>
 *   <li>Combined data retrieval for UI display</li>
 *   <li>Client-initiated message submission</li>
 *   <li>Server-side message response processing (M2M endpoint)</li>
 * </ul>
 *
 * @see GTNetJpaRepositoryImpl for implementation details
 */
public interface GTNetJpaRepositoryCustom extends BaseRepositoryCustom<GTNet> {

  /**
   * Retrieves all GTNet domains with their associated message history.
   *
   * Combines multiple queries into a single response for efficient UI rendering:
   * <ul>
   *   <li>All GTNet entries (domains)</li>
   *   <li>All messages grouped by domain ID</li>
   *   <li>The local instance's GTNet ID (for highlighting)</li>
   * </ul>
   *
   * @return combined DTO for the GTNet setup screen
   */
  GTNetWithMessages getAllGTNetsWithMessages();

  /**
   * Submits a message from the UI to one or more remote domains.
   *
   * Handles the complete message submission workflow:
   * <ol>
   *   <li>Determines target domains (single or broadcast based on message code)</li>
   *   <li>Ensures handshake is complete for each target (creates if needed)</li>
   *   <li>Creates and saves the outgoing GTNetMessage</li>
   *   <li>Sends the message via BaseDataClient</li>
   *   <li>Saves any synchronous responses received</li>
   * </ol>
   *
   * @param msgRequest the message details from the UI
   * @return updated GTNetWithMessages for UI refresh
   */
  GTNetWithMessages submitMsg(MsgRequest msgRequest);

  /**
   * Processes an incoming M2M message and generates an appropriate response.
   *
   * This is the server-side entry point for all incoming GTNet traffic. Dispatches based on
   * message code to handle:
   * <ul>
   *   <li>GT_NET_PING - Liveness checks</li>
   *   <li>GT_NET_FIRST_HANDSHAKE_S - Initial token exchange</li>
   *   <li>Other message codes - (TODO: implementation incomplete)</li>
   * </ul>
   *
   * @param messageEnvelope the incoming message
   * @return response envelope to send back to the caller
   * @throws Exception if message processing fails
   */
  MessageEnvelope getMsgResponse(MessageEnvelope messageEnvelope) throws Exception;

  /**
   * Validates the authentication token from an incoming M2M request.
   *
   * Compares the provided token against the tokenThis we generated and sent to the remote domain
   * during handshake. If the tokens don't match or the domain is unknown, throws an exception
   * that results in HTTP 401 Unauthorized.
   *
   * @param sourceDomain the domain URL from the incoming message
   * @param authToken the token from the Authorization header
   * @throws SecurityException if the token is invalid or missing
   */
  void validateIncomingToken(String sourceDomain, String authToken);
}
