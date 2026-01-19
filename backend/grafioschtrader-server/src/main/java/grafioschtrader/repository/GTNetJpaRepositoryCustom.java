package grafioschtrader.repository;

import java.util.List;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
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
   * Updates a GTNet entity with selective attribute modifications.
   * Only attributes marked with appropriate update annotations will be changed.
   * When serverBusy status changes, automatically notifies all connected peers.
   *
   * @param gtNet the entity containing new values to save
   * @return the updated GTNet entity
   * @throws Exception if the save operation fails or validation errors occur
   */
  GTNet saveOnlyAttributes(GTNet gtNet) throws Exception;

  /**
   * Retrieves all GTNet domains with message counts for lazy loading.
   *
   * Combines multiple queries into a single response for efficient UI rendering:
   * <ul>
   *   <li>All GTNet entries (domains)</li>
   *   <li>Message counts per domain (for determining if expander should show)</li>
   *   <li>The local instance's GTNet ID (for highlighting)</li>
   * </ul>
   *
   * Messages are loaded lazily via {@link #getMessagesByIdGtNet(Integer)} when expanded.
   *
   * @return combined DTO for the GTNet setup screen
   */
  GTNetWithMessages getAllGTNetsWithMessages();

  /**
   * Retrieves all messages for a specific GTNet domain.
   * Used for lazy loading when a row is expanded in the UI.
   *
   * @param idGtNet the GTNet domain ID
   * @return list of messages ordered by timestamp descending (newest first)
   */
  List<GTNetMessage> getMessagesByIdGtNet(Integer idGtNet);

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

  /**
   * Broadcasts a settings update notification to all GTNet peers with configured exchange.
   *
   * Sends GT_NET_SETTINGS_UPDATED_ALL_C to all peers to inform them that this server's settings
   * (dailyRequestLimit, GTNetEntity.acceptRequest, serverState, maxLimit) have changed.
   * The message envelope automatically includes the updated sourceGtNet DTO which peers use
   * to synchronize their local copy of this server's settings.
   *
   * This method is called by the background task GTNetSettingsBroadcastTask to avoid blocking the UI.
   */
  void broadcastSettingsUpdate();

  /**
   * Deletes a batch of GTNet messages along with their cascade-deleted responses.
   * Validates that all specified messages are deletable before performing deletion.
   *
   * @param idGtNetMessageList the IDs of the messages to delete
   */
  void deleteMessageBatch(java.util.List<Integer> idGtNetMessageList);
}
