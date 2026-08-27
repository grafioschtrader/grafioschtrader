package grafiosch.integration.gtnet;

import grafiosch.gtnet.IExchangeKindType;

/**
 * The exchange kinds of the reusable integration host.
 *
 * <p>
 * Registering exchange kinds is an application obligation: {@code ExchangeKindTypeRegistry} and the static array behind
 * {@code GTNetEntity.registerExchangeKindTypes} are filled from the consuming application alone. Without them
 * {@code GTNetWithMessages.exchangeKindTypes} comes back empty, the GTNet setup table renders no per-kind columns, the
 * peer edit dialog renders no batch entity table and no kind can be requested from a peer - so this host has to supply
 * its own, exactly as it supplies the tenant entity.
 * </p>
 *
 * <p>
 * The two constants are deliberately host-owned rather than a copy of the Grafioschtrader kinds, and they cover both
 * sides of every flag so the library behaviour that branches on them is exercised: {@link #INTEGRATION_STREAM} is
 * syncable and push-capable, {@link #INTEGRATION_LOOKUP} is neither. The latter is what makes
 * {@code GTNetJpaRepositoryImpl.validateEntityPushSupport} reject {@code AC_PUSH_OPEN} and what keeps a kind out of the
 * syncable column group.
 * </p>
 */
public enum IntegrationExchangeKindType implements IExchangeKindType {

  /** Bulk-synchronised, push-capable exchange kind - the ordinary case. */
  INTEGRATION_STREAM((byte) 0),

  /** On-demand lookup kind: excluded from bulk sync and not configurable as {@code AC_PUSH_OPEN}. */
  INTEGRATION_LOOKUP((byte) 1) {
    @Override
    public boolean isSyncable() {
      return false;
    }

    @Override
    public boolean supportsPush() {
      return false;
    }
  };

  private final Byte value;

  private IntegrationExchangeKindType(final Byte value) {
    this.value = value;
  }

  @Override
  public Byte getValue() {
    return this.value;
  }
}
