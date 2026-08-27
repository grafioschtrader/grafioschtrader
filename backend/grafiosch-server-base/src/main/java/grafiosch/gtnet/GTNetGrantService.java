package grafiosch.gtnet;

import java.util.Optional;

import org.springframework.stereotype.Service;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfigEntity;
import grafiosch.entities.GTNetEntity;

/**
 * Answers whether a peer may be served a kind of data exchange.
 *
 * <p>
 * The documented state machine goes {@code Connected → DataExchangeActive} on an accepted data request, with a revoke
 * as the way back, but nothing enforced it: every inbound data handler tested only this instance's own accept flag,
 * which is a global "do I serve this kind at all" rather than a decision about one peer. Possession of a token plus a
 * globally open entity was therefore sufficient, and a peer that had merely completed a handshake could ask for
 * everything.
 * </p>
 *
 * <p>
 * The grant is the {@link GTNetConfigEntity} hanging off the peer's {@link GTNetEntity} for that kind. It is written
 * when a data request is accepted in either direction, and a revoke sets its {@code exchange} flag to false. It is
 * local: {@code syncGtNetEntitiesFromDTO} deliberately leaves it alone when a peer's published state is synchronized,
 * so a peer cannot grant itself anything by what it publishes about itself.
 * </p>
 *
 * <p>
 * The grant carries no direction. The same row is created whether we requested and they accepted, or they requested and
 * we accepted, so a grant in either direction authorises serving. Giving it a direction is a schema change and is
 * deliberately not part of this work.
 * </p>
 */
@Service
public class GTNetGrantService {

  /** The stable reason a message is refused for want of a grant. */
  public static final String NO_GRANT = "NO_GRANT";

  private final ExchangeKindTypeRegistry exchangeKindRegistry;

  public GTNetGrantService(ExchangeKindTypeRegistry exchangeKindRegistry) {
    this.exchangeKindRegistry = exchangeKindRegistry;
  }

  /**
   * Whether this peer has an accepted, unrevoked grant for the given kind.
   *
   * @param remoteGTNet the peer asking, null when the peer is unknown
   * @param kind        the exchange kind being asked for
   * @return true when a grant exists and has not been revoked
   */
  public boolean hasGrant(GTNet remoteGTNet, IExchangeKindType kind) {
    if (remoteGTNet == null || kind == null) {
      return false;
    }
    return remoteGTNet.getEntityByKind(kind.getValue()).map(GTNetEntity::getGtNetConfigEntity)
        .filter(GTNetConfigEntity::isExchange).isPresent();
  }

  /**
   * Whether this peer has a grant for at least one syncable kind.
   *
   * <p>
   * This is the form the exchange-sync code needs. One sync request covers every kind at once, so refusing it wholesale
   * because a peer holds only one of two grants would end an exchange that both sides agreed to.
   * </p>
   *
   * @param remoteGTNet the peer asking, null when the peer is unknown
   * @return true when at least one syncable kind is granted
   */
  public boolean hasAnyGrant(GTNet remoteGTNet) {
    return remoteGTNet != null
        && exchangeKindRegistry.getSyncableKinds().stream().anyMatch(k -> hasGrant(remoteGTNet, k));
  }

  /**
   * The grant row of a peer for one kind, when it exists at all — revoked or not.
   *
   * @param remoteGTNet the peer
   * @param kind        the exchange kind
   * @return the grant row, empty when the peer never had one for that kind
   */
  public Optional<GTNetConfigEntity> findGrant(GTNet remoteGTNet, IExchangeKindType kind) {
    if (remoteGTNet == null || kind == null) {
      return Optional.empty();
    }
    return remoteGTNet.getEntityByKind(kind.getValue()).map(GTNetEntity::getGtNetConfigEntity);
  }

  /**
   * Ends a grant without creating one.
   *
   * <p>
   * This is what a rejection and a revoke leave behind, and it is deliberately the only state either of them writes.
   * Setting {@code acceptRequest} and {@code serverState} on the peer's row instead would look like a state change and
   * be undone by the peer's very next envelope, because those two fields are re-synchronized from what the peer
   * publishes about itself.
   * </p>
   *
   * @param remoteGTNet the peer whose grant ends
   * @param kind        the exchange kind that is no longer granted
   * @return true when a grant was present and has been cleared
   */
  public boolean clearGrant(GTNet remoteGTNet, IExchangeKindType kind) {
    return findGrant(remoteGTNet, kind).filter(GTNetConfigEntity::isExchange).map(configEntity -> {
      configEntity.setExchange(false);
      return true;
    }).orElse(false);
  }
}
