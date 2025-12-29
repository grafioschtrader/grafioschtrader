package grafioschtrader.gtnet.handler.impl;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerOnlineStatusTypes;
import grafioschtrader.gtnet.handler.AbstractResponseHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.m2m.model.GTNetPublicDTO;
import grafioschtrader.repository.GTNetJpaRepository;

/**
 * Handler for server list response messages (accept and reject).
 *
 * Processes both GT_NET_UPDATE_SERVERLIST_ACCEPT_S and GT_NET_UPDATE_SERVERLIST_REJECTED_S responses.
 *
 * When accepted, the response includes a list of known servers in the payload. This handler processes the list and adds
 * any new servers to the local GTNet table (if allowServerCreation is enabled on our own entry).
 */
@Component
public class ServerlistResponseHandler extends AbstractResponseHandler {

  private static final Logger log = LoggerFactory.getLogger(ServerlistResponseHandler.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_ACCEPT_S;
  }

  @Override
  public Set<GTNetMessageCodeType> getSupportedMessageCodes() {
    return Set.of(GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_ACCEPT_S,
        GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_REJECTED_S);
  }

  @Override
  protected void processResponseSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNetMessageCodeType messageCode = context.getMessageCode();

    if (messageCode == GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_ACCEPT_S) {
      log.info("Server list request accepted by {} - message stored with id {}", context.getSourceDomain(),
          storedMessage.getIdGtNetMessage());
      processServerListPayload(context);
    } else {
      log.info("Server list request rejected by {} - message stored with id {}", context.getSourceDomain(),
          storedMessage.getIdGtNetMessage());
    }
  }

  /**
   * Processes the server list payload from an accepted response.
   *
   * For each server in the list:
   * <ul>
   *   <li>If the server already exists in our GTNet table, update its status</li>
   *   <li>If the server is new and our myGTNet has allowServerCreation=true, add it</li>
   * </ul>
   */
  private void processServerListPayload(GTNetMessageContext context) {
    if (!context.hasPayload()) {
      log.debug("No server list payload in response from {}", context.getSourceDomain());
      return;
    }

    try {
      List<GTNetPublicDTO> serverList = context.getObjectMapper().convertValue(context.getPayload(),
          new TypeReference<List<GTNetPublicDTO>>() {
          });

      GTNet myGTNet = context.getMyGTNet();
      int newServers = 0;
      int updatedServers = 0;

      for (GTNetPublicDTO serverDto : serverList) {
        // Skip our own entry
        if (serverDto.getDomainRemoteName().equals(myGTNet.getDomainRemoteName())) {
          continue;
        }

        GTNet existingServer = gtNetJpaRepository.findByDomainRemoteName(serverDto.getDomainRemoteName());

        if (existingServer != null) {
          // Update existing server's status
          boolean updated = updateExistingServer(existingServer, serverDto);
          if (updated) {
            updatedServers++;
          }
        } else if (myGTNet.isAllowServerCreation()) {
          // Add new server
          createNewServer(serverDto);
          newServers++;
        }
      }

      log.info("Processed server list from {}: {} new servers added, {} servers updated", context.getSourceDomain(),
          newServers, updatedServers);

    } catch (Exception e) {
      log.warn("Failed to process server list payload from {}: {}", context.getSourceDomain(), e.getMessage());
    }
  }

  /**
   * Updates an existing server with information from the DTO.
   *
   * @return true if any changes were made
   */
  private boolean updateExistingServer(GTNet existing, GTNetPublicDTO dto) {
    boolean changed = false;

    // Update spread capability
    if (existing.isSpreadCapability() != dto.isSpreadCapability()) {
      existing.setSpreadCapability(dto.isSpreadCapability());
      changed = true;
    }

    // Update timezone if provided
    if (dto.getTimeZone() != null && !dto.getTimeZone().equals(existing.getTimeZone())) {
      existing.setTimeZone(dto.getTimeZone());
      changed = true;
    }

    if (changed) {
      gtNetJpaRepository.save(existing);
    }

    return changed;
  }

  /**
   * Creates a new GTNet entry from the DTO.
   */
  private void createNewServer(GTNetPublicDTO dto) {
    GTNet newServer = new GTNet();
    newServer.setDomainRemoteName(dto.getDomainRemoteName());
    newServer.setTimeZone(dto.getTimeZone() != null ? dto.getTimeZone() : "UTC");
    newServer.setSpreadCapability(dto.isSpreadCapability());
    newServer.setDailyRequestLimit(dto.getDailyRequestLimit());
    newServer.setServerOnline(GTNetServerOnlineStatusTypes.SOS_UNKNOWN);
    newServer.setServerBusy(false);
    newServer.setAllowServerCreation(false);

    gtNetJpaRepository.save(newServer);
    log.debug("Added new server from shared list: {}", dto.getDomainRemoteName());
  }
}
