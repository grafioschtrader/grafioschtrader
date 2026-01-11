package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.common.DataHelper;
import grafiosch.entities.TaskDataChange;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.RepositoryHelper;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetConfig;
import grafioschtrader.entities.GTNetConfigEntity;
import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import grafioschtrader.entities.GTNetMessageAnswer;
import grafioschtrader.gtnet.AcceptRequestTypes;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetModelHelper;
import grafioschtrader.gtnet.GTNetModelHelper.GTNetMsgRequest;
import grafioschtrader.gtnet.GTNetServerOnlineStatusTypes;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.SendReceivedType;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.GTNetMessageHandler;
import grafioschtrader.gtnet.handler.GTNetMessageHandlerRegistry;
import grafioschtrader.gtnet.handler.HandlerResult;
import grafioschtrader.gtnet.m2m.model.GTNetEntityPublicDTO;
import grafioschtrader.gtnet.m2m.model.GTNetPublicDTO;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.GTNetWithMessages;
import grafioschtrader.gtnet.model.MsgRequest;
import grafioschtrader.gtnet.model.msg.FirstHandshakeMsg;
import grafioschtrader.m2m.GTNetMessageHelper;
import grafioschtrader.m2m.client.BaseDataClient;
import grafioschtrader.m2m.client.BaseDataClient.SendResult;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TaskTypeExtended;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

public class GTNetJpaRepositoryImpl extends BaseRepositoryImpl<GTNet> implements GTNetJpaRepositoryCustom {

  private static final Logger log = LoggerFactory.getLogger(GTNetJpaRepositoryImpl.class);

  /** Message codes that require a response (_RR_ codes) */
  private static final List<Byte> RR_MESSAGE_CODES = List.of(
      GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_SEL_RR_S.getValue(),
      GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_SEL_RR_C.getValue(),
      GTNetMessageCodeType.GT_NET_DATA_REQUEST_SEL_RR_C.getValue());

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetConfigJpaRepository gtNetConfigJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private GTNetMessageAnswerJpaRepository gtNetMessageAnswerJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private Validator validator;

  @Autowired
  private BaseDataClient baseDataClient;

  @Autowired
  @Lazy
  private GTNetMessageHandlerRegistry handlerRegistry;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Override
  @Transactional
  public GTNet saveOnlyAttributes(GTNet gtNet) throws Exception {
    GTNet existingEntity = gtNetJpaRepository.findById(gtNet.getIdGtNet()).orElse(null);
    return saveOnlyAttributes(gtNet, existingEntity, getUpdatePropertyLevels(existingEntity));
  }

  @Override
  @Transactional
  public GTNetWithMessages getAllGTNetsWithMessages() {
    List<GTNetMessage> gtNetMessages = gtNetMessageJpaRepository.findAllByOrderByIdGtNetAscTimestampDesc();

    // Fetch all unanswered requests and group by idGtNet
    Map<Integer, List<Integer>> outgoingPendingReplies = groupPendingByGtNet(
        gtNetMessageJpaRepository.findUnansweredRequests(SendReceivedType.SEND.getValue(), RR_MESSAGE_CODES));
    Map<Integer, List<Integer>> incomingPendingReplies = groupPendingByGtNet(
        gtNetMessageJpaRepository.findUnansweredRequests(SendReceivedType.RECEIVED.getValue(), RR_MESSAGE_CODES));

    // Group messages by idGtNet
    Map<Integer, List<GTNetMessage>> gtNetMessageMap = gtNetMessages.stream()
        .collect(Collectors.groupingBy(GTNetMessage::getIdGtNet));

    return new GTNetWithMessages(gtNetJpaRepository.findAll(), gtNetMessageMap,
        outgoingPendingReplies, incomingPendingReplies, globalparametersService.getGTNetMyEntryID());
  }

  /**
   * Groups the query result (id_gt_net, id_gt_net_message) into a Map by idGtNet.
   */
  private Map<Integer, List<Integer>> groupPendingByGtNet(List<Object[]> queryResult) {
    return queryResult.stream().collect(Collectors.groupingBy(
        row -> ((Number) row[0]).intValue(),
        Collectors.mapping(row -> ((Number) row[1]).intValue(), Collectors.toList())));
  }

  @Override
  public GTNet saveOnlyAttributes(final GTNet gtNet, final GTNet existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {

    Integer myInstanceEntry = globalparametersService.getGTNetMyEntryID();
    
    Optional<GTNet> myGTNetEntryOpt = myInstanceEntry != null? gtNetJpaRepository.findById(myInstanceEntry): Optional.empty(); 
    // Validate remote URL is reachable

    if (gtNetJpaRepository.count() == 0) {
      baseDataClient.getActuatorInfo(gtNet.getDomainRemoteName());
    }

    // Track serverBusy change before saving
    boolean serverBusyChanged = existingEntity != null && existingEntity.isServerBusy() != gtNet.isServerBusy();
    boolean newServerBusyValue = gtNet.isServerBusy();

    // Track settings changes (only for myGTNet)
    boolean isMyEntry = myInstanceEntry != null && myInstanceEntry.equals(gtNet.getIdGtNet());
    boolean settingsChanged = isMyEntry && existingEntity != null && hasSettingsChanged(existingEntity, gtNet);

    GTNet gtNetNew = RepositoryHelper.saveOnlyAttributes(gtNetJpaRepository, gtNet, existingEntity,
        updatePropertyLevelClasses);
    if (isDomainNameThisMachine(gtNet.getDomainRemoteName())) {
      globalparametersService.saveGTNetMyEntryID(gtNetNew.getIdGtNet());
      // Local server is always online - set status if not already set
      if (gtNetNew.getServerOnline() != GTNetServerOnlineStatusTypes.SOS_ONLINE) {
        gtNetNew.setServerOnline(GTNetServerOnlineStatusTypes.SOS_ONLINE);
        gtNetNew = gtNetJpaRepository.save(gtNetNew);
      }
    }

    // If serverBusy changed, notify all connected peers
    if (serverBusyChanged) {
      sendServerBusyNotification(gtNetNew, newServerBusyValue);
    }

    // If settings changed, schedule background task to notify all connected peers
    if (settingsChanged) {
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.GTNET_SETTINGS_BROADCAST,
          TaskDataExecPriority.PRIO_NORMAL));
      log.info("Scheduled GTNet settings broadcast task for immediate execution");
    }

