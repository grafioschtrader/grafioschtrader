package grafioschtrader.service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.gtnet.m2m.model.GTNetPublicDTO;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.m2m.GTNetMessageHelper;
import grafiosch.m2m.client.BaseDataClient;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.repository.GTNetConfigJpaRepository;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetSupplierDetail;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.model.msg.ExchangeSyncMsg;
import grafioschtrader.gtnet.model.msg.ExchangeSyncMsg.ExchangeSyncItem;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GTNetSupplierDetailJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Service for synchronizing GTNetExchange configurations between GTNet peers.
 *
 * Handles the bidirectional exchange of which instruments each server is willing to share price data for, and updates
 * GTNetSupplierDetail accordingly.
 *
 * <h3>Flow for AC_OPEN Bidirectional Exchange</h3>
 * <ol>
 * <li>Local server sends GT_NET_EXCHANGE_SYNC_SEL_RR_C with changed GTNetExchange items since last sync</li>
 * <li>Remote server updates its GTNetSupplierDetail based on received items</li>
 * <li>Remote server responds GT_NET_EXCHANGE_SYNC_RESPONSE_S with its own changed items</li>
 * <li>Local server updates its GTNetSupplierDetail based on response</li>
 * </ol>
 */
@Service
public class GTNetExchangeSyncService {

  private static final Logger log = LoggerFactory.getLogger(GTNetExchangeSyncService.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetSupplierDetailJpaRepository gtNetSupplierDetailJpaRepository;

  @Autowired
  private GTNetConfigJpaRepository gtNetConfigJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private BaseDataClient baseDataClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Synchronizes exchange configuration with a single peer using incremental mode.
   *
   * @param peer the remote GTNet server
   * @param sinceTimestamp only include changes after this timestamp
   * @return true if sync was successful
   */
  public boolean syncWithPeer(GTNet peer, Date sinceTimestamp) {
    return syncWithPeer(peer, sinceTimestamp, false);
  }

  /**
   * Synchronizes exchange configuration with a single peer.
   *
   * @param peer the remote GTNet server
   * @param sinceTimestamp only include changes after this timestamp (ignored in full recreation mode)
   * @param fullRecreation if true, ignores timestamp and recreates all GTNetSupplierDetail entries for this peer
   * @return true if sync was successful
   */
  @Transactional
  public boolean syncWithPeer(GTNet peer, Date sinceTimestamp, boolean fullRecreation) {
    GTNetConfig config = peer.getGtNetConfig();
    if (config == null) {
      log.debug("Skipping peer without config: {}", peer.getDomainRemoteName());
      return false;
    }

    // Build request with local items (all items in full recreation mode, changed items otherwise)
    List<ExchangeSyncItem> itemsToSend = fullRecreation
        ? getAllExchangeItems()
        : getChangedExchangeItems(sinceTimestamp);
    ExchangeSyncMsg requestPayload = ExchangeSyncMsg.forRequest(
        fullRecreation ? null : sinceTimestamp, itemsToSend);

    // Get local GTNet entry
    Integer myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository);
    GTNet myGTNet = gtNetJpaRepository.findById(myGTNetId)
        .orElseThrow(() -> new IllegalStateException("Local GTNet entry not found"));

    // Build and send request
    MessageEnvelope requestEnvelope = new MessageEnvelope();
    requestEnvelope.sourceDomain = myGTNet.getDomainRemoteName();
    requestEnvelope.sourceGtNet = new GTNetPublicDTO(myGTNet);
    requestEnvelope.serverBusy = myGTNet.isServerBusy();
    requestEnvelope.messageCode = GTNetMessageCodeType.GT_NET_EXCHANGE_SYNC_SEL_RR_C.getValue();
    requestEnvelope.timestamp = new Date();
    requestEnvelope.payload = objectMapper.valueToTree(requestPayload);

    log.debug("Sending exchange sync to {} with {} items (fullRecreation={})",
        peer.getDomainRemoteName(), itemsToSend.size(), fullRecreation);

    SendResult result = baseDataClient.sendToMsgWithStatus(
        config.getTokenRemote(), peer.getDomainRemoteName(), requestEnvelope);

    if (result.isFailed()) {
      if (result.httpError()) {
        log.warn("Peer {} returned HTTP error {} for exchange sync", peer.getDomainRemoteName(), result.httpStatusCode());
      } else {
        log.warn("Peer {} is unreachable for exchange sync", peer.getDomainRemoteName());
      }
      return false;
    }

    MessageEnvelope response = result.response();
    if (response == null || response.payload == null) {
      log.debug("No exchange sync data received from {}", peer.getDomainRemoteName());
      return true; // Successful but no data
    }

    // Process response - update or recreate supplier details based on mode
    try {
      ExchangeSyncMsg responsePayload = objectMapper.treeToValue(response.payload, ExchangeSyncMsg.class);
      if (fullRecreation) {
        recreateSupplierDetails(peer, responsePayload.items);
      } else {
        updateSupplierDetails(peer, responsePayload.items);
      }
      log.info("Exchange sync with {} complete: sent {}, received {} (fullRecreation={})",
          peer.getDomainRemoteName(), itemsToSend.size(), responsePayload.getItemCount(), fullRecreation);
      return true;
    } catch (JsonProcessingException e) {
      log.error("Failed to parse exchange sync response from {}", peer.getDomainRemoteName(), e);
      return false;
    }
  }

