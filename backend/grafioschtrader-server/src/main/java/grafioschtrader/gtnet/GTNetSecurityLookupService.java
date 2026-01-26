package grafioschtrader.gtnet;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.m2m.model.GTNetPublicDTO;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.gtnet.model.ConnectorHint;
import grafiosch.gtnet.model.ConnectorHint.ConnectorCapability;
import grafiosch.m2m.GTNetMessageHelper;
import grafiosch.m2m.client.BaseDataClient;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.gtnet.model.SecurityGtnetLookupDTO;
import grafioschtrader.gtnet.model.SecurityGtnetLookupRequest;
import grafioschtrader.gtnet.model.SecurityGtnetLookupResponse;
import grafioschtrader.gtnet.model.msg.SecurityLookupMsg;
import grafioschtrader.gtnet.model.msg.SecurityLookupResponseMsg;

/**
 * Service for looking up security metadata from GTNet peers.
 * Queries remote GTNet instances that have SECURITY_METADATA exchange enabled
 * and aggregates results from all responding peers.
 */
@Service
public class GTNetSecurityLookupService {

  private static final Logger log = LoggerFactory.getLogger(GTNetSecurityLookupService.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private BaseDataClient baseDataClient;
  

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private List<IFeedConnector> feedConnectors;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Looks up security metadata by querying GTNet peers that support SECURITY_METADATA exchange.
   * Sends requests to all available peers and aggregates their responses.
   *
   * @param request the search criteria (ISIN, currency, and/or ticker symbol)
   * @return response containing matching securities from all responding peers and query statistics
   */
  public SecurityGtnetLookupResponse lookupSecurity(SecurityGtnetLookupRequest request) {
    List<SecurityGtnetLookupDTO> results = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    // Validate request
    if (!request.isValid()) {
      SecurityGtnetLookupResponse response = new SecurityGtnetLookupResponse(results, 0, 0);
      response.addError("Invalid request: (ISIN or ticker) and currency are required");
      return response;
    }

    // Check if GTNet is enabled
    if (!globalparametersJpaRepository.isGTNetEnabled()) {
      SecurityGtnetLookupResponse response = new SecurityGtnetLookupResponse(results, 0, 0);
      response.addError("GTNet is not enabled");
      return response;
    }

    // Get local GTNet entry for source identification
    Integer myGTNetId;
    GTNet myGTNet;
    try {
      myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository);
      myGTNet = gtNetJpaRepository.findById(myGTNetId)
          .orElseThrow(() -> new IllegalStateException("Local GTNet entry not found: " + myGTNetId));
    } catch (Exception e) {
      log.error("Failed to get local GTNet entry", e);
      SecurityGtnetLookupResponse response = new SecurityGtnetLookupResponse(results, 0, 0);
      response.addError("Local GTNet configuration error: " + e.getMessage());
      return response;
    }

    // Find peers that accept SECURITY_METADATA requests and are online
    List<GTNet> suppliers = gtNetJpaRepository.findSecurityMetadataSuppliers();

    // Exclude own entry to prevent self-communication
    suppliers = suppliers.stream()
        .filter(supplier -> !supplier.getIdGtNet().equals(myGTNetId))
        .toList();

    if (suppliers.isEmpty()) {
      SecurityGtnetLookupResponse response = new SecurityGtnetLookupResponse(results, 0, 0);
      response.addError("No GTNet peers available for security metadata lookup");
      return response;
    }

    int peersQueried = 0;
    int peersResponded = 0;

    // Build request payload
    SecurityLookupMsg requestPayload = new SecurityLookupMsg(
        request.getIsin(), request.getCurrency(), request.getTickerSymbol());

    // Query each peer
    for (GTNet supplier : suppliers) {
      GTNetConfig config = supplier.getGtNetConfig();
      if (config == null || !config.isAuthorizedRemoteEntry()) {
        log.debug("Skipping unauthorized server: {}", supplier.getDomainRemoteName());
        continue;
      }

      peersQueried++;

      try {
        // Build MessageEnvelope
        MessageEnvelope requestEnvelope = new MessageEnvelope();
        requestEnvelope.sourceDomain = myGTNet.getDomainRemoteName();
        requestEnvelope.sourceGtNet = new GTNetPublicDTO(myGTNet);
        requestEnvelope.serverBusy = myGTNet.isServerBusy();
        requestEnvelope.messageCode = GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_SEL_C.getValue();
        requestEnvelope.timestamp = new Date();
        requestEnvelope.payload = objectMapper.valueToTree(requestPayload);

        log.debug("Sending security lookup request to {}", supplier.getDomainRemoteName());

        // Send request
        SendResult result = baseDataClient.sendToMsgWithStatus(
            config.getTokenRemote(),
            supplier.getDomainRemoteName(),
            requestEnvelope);

        if (result.isFailed()) {
          if (result.httpError()) {
            log.warn("GTNet server {} returned HTTP error {}", supplier.getDomainRemoteName(), result.httpStatusCode());
          } else {
            log.warn("GTNet server {} is unreachable", supplier.getDomainRemoteName());
          }
          continue;
        }

        MessageEnvelope response = result.response();
        if (response == null) {
          log.debug("No response from {}", supplier.getDomainRemoteName());
          continue;
        }

        peersResponded++;

        // Check response message code
        GTNetMessageCode responseCode = GTNetMessageCodeType.getMessageCodeByValue(response.messageCode);
        if (responseCode == GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_NOT_FOUND_S) {
          log.debug("No matching securities found on {}", supplier.getDomainRemoteName());
          continue;
        }

        if (responseCode != GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_RESPONSE_S) {
          log.warn("Unexpected response code {} from {}", response.messageCode, supplier.getDomainRemoteName());
          continue;
        }

        // Parse response payload
        if (response.payload == null) {
          log.debug("Empty payload from {}", supplier.getDomainRemoteName());
          continue;
        }

        SecurityLookupResponseMsg responsePayload = objectMapper.treeToValue(
            response.payload, SecurityLookupResponseMsg.class);

        if (responsePayload == null || responsePayload.isEmpty()) {
          log.debug("No securities in response from {}", supplier.getDomainRemoteName());
          continue;
        }

        // Add results with source domain tracking and connector matching
        for (SecurityGtnetLookupDTO dto : responsePayload.securities) {
          dto.setSourceDomain(supplier.getDomainRemoteName());
          matchConnectors(dto);
          results.add(dto);
        }

        log.info("Received {} securities from {}", responsePayload.securities.size(), supplier.getDomainRemoteName());

      } catch (Exception e) {
        log.error("Failed to query GTNet server {}", supplier.getDomainRemoteName(), e);
        errors.add("Error querying " + supplier.getDomainRemoteName() + ": " + e.getMessage());
      }
    }

    SecurityGtnetLookupResponse response = new SecurityGtnetLookupResponse(results, peersQueried, peersResponded);
    errors.forEach(response::addError);

    log.info("Security lookup complete: {} peers queried, {} responded, {} results",
        peersQueried, peersResponded, results.size());

    return response;
  }

