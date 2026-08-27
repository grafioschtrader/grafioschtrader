package grafiosch.gtnet.handler;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetEntity;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.IExchangeKindType;

/**
 * Abstract base class for handling data request accept response messages.
 *
 * <p>
 * The entity kinds of the request being accepted come from {@link AbstractDataResponseHandler}; what is added here is
 * the state an acceptance leaves behind — the grant that authorises the exchange from now on.
 * </p>
 */
public abstract class AbstractDataRequestAcceptHandler extends AbstractDataResponseHandler {

  /**
   * Updates a GTNetEntity to add RECEIVE capability for the specified entity kind. When they accept our request, we
   * will RECEIVE data from them.
   *
   * @param remoteGTNet the remote GTNet
   * @param kind        the entity kind to update
   */
  protected void updateEntityForReceive(GTNet remoteGTNet, IExchangeKindType kind) {
    GTNetEntity entity = remoteGTNet.getOrCreateEntityByKind(kind.getValue());
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);
    entity.getOrCreateConfigEntity();
  }
}
