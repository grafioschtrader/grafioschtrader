package grafiosch.repository;

import java.util.List;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMaintenanceWindow;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.gtnet.model.GTNetMessageAttemptView;
import grafiosch.gtnet.model.GTNetWithMessages;
import grafiosch.gtnet.model.MsgRequest;
import grafiosch.gtnet.model.MultiTargetMsgRequest;

/**
 * Custom repository interface for complex GTNet operations not covered by Spring Data JPA.
 *
 * Provides three main capabilities:
 * <ul>
 * <li>Combined data retrieval for UI display</li>
 * <li>Client-initiated message submission</li>
 * <li>Server-side message response processing (M2M endpoint)</li>
 * </ul>
 *
 * @see BaseRepositoryCustom for base repository interface
 */
public interface GTNetJpaRepositoryCustom extends BaseRepositoryCustom<GTNet> {

  /**
   * Updates a GTNet entity with selective attribute modifications. Only attributes marked with appropriate update
   * annotations will be changed. When serverBusy status changes, automatically notifies all connected peers.
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
   * <li>All GTNet entries (domains)</li>
   * <li>Message counts per domain (for determining if expander should show)</li>
   * <li>The local instance's GTNet ID (for highlighting)</li>
   * </ul>
   *
   * Messages are loaded lazily via {@link #getMessagesByIdGtNet(Integer)} when expanded.
   *
   * @return combined DTO for the GTNet setup screen
   */
  GTNetWithMessages getAllGTNetsWithMessages();

  /**
   * Retrieves the announced maintenance windows of one GTNet domain, most recent first. Loaded lazily when the
   * maintenance panel of the expanded row is opened; the counts alone come with {@link #getAllGTNetsWithMessages()}.
   *
   * @param idGtNet the domain whose windows are wanted
   * @return the windows of that domain, empty when it announced none
   */
  List<GTNetMaintenanceWindow> getMaintenanceWindowsByIdGtNet(Integer idGtNet);

  /** Returns the per-target outcomes of messages stored under one GTNet entry. */
  List<GTNetMessageAttemptView> getMessageAttemptsByIdGtNet(Integer idGtNet);

  /**
   * Retrieves all messages for a specific GTNet domain. Used for lazy loading when a row is expanded in the UI.
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
   * <li>Determines target domains (single or broadcast based on message code)</li>
   * <li>Ensures handshake is complete for each target (creates if needed)</li>
   * <li>Creates and saves the outgoing GTNetMessage</li>
   * <li>Sends the message via BaseDataClient</li>
   * <li>Saves any synchronous responses received</li>
   * </ol>
   *
   * @param msgRequest the message details from the UI
   * @return updated GTNetWithMessages for UI refresh
   */
  GTNetWithMessages submitMsg(MsgRequest msgRequest);

  /**
   * Processes an incoming M2M message and generates an appropriate response.
   *
   * This is the server-side entry point for all incoming GTNet traffic. Dispatches based on message code to handle:
   * <ul>
   * <li>GT_NET_PING - Liveness checks</li>
   * <li>GT_NET_FIRST_HANDSHAKE_S - Initial token exchange</li>
   * <li>Other message codes - Via handler registry</li>
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
   * Compares the provided token against the tokenThis we generated and sent to the remote domain during handshake. If
   * the tokens don't match or the domain is unknown, throws an exception that results in HTTP 401 Unauthorized.
   *
   * @param sourceDomain the domain URL from the incoming message
   * @param authToken    the token from the Authorization header
   * @throws SecurityException if the token is invalid or missing
   */
  void validateIncomingToken(String sourceDomain, String authToken);

  /**
   * Broadcasts a settings update notification to all GTNet peers with configured exchange.
   *
   * Sends GT_NET_SETTINGS_UPDATED_ALL_C to all peers to inform them that this server's settings (dailyRequestLimit,
   * GTNetEntity.acceptRequest, serverState, maxLimit) have changed. The message envelope automatically includes the
   * updated sourceGtNet DTO which peers use to synchronize their local copy of this server's settings.
   *
   * This method is called by the background task GTNetSettingsBroadcastTask to avoid blocking the UI.
   */
  void broadcastSettingsUpdate();

  /**
   * Deletes a batch of GTNet messages along with their cascade-deleted responses. Validates that all specified messages
   * are deletable before performing deletion.
   *
   * @param idGtNetMessageList the IDs of the messages to delete
   */
  void deleteMessageBatch(List<Integer> idGtNetMessageList);