    return gtNetNew;
  }

  /**
   * Sends a serverBusy status notification to all peers with configured exchange.
   *
   * @param myGTNet the local GTNet entry
   * @param isBusy  true if server is now busy, false if released
   */
  private void sendServerBusyNotification(GTNet myGTNet, boolean isBusy) {
    GTNetMessageCodeType messageCode = isBusy ? GTNetMessageCodeType.GT_NET_BUSY_ALL_C
        : GTNetMessageCodeType.GT_NET_RELEASED_BUSY_ALL_C;

    MsgRequest msgRequest = new MsgRequest();
    msgRequest.messageCode = messageCode;
    GTNetModelHelper.GTNetMsgRequest gtNetMsgRequest = GTNetModelHelper.getMsgClassByMessageCode(messageCode);
    List<GTNet> targets = gtNetJpaRepository.findWithConfiguredExchange();

    if (!targets.isEmpty()) {
      sendAndSaveMsg(myGTNet, targets, gtNetMsgRequest, msgRequest);
    }
    // Save broadcast to own entry for visibility
    saveBroadcastToOwnEntry(myGTNet, msgRequest);
  }

  /**
   * Checks if GTNet or GTNetEntity settings have changed that should trigger a broadcast.
   * Compares dailyRequestLimit and entity-level settings (acceptRequest, serverState, maxLimit).
   */
  private boolean hasSettingsChanged(GTNet existing, GTNet updated) {
    // Check dailyRequestLimit
    if (!java.util.Objects.equals(existing.getDailyRequestLimit(), updated.getDailyRequestLimit())) {
      return true;
    }

    // Check GTNetEntity changes
    for (GTNetEntity updatedEntity : updated.getGtNetEntities()) {
      GTNetEntity existingEntity = findMatchingEntity(existing, updatedEntity.getEntityKind());
      if (existingEntity == null) {
        return true; // New entity
      }
      if (existingEntity.getAcceptRequest() != updatedEntity.getAcceptRequest()
          || existingEntity.getServerState() != updatedEntity.getServerState()
          || !java.util.Objects.equals(existingEntity.getMaxLimit(), updatedEntity.getMaxLimit())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Finds a GTNetEntity by entity kind within a GTNet.
   */
  private GTNetEntity findMatchingEntity(GTNet gtNet, GTNetExchangeKindType kind) {
    return gtNet.getGtNetEntities().stream()
        .filter(e -> e.getEntityKind() == kind)
        .findFirst()
        .orElse(null);
  }

  /**
   * Sends settings update notification to all peers with configured exchange.
   * Called when dailyRequestLimit or GTNetEntity settings change for myGTNet.
   */
  private void sendSettingsUpdatedNotification(GTNet myGTNet) {
    GTNetMessageCodeType messageCode = GTNetMessageCodeType.GT_NET_SETTINGS_UPDATED_ALL_C;

    MsgRequest msgRequest = new MsgRequest();
    msgRequest.messageCode = messageCode;
    GTNetModelHelper.GTNetMsgRequest gtNetMsgRequest = GTNetModelHelper.getMsgClassByMessageCode(messageCode);
    List<GTNet> targets = gtNetJpaRepository.findWithConfiguredExchange();

    if (!targets.isEmpty()) {
      sendAndSaveMsg(myGTNet, targets, gtNetMsgRequest, msgRequest);
    }
    // Save broadcast to own entry for visibility
    saveBroadcastToOwnEntry(myGTNet, msgRequest);
  }

  static boolean isDomainNameThisMachine(String domainName)
      throws SocketException, UnknownHostException, URISyntaxException {
    URI uri = new URI(domainName);
    String host = uri.getHost();
    InetAddress[] searchAddr = InetAddress.getAllByName(host);
    Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
    for (NetworkInterface netint : Collections.list(nets)) {
      if (!netint.isLoopback()
          && !Collections.disjoint(Collections.list(netint.getInetAddresses()), Arrays.asList(searchAddr))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public GTNetWithMessages submitMsg(MsgRequest msgRequest) {
    // For response messages, gtNetMsgRequest may be null (responses don't have registered model classes)
    GTNetMsgRequest gtNetMsgRequest = GTNetModelHelper.getMsgClassByMessageCode(msgRequest.messageCode);

    List<GTNet> gtNetList = getTargetDomains(msgRequest);
    GTNet sourceGTNet = gtNetJpaRepository
        .findById(GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersService)).orElseThrow();

    if (gtNetMsgRequest != null) {
      // Request message - needs model validation and expects response
      sendAndSaveMsg(sourceGTNet, gtNetList, gtNetMsgRequest, msgRequest);
    } else {
      // Response message - no model validation needed, just send the reply
      sendResponseMsg(sourceGTNet, gtNetList, msgRequest);
    }

    // For broadcast messages, save a copy under own server entry for visibility
    if (msgRequest.messageCode.name().contains(GTNetModelHelper.MESSAGE_TO_ALL)) {
      saveBroadcastToOwnEntry(sourceGTNet, msgRequest);
    }

    return this.getAllGTNetsWithMessages();
  }

  /**
   * Saves a copy of a broadcast message under the own server's GTNet entry.
   * This allows administrators to see sent broadcast messages in their own server's message list.
   */
  private void saveBroadcastToOwnEntry(GTNet sourceGTNet, MsgRequest msgRequest) {
    gtNetMessageJpaRepository.saveMsg(
        new GTNetMessage(sourceGTNet.getIdGtNet(), new Date(), SendReceivedType.SEND.getValue(),
            null, msgRequest.messageCode.getValue(), msgRequest.message, msgRequest.gtNetMessageParamMap));
  }

  private List<GTNet> getTargetDomains(MsgRequest msgRequest) {
    if (msgRequest.messageCode.name().contains(GTNetModelHelper.MESSAGE_TO_ALL)) {
      // All broadcast messages go to servers with configured data exchange
      return switch (msgRequest.messageCode) {
      case GT_NET_OFFLINE_ALL_C, GT_NET_ONLINE_ALL_C, GT_NET_BUSY_ALL_C, GT_NET_RELEASED_BUSY_ALL_C, GT_NET_MAINTENANCE_ALL_C, GT_NET_OPERATION_DISCONTINUED_ALL_C -> gtNetJpaRepository
          .findWithConfiguredExchange();
      default -> List.of();
      };
    } else {
      List<GTNet> gtNetList = new ArrayList<>();
      gtNetJpaRepository.findById(msgRequest.idGTNetTargetDomain).stream().forEach(n -> gtNetList.add(n));
      return gtNetList;
    }
  }

  // Send Message
  ///////////////////////////////////////////////////////////////////////
  /**
   * Message is created and send to the remote servers.
   *
   * Converts the gtNetMessageParamMap to the typed model class for validation and serialization as payload. This
   * ensures type safety and proper handling of complex types like Set&lt;Enum&gt; and LocalDateTime on both sender and
   * receiver sides.
   *
   * @param sourceGTNet     the local GTNet entry
   * @param gtNetList       list of target GTNet entries to send to
   * @param gtNetMsgRequest metadata about the message type including model class
   * @param msgRequest      the request containing message parameters
   */
  private void sendAndSaveMsg(GTNet sourceGTNet, List<GTNet> gtNetList, GTNetMsgRequest gtNetMsgRequest,
      MsgRequest msgRequest) {
    // Convert map to typed model class for validation and payload serialization
    Object payloadModel = null;
    if (gtNetMsgRequest != null && gtNetMsgRequest.model != null && msgRequest.gtNetMessageParamMap != null
        && !msgRequest.gtNetMessageParamMap.isEmpty()) {
      payloadModel = convertMapToTypedModel(gtNetMsgRequest.model, msgRequest.gtNetMessageParamMap);
      validateModel(payloadModel);
    }

    for (GTNet targetGTNet : gtNetList) {
      GTNetMessage gtNetMessage = gtNetMessageJpaRepository.saveMsg(
          new GTNetMessage(targetGTNet.getIdGtNet(), new Date(), SendReceivedType.SEND.getValue(), msgRequest.replyTo,
              msgRequest.messageCode.getValue(), msgRequest.message, msgRequest.gtNetMessageParamMap));
      MessageEnvelope meResponse = sendMessage(sourceGTNet, targetGTNet, gtNetMessage, payloadModel);
      if (gtNetMsgRequest != null && gtNetMsgRequest.responseExpected && meResponse != null) {
        // Save received response with idSourceGtNetMessage from remote and replyTo pointing to our request
        GTNetMessage responseMsg = new GTNetMessage(targetGTNet.getIdGtNet(), meResponse.timestamp,
            SendReceivedType.RECEIVED.getValue(), gtNetMessage.getIdGtNetMessage(), meResponse.messageCode,
            meResponse.message, meResponse.gtNetMessageParamMap);
        responseMsg.setIdSourceGtNetMessage(meResponse.idSourceGtNetMessage);
        // Store waitDaysApply from the response envelope (cooling-off period set by remote admin)
        if (meResponse.waitDaysApply != null) {
          responseMsg.setWaitDaysApply(meResponse.waitDaysApply);
        }
        gtNetMessageJpaRepository.save(responseMsg);

        // Process payload from synchronous responses that contain data
        processSynchronousResponsePayload(sourceGTNet, meResponse, targetGTNet, gtNetMessage);
      }

      // Apply side effects for outgoing announcement messages
      applyOutgoingSideEffects(targetGTNet, msgRequest);
    }
  }

  /**
   * Processes payloads from synchronous responses that contain data requiring local processing.
   * This method handles responses that would normally be processed by response handlers when received
   * asynchronously via the M2M endpoint, but need special handling when received as immediate HTTP responses.
   *
   * @param myGTNet       the local GTNet entry
   * @param meResponse    the response envelope containing the payload
   * @param targetGTNet   the remote GTNet entry that sent the response
   * @param gtNetMessage  the original request message we sent
   */
  private void processSynchronousResponsePayload(GTNet myGTNet, MessageEnvelope meResponse,
      GTNet targetGTNet, GTNetMessage gtNetMessage) {
    GTNetMessageCodeType responseCode = GTNetMessageCodeType.getGTNetMessageCodeTypeByValue(meResponse.messageCode);

    if (responseCode == GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_ACCEPT_S) {
      if (meResponse.payload != null && !meResponse.payload.isNull()) {
        processServerListPayload(myGTNet, meResponse);
      }
    } else if (responseCode == GTNetMessageCodeType.GT_NET_DATA_REQUEST_ACCEPT_S) {
      // When they accept our data request, we will RECEIVE data from them
      Set<GTNetExchangeKindType> acceptedKinds = parseEntityKinds(gtNetMessage.getGtNetMessageParamMap());
      for (GTNetExchangeKindType kind : acceptedKinds) {
        updateEntityForReceive(targetGTNet, kind);
      }
      gtNetJpaRepository.save(targetGTNet);
      log.info("Created GTNetConfigEntity with RECEIVE capability for {} entity kinds from {}",
          acceptedKinds.size(), targetGTNet.getDomainRemoteName());
    }
  }

  /**
   * Updates a GTNetEntity to add RECEIVE capability for the specified entity kind.
   * When they accept our request, we will RECEIVE data from them.
   */
  private void updateEntityForReceive(GTNet remoteGTNet, GTNetExchangeKindType kind) {
    GTNetEntity entity = remoteGTNet.getOrCreateEntity(kind);
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);

    if (entity.getGtNetConfigEntity() == null) {
      GTNetConfigEntity configEntity = new GTNetConfigEntity();
      if (entity.getIdGtNetEntity() != null) {
        configEntity.setIdGtNetEntity(entity.getIdGtNetEntity());
      }
      entity.setGtNetConfigEntity(configEntity);
    }
    // Config entity defaults to exchange=true, no need to set explicitly
  }

  /**
   * Processes a server list payload from a serverlist accept response. Creates or updates GTNet entries
   * based on the received server list, respecting the allowServerCreation flag on our own GTNet entry.
   *
   * @param myGTNet    the local GTNet entry
   * @param meResponse the response envelope containing the server list payload
   */
  private void processServerListPayload(GTNet myGTNet, MessageEnvelope meResponse) {
    try {
      List<GTNetPublicDTO> serverList = objectMapper.convertValue(meResponse.payload,
          new TypeReference<List<GTNetPublicDTO>>() {});

      int newServers = 0;
      int updatedServers = 0;

      for (GTNetPublicDTO serverDto : serverList) {
        // Skip our own entry
        if (serverDto.getDomainRemoteName().equals(myGTNet.getDomainRemoteName())) {
          continue;
        }

        GTNet existingServer = gtNetJpaRepository.findByDomainRemoteName(serverDto.getDomainRemoteName());

        if (existingServer != null) {
          // Update existing server's status if remote data is newer
          boolean updated = updateServerFromDTO(existingServer, serverDto);
          if (updated) {
            updatedServers++;
          }
        } else if (myGTNet.isAllowServerCreation()) {
          // Add new server
          createServerFromDTO(serverDto);
          newServers++;
        }
      }

      log.info("Processed synchronous server list response: {} new servers added, {} servers updated",
          newServers, updatedServers);

    } catch (Exception e) {
      log.warn("Failed to process server list payload from synchronous response: {}", e.getMessage());
    }
  }

  /**
   * Updates an existing server entry with information from a DTO if the remote data is newer.
   *
   * @param existing the existing GTNet entry
   * @param dto      the DTO with updated information
   * @return true if any changes were made
   */
  private boolean updateServerFromDTO(GTNet existing, GTNetPublicDTO dto) {
    // Only update if remote data is newer than ours
    if (dto.getLastModifiedTime() != null && existing.getLastModifiedTime() != null
        && !dto.getLastModifiedTime().after(existing.getLastModifiedTime())) {
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

  /**
   * Creates a new GTNet entry from a DTO.
   *
   * @param dto the DTO containing server information
   */
  private void createServerFromDTO(GTNetPublicDTO dto) {
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

  /**
   * Applies side effects for outgoing announcement messages (like revokes).
   * When we send a revoke, we disable exchange for the specified entity kinds.
   */
  private void applyOutgoingSideEffects(GTNet targetGTNet, MsgRequest msgRequest) {
    if (msgRequest.messageCode == GTNetMessageCodeType.GT_NET_DATA_REVOKE_SEL_C) {
      Set<GTNetExchangeKindType> revokedKinds = parseEntityKinds(msgRequest.gtNetMessageParamMap);
      for (GTNetExchangeKindType kind : revokedKinds) {
        targetGTNet.getEntity(kind).ifPresent(entity -> {
          GTNetConfigEntity configEntity = entity.getGtNetConfigEntity();
          if (configEntity != null) {
            // Disable exchange since we're revoking our side
            configEntity.setExchange(false);
          }
        });
      }
      gtNetJpaRepository.save(targetGTNet);
    }
  }

  /**
   * Parses entityKinds from message parameters.
   */
  private Set<GTNetExchangeKindType> parseEntityKinds(Map<String, GTNetMessage.GTNetMessageParam> paramMap) {
    if (paramMap == null) {
      return Set.of(GTNetExchangeKindType.LAST_PRICE, GTNetExchangeKindType.HISTORICAL_PRICES);
    }
    GTNetMessage.GTNetMessageParam param = paramMap.get("entityKinds");
    if (param == null || param.getParamValue() == null || param.getParamValue().isBlank()) {
      return Set.of(GTNetExchangeKindType.LAST_PRICE, GTNetExchangeKindType.HISTORICAL_PRICES);
    }
    return Arrays.stream(param.getParamValue().split(","))
        .map(String::trim)
        .map(v -> {
          try {
            return GTNetExchangeKindType.valueOf(v.toUpperCase());
          } catch (IllegalArgumentException e) {
            try {
              return GTNetExchangeKindType.getGTNetExchangeKindType(Byte.parseByte(v));
            } catch (NumberFormatException nfe) {
              return null;
            }
          }
        })
        .filter(k -> k != null)
        .collect(Collectors.toSet());
  }

  /**
   * Sends a response message to the original requester. Response messages don't need model validation
   * as they are simple acknowledgments with an optional message text. The replyTo field links the
   * response to the original request message.
   *
   * @param sourceGTNet the local GTNet entry
   * @param gtNetList   list of target GTNet entries (typically just one - the original requester)
   * @param msgRequest  the request containing the response message code, replyTo, optional message, and waitDaysApply
   */
  private void sendResponseMsg(GTNet sourceGTNet, List<GTNet> gtNetList, MsgRequest msgRequest) {
    // Look up the original request message to get the requester's original message ID
    Integer replyToSourceId = null;
    if (msgRequest.replyTo != null) {
      GTNetMessage originalRequest = gtNetMessageJpaRepository.findById(msgRequest.replyTo).orElse(null);
      if (originalRequest != null) {
        replyToSourceId = originalRequest.getIdSourceGtNetMessage();
      }
    }

    for (GTNet targetGTNet : gtNetList) {
      GTNetMessage gtNetMessage = new GTNetMessage(targetGTNet.getIdGtNet(), new Date(), SendReceivedType.SEND.getValue(), msgRequest.replyTo,
              msgRequest.messageCode.getValue(), msgRequest.message, null);
      // Set waitDaysApply if provided by admin
      if (msgRequest.waitDaysApply != null) {
        gtNetMessage.setWaitDaysApply(msgRequest.waitDaysApply);
      }
      gtNetMessage = gtNetMessageJpaRepository.saveMsg(gtNetMessage);

      // Apply side effects for specific response codes
      applyManualResponseSideEffects(sourceGTNet, targetGTNet, msgRequest);

      // Send the response with replyToSourceId so the receiver can link it to their original request
      // Include payload for specific response codes
      Object payload = buildManualResponsePayload(sourceGTNet, targetGTNet, msgRequest.messageCode);
      sendResponseMessage(sourceGTNet, targetGTNet, gtNetMessage, replyToSourceId, payload);
    }
  }

  /**
   * Applies side effects for manual responses that require state changes.
   */
  private void applyManualResponseSideEffects(GTNet sourceGTNet, GTNet targetGTNet, MsgRequest msgRequest) {
    GTNetMessageCodeType responseCode = msgRequest.messageCode;

    if (responseCode == GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_ACCEPT_S) {
      // Grant server list access to this remote
      GTNetConfig config = targetGTNet.getGtNetConfig();
      if (config != null && !config.isServerlistAccessGranted()) {
        config.setServerlistAccessGranted(true);
        gtNetConfigJpaRepository.save(config);
        log.info("Granted server list access to {} via manual response", targetGTNet.getDomainRemoteName());
      }
    } else if (responseCode == GTNetMessageCodeType.GT_NET_DATA_REQUEST_ACCEPT_S) {
      // When we accept their data request, we will SEND data to them
      // Get the entityKinds from the original request message
      Set<GTNetExchangeKindType> acceptedKinds = getEntityKindsFromOriginalRequest(msgRequest.replyTo);
      for (GTNetExchangeKindType kind : acceptedKinds) {
        updateEntityForSend(targetGTNet, kind);
      }
      gtNetJpaRepository.save(targetGTNet);
      log.info("Created GTNetConfigEntity with SEND capability for {} entity kinds to {}",
          acceptedKinds.size(), targetGTNet.getDomainRemoteName());

      // Also update sourceGTNet (myGTNet) to reflect that this server offers these entity kinds.
      // This ensures the serverState is correctly communicated to remote servers via MessageEnvelope.
      for (GTNetExchangeKindType kind : acceptedKinds) {
        updateMyEntityForAccept(sourceGTNet, kind);
      }
      gtNetJpaRepository.save(sourceGTNet);
    }
  }

  /**
   * Gets the entityKinds from the original request message.
   */
  private Set<GTNetExchangeKindType> getEntityKindsFromOriginalRequest(Integer replyToMessageId) {
    if (replyToMessageId == null) {
      return Set.of(GTNetExchangeKindType.LAST_PRICE, GTNetExchangeKindType.HISTORICAL_PRICES);
    }
    GTNetMessage originalRequest = gtNetMessageJpaRepository.findById(replyToMessageId).orElse(null);
    if (originalRequest == null || originalRequest.getGtNetMessageParamMap() == null) {
      return Set.of(GTNetExchangeKindType.LAST_PRICE, GTNetExchangeKindType.HISTORICAL_PRICES);
    }
    return parseEntityKinds(originalRequest.getGtNetMessageParamMap());
  }

  /**
   * Updates a GTNetEntity to add SEND capability for the specified entity kind.
   * When we accept their request, we will SEND data to them.
   */
  private void updateEntityForSend(GTNet remoteGTNet, GTNetExchangeKindType kind) {
    GTNetEntity entity = remoteGTNet.getOrCreateEntity(kind);
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);

    if (entity.getGtNetConfigEntity() == null) {
      GTNetConfigEntity configEntity = new GTNetConfigEntity();
      if (entity.getIdGtNetEntity() != null) {
        configEntity.setIdGtNetEntity(entity.getIdGtNetEntity());
      }
      entity.setGtNetConfigEntity(configEntity);
    }
    // Config entity defaults to exchange=true, no need to set explicitly
  }

  /**
   * Updates myGTNet's entity to reflect that this server offers the specified entity kind.
   * This ensures the serverState is correctly communicated to remote servers via MessageEnvelope.
   * Only sets acceptRequest to AC_OPEN if currently closed - preserves AC_PUSH_OPEN if already set.
   */
  private void updateMyEntityForAccept(GTNet myGTNet, GTNetExchangeKindType kind) {
    GTNetEntity entity = myGTNet.getOrCreateEntity(kind);
    // Only upgrade from CLOSED to OPEN, don't downgrade from PUSH_OPEN to OPEN
    if (!entity.isAccepting()) {
      entity.setAcceptRequest(AcceptRequestTypes.AC_OPEN);
    }
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);
  }

  /**
   * Builds payload for manual responses that require data in the envelope.
   *
   * @return payload object or null if no payload is needed
   */
  private Object buildManualResponsePayload(GTNet sourceGTNet, GTNet targetGTNet, GTNetMessageCodeType responseCode) {
    if (responseCode == GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_ACCEPT_S) {
      // Include server list in payload
      List<GTNet> shareableServers = gtNetJpaRepository.findShareableServers(targetGTNet.getIdGtNet());
      List<GTNetPublicDTO> serverList = shareableServers.stream().map(GTNetPublicDTO::new).collect(Collectors.toList());
      log.info("Including {} servers in manual server list response to {}", serverList.size(),
          targetGTNet.getDomainRemoteName());
      return serverList;
    }
    return null;
  }

  /**
   * Sends a response message to a remote GTNet server with replyToSourceId for correlation.
   *
   * @param sourceGTNet     the local GTNet entry
   * @param targetGTNet     the remote GTNet entry
   * @param gtNetMessage    the message to send
   * @param replyToSourceId the original request's source ID for correlation
   * @param payload         optional payload to include in the envelope (can be null)
   */
  private MessageEnvelope sendResponseMessage(GTNet sourceGTNet, GTNet targetGTNet, GTNetMessage gtNetMessage,
      Integer replyToSourceId, Object payload) {
    if (hasOrCreateFirstContact(sourceGTNet, targetGTNet)) {
      MessageEnvelope meRequest = new MessageEnvelope(sourceGTNet, gtNetMessage);
      meRequest.replyToSourceId = replyToSourceId;
      if (payload != null) {
        meRequest.payload = objectMapper.convertValue(payload, JsonNode.class);
      }

      String tokenRemote = targetGTNet.getGtNetConfig() != null ? targetGTNet.getGtNetConfig().getTokenRemote() : null;
      SendResult result = baseDataClient.sendToMsgWithStatus(tokenRemote, targetGTNet.getDomainRemoteName(), meRequest);

      // Update target server's status based on response
      if (result.serverReachable() && result.response() != null) {
        updateRemoteGTNetFromEnvelope(targetGTNet, result.response());
      } else if (!result.serverReachable()) {
        if (targetGTNet.getServerOnline() != GTNetServerOnlineStatusTypes.SOS_OFFLINE) {
          targetGTNet.setServerOnline(GTNetServerOnlineStatusTypes.SOS_OFFLINE);
          gtNetJpaRepository.save(targetGTNet);
        }
      }
      return result.response();
    }
    return null;
  }

  private boolean hasOrCreateFirstContact(GTNet sourceGTNet, GTNet targetGTNet) {
    if (targetGTNet.getGtNetConfig() == null) {
      // Generate the token that the remote will use to authenticate back to us
      String tokenForRemote = DataHelper.generateGUID();
      Map<String, GTNetMessageParam> msgMap = convertPojoToMap(new FirstHandshakeMsg(tokenForRemote));
      GTNetMessage gtNetMessageRequest = gtNetMessageJpaRepository
          .saveMsg(new GTNetMessage(targetGTNet.getIdGtNet(), new Date(), SendReceivedType.SEND.getValue(), null,
              GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_SEL_RR_S.getValue(), null, msgMap));
      // Send our GTNet entity in the payload so the receiver can register us
      MessageEnvelope meResponse = sendMessage(sourceGTNet, targetGTNet, gtNetMessageRequest, sourceGTNet);
      if (meResponse != null
          && meResponse.messageCode == GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_ACCEPT_S.getValue()) {
        // Extract the token they gave us from their response
        FirstHandshakeMsg responseMsgData = convertMapToPojo(FirstHandshakeMsg.class, meResponse.gtNetMessageParamMap);
        // Create GTNetConfig to store the tokens
        GTNetConfig gtNetConfig = new GTNetConfig();
        gtNetConfig.setIdGtNet(targetGTNet.getIdGtNet());  // Set FK manually
        // Store their token (what we use to call them) as tokenRemote
        gtNetConfig.setTokenRemote(responseMsgData.tokenThis);
        // Store our token (what they use to call us) as tokenThis
        gtNetConfig.setTokenThis(tokenForRemote);
        gtNetConfigJpaRepository.save(gtNetConfig);  // Save config separately
        // Update in-memory object so subsequent calls in same transaction have the token
        targetGTNet.setGtNetConfig(gtNetConfig);

        // Save received response message with idSourceGtNetMessage from remote and replyTo pointing to our request
        GTNetMessage gtNetMessageResponse = new GTNetMessage(targetGTNet.getIdGtNet(), meResponse.timestamp,
            SendReceivedType.RECEIVED.getValue(), gtNetMessageRequest.getIdGtNetMessage(), meResponse.messageCode,
            meResponse.message, meResponse.gtNetMessageParamMap);
        gtNetMessageResponse.setIdSourceGtNetMessage(meResponse.idSourceGtNetMessage);
        // Store waitDaysApply if provided (typically for rejection responses)
        if (meResponse.waitDaysApply != null) {
          gtNetMessageResponse.setWaitDaysApply(meResponse.waitDaysApply);
        }
        gtNetMessageJpaRepository.save(gtNetMessageResponse);
        return true;
      }
      return false;
    } else {
      return true;
    }
  }

  private Map<String, GTNetMessageParam> convertPojoToMap(Object msgPojo) {
    Map<String, String> fhMap = objectMapper.convertValue(msgPojo, new TypeReference<Map<String, String>>() {
    });
    Map<String, GTNetMessageParam> msgMap = new HashMap<>();
    for (Map.Entry<String, String> entry : fhMap.entrySet()) {
      msgMap.put(entry.getKey(), new GTNetMessageParam(entry.getValue()));
    }
    return msgMap;
  }

  private <T> T convertMapToPojo(Class<T> clazz, Map<String, GTNetMessageParam> map) {
    Map<String, String> valueMap = map.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getParamValue()));
    return objectMapper.convertValue(valueMap, clazz);
  }

  /**
   * Converts a gtNetMessageParamMap to a typed model class with special handling for complex types.
   *
   * Handles:
   * <ul>
   *   <li>Comma-separated strings → Set&lt;Enum&gt; (e.g., "LAST_PRICE,HISTORICAL_PRICES" → Set&lt;GTNetExchangeKindType&gt;)</li>
   *   <li>ISO-8601 strings → LocalDateTime</li>
   *   <li>Simple string fields</li>
   * </ul>
   *
   * @param clazz the target model class
   * @param map   the parameter map from the request
   * @param <T>   the model type
   * @return the instantiated and populated model object
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private <T> T convertMapToTypedModel(Class<T> clazz, Map<String, GTNetMessageParam> map) {
    try {
      T instance = clazz.getDeclaredConstructor().newInstance();

      for (Field field : clazz.getFields()) {
        GTNetMessageParam param = map.get(field.getName());
        if (param == null || param.getParamValue() == null) {
          continue;
        }

        String value = param.getParamValue();
        Object convertedValue;

        if (Set.class.isAssignableFrom(field.getType())) {
          // Handle Set<Enum> - convert comma-separated string to EnumSet
          ParameterizedType paramType = (ParameterizedType) field.getGenericType();
          Class<?> enumClass = (Class<?>) paramType.getActualTypeArguments()[0];
          if (enumClass.isEnum()) {
            Set enumSet = EnumSet.noneOf((Class<Enum>) enumClass);
            for (String enumName : value.split(",")) {
              String trimmed = enumName.trim();
              if (!trimmed.isEmpty()) {
                enumSet.add(Enum.valueOf((Class<Enum>) enumClass, trimmed));
              }
            }
            convertedValue = enumSet;
          } else {
            continue;
          }
        } else if (LocalDateTime.class.isAssignableFrom(field.getType())) {
          // Handle LocalDateTime - parse ISO-8601 string
          convertedValue = LocalDateTime.parse(value);
        } else if (field.getType().isEnum()) {
          // Handle single enum value
          convertedValue = Enum.valueOf((Class<Enum>) field.getType(), value);
        } else {
          // Default: use Jackson for other types
          convertedValue = objectMapper.convertValue(value, field.getType());
        }

        field.set(instance, convertedValue);
      }

      return instance;
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to convert map to " + clazz.getSimpleName() + ": " + e.getMessage(), e);
    }
  }

  /**
   * Validates a model object using Jakarta Bean Validation.
   *
   * @param model the model to validate
   * @throws ConstraintViolationException if validation fails
   */
  private void validateModel(Object model) {
    Set<ConstraintViolation<Object>> violations = validator.validate(model);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  private MessageEnvelope sendMessage(GTNet sourceGTNet, GTNet targetGTNet, GTNetMessage gtNetMessage,
      Object payLoadObject) {
    if (gtNetMessage.getMessageCode() != GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_SEL_RR_S
        ? hasOrCreateFirstContact(sourceGTNet, targetGTNet)
        : true) {
      return sendMessageWithStatusUpdate(sourceGTNet, targetGTNet, gtNetMessage, payLoadObject);
    }
    return null;
  }

  /**
   * Sends a message to a remote GTNet server and updates the target's online/busy status.
   *
   * @param sourceGTNet   the local GTNet entry (provides serverBusy flag for outgoing envelope)
   * @param targetGTNet   the remote GTNet entry to send to (will be updated with online/busy status)
   * @param gtNetMessage  the message to send
   * @param payLoadObject optional payload object to include
   * @return the response envelope, or null if unreachable
   */
  private MessageEnvelope sendMessageWithStatusUpdate(GTNet sourceGTNet, GTNet targetGTNet, GTNetMessage gtNetMessage,
      Object payLoadObject) {
    MessageEnvelope meRequest = new MessageEnvelope(sourceGTNet, gtNetMessage);
    if (payLoadObject != null) {
      meRequest.payload = objectMapper.convertValue(payLoadObject, JsonNode.class);
    }

    String tokenRemote = targetGTNet.getGtNetConfig() != null ? targetGTNet.getGtNetConfig().getTokenRemote() : null;
    SendResult result = baseDataClient.sendToMsgWithStatus(tokenRemote, targetGTNet.getDomainRemoteName(), meRequest);

    // Update target server's status based on response
    if (result.serverReachable() && result.response() != null) {
      updateRemoteGTNetFromEnvelope(targetGTNet, result.response());
    } else if (!result.serverReachable()) {
      // Server unreachable - update online status
      if (targetGTNet.getServerOnline() != GTNetServerOnlineStatusTypes.SOS_OFFLINE) {
        targetGTNet.setServerOnline(GTNetServerOnlineStatusTypes.SOS_OFFLINE);
        gtNetJpaRepository.save(targetGTNet);
      }
    }

    return result.response();
  }

  /**
   * Sends a ping message to a remote GTNet server and updates its status.
   *
   * @param sourceGTNet the local GTNet entry (provides serverBusy and domain info)
   * @param targetGTNet the remote GTNet entry to ping (will be updated with online/busy status)
   * @return the response envelope, or null if unreachable
   */
  private MessageEnvelope sendPing(GTNet sourceGTNet, GTNet targetGTNet) {
    GTNetMessage gtNetMessagePing = new GTNetMessage(null, new Date(), SendReceivedType.SEND.getValue(), null,
        GTNetMessageCodeType.GT_NET_PING.getValue(), null, null);
    return sendMessageWithStatusUpdate(sourceGTNet, targetGTNet, gtNetMessagePing, null);
  }

  // Receive Message
  ///////////////////////////////////////////////////////////////////////
  /**
   * Processes an incoming GTNet message and returns the appropriate response.
   *
   * This method delegates to the handler registry, which routes the message to the appropriate handler based on the
   * message code. The handler determines whether to return an immediate response, await manual handling, or process
   * without response (for announcements).
   *
   * Additionally updates the remote GTNet's serverBusy and serverOnline status based on the incoming message.
   *
   * @param me the incoming message envelope
   * @return the response envelope, or null if no immediate response is needed
   */
  @Override
  public MessageEnvelope getMsgResponse(MessageEnvelope me) throws Exception {
    GTNet myGTNet = gtNetJpaRepository
        .getReferenceById(GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersService));

    GTNetMessageCodeType messageCode = GTNetMessageCodeType.getGTNetMessageCodeTypeByValue(me.messageCode);
    if (messageCode == null) {
      log.warn("Unknown message code received: {}", me.messageCode);
      return buildErrorResponse(myGTNet, "UNKNOWN_MESSAGE_CODE", "Unknown message code: " + me.messageCode);
    }

    // Check if handler is registered
    if (!handlerRegistry.hasHandler(messageCode)) {
      log.warn("No handler registered for message code: {}", messageCode);
      return buildErrorResponse(myGTNet, "NO_HANDLER", "No handler for message code: " + messageCode);
    }

    // Look up remote GTNet (may be null for first handshake)
    GTNet remoteGTNet = gtNetJpaRepository.findByDomainRemoteName(me.sourceDomain);

    // Update remote server's status from the envelope's sourceGtNet
    if (remoteGTNet != null) {
      updateRemoteGTNetFromEnvelope(remoteGTNet, me);
    }

    // Look up auto-response rules for this message code, ordered by priority
    List<GTNetMessageAnswer> autoResponseRules = gtNetMessageAnswerJpaRepository
        .findByRequestMsgCodeOrderByPriority(me.messageCode);

    // Build context for the handler
    GTNetMessageContext context = new GTNetMessageContext(myGTNet, remoteGTNet, me, autoResponseRules, objectMapper);

    // Get the handler and process the message
    GTNetMessageHandler handler = handlerRegistry.getHandler(messageCode);
    HandlerResult result = handler.handle(context);

    // Process the result and include our serverBusy status in responses
    return switch (result) {
    case HandlerResult.ImmediateResponse r -> addServerBusyToResponse(r.response(), myGTNet);
    case HandlerResult.AwaitingManualResponse r -> {
      log.info("Message {} from {} awaiting manual response", messageCode, me.sourceDomain);
      yield null;
    }
    case HandlerResult.NoResponseNeeded r -> null;
    case HandlerResult.ProcessingError e -> {
      log.error("Error processing message {}: {} - {}", messageCode, e.errorCode(), e.message());
      yield buildErrorResponse(myGTNet, e.errorCode(), e.message());
    }
    };
  }

  /**
   * Updates the local GTNet entry for a remote server based on the sourceGtNet in the received envelope.
   * Synchronizes status flags, timezone, capabilities, and entity exchange settings.
   *
   * @param localRemoteEntry the local GTNet entry representing the remote server (to be updated)
   * @param envelope         the received message envelope containing sourceGtNet
   */
  private void updateRemoteGTNetFromEnvelope(GTNet localRemoteEntry, MessageEnvelope envelope) {
    boolean needsSave = false;

    // Server communicated with us, so it's online
    if (localRemoteEntry.getServerOnline() != GTNetServerOnlineStatusTypes.SOS_ONLINE) {
      localRemoteEntry.setServerOnline(GTNetServerOnlineStatusTypes.SOS_ONLINE);
      needsSave = true;
    }

    // Update busy status from envelope
    if (localRemoteEntry.isServerBusy() != envelope.serverBusy) {
      localRemoteEntry.setServerBusy(envelope.serverBusy);
      needsSave = true;
    }

    // Sync additional fields from sourceGtNet DTO if present
    GTNetPublicDTO sourceGtNet = envelope.sourceGtNet;
    if (sourceGtNet != null) {
      // Sync timezone
      if (sourceGtNet.getTimeZone() != null
          && !sourceGtNet.getTimeZone().equals(localRemoteEntry.getTimeZone())) {
        localRemoteEntry.setTimeZone(sourceGtNet.getTimeZone());
        needsSave = true;
      }

      // Sync spread capability
      if (sourceGtNet.isSpreadCapability() != localRemoteEntry.isSpreadCapability()) {
        localRemoteEntry.setSpreadCapability(sourceGtNet.isSpreadCapability());
        needsSave = true;
      }

      // Sync the remote's daily request limit (what THEY accept from US)
      if (!java.util.Objects.equals(sourceGtNet.getDailyRequestLimit(), localRemoteEntry.getDailyRequestLimit())) {
        localRemoteEntry.setDailyRequestLimit(sourceGtNet.getDailyRequestLimit());
        needsSave = true;
      }

      // Sync GTNetEntities (data exchange capabilities)
      if (sourceGtNet.getGtNetEntities() != null) {
        needsSave |= syncGtNetEntitiesFromDTO(localRemoteEntry, sourceGtNet.getGtNetEntities());
      }
    }

    if (needsSave) {
      gtNetJpaRepository.save(localRemoteEntry);
    }
  }

  /**
   * Synchronizes the GTNetEntity list from a remote source DTO into the local entry.
   * Updates existing entities and adds new ones based on entityKind.
   *
   * @param localEntry     the local GTNet entry to update
   * @param sourceEntities the entity DTOs from the remote source
   * @return true if any changes were made
   */
  private boolean syncGtNetEntitiesFromDTO(GTNet localEntry, List<GTNetEntityPublicDTO> sourceEntities) {
    boolean changed = false;

    for (GTNetEntityPublicDTO sourceEntity : sourceEntities) {
      GTNetEntity localEntity = localEntry.getOrCreateEntity(sourceEntity.getEntityKind());

      // Sync acceptRequest and serverState from remote
      if (localEntity.getAcceptRequest() != sourceEntity.getAcceptRequest()) {
        localEntity.setAcceptRequest(sourceEntity.getAcceptRequest());
        changed = true;
      }
      if (localEntity.getServerState() != sourceEntity.getServerState()) {
        localEntity.setServerState(sourceEntity.getServerState());
        changed = true;
      }

      // Note: We don't sync the local config (exchange direction is local decision)
      if (localEntity.getIdGtNetEntity() == null) {
        // New entity was created
        changed = true;
      }
    }

    return changed;
  }

  /**
   * Ensures the response envelope contains the local server's GTNet info.
   * This is a safety check in case the envelope was created without using the standard constructor.
   *
   * @param response the response envelope to modify
   * @param myGTNet  the local GTNet entry
   * @return the modified response
   */
  private MessageEnvelope addServerBusyToResponse(MessageEnvelope response, GTNet myGTNet) {
    if (response != null) {
      response.serverBusy = myGTNet.isServerBusy();
      if (response.sourceGtNet == null) {
        response.sourceGtNet = new GTNetPublicDTO(myGTNet);
      }
    }
    return response;
  }

  /**
   * Builds an error response envelope with the local server's busy status.
   */
  private MessageEnvelope buildErrorResponse(GTNet myGTNet, String errorCode, String message) {
    GTNetMessage errorMsg = new GTNetMessage(null, new Date(), SendReceivedType.ANSWER.getValue(), null,
        GTNetMessageCodeType.GT_NET_PING.getValue(), message, null);
    errorMsg.setErrorMsgCode(errorCode);
    MessageEnvelope errorEnvelope = new MessageEnvelope(myGTNet, errorMsg);
    return errorEnvelope;
  }

  @Override
  public void validateIncomingToken(String sourceDomain, String authToken) {
    if (authToken == null || authToken.isBlank()) {
      throw new SecurityException("Missing authentication token");
    }

    GTNet remoteGTNet = gtNetJpaRepository.findByDomainRemoteName(sourceDomain);
    if (remoteGTNet == null) {
      throw new SecurityException("Unknown source domain: " + sourceDomain);
    }

    GTNetConfig gtNetConfig = remoteGTNet.getGtNetConfig();
    String expectedToken = gtNetConfig != null ? gtNetConfig.getTokenThis() : null;
    if (expectedToken == null || !expectedToken.equals(authToken)) {
      throw new SecurityException("Invalid authentication token for domain: " + sourceDomain);
    }
  }

  @Override
  @Transactional
  public void broadcastSettingsUpdate() {
    Integer myEntryId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersService);
    GTNet myGTNet = gtNetJpaRepository.findById(myEntryId).orElseThrow(
        () -> new IllegalStateException("My GTNet entry not found with ID: " + myEntryId));

    sendSettingsUpdatedNotification(myGTNet);
  }

}
