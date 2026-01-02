package grafioschtrader.service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetConfig;
import grafioschtrader.entities.GTNetExchange;
import grafioschtrader.entities.GTNetSupplierDetail;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.PriceType;
import grafioschtrader.gtnet.m2m.model.GTNetPublicDTO;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.msg.ExchangeSyncMsg;
import grafioschtrader.gtnet.model.msg.ExchangeSyncMsg.ExchangeSyncItem;
import grafioschtrader.m2m.GTNetMessageHelper;
import grafioschtrader.m2m.client.BaseDataClient;
import grafioschtrader.m2m.client.BaseDataClient.SendResult;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GTNetConfigJpaRepository;
import grafioschtrader.repository.GTNetExchangeJpaRepository;
import grafioschtrader.repository.GTNetJpaRepository;
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
  private GTNetExchangeJpaRepository gtNetExchangeJpaRepository;

  @Autowired
  private GTNetSupplierDetailJpaRepository gtNetSupplierDetailJpaRepository;

  @Autowired
  private GTNetConfigJpaRepository gtNetConfigJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private BaseDataClient baseDataClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Synchronizes exchange configuration with a single peer.
   *
   * @param peer the remote GTNet server
   * @param sinceTimestamp only include changes after this timestamp
   * @return true if sync was successful
   */
  public boolean syncWithPeer(GTNet peer, Date sinceTimestamp) {
    GTNetConfig config = peer.getGtNetConfig();
    if (config == null) {
      log.debug("Skipping peer without config: {}", peer.getDomainRemoteName());
      return false;
    }

    // Build request with local changed items
    List<ExchangeSyncItem> changedItems = getChangedExchangeItems(sinceTimestamp);
    ExchangeSyncMsg requestPayload = ExchangeSyncMsg.forRequest(sinceTimestamp, changedItems);

    // Get local GTNet entry
    Integer myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersService);
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

    log.debug("Sending exchange sync to {} with {} items", peer.getDomainRemoteName(), changedItems.size());

    SendResult result = baseDataClient.sendToMsgWithStatus(
        config.getTokenRemote(), peer.getDomainRemoteName(), requestEnvelope);

    if (!result.serverReachable()) {
      log.warn("Peer {} is unreachable for exchange sync", peer.getDomainRemoteName());
      return false;
    }

    MessageEnvelope response = result.response();
    if (response == null || response.payload == null) {
      log.debug("No exchange sync data received from {}", peer.getDomainRemoteName());
      return true; // Successful but no data
    }

    // Process response - update supplier details
    try {
      ExchangeSyncMsg responsePayload = objectMapper.treeToValue(response.payload, ExchangeSyncMsg.class);
      updateSupplierDetails(peer, responsePayload.items);
      log.info("Exchange sync with {} complete: sent {}, received {}",
          peer.getDomainRemoteName(), changedItems.size(), responsePayload.getItemCount());
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
    return gtNetExchangeJpaRepository.findByLastModifiedTimeAfter(sinceTimestamp).stream()
        .filter(e -> e.isLastpriceSend() || e.isHistoricalSend())
        .map(this::toExchangeSyncItem)
        .filter(item -> item != null)
        .collect(Collectors.toList());
  }

  /**
   * Converts a GTNetExchange entity to an ExchangeSyncItem for the sync payload.
   */
  private ExchangeSyncItem toExchangeSyncItem(GTNetExchange exchange) {
    Securitycurrency<?> sc = exchange.getSecuritycurrency();
    if (sc instanceof Security security) {
      return ExchangeSyncItem.forSecurity(
          security.getIsin(), security.getCurrency(),
          exchange.isLastpriceSend(), exchange.isHistoricalSend());
    } else if (sc instanceof Currencypair cp) {
      return ExchangeSyncItem.forCurrencypair(
          cp.getFromCurrency(), cp.getToCurrency(),
          exchange.isLastpriceSend(), exchange.isHistoricalSend());
    }
    return null;
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

        // Handle intraday price type
        if (item.lastpriceSend) {
          if (upsertSupplierDetail(config, sc, PriceType.LASTPRICE)) {
            created++;
          }
        } else {
          if (deleteSupplierDetail(config, sc, PriceType.LASTPRICE)) {
            deleted++;
          }
        }

        // Handle historical price type
        if (item.historicalSend) {
          if (upsertSupplierDetail(config, sc, PriceType.HISTORICAL)) {
            created++;
          }
        } else {
          if (deleteSupplierDetail(config, sc, PriceType.HISTORICAL)) {
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
  private boolean upsertSupplierDetail(GTNetConfig config, Securitycurrency<?> sc, PriceType priceType) {
    Optional<GTNetSupplierDetail> existing = findSupplierDetail(config, sc, priceType);
    if (existing.isPresent()) {
      return false; // Already exists, no action needed
    }

    GTNetSupplierDetail detail = new GTNetSupplierDetail();
    detail.setGtNetConfig(config);
    detail.setSecuritycurrency(sc);
    detail.setPriceType(priceType);
    gtNetSupplierDetailJpaRepository.save(detail);
    return true;
  }

  /**
   * Deletes a GTNetSupplierDetail entry if it exists.
   *
   * @return true if an entry was deleted
   */
  private boolean deleteSupplierDetail(GTNetConfig config, Securitycurrency<?> sc, PriceType priceType) {
    Optional<GTNetSupplierDetail> existing = findSupplierDetail(config, sc, priceType);
    if (existing.isPresent()) {
      gtNetSupplierDetailJpaRepository.delete(existing.get());
      return true;
    }
    return false;
  }

  /**
   * Finds a supplier detail by config, securitycurrency, and price type.
   */
  private Optional<GTNetSupplierDetail> findSupplierDetail(GTNetConfig config, Securitycurrency<?> sc,
      PriceType priceType) {
    // Use a native query or iterate - for now, we fetch all and filter
    // This could be optimized with a proper repository method
    return gtNetSupplierDetailJpaRepository.findAll().stream()
        .filter(d -> d.getGtNetConfig() != null
            && d.getGtNetConfig().getIdGtNet().equals(config.getIdGtNet())
            && d.getSecuritycurrency() != null
            && d.getSecuritycurrency().getIdSecuritycurrency().equals(sc.getIdSecuritycurrency())
            && d.getPriceType() == priceType)
        .findFirst();
  }
}
