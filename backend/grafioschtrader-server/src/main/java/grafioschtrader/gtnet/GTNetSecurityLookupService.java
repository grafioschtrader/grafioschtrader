package grafioschtrader.gtnet;

import java.util.ArrayList;
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
import grafiosch.entities.MultilanguageString;
import grafiosch.entities.GTNetConfig;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.m2m.model.GTNetPublicDTO;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.m2m.GTNetMessageHelper;
import grafiosch.m2m.client.BaseDataClient;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.gtnet.model.ConnectorHint;
import grafioschtrader.gtnet.model.ConnectorHint.ConnectorCapability;
import grafioschtrader.gtnet.model.SecurityGtnetLookupDTO;
import grafioschtrader.gtnet.model.SecurityGtnetLookupRequest;
import grafioschtrader.gtnet.model.SecurityGtnetLookupResponse;
import grafioschtrader.gtnet.model.SubCategoryDetector;
import grafioschtrader.gtnet.model.SubCategoryScheme;
import grafioschtrader.entities.GTNetSecurityImpPos;
import grafioschtrader.repository.AssetclassJpaRepository;
import grafioschtrader.gtnet.model.msg.SecurityBatchLookupMsg;
import grafioschtrader.gtnet.model.msg.SecurityBatchLookupResponseMsg;
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

  @Autowired
  private AssetclassJpaRepository assetclassJpaRepository;

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

        // Add results with source domain tracking, connector and asset class matching
        for (SecurityGtnetLookupDTO dto : responsePayload.securities) {
          dto.setSourceDomain(supplier.getDomainRemoteName());
          matchConnectors(dto);
          matchAssetClass(dto);
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
   * Performs batch lookup for multiple securities across all GTNet peers.
   * Sends a single batch request to each peer containing all positions, reducing network round-trips.
   * Results are aggregated and grouped by position ID for easy linking.
   *
   * @param positions list of import positions to look up (only those with valid ISIN or ticker)
   * @return map from position ID to list of matching DTOs from all peers
   */
  public Map<Integer, List<SecurityGtnetLookupDTO>> lookupSecuritiesBatch(List<GTNetSecurityImpPos> positions) {
    Map<Integer, List<SecurityGtnetLookupDTO>> resultsByPosition = new HashMap<>();

    if (positions == null || positions.isEmpty()) {
      return resultsByPosition;
    }

    // Check if GTNet is enabled
    if (!globalparametersJpaRepository.isGTNetEnabled()) {
      log.warn("GTNet is not enabled, batch lookup skipped");
      return resultsByPosition;
    }

    // Get local GTNet entry for source identification
    Integer myGTNetId;
    GTNet myGTNet;
    try {
      myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository);
      myGTNet = gtNetJpaRepository.findById(myGTNetId)
          .orElseThrow(() -> new IllegalStateException("Local GTNet entry not found: " + myGTNetId));
    } catch (Exception e) {
      log.error("Failed to get local GTNet entry for batch lookup", e);
      return resultsByPosition;
    }

    // Find peers that accept SECURITY_METADATA requests and are online
    List<GTNet> suppliers = gtNetJpaRepository.findSecurityMetadataSuppliers();

    // Exclude own entry to prevent self-communication
    suppliers = suppliers.stream()
        .filter(supplier -> !supplier.getIdGtNet().equals(myGTNetId))
        .toList();

    if (suppliers.isEmpty()) {
      log.warn("No GTNet peers available for batch security lookup");
      return resultsByPosition;
    }

    // Build batch request payload with mapping from query index to position ID
    SecurityBatchLookupMsg batchRequest = new SecurityBatchLookupMsg();
    Map<Integer, Integer> queryIndexToPositionId = new HashMap<>();

    int queryIndex = 0;
    for (GTNetSecurityImpPos pos : positions) {
      // Only include positions with valid identifiers
      boolean hasIsin = pos.getIsin() != null && !pos.getIsin().isBlank();
      boolean hasTicker = pos.getTickerSymbol() != null && !pos.getTickerSymbol().isBlank();

      if (hasIsin || hasTicker) {
        SecurityLookupMsg query = new SecurityLookupMsg(pos.getIsin(), pos.getCurrency(), pos.getTickerSymbol());
        batchRequest.addQuery(query);
        queryIndexToPositionId.put(queryIndex, pos.getIdGtNetSecurityImpPos());
        queryIndex++;
      }
    }

    if (batchRequest.isEmpty()) {
      log.debug("No valid positions for batch lookup");
      return resultsByPosition;
    }

    log.info("Starting batch lookup for {} positions across {} peers", batchRequest.size(), suppliers.size());

    int peersQueried = 0;
    int peersResponded = 0;

    // Query each peer with batch request
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
        requestEnvelope.messageCode = GTNetMessageCodeType.GT_NET_SECURITY_BATCH_LOOKUP_SEL_C.getValue();
        requestEnvelope.timestamp = new Date();
        requestEnvelope.payload = objectMapper.valueToTree(batchRequest);

        log.debug("Sending batch lookup request ({} queries) to {}", batchRequest.size(), supplier.getDomainRemoteName());

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
        if (responseCode != GTNetMessageCodeType.GT_NET_SECURITY_BATCH_LOOKUP_RESPONSE_S) {
          log.warn("Unexpected response code {} from {}", response.messageCode, supplier.getDomainRemoteName());
          continue;
        }

        // Parse response payload
        if (response.payload == null) {
          log.debug("Empty payload from {}", supplier.getDomainRemoteName());
          continue;
        }

        SecurityBatchLookupResponseMsg responsePayload = objectMapper.treeToValue(
            response.payload, SecurityBatchLookupResponseMsg.class);

        if (responsePayload == null || responsePayload.isEmpty()) {
          log.debug("No results in batch response from {}", supplier.getDomainRemoteName());
          continue;
        }

        // Process results and map back to position IDs
        int totalResultsFromPeer = 0;
        for (Map.Entry<Integer, List<SecurityGtnetLookupDTO>> entry : responsePayload.getResults().entrySet()) {
          Integer qIndex = entry.getKey();
          Integer positionId = queryIndexToPositionId.get(qIndex);

          if (positionId == null) {
            log.warn("Received results for unknown query index {} from {}", qIndex, supplier.getDomainRemoteName());
            continue;
          }

          List<SecurityGtnetLookupDTO> dtoList = entry.getValue();
          if (dtoList != null && !dtoList.isEmpty()) {
            // Process each DTO: set source domain, match connectors and asset classes
            for (SecurityGtnetLookupDTO dto : dtoList) {
              dto.setSourceDomain(supplier.getDomainRemoteName());
              matchConnectors(dto);
              matchAssetClass(dto);
            }

            // Add to results for this position
            resultsByPosition
                .computeIfAbsent(positionId, k -> new ArrayList<>())
                .addAll(dtoList);
            totalResultsFromPeer += dtoList.size();
          }
        }

        log.info("Received {} securities from {} for batch lookup", totalResultsFromPeer, supplier.getDomainRemoteName());

      } catch (Exception e) {
        log.error("Failed to query GTNet server {} for batch lookup", supplier.getDomainRemoteName(), e);
      }
    }

    log.info("Batch lookup complete: {} peers queried, {} responded, {} positions with results",
        peersQueried, peersResponded, resultsByPosition.size());

    return resultsByPosition;
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

  /**
   * Finds a matching local asset class for the given DTO. Matching priority:
   * <ol>
   *   <li>Exact match: categoryType + specialInvestmentInstrument + subCategoryNLS (best similarity score)</li>
   *   <li>Scheme match: categoryType + specialInvestmentInstrument + same categorization scheme</li>
   *   <li>Partial match: categoryType + specialInvestmentInstrument only</li>
   * </ol>
   *
   * @param dto the security DTO to process
   */
  private void matchAssetClass(SecurityGtnetLookupDTO dto) {
    if (dto.getCategoryType() == null || dto.getSpecialInvestmentInstrument() == null) {
      return;
    }

    List<Assetclass> candidates = assetclassJpaRepository.findByCategoryTypeAndSpecialInvestmentInstrument(
        dto.getCategoryType().getValue(), dto.getSpecialInvestmentInstrument().getValue());

    if (candidates.isEmpty()) {
      log.debug("No local asset class found for categoryType={}, specialInvestmentInstrument={}",
          dto.getCategoryType(), dto.getSpecialInvestmentInstrument());
      return;
    }

    SubCategoryScheme dtoScheme = dto.getSubCategoryScheme();
    Assetclass schemeMatch = null;
    Assetclass bestMatch = null;
    double bestSimilarity = 0.0;

    // Find the BEST match with subCategoryNLS text (highest similarity score)
    if (dto.getSubCategoryNLS() != null && !dto.getSubCategoryNLS().isEmpty()) {
      for (Assetclass candidate : candidates) {
        double similarity = getSubCategorySimilarity(candidate, dto.getSubCategoryNLS());
        if (similarity >= SubCategoryDetector.SIMILARITY_THRESHOLD && similarity > bestSimilarity) {
          bestSimilarity = similarity;
          bestMatch = candidate;
        }
        // Track first candidate with matching scheme for fallback
        if (schemeMatch == null && dtoScheme != null && dtoScheme != SubCategoryScheme.UNKNOWN) {
          SubCategoryScheme candidateScheme = detectLocalScheme(candidate);
          if (candidateScheme == dtoScheme) {
            schemeMatch = candidate;
          }
        }
      }
    }

    // Use the best match if found
    if (bestMatch != null) {
      dto.setMatchedAssetClassId(bestMatch.getIdAssetClass());
      dto.setAssetClassMatchType("EXACT");
      log.debug("Best asset class match for {}: idAssetClass={}, similarity={}",
          dto.getIsin(), bestMatch.getIdAssetClass(), bestSimilarity);
      return;
    }

    // Fallback to scheme match (same categorization approach)
    if (schemeMatch != null) {
      dto.setMatchedAssetClassId(schemeMatch.getIdAssetClass());
      dto.setAssetClassMatchType("SCHEME_MATCH");
      log.debug("Scheme-based asset class match for {}: idAssetClass={}, scheme={}",
          dto.getIsin(), schemeMatch.getIdAssetClass(), dtoScheme);
      return;
    }

    // Final fallback to first match by categoryType + specialInvestmentInstrument
    dto.setMatchedAssetClassId(candidates.get(0).getIdAssetClass());
    dto.setAssetClassMatchType("PARTIAL");
    log.debug("Partial asset class match for {}: idAssetClass={}", dto.getIsin(), candidates.get(0).getIdAssetClass());
  }

  /**
   * Calculates the maximum similarity score between a local asset class subcategory and the DTO subcategory values.
   * Compares each language that exists in both and returns the highest similarity score found.
   *
   * @return the highest similarity score (0.0 to 1.0), or 0.0 if no comparison was possible
   */
  private double getSubCategorySimilarity(Assetclass assetclass, java.util.Map<String, String> dtoSubCategory) {
    MultilanguageString localSubCat = assetclass.getSubCategoryNLS();
    if (localSubCat == null || localSubCat.getMap() == null || localSubCat.getMap().isEmpty()) {
      return 0.0;
    }

    double maxSimilarity = 0.0;
    for (java.util.Map.Entry<String, String> entry : dtoSubCategory.entrySet()) {
      String localValue = localSubCat.getMap().get(entry.getKey());
      if (localValue != null) {
        double similarity = SubCategoryDetector.getSimilarity(localValue, entry.getValue());
        if (similarity > maxSimilarity) {
          maxSimilarity = similarity;
          log.debug("Similarity check: '{}' vs '{}' = {}", localValue, entry.getValue(), similarity);
        }
      }
    }
    return maxSimilarity;
  }

  /**
   * Detects the categorization scheme of a local asset class.
   */
  private SubCategoryScheme detectLocalScheme(Assetclass assetclass) {
    MultilanguageString subCat = assetclass.getSubCategoryNLS();
    if (subCat == null || subCat.getMap() == null || subCat.getMap().isEmpty()) {
      return SubCategoryScheme.UNKNOWN;
    }
    return SubCategoryDetector.detect(subCat.getMap());
  }
}
