package grafioschtrader.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.entities.GTNet;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.repository.GTNetExchangeLogJpaRepository;

/**
 * Service for logging GTNet exchange operations.
 * Provides convenient methods for logging as supplier or consumer.
 */
@Service
public class GTNetExchangeLogService {

  @Autowired
  private GTNetExchangeLogJpaRepository gtNetExchangeLogJpaRepository;

  /**
   * Logs an exchange operation from the supplier (receiver) perspective.
   *
   * @param remoteGTNet the remote GTNet that sent the request
   * @param entityKind the type of data exchanged
   * @param entitiesReceived number of entities received in the request
   * @param entitiesWithNewerData number of entities we had newer data for
   * @param entitiesSent number of entities sent in the response
   */
  public void logAsSupplier(GTNet remoteGTNet, GTNetExchangeKindType entityKind,
      int entitiesReceived, int entitiesWithNewerData, int entitiesSent) {
    gtNetExchangeLogJpaRepository.logExchange(
        remoteGTNet, entityKind, true,
        entitiesReceived, entitiesWithNewerData, entitiesSent);
  }

  /**
   * Logs an exchange operation from the consumer (requester) perspective.
   *
   * @param remoteGTNet the remote GTNet that provided the data
   * @param entityKind the type of data exchanged
   * @param entitiesSent number of entities sent in the request
   * @param entitiesUpdated number of entities successfully updated from the response
   * @param entitiesReceived number of entities received in the response
   */
  public void logAsConsumer(GTNet remoteGTNet, GTNetExchangeKindType entityKind,
      int entitiesSent, int entitiesUpdated, int entitiesReceived) {
    gtNetExchangeLogJpaRepository.logExchange(
        remoteGTNet, entityKind, false,
        entitiesSent, entitiesUpdated, entitiesReceived);
  }
}