  /**
   * Gets exchange items that have changed since the given timestamp.
   *
   * @param sinceTimestamp the timestamp after which to find changes
   * @return list of exchange sync items with send flags enabled
   */
  public List<ExchangeSyncItem> getChangedExchangeItems(Date sinceTimestamp) {
    List<ExchangeSyncItem> items = new java.util.ArrayList<>();

    // Get changed securities with GTNet send enabled
    List<Security> changedSecurities = securityJpaRepository.findByGtNetLastModifiedTimeAfterAndIsinIsNotNull(sinceTimestamp);
    for (Security security : changedSecurities) {
      if (security.isGtNetLastpriceSend() || security.isGtNetHistoricalSend()) {
        items.add(ExchangeSyncItem.forSecurity(
            security.getIsin(), security.getCurrency(),
            security.isGtNetLastpriceSend(), security.isGtNetHistoricalSend()));
      }
    }

    // Get changed currency pairs with GTNet send enabled
    List<Currencypair> changedCurrencypairs = currencypairJpaRepository.findByGtNetLastModifiedTimeAfter(sinceTimestamp);
    for (Currencypair cp : changedCurrencypairs) {
      if (cp.isGtNetLastpriceSend() || cp.isGtNetHistoricalSend()) {
        items.add(ExchangeSyncItem.forCurrencypair(
            cp.getFromCurrency(), cp.getToCurrency(),
            cp.isGtNetLastpriceSend(), cp.isGtNetHistoricalSend()));
      }
    }

    return items;
  }

  /**
   * Gets ALL exchange items with send flags enabled, ignoring timestamps.
   * Used for full recreation mode where all eligible instruments are synchronized.
   *
   * @return list of all exchange sync items with at least one send flag enabled
   */
  public List<ExchangeSyncItem> getAllExchangeItems() {
    List<ExchangeSyncItem> items = new java.util.ArrayList<>();

    // All securities with GTNet send enabled
    List<Security> allSecurities = securityJpaRepository.findAllWithGtNetSendEnabled();
    for (Security security : allSecurities) {
      items.add(ExchangeSyncItem.forSecurity(
          security.getIsin(), security.getCurrency(),
          security.isGtNetLastpriceSend(), security.isGtNetHistoricalSend()));
    }

    // All currency pairs with GTNet send enabled
    List<Currencypair> allCurrencypairs = currencypairJpaRepository.findAllWithGtNetSendEnabled();
    for (Currencypair cp : allCurrencypairs) {
      items.add(ExchangeSyncItem.forCurrencypair(
          cp.getFromCurrency(), cp.getToCurrency(),
          cp.isGtNetLastpriceSend(), cp.isGtNetHistoricalSend()));
    }

    return items;
  }

