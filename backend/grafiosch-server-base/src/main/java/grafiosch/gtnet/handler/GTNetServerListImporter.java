package grafiosch.gtnet.handler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.gtnet.GTNetServerOnlineStatusTypes;
import grafiosch.gtnet.m2m.model.GTNetPublicDTO;
import grafiosch.repository.GTNetJpaRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Merges a server list that a peer returned with {@code GT_NET_UPDATE_SERVERLIST_ACCEPT_S} into the local GTNet table.
 *
 * <p>
 * The list arrives either in the synchronous reply envelope or as an inbound response of its own; both paths use this
 * class so they cannot drift apart. A known server is updated when the peer's data is newer. An unknown server is always
 * added: the list was requested by this instance, and an entry created from it carries neither a token nor an exchange
 * grant, so nothing can be exchanged with it before a handshake. {@code allowServerCreation} of the own entry is
 * therefore not consulted here; it only governs whether an unknown server may create its entry through a handshake.
 * </p>
 */
@Component
public class GTNetServerListImporter {

  private static final Logger log = LoggerFactory.getLogger(GTNetServerListImporter.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  /**
   * Imports the server list carried in a payload.
   *
   * @param myGTNet      the own GTNet entry, whose domain is skipped when it appears in the list
   * @param sourceDomain the domain of the peer that supplied the list, used for logging only
   * @param payload      the payload holding a JSON array of {@link GTNetPublicDTO}; null or JSON null imports nothing
   * @param objectMapper the mapper that converts the payload
   * @return the counts of what was added, updated and skipped
   */
  public ImportResult importServerList(GTNet myGTNet, String sourceDomain, JsonNode payload,
      ObjectMapper objectMapper) {
    if (payload == null || payload.isNull()) {
      log.warn("Server list from {} was accepted but carried no payload", sourceDomain);
      return new ImportResult(0, 0, 0, 0);
    }
    List<GTNetPublicDTO> serverList;
    try {
      serverList = objectMapper.convertValue(payload, new TypeReference<List<GTNetPublicDTO>>() {
      });
    } catch (RuntimeException e) {
      log.warn("Server list from {} could not be read: {}", sourceDomain, e.getMessage());
      return new ImportResult(0, 0, 0, 0);
    }

    int added = 0;
    int updated = 0;
    int unchanged = 0;
    int skipped = 0;
    for (GTNetPublicDTO serverDto : serverList) {
      String domain = serverDto.getDomainRemoteName();
      if (domain == null || domain.isBlank() || domain.equals(myGTNet.getDomainRemoteName())) {
        skipped++;
        continue;
      }
      GTNet existingServer = gtNetJpaRepository.findByDomainRemoteName(domain);
      if (existingServer == null) {
        createServer(serverDto);
        added++;
      } else if (updateServer(existingServer, serverDto)) {
        updated++;
      } else {
        unchanged++;
      }
    }

    ImportResult result = new ImportResult(added, updated, unchanged, skipped);
    log.info("Server list from {} with {} entries: {} added, {} updated, {} unchanged, {} skipped (own or no domain)",
        sourceDomain, serverList.size(), added, updated, unchanged, skipped);
    return result;
  }

  /**
   * Updates a known server from the DTO when the peer's data is newer than ours or either side has no timestamp.
   *
   * @return true if a field changed and the entry was saved
   */
  private boolean updateServer(GTNet existing, GTNetPublicDTO dto) {
    if (dto.getLastModifiedTime() != null && existing.getLastModifiedTime() != null
        && !dto.getLastModifiedTime().isAfter(existing.getLastModifiedTime())) {
      return false;
    }
    boolean changed = false;
    if (existing.isSpreadCapability() != dto.isSpreadCapability()) {
      existing.setSpreadCapability(dto.isSpreadCapability());
      changed = true;
    }
    if (dto.getTimeZone() != null && !dto.getTimeZone().equals(existing.getTimeZone())) {
      existing.setTimeZone(dto.getTimeZone());
      changed = true;
    }
    if (dto.getDailyRequestLimit() != null && !dto.getDailyRequestLimit().equals(existing.getDailyRequestLimit())) {
      existing.setDailyRequestLimit(dto.getDailyRequestLimit());
      changed = true;
    }
    if (changed) {
      gtNetJpaRepository.save(existing);
    }
    return changed;
  }

  /** Creates the entry of a server that is not yet known locally. Its online state stays unknown until checked. */
  private void createServer(GTNetPublicDTO dto) {
    GTNet newServer = new GTNet();
    newServer.setDomainRemoteName(dto.getDomainRemoteName());
    newServer.setTimeZone(dto.getTimeZone() != null ? dto.getTimeZone() : "UTC");
    newServer.setSpreadCapability(dto.isSpreadCapability());
    newServer.setDailyRequestLimit(dto.getDailyRequestLimit());
    newServer.setServerOnline(GTNetServerOnlineStatusTypes.SOS_UNKNOWN);
    newServer.setServerBusy(false);
    newServer.setAllowServerCreation(false);
    gtNetJpaRepository.save(newServer);
    log.info("Added new server from shared list: {}", dto.getDomainRemoteName());
  }

  /**
   * Outcome of one import.
   *
   * @param added     servers that were unknown and have been created
   * @param updated   known servers whose data was changed
   * @param unchanged known servers left as they were, because our data was as new or nothing differed
   * @param skipped   entries ignored because they named no domain or the own instance
   */
  public record ImportResult(int added, int updated, int unchanged, int skipped) {
  }
}
