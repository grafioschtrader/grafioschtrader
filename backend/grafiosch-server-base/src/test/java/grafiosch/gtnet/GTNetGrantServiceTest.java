package grafiosch.gtnet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfigEntity;
import grafiosch.entities.GTNetEntity;

/**
 * Guards what {@link GTNetGrantService} accepts as an agreed data exchange. The gate is the whole point of the change:
 * a completed handshake used to be enough to be served, because the accept flag it was tested against is global rather
 * than per peer.
 */
class GTNetGrantServiceTest {

  private static final TestKind LAST_PRICE = new TestKind("LAST_PRICE", (byte) 0, true);
  private static final TestKind HISTORICAL_PRICES = new TestKind("HISTORICAL_PRICES", (byte) 1, true);
  private static final TestKind SECURITY_METADATA = new TestKind("SECURITY_METADATA", (byte) 2, false);

  private final GTNetGrantService grantService = new GTNetGrantService(registryWithAllKinds());

  @Test
  void servesAPeerWithAnAcceptedGrant() {
    GTNet peer = peerWith(LAST_PRICE, true);
    assertThat(grantService.hasGrant(peer, LAST_PRICE)).isTrue();
  }

  @Test
  void refusesAPeerWhoseGrantWasRevoked() {
    GTNet peer = peerWith(LAST_PRICE, false);
    assertThat(grantService.hasGrant(peer, LAST_PRICE)).isFalse();
  }

  @Test
  void refusesAPeerThatHasTheEntityButNeverAskedForTheExchange() {
    GTNet peer = new GTNet();
    peer.getOrCreateEntityByKind(LAST_PRICE.getValue());
    assertThat(grantService.hasGrant(peer, LAST_PRICE)).isFalse();
  }

  @Test
  void refusesAKindThePeerWasNeverGranted() {
    GTNet peer = peerWith(LAST_PRICE, true);
    assertThat(grantService.hasGrant(peer, HISTORICAL_PRICES)).isFalse();
  }

  @Test
  void refusesAnUnknownPeer() {
    assertThat(grantService.hasGrant(null, LAST_PRICE)).isFalse();
  }

  @Test
  void admitsAWholeExchangeSyncOnASingleSyncableGrant() {
    // One sync request covers every kind at once, so refusing a peer that holds one of two grants would end an
    // exchange both sides agreed to.
    assertThat(grantService.hasAnyGrant(peerWith(HISTORICAL_PRICES, true))).isTrue();
  }

  @Test
  void doesNotAdmitAnExchangeSyncOnANonSyncableGrant() {
    assertThat(grantService.hasAnyGrant(peerWith(SECURITY_METADATA, true))).isFalse();
  }

  @Test
  void clearingEndsTheGrantOnceAndReportsWhetherItChangedAnything() {
    GTNet peer = peerWith(LAST_PRICE, true);

    assertThat(grantService.clearGrant(peer, LAST_PRICE)).isTrue();
    assertThat(grantService.hasGrant(peer, LAST_PRICE)).isFalse();
    assertThat(grantService.clearGrant(peer, LAST_PRICE)).isFalse();
  }

  @Test
  void clearingNeverCreatesAGrantRow() {
    GTNet peer = new GTNet();
    peer.getOrCreateEntityByKind(LAST_PRICE.getValue());

    assertThat(grantService.clearGrant(peer, LAST_PRICE)).isFalse();
    assertThat(grantService.findGrant(peer, LAST_PRICE)).isEmpty();
  }

  /**
   * The grant carries no direction, so a grant written when we accepted their request also authorises us to serve them.
   * Pinned here so that giving it a direction later is a deliberate change rather than an accident.
   */
  @Test
  void treatsAGrantAsDirectionAgnostic() {
    GTNet peer = peerWith(LAST_PRICE, true);
    assertThat(grantService.hasGrant(peer, LAST_PRICE)).isTrue();
    assertThat(grantService.hasAnyGrant(peer)).isTrue();
  }

  private static GTNet peerWith(IExchangeKindType kind, boolean exchange) {
    GTNet peer = new GTNet();
    GTNetEntity entity = peer.getOrCreateEntityByKind(kind.getValue());
    GTNetConfigEntity configEntity = new GTNetConfigEntity();
    configEntity.setExchange(exchange);
    entity.setGtNetConfigEntity(configEntity);
    return peer;
  }

  private static ExchangeKindTypeRegistry registryWithAllKinds() {
    ExchangeKindTypeRegistry registry = new ExchangeKindTypeRegistry();
    registry.registerExchangeKind(LAST_PRICE);
    registry.registerExchangeKind(HISTORICAL_PRICES);
    registry.registerExchangeKind(SECURITY_METADATA);
    return registry;
  }

  /** A kind of the application layer, which the library cannot see, played by the test. */
  private record TestKind(String name, Byte value, boolean syncable) implements IExchangeKindType {

    @Override
    public Byte getValue() {
      return value;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public boolean isSyncable() {
      return syncable;
    }
  }
}