  /**
   * Recreates ALL GTNetSupplierDetail entries for a peer.
   * Deletes all existing entries first, then creates new ones from response items.
   * Used for full recreation mode to ensure complete synchronization.
   *
   * @param supplier the remote GTNet server that sent the items
   * @param items the list of exchange sync items from the remote server
   */
  @Transactional
  public void recreateSupplierDetails(GTNet supplier, List<ExchangeSyncItem> items) {
    GTNetConfig config = supplier.getGtNetConfig();
    if (config == null) {
      log.warn("No config found for supplier {}", supplier.getDomainRemoteName());
      return;
    }

    // Delete all existing supplier details for this peer
    gtNetSupplierDetailJpaRepository.deleteByIdGtNet(config.getIdGtNet());
    log.debug("Deleted existing supplier details for {}", supplier.getDomainRemoteName());

    int created = 0;

    // Create new entries from response items
    if (items != null) {
      for (ExchangeSyncItem item : items) {
        try {
          Securitycurrency<?> sc = findSecuritycurrency(item);
          if (sc == null) {
            log.debug("Instrument not found locally: {}", item.getKey());
            continue;
          }

          // Create entry for lastprice if enabled
          if (item.lastpriceSend) {
            createSupplierDetail(config, sc, GTNetExchangeKindType.LAST_PRICE);
            created++;
          }

          // Create entry for historical if enabled
          if (item.historicalSend) {
            createSupplierDetail(config, sc, GTNetExchangeKindType.HISTORICAL_PRICES);
            created++;
          }
        } catch (Exception e) {
          log.warn("Failed to create supplier detail for {}: {}", item.getKey(), e.getMessage());
        }
      }
    }

    // Update supplier's last update timestamp
    config.setSupplierLastUpdate(java.time.LocalDateTime.now());
    gtNetConfigJpaRepository.save(config);

    log.debug("Recreated supplier details for {}: {} entries created", supplier.getDomainRemoteName(), created);
  }

  /**
   * Creates a new GTNetSupplierDetail entry.
   *
   * @param config the GTNet config for the supplier
   * @param sc the security or currency pair
   * @param entityKind the entity kind (LAST_PRICE or HISTORICAL_PRICES)
   */
  private void createSupplierDetail(GTNetConfig config, Securitycurrency<?> sc, GTNetExchangeKindType entityKind) {
    GTNetSupplierDetail detail = new GTNetSupplierDetail();
    detail.setGtNetConfig(config);
    detail.setSecuritycurrency(sc);
    detail.setEntityKind(entityKind);
    gtNetSupplierDetailJpaRepository.save(detail);
  }