  /**
   * Submits an admin message to multiple selected targets via background delivery.
   *
   * <p>
   * Unlike {@link #submitMsg(MsgRequest)} which sends immediately, this method creates a single GTNetMessage and queues
   * delivery via GTNetMessageAttempt entries for each target. The actual delivery is handled by the
   * GTNetAdminMessageDeliveryTask background job.
   * </p>
   *
   * <p>
   * This is used when an administrator selects multiple peers via checkboxes in the GTNetAdminMessagesComponent and
   * sends an admin message to all of them.
   * </p>
   *
   * @param multiTargetMsgRequest the message details including list of target domain IDs
   * @return updated GTNetWithMessages for UI refresh
   */
  GTNetWithMessages submitMsgToMultiple(MultiTargetMsgRequest multiTargetMsgRequest);

  /**
   * Deletes a GTNet server entry and all its dependent data via cascade.
   *
   * Validates that the entry is not the local server's own entry and that there are no pending request-response
   * messages with this server before performing the deletion.
   *
   * @param idGtNet the ID of the GTNet entry to delete
   */
  void deleteGTNet(Integer idGtNet);

  /**
   * Exports GTNet configuration tables as SQL statements (DELETE + INSERT). Tables in {@code deleteOnlyTables} get
   * DELETE statements but are not exported (no INSERT). Tables in {@code exportAndDeleteTables} get both.
   *
   * @param exportHeader          header comment line for identifying the export type
   * @param deleteOnlyTables      tables to delete during import but not export (data rebuilt by jobs)
   * @param exportAndDeleteTables tables to both delete and export, in delete order (children first)
   * @return SQL string containing DELETE, INSERT, and UPDATE statements
   */
  String exportGTNetConfig(String exportHeader, String[] deleteOnlyTables, String[] exportAndDeleteTables);

  /**
   * Imports GTNet configuration from a SQL export string. Validates the header marker and each statement before
   * execution. Runs within a single transaction — rolls back on any failure.
   *
   * <p>
   * The uploaded file carries GTNet tables only, never {@code globalparameters}, so the own-entry parameter
   * {@code g.gnet.my.entry.id} is re-resolved from the imported rows as part of the import: by the domain name this
   * instance identified itself with before the import, otherwise by matching an imported domain against this machine's
   * network interfaces. When neither identifies an entry the parameter is left as it is and the failure is logged, so
   * that a host whose public domain resolves to no locally bound address does not lose an import over it.
   * </p>
   *
   * @param sqlStatements  the SQL export content to import
   * @param expectedHeader the expected header comment (e.g., "-- GTNET_EXPORT_V1_BASE")
   */
  void importGTNetConfig(String sqlStatements, String expectedHeader);

  /**
   * Triggers an immediate online-status check for a single GTNet peer.
   *
   * <p>
   * Behaviour mirrors the scheduled {@code GTNetServerStatusCheckTask} but for one peer:
   * <ul>
   * <li>If the outbound handshake is complete, the peer is pinged. Its {@code serverOnline}, {@code serverBusy} and
   * child {@code GTNetEntity.serverState} values are updated and persisted.</li>
   * <li>If the outbound handshake is incomplete ({@code tokenRemote} is null), the peer is set to {@code SOS_UNKNOWN}
   * and all its entities are closed.</li>
   * <li>If the target is the local server entry or the local entry is not configured, the peer is returned
   * unchanged.</li>
   * </ul>
   *
   * @param idGtNet the ID of the remote GTNet entry to probe
   * @return the updated {@link GTNet} entity
   * @throws java.util.NoSuchElementException if no GTNet entry exists with the given ID
   */
  GTNet checkPeerStatusNow(Integer idGtNet);

  /**
   * Clears the credentials this instance shares with one peer, so that a fresh first handshake from that peer is
   * admitted again.
   *
   * <p>
   * A peer that has lost its own copy of the tokens - a rebuilt database, a restored backup, a migrated instance -
   * cannot repair the relationship by itself: the first handshake is the one unauthenticated code, and since it may
   * create a relationship but never replace one, it is refused with {@code HANDSHAKE_ALREADY_ESTABLISHED} as long as
   * a token is on record here. Rotation through the authenticated token refresh is closed to that peer for the same
   * reason. Re-admitting it is therefore a deliberate act of the administrator on this side, and this is that act.
   * </p>
   *
   * <p>
   * Only the credentials are dropped. The peer row, its messages, its exchange kinds and the operator's settings for
   * it - connection timeout, server list grant, violation count - all survive, which is what separates this from
   * deleting the peer. Because the peer can no longer be reached, it is left {@code SOS_UNKNOWN} with its entities
   * closed, exactly as a peer that was never handshaked.
   * </p>
   *
   * @param idGtNet the ID of the remote GTNet entry whose handshake is to be reset
   * @return every GTNet entry with its messages, so the caller can refresh its table
   * @throws grafiosch.exceptions.DataViolationException if the ID names the local server entry
   * @throws java.util.NoSuchElementException            if no GTNet entry exists with the given ID
   */
  GTNetWithMessages resetHandshake(Integer idGtNet);
}
