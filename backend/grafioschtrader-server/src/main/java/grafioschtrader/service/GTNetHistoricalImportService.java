package grafioschtrader.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.gtnet.m2m.model.GTNetPublicDTO;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.m2m.GTNetMessageHelper;
import grafiosch.m2m.client.BaseDataClient;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.m2m.model.HistoryquoteRecordDTO;
import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageQueryMsg;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageQueryMsg.InstrumentIdentifier;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageResponseMsg;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageResponseMsg.InstrumentCoverageDTO;
import grafioschtrader.gtnet.model.msg.HistoryquoteExchangeMsg;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Service for importing historical price data from GTNet peers after security creation.
 *
 * This service orchestrates the historical data import workflow:
 * <ol>
 * <li>Query coverage metadata from all eligible GTNet peers (message code 85)</li>
 * <li>Select the best peer based on coverage length and success rate</li>
 * <li>Fetch historical data from the selected peer (message code 80)</li>
 * <li>Fall back to connector if GTNet peers don't have data</li>
 * </ol>
 *
 * @see grafioschtrader.task.exec.GTNetSecurityImportTask
 */
@Service
public class GTNetHistoricalImportService extends BaseGTNetExchangeService {

  private static final Logger log = LoggerFactory.getLogger(GTNetHistoricalImportService.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private BaseDataClient baseDataClient;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private List<IFeedConnector> feedConnectorbeans;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Queries all eligible GTNet peers for coverage metadata for the given securities.
   *
   * @param securities list of securities to query coverage for
   * @return map of peer to coverage response, only containing peers that responded successfully
   */
  public Map<GTNet, HistoryquoteCoverageResponseMsg> queryCoverageFromPeers(List<Security> securities) {
    if (!globalparametersJpaRepository.isGTNetEnabled() || securities == null || securities.isEmpty()) {
      return Collections.emptyMap();
    }

    // Build coverage query
    List<InstrumentIdentifier> securityIdentifiers = securities.stream()
        .filter(s -> s.getIsin() != null && s.getCurrency() != null)
        .map(s -> InstrumentIdentifier.forSecurity(s.getIsin(), s.getCurrency()))
        .toList();

    if (securityIdentifiers.isEmpty()) {
      return Collections.emptyMap();
    }

    HistoryquoteCoverageQueryMsg query = HistoryquoteCoverageQueryMsg.forQuery(securityIdentifiers, null);

    // Get eligible peers (PUSH_OPEN and OPEN)
    List<GTNet> peers = getEligiblePeers();
    if (peers.isEmpty()) {
      return Collections.emptyMap();
    }

    log.info("Querying {} GTNet peers for coverage of {} securities", peers.size(), securityIdentifiers.size());

    Map<GTNet, HistoryquoteCoverageResponseMsg> results = new HashMap<>();

    for (GTNet peer : peers) {
      try {
        HistoryquoteCoverageResponseMsg response = queryCoverageFromPeer(peer, query);
        if (response != null && !response.isEmpty()) {
          results.put(peer, response);
        }
      } catch (Exception e) {
        log.warn("Failed to query coverage from {}: {}", peer.getDomainRemoteName(), e.getMessage());
      }
    }

    log.info("Received coverage responses from {} peers", results.size());
    return results;
  }

  /**
   * Selects the best peer for fetching historical data based on coverage and success rate.
   *
   * Selection criteria:
   * <ol>
   * <li>Coverage length: Days from minDate to maxDate (longer = better)</li>
   * <li>Success rate: Based on past exchange logs (currently not weighted)</li>
   * <li>Priority: Based on consumerUsage configuration (lower = better)</li>
   * </ol>
   *
   * @param coverageByPeer map of peer to coverage response
   * @param isin           the ISIN to look up
   * @param currency       the currency to look up
   * @return the best peer selection, or null if no peer has coverage
   */
  public PeerSelection selectBestPeer(Map<GTNet, HistoryquoteCoverageResponseMsg> coverageByPeer, String isin,
      String currency) {
    if (coverageByPeer == null || coverageByPeer.isEmpty() || isin == null || currency == null) {
      return null;
    }

    String key = isin + ":" + currency;
    PeerSelection best = null;
    long bestCoverageDays = 0;

    for (Map.Entry<GTNet, HistoryquoteCoverageResponseMsg> entry : coverageByPeer.entrySet()) {
      GTNet peer = entry.getKey();
      Map<String, InstrumentCoverageDTO> coverageMap = entry.getValue().buildCoverageMap();
      InstrumentCoverageDTO coverage = coverageMap.get(key);

      if (coverage != null && coverage.isAvailable()) {
        long coverageDays = coverage.getCoverageDays();
        if (coverageDays > bestCoverageDays) {
          bestCoverageDays = coverageDays;
          best = new PeerSelection(peer, coverage.getMinDate(), coverage.getMaxDate(), coverageDays);
        }
      }
    }

    if (best != null) {
      log.debug("Selected peer {} for {}: {} days coverage from {} to {}", best.peer.getDomainRemoteName(), key,
          best.coverageDays, best.minDate, best.maxDate);
    }

    return best;
  }

  /**
   * Fetches historical price data from a specific GTNet peer.
   *
   * @param peer     the peer to fetch from
   * @param security the security to fetch data for
   * @param fromDate the start date (inclusive)
   * @param toDate   the end date (inclusive)
   * @return list of historyquotes, or empty list if fetch failed
   */
  public List<Historyquote> fetchFromPeer(GTNet peer, Security security, Date fromDate, Date toDate) {
    if (peer == null || security == null || security.getIsin() == null) {
      return Collections.emptyList();
    }

    GTNetConfig config = peer.getGtNetConfig();
    if (config == null || !config.isAuthorizedRemoteEntry()) {
      log.debug("Skipping unauthorized peer: {}", peer.getDomainRemoteName());
      return Collections.emptyList();
    }

    // Build request
    InstrumentHistoryquoteDTO requestDto = InstrumentHistoryquoteDTO.forSecurityRequest(security.getIsin(),
        security.getCurrency(), fromDate, toDate);
    HistoryquoteExchangeMsg request = HistoryquoteExchangeMsg.forRequest(List.of(requestDto), null);

    // Get local GTNet entry for source identification
    Integer myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository);
    GTNet myGTNet = gtNetJpaRepository.findById(myGTNetId)
        .orElseThrow(() -> new IllegalStateException("Local GTNet entry not found: " + myGTNetId));

    // Build envelope
    MessageEnvelope envelope = new MessageEnvelope();
    envelope.sourceDomain = myGTNet.getDomainRemoteName();
    envelope.sourceGtNet = new GTNetPublicDTO(myGTNet);
    envelope.serverBusy = myGTNet.isServerBusy();
    envelope.messageCode = GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C.getValue();
    envelope.timestamp = new Date();
    envelope.payload = objectMapper.valueToTree(request);

    log.debug("Fetching historyquotes from {} for {} ({}) from {} to {}", peer.getDomainRemoteName(), security.getIsin(),
        security.getCurrency(), fromDate, toDate);

    // Send request
    SendResult result = baseDataClient.sendToMsgWithStatus(config.getTokenRemote(), peer.getDomainRemoteName(),
        envelope);

    if (result.isFailed()) {
      log.warn("Failed to fetch historyquotes from {}: HTTP {}",
          peer.getDomainRemoteName(), result.httpStatusCode());
      return Collections.emptyList();
    }

    MessageEnvelope response = result.response();
    if (response == null || response.payload == null) {
      log.debug("No historyquote data received from {}", peer.getDomainRemoteName());
      return Collections.emptyList();
    }

    try {
      HistoryquoteExchangeMsg responsePayload = objectMapper.treeToValue(response.payload, HistoryquoteExchangeMsg.class);
      if (responsePayload == null || responsePayload.securities == null || responsePayload.securities.isEmpty()) {
        return Collections.emptyList();
      }

      // Convert to Historyquote entities
      List<Historyquote> historyquotes = new ArrayList<>();
      for (InstrumentHistoryquoteDTO dto : responsePayload.getSecuritiesWithData()) {
        if (dto.getRecords() != null) {
          for (HistoryquoteRecordDTO record : dto.getRecords()) {
            if (record.getDate() != null && record.getClose() != null) {
              Historyquote hq = new Historyquote();
              hq.setIdSecuritycurrency(security.getIdSecuritycurrency());
              hq.setDate(record.getDate());
              hq.setOpen(record.getOpen());
              hq.setHigh(record.getHigh());
              hq.setLow(record.getLow());
              hq.setClose(record.getClose());
              hq.setVolume(record.getVolume());
              historyquotes.add(hq);
            }
          }
        }
      }

      log.info("Received {} historyquotes from {} for {}", historyquotes.size(), peer.getDomainRemoteName(),
          security.getIsin());
      return historyquotes;

    } catch (Exception e) {
      log.error("Failed to parse historyquote response from {}: {}", peer.getDomainRemoteName(), e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * Falls back to using the configured connector to load historical data.
   *
   * @param security the security to load data for
   * @return true if data was successfully loaded via connector, false otherwise
   */
  public boolean fallbackToConnector(Security security) {
    if (security == null || security.getIdConnectorHistory() == null) {
      return false;
    }

    IFeedConnector connector = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
        security.getIdConnectorHistory(), IFeedConnector.FeedSupport.FS_HISTORY);

    if (connector == null) {
      log.debug("No history connector found for security {}", security.getIdSecuritycurrency());
      return false;
    }

    try {
      Date startDate = globalparametersService.getStartFeedDate();
      Date endDate = new Date();

      // Directly call the connector's method to get historical quotes
      List<Historyquote> quotes = connector.getEodSecurityHistory(security, startDate, endDate);

      if (quotes != null && !quotes.isEmpty()) {
        // Set idSecuritycurrency on each quote
        quotes.forEach(hq -> hq.setIdSecuritycurrency(security.getIdSecuritycurrency()));

        // Save quotes
        historyquoteJpaRepository.saveAll(quotes);
        log.info("Loaded {} historyquotes via connector for security {}", quotes.size(),
            security.getIdSecuritycurrency());
        return true;
      }
    } catch (Exception e) {
      log.warn("Connector fallback failed for security {}: {}", security.getIdSecuritycurrency(), e.getMessage());
    }

    return false;
  }

  /**
   * Queries coverage metadata from a single peer.
   */
  private HistoryquoteCoverageResponseMsg queryCoverageFromPeer(GTNet peer, HistoryquoteCoverageQueryMsg query) {
    GTNetConfig config = peer.getGtNetConfig();
    if (config == null || !config.isAuthorizedRemoteEntry()) {
      log.debug("Skipping unauthorized peer: {}", peer.getDomainRemoteName());
      return null;
    }

    Integer myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository);
    GTNet myGTNet = gtNetJpaRepository.findById(myGTNetId)
        .orElseThrow(() -> new IllegalStateException("Local GTNet entry not found: " + myGTNetId));

    MessageEnvelope envelope = new MessageEnvelope();
    envelope.sourceDomain = myGTNet.getDomainRemoteName();
    envelope.sourceGtNet = new GTNetPublicDTO(myGTNet);
    envelope.serverBusy = myGTNet.isServerBusy();
    envelope.messageCode = GTNetMessageCodeType.GT_NET_HISTORYQUOTE_COVERAGE_SEL_C.getValue();
    envelope.timestamp = new Date();
    envelope.payload = objectMapper.valueToTree(query);

    SendResult result = baseDataClient.sendToMsgWithStatus(config.getTokenRemote(), peer.getDomainRemoteName(),
        envelope);

    if (result.isFailed()) {
      log.debug("Coverage query to {} failed: HTTP {}", peer.getDomainRemoteName(), result.httpStatusCode());
      return null;
    }

    MessageEnvelope response = result.response();
    if (response == null || response.payload == null) {
      return null;
    }

    try {
      return objectMapper.treeToValue(response.payload, HistoryquoteCoverageResponseMsg.class);
    } catch (Exception e) {
      log.warn("Failed to parse coverage response from {}: {}", peer.getDomainRemoteName(), e.getMessage());
      return null;
    }
  }

  /**
   * Gets all eligible peers for historical data queries (PUSH_OPEN and OPEN).
   */
  private List<GTNet> getEligiblePeers() {
    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    List<GTNet> peers = new ArrayList<>();

    // Add PUSH_OPEN suppliers
    peers.addAll(gtNetJpaRepository.findHistoryquotePushOpenSuppliers());

    // Add OPEN suppliers
    peers.addAll(gtNetJpaRepository.findHistoryquoteOpenSuppliers());

    // Exclude own entry
    if (myEntryId != null) {
      peers.removeIf(p -> p.getIdGtNet().equals(myEntryId));
    }

    return peers;
  }

  /**
   * Result of peer selection for historical data fetch.
   */
  public static class PeerSelection {
    public final GTNet peer;
    public final Date minDate;
    public final Date maxDate;
    public final long coverageDays;

    public PeerSelection(GTNet peer, Date minDate, Date maxDate, long coverageDays) {
      this.peer = peer;
      this.minDate = minDate;
      this.maxDate = maxDate;
      this.coverageDays = coverageDays;
    }
  }
}
