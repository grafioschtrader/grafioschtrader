package grafiosch.gtnet;

import java.util.List;
import java.util.Objects;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetEntity;
import grafiosch.gtnet.m2m.model.GTNetEntityPublicDTO;
import grafiosch.gtnet.m2m.model.GTNetPublicDTO;

/**
 * Copies what a peer publishes about itself in {@code MessageEnvelope.sourceGtNet} onto the local entry that represents
 * that peer. Every envelope a peer sends carries this description, the answer to a ping included, so both the message
 * dispatch and the status check apply it through this one place instead of each keeping its own subset.
 *
 * <p>
 * Only published attributes are taken over: time zone, spread capability, the daily request limit the peer grants us,
 * and per entity kind its acceptRequest, serverState and maxLimit. The local configuration of an entity kind, the
 * {@code GTNetConfigEntity} that carries the grant, is never touched, so a peer cannot grant itself anything by what it
 * publishes.
 * </p>
 */
public final class GTNetPublishedStateSync {

  private GTNetPublishedStateSync() {
  }

  /**
   * Applies the published description of a peer to the local entry representing it. Nothing is saved here; the caller
   * persists the entry when this method reports a change.
   *
   * @param localRemoteEntry the local entry of the peer, changed in place
   * @param published        what the peer published about itself, may be null when an envelope carries none
   * @return true when at least one attribute or entity of the local entry changed
   */
  public static boolean apply(GTNet localRemoteEntry, GTNetPublicDTO published) {
    if (published == null) {
      return false;
    }
    boolean changed = false;
    if (published.getTimeZone() != null && !published.getTimeZone().equals(localRemoteEntry.getTimeZone())) {
      localRemoteEntry.setTimeZone(published.getTimeZone());
      changed = true;
    }
    if (published.isSpreadCapability() != localRemoteEntry.isSpreadCapability()) {
      localRemoteEntry.setSpreadCapability(published.isSpreadCapability());
      changed = true;
    }
    // The limit the peer accepts from us, which our outgoing guard measures against
    if (!Objects.equals(published.getDailyRequestLimit(), localRemoteEntry.getDailyRequestLimit())) {
      localRemoteEntry.setDailyRequestLimit(published.getDailyRequestLimit());
      changed = true;
    }
    if (published.getGtNetEntities() != null) {
      changed |= applyEntities(localRemoteEntry, published.getGtNetEntities());
    }
    return changed;
  }

  /**
   * Synchronizes the published entity kinds. Existing entities are updated and missing ones are created by kind.
   *
   * @param localEntry     the local entry of the peer
   * @param sourceEntities the entity kinds the peer published
   * @return true if any changes were made
   */
  private static boolean applyEntities(GTNet localEntry, List<GTNetEntityPublicDTO> sourceEntities) {
    boolean changed = false;
    for (GTNetEntityPublicDTO sourceEntity : sourceEntities) {
      GTNetEntity localEntity = localEntry.getOrCreateEntityByKind(sourceEntity.getEntityKind());
      if (localEntity.getAcceptRequest() != sourceEntity.getAcceptRequest()) {
        localEntity.setAcceptRequest(sourceEntity.getAcceptRequest());
        changed = true;
      }
      if (localEntity.getServerState() != sourceEntity.getServerState()) {
        localEntity.setServerState(sourceEntity.getServerState());
        changed = true;
      }
      if (!Objects.equals(localEntity.getMaxLimit(), sourceEntity.getMaxLimit())) {
        localEntity.setMaxLimit(sourceEntity.getMaxLimit());
        changed = true;
      }
      // The local config is not synchronized: the exchange direction is a local decision
      if (localEntity.getIdGtNetEntity() == null) {
        changed = true;
      }
    }
    return changed;
  }
}
