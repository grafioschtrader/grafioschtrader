package grafioschtrader.gtnet.handler.impl;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetConfigEntity;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.AcceptRequestTypes;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractAnnouncementHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;

/**
 * Unified handler for GT_NET_DATA_REVOKE_SEL_C messages.
 *
 * Replaces the separate LastpriceRevokeHandler, EntityRevokeHandler, and BothRevokeHandler.
 * Processes revocations for any combination of data types via the entityKinds parameter.
 *
 * If no entityKinds parameter is provided, all known entity kinds are revoked.
 */
@Component
public class DataRevokeHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_DATA_REVOKE_SEL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    Set<GTNetExchangeKindType> revokedKinds = getRevokedEntityKinds(context);

    for (GTNetExchangeKindType kind : revokedKinds) {
      updateEntityForRevoke(remoteGTNet, kind);
    }

    saveRemoteGTNet(remoteGTNet);
  }

  /**
   * Updates a GTNetEntity to revoked state for the specified entity kind.
   * When we receive a revoke from them, they are stopping their side of the exchange,
   * so we lose RECEIVE capability. The entity state is also set to CLOSED.
   */
  private void updateEntityForRevoke(GTNet remoteGTNet, GTNetExchangeKindType kind) {
    remoteGTNet.getGtNetEntities().stream()
        .filter(e -> e.getEntityKind() == kind)
        .findFirst()
        .ifPresent(entity -> {
          entity.setAcceptRequest(AcceptRequestTypes.AC_CLOSED);
          entity.setServerState(GTNetServerStateTypes.SS_CLOSED);

          // Disable exchange since they revoked their side
          GTNetConfigEntity configEntity = entity.getGtNetConfigEntity();
          if (configEntity != null) {
            configEntity.setExchange(false);
          }
        });
  }

  /**
   * Parses the entityKinds parameter from the message context.
   * If no parameter is provided, returns all known entity kinds.
   *
   * @param context the message context
   * @return set of entity kinds to revoke
   */
  private Set<GTNetExchangeKindType> getRevokedEntityKinds(GTNetMessageContext context) {
    String entityKindsParam = context.getParamValue("entityKinds");
    if (entityKindsParam == null || entityKindsParam.isBlank()) {
      // Default: revoke all known types
      return Set.of(GTNetExchangeKindType.LAST_PRICE, GTNetExchangeKindType.HISTORICAL_PRICES);
    }

    return Arrays.stream(entityKindsParam.split(","))
        .map(String::trim)
        .map(this::parseEntityKind)
        .filter(kind -> kind != null)
        .collect(Collectors.toSet());
  }

  /**
   * Parses a single entity kind value (either numeric or name).
   */
  private GTNetExchangeKindType parseEntityKind(String value) {
    try {
      // Try parsing as numeric value first
      byte numericValue = Byte.parseByte(value);
      return GTNetExchangeKindType.getGTNetExchangeKindType(numericValue);
    } catch (NumberFormatException e) {
      // Try parsing as enum name
      try {
        return GTNetExchangeKindType.valueOf(value.toUpperCase());
      } catch (IllegalArgumentException ex) {
        return null;
      }
    }
  }
}
