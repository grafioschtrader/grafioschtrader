package grafiosch.gtnet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetEntity;
import grafiosch.m2m.GTNetMessageHelper;
import grafiosch.m2m.client.BaseDataClient;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;

/**
 * Single-peer GTNet status probe. Shared by the scheduled {@code GTNetServerStatusCheckTask}
 * and the administrator-triggered {@code POST /gtnet/{idGtNet}/checkstatus} REST endpoint.
 *
 * <p>A peer is considered {@link GTNetServerOnlineStatusTypes#SOS_ONLINE} only when a ping
 * round-trips successfully as a valid GTNet protocol response. TCP reachability alone is
 * not sufficient: a reverse proxy or DuckDNS placeholder page can accept the connection and
 * reply with an HTTP error while the GT application itself is down. Peers that cannot be
 * probed because the outbound handshake is incomplete ({@code gtNetConfig.tokenRemote} is
 * {@code null}) are marked {@link GTNetServerOnlineStatusTypes#SOS_UNKNOWN} rather than left
 * at a stale status set by an inbound handshake message.
 */
@Service
public class GTNetStatusCheckService {

  private static final Logger log = LoggerFactory.getLogger(GTNetStatusCheckService.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private BaseDataClient baseDataClient;

  /**
   * Pings {@code peer} from the identity of {@code myGTNet} and persists the resulting
   * online and busy status, together with the per-entity {@code serverState}.
   *
   * <p>Status decision:
   * <ul>
   *   <li>{@link SendResult#isDelivered()} true  &rarr; {@link GTNetServerOnlineStatusTypes#SOS_ONLINE}</li>
   *   <li>{@link SendResult#isDelivered()} false &rarr; {@link GTNetServerOnlineStatusTypes#SOS_OFFLINE}
   *       (covers both network-unreachable and HTTP-error responses from a proxy)</li>
   *   <li>Thrown exception when the previous status was ONLINE &rarr; SOS_OFFLINE</li>
   * </ul>
   *
   * @param peer    the remote peer to probe; must have a completed outbound handshake
   *                ({@code gtNetConfig.tokenRemote} not null)
   * @param myGTNet the local GTNet entry used as the sender identity
   * @return the (possibly updated and saved) peer entity
   */
  @Transactional
  public GTNet checkAndUpdatePeer(GTNet peer, GTNet myGTNet) {
    GTNetServerOnlineStatusTypes previousStatus = peer.getServerOnline();

    try {
      SendResult result = GTNetMessageHelper.sendPingWithStatus(baseDataClient, myGTNet, peer,
          globalparametersJpaRepository);

      boolean delivered = result.isDelivered();
      GTNetServerOnlineStatusTypes newStatus = GTNetServerOnlineStatusTypes.fromReachable(delivered);
      peer.setServerOnline(newStatus);

      if (delivered) {
        peer.setServerBusy(result.serverBusy());
        log.debug("Peer {} is online (busy={})", peer.getDomainRemoteName(), peer.isServerBusy());
      } else {
        peer.setServerBusy(false);
        if (result.httpError()) {
          log.info("Peer {} marked offline: HTTP {}", peer.getDomainRemoteName(), result.httpStatusCode());
        } else {
          log.debug("Peer {} is offline (unreachable)", peer.getDomainRemoteName());
        }
      }

      GTNetServerStateTypes entityState = determineEntityState(delivered, peer.isServerBusy());
      boolean stateChanged = updateEntityStates(peer, entityState);

      if (previousStatus != newStatus || stateChanged) {
        gtNetJpaRepository.save(peer);
      }
      return peer;
    } catch (Exception e) {
      log.warn("Error checking status for peer {}: {}", peer.getDomainRemoteName(), e.getMessage());
      if (previousStatus == GTNetServerOnlineStatusTypes.SOS_ONLINE) {
        peer.setServerOnline(GTNetServerOnlineStatusTypes.SOS_OFFLINE);
        peer.setServerBusy(false);
        updateEntityStates(peer, GTNetServerStateTypes.SS_CLOSED);
        gtNetJpaRepository.save(peer);
      }
      return peer;
    }
  }

  /**
   * Resets a peer's online status to {@link GTNetServerOnlineStatusTypes#SOS_UNKNOWN} and
   * closes all its GTNetEntity rows. Used when the outbound handshake is incomplete so a
   * ping cannot be authenticated. Without this reset, a stale {@code SOS_ONLINE} written
   * by an inbound handshake envelope would persist indefinitely.
   *
   * @param peer the remote peer whose status we cannot verify
   * @return the (possibly updated and saved) peer entity
   */
  @Transactional
  public GTNet markUnverifiable(GTNet peer) {
    boolean changed = false;
    if (peer.getServerOnline() != GTNetServerOnlineStatusTypes.SOS_UNKNOWN) {
      peer.setServerOnline(GTNetServerOnlineStatusTypes.SOS_UNKNOWN);
      changed = true;
    }
    if (peer.isServerBusy()) {
      peer.setServerBusy(false);
      changed = true;
    }
    changed |= updateEntityStates(peer, GTNetServerStateTypes.SS_CLOSED);
    if (changed) {
      log.info("Peer {} has no outbound handshake token; resetting status to UNKNOWN",
          peer.getDomainRemoteName());
      gtNetJpaRepository.save(peer);
    }
    return peer;
  }

  private GTNetServerStateTypes determineEntityState(boolean serverReachable, boolean serverBusy) {
    if (serverReachable && !serverBusy) {
      return GTNetServerStateTypes.SS_OPEN;
    }
    return GTNetServerStateTypes.SS_CLOSED;
  }

  private boolean updateEntityStates(GTNet peer, GTNetServerStateTypes newState) {
    boolean anyChanged = false;
    for (GTNetEntity entity : peer.getGtNetEntities()) {
      if (entity.getServerState() != newState) {
        entity.setServerState(newState);
        anyChanged = true;
      }
    }
    return anyChanged;
  }
}