  /**
   * Updates GTNetSupplierDetail entries based on received sync items from a remote peer.
   *
   * For each item:
   * <ul>
   * <li>If lastpriceSend is true, create/update supplier detail for INTRADAY</li>
   * <li>If lastpriceSend is false, delete supplier detail for INTRADAY (if exists)</li>
   * <li>If historicalSend is true, create/update supplier detail for HISTORICAL</li>
   * <li>If historicalSend is false, delete supplier detail for HISTORICAL (if exists)</li>
   * </ul>
   *
   * @param supplier the remote GTNet server that sent the items
   * @param items the list of exchange sync items from the remote server
   */
  @Transactional
  public void updateSupplierDetails(GTNet supplier, List<ExchangeSyncItem> items) {
    if (items == null || items.isEmpty()) {
      return;
    }

    GTNetConfig config = supplier.getGtNetConfig();
    if (config == null) {
      log.warn("No config found for supplier {}", supplier.getDomainRemoteName());
      return;
    }

    int created = 0;
    int deleted = 0;

    for (ExchangeSyncItem item : items) {
      try {
        Securitycurrency<?> sc = findSecuritycurrency(item);
        if (sc == null) {
          log.debug("Instrument not found locally: {}", item.getKey());
          continue;
        }

        // Handle intraday entity kind
        if (item.lastpriceSend) {
          if (upsertSupplierDetail(config, sc, GTNetExchangeKindType.LAST_PRICE)) {
            created++;
          }
        } else {
          if (deleteSupplierDetail(config, sc, GTNetExchangeKindType.LAST_PRICE)) {
            deleted++;
          }
        }

        // Handle historical entity kind
        if (item.historicalSend) {
          if (upsertSupplierDetail(config, sc, GTNetExchangeKindType.HISTORICAL_PRICES)) {
            created++;
          }
        } else {
          if (deleteSupplierDetail(config, sc, GTNetExchangeKindType.HISTORICAL_PRICES)) {
            deleted++;
          }
        }
      } catch (Exception e) {
        log.warn("Failed to update supplier detail for {}: {}", item.getKey(), e.getMessage());
      }
    }

    // Update supplier's last update timestamp
    config.setSupplierLastUpdate(java.time.LocalDateTime.now());
    gtNetConfigJpaRepository.save(config);

    log.debug("Updated supplier details for {}: {} created/updated, {} deleted",
        supplier.getDomainRemoteName(), created, deleted);
  }

  /**
   * Finds a security or currency pair by the sync item's identifying fields.
   */
  private Securitycurrency<?> findSecuritycurrency(ExchangeSyncItem item) {
    if (item.isSecurity()) {
      return securityJpaRepository.findByIsinAndCurrency(item.isin, item.currency);
    } else if (item.isCurrencypair()) {
      return currencypairJpaRepository.findByFromCurrencyAndToCurrency(item.currency, item.toCurrency);
    }
    return null;
  }

  /**
   * Creates or updates a GTNetSupplierDetail entry.
   *
   * @return true if a new entry was created, false if updated or already exists
   */
  private boolean upsertSupplierDetail(GTNetConfig config, Securitycurrency<?> sc, GTNetExchangeKindType entityKind) {
    Optional<GTNetSupplierDetail> existing = findSupplierDetail(config, sc, entityKind);
    if (existing.isPresent()) {
      return false; // Already exists, no action needed
    }

    GTNetSupplierDetail detail = new GTNetSupplierDetail();
    detail.setGtNetConfig(config);
    detail.setSecuritycurrency(sc);
    detail.setEntityKind(entityKind);
    gtNetSupplierDetailJpaRepository.save(detail);
    return true;
  }

  /**
   * Deletes a GTNetSupplierDetail entry if it exists.
   *
   * @return true if an entry was deleted
   */
  private boolean deleteSupplierDetail(GTNetConfig config, Securitycurrency<?> sc, GTNetExchangeKindType entityKind) {
    Optional<GTNetSupplierDetail> existing = findSupplierDetail(config, sc, entityKind);
    if (existing.isPresent()) {
      gtNetSupplierDetailJpaRepository.delete(existing.get());
      return true;
    }
    return false;
  }

  /**
   * Finds a supplier detail by config, securitycurrency, and entity kind.
   */
  private Optional<GTNetSupplierDetail> findSupplierDetail(GTNetConfig config, Securitycurrency<?> sc,
      GTNetExchangeKindType entityKind) {
    // Use a native query or iterate - for now, we fetch all and filter
    // This could be optimized with a proper repository method
    return gtNetSupplierDetailJpaRepository.findAll().stream()
        .filter(d -> d.getGtNetConfig() != null
            && d.getGtNetConfig().getIdGtNet().equals(config.getIdGtNet())
            && d.getSecuritycurrency() != null
            && d.getSecuritycurrency().getIdSecuritycurrency().equals(sc.getIdSecuritycurrency())
            && d.getEntityKind() == entityKind)
        .findFirst();
  }
}