  /**
   * Checks if there are accessible GTNet peers that support SECURITY_METADATA exchange.
   * A peer is considered accessible if:
   * <ul>
   *   <li>It has a SECURITY_METADATA entity with acceptRequest > 0</li>
   *   <li>The server is online (serverOnline = SOS_ONLINE = 1)</li>
   * </ul>
   *
   * @return true if at least one accessible peer supports security metadata exchange
   */
  public boolean hasAccessibleSecurityMetadataPeers() {
    return gtNetJpaRepository.countBySecurityMetadataAcceptingAndOnline() > 0;
  }

  /**
   * Matches connector hints from a peer against local connectors.
   * For each capability (HISTORY, INTRADAY, DIVIDEND, SPLIT), finds the first matching local connector
   * and sets the matched connector ID and URL extension on the DTO.
   * Also calculates a match score based on how many connectors were matched.
   *
   * @param dto the security DTO to process
   */
  private void matchConnectors(SecurityGtnetLookupDTO dto) {
    List<ConnectorHint> hints = dto.getConnectorHints();
    if (hints == null || hints.isEmpty()) {
      dto.setConnectorMatchScore(0);
      return;
    }

    int matchScore = 0;

    for (ConnectorHint hint : hints) {
      String localConnectorId = findLocalConnector(hint.getConnectorFamily());
      if (localConnectorId == null) {
        continue;
      }

      // Match each capability
      if (hint.getCapabilities() != null) {
        for (ConnectorCapability capability : hint.getCapabilities()) {
          switch (capability) {
            case HISTORY:
              if (dto.getMatchedHistoryConnector() == null) {
                dto.setMatchedHistoryConnector(localConnectorId);
                dto.setMatchedHistoryUrlExtension(hint.getUrlExtensionPattern());
                matchScore++;
              }
              break;
            case INTRADAY:
              if (dto.getMatchedIntraConnector() == null) {
                dto.setMatchedIntraConnector(localConnectorId);
                dto.setMatchedIntraUrlExtension(hint.getUrlExtensionPattern());
                matchScore++;
              }
              break;
            case DIVIDEND:
              if (dto.getMatchedDividendConnector() == null) {
                dto.setMatchedDividendConnector(localConnectorId);
                dto.setMatchedDividendUrlExtension(hint.getUrlExtensionPattern());
                matchScore++;
              }
              break;
            case SPLIT:
              if (dto.getMatchedSplitConnector() == null) {
                dto.setMatchedSplitConnector(localConnectorId);
                dto.setMatchedSplitUrlExtension(hint.getUrlExtensionPattern());
                matchScore++;
              }
              break;
          }
        }
      }
    }

    dto.setConnectorMatchScore(matchScore);
    log.debug("Connector matching for {}: score={}, history={}, intraday={}, dividend={}, split={}",
        dto.getIsin(), matchScore, dto.getMatchedHistoryConnector(), dto.getMatchedIntraConnector(),
        dto.getMatchedDividendConnector(), dto.getMatchedSplitConnector());
  }

  /**
   * Finds a local connector matching the given family name.
   *
   * @param connectorFamily the connector family (e.g., "six", "yahoo")
   * @return the full connector ID if found locally, null otherwise
   */
  private String findLocalConnector(String connectorFamily) {
    if (connectorFamily == null || connectorFamily.isBlank()) {
      return null;
    }

    // Build the expected connector ID
    String expectedId = BaseFeedConnector.ID_PREFIX + connectorFamily;

    // Search through available connectors
    for (IFeedConnector connector : feedConnectors) {
      if (connector.getID().equals(expectedId)) {
        return expectedId;
      }
    }

    return null;
  }
}
