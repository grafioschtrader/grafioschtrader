package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.common.DataHelper;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetConfigEntity;
import grafiosch.entities.GTNetEntity;
import grafiosch.entities.GTNetMaintenanceWindow;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.entities.GTNetMessageAnswer;
import grafiosch.entities.GTNetMessageAttempt;
import grafiosch.entities.GTNetSupplierDetail;
import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exportdelete.MySqlInsertStatementGenerator;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.DeliveryStatus;
import grafiosch.gtnet.ExchangeKindTypeRegistry;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetCommandValidator;
import grafiosch.gtnet.GTNetDomainService;
import grafiosch.gtnet.GTNetEnvelopeValidator;
import grafiosch.gtnet.GTNetGrantService;
import grafiosch.gtnet.GTNetIdempotencyService;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.gtnet.GTNetProtocolDescriptor;
import grafiosch.gtnet.GTNetRequestBudgetService;
import grafiosch.gtnet.GTNetServerOnlineStatusTypes;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.GTNetStatusCheckService;
import grafiosch.gtnet.GTNetTime;
import grafiosch.gtnet.GTNetTimeoutHelper;
import grafiosch.gtnet.GTNetTokenRotationService;
import grafiosch.gtnet.IExchangeKindType;
import grafiosch.gtnet.MessageCategory;
import grafiosch.gtnet.MessageParamDateParser;
import grafiosch.gtnet.MessageVisibility;
import grafiosch.gtnet.SendReceivedType;
import grafiosch.gtnet.handler.GTNetCoolingOffService;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.GTNetMessageHandler;
import grafiosch.gtnet.handler.GTNetMessageHandlerRegistry;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.m2m.model.GTNetEntityPublicDTO;
import grafiosch.gtnet.m2m.model.GTNetPublicDTO;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.gtnet.model.ExchangeKindTypeInfo;
import grafiosch.gtnet.model.GTNetMessageAttemptView;
import grafiosch.gtnet.model.GTNetWithMessages;
import grafiosch.gtnet.model.MsgRequest;
import grafiosch.gtnet.model.MultiTargetMsgRequest;
import grafiosch.gtnet.model.msg.FirstHandshakeMsg;
import grafiosch.m2m.GTNetMessageHelper;
import grafiosch.m2m.client.BaseDataClient;
import grafiosch.m2m.client.BaseDataClient.SendResult;
import grafiosch.service.DailyLimitService;
import grafiosch.types.OperationType;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class GTNetJpaRepositoryImpl extends BaseRepositoryImpl<GTNet> implements GTNetJpaRepositoryCustom {

  private static final Logger log = LoggerFactory.getLogger(GTNetJpaRepositoryImpl.class);

  /** Message codes for future-oriented messages that use background delivery */
  private static final Set<GTNetMessageCode> FUTURE_ORIENTED_MESSAGE_CODES = Set.of(
      GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C, GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C,
      GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C,
      GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C);

  /** Base GTNet tables in delete order (children first). Insert order is reversed. */
  public static final String[] GTNET_BASE_TABLES_DELETE_ORDER = { GTNetMessageAttempt.TABNAME,
      GTNetMaintenanceWindow.TABNAME, GTNetMessage.GT_NET_MESSAGE_PARAM, GTNetMessage.TABNAME,
      GTNetConfigEntity.TABNAME, GTNetSupplierDetail.TABNAME, GTNetEntity.TABNAME, GTNetConfig.TABNAME,
      GTNetMessageAnswer.TABNAME, GTNet.TABNAME };

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetConfigJpaRepository gtNetConfigJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private GTNetMessageAnswerJpaRepository gtNetMessageAnswerJpaRepository;

  @Autowired
  private GTNetMessageAttemptJpaRepository gtNetMessageAttemptJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private DailyLimitService dailyLimitService;

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

  @Autowired
  private ExchangeKindTypeRegistry exchangeKindTypeRegistry;

  @Autowired
  private GTNetMessageCodeRegistry messageCodeRegistry;

  @Autowired
  private GTNetStatusCheckService statusCheckService;

  @Autowired
  private GTNetCoolingOffService coolingOffService;

  @Autowired
  private GTNetRequestBudgetService requestBudgetService;

  @Autowired
  private GTNetEnvelopeValidator envelopeValidator;

  @Autowired
  private GTNetIdempotencyService idempotencyService;

  @Autowired
  private GTNetTokenRotationService tokenRotationService;

  @Autowired
  private GTNetGrantService grantService;

  @Autowired
  private GTNetDomainService domainService;

  @Autowired
  private GTNetCommandValidator commandValidator;

  @Autowired
  private GTNetMaintenanceWindowJpaRepository gtNetMaintenanceWindowJpaRepository;

  @Override
  @Transactional
  public GTNet saveOnlyAttributes(GTNet gtNet) throws Exception {
    GTNet existingEntity = gtNetJpaRepository.findById(gtNet.getIdGtNet()).orElse(null);
    return saveOnlyAttributes(gtNet, existingEntity, getUpdatePropertyLevels(existingEntity));
  }

  @Override
  @Transactional
  public GTNetWithMessages getAllGTNetsWithMessages() {
    // Fetch all unanswered requests and group by idGtNet
    Map<Integer, List<Integer>> outgoingPendingReplies = groupPendingByGtNet(gtNetMessageJpaRepository
        .findUnansweredRequests(SendReceivedType.SEND.getValue(), messageCodeRegistry.requestCodesRequiringResponse()));
    Map<Integer, List<Integer>> incomingPendingReplies = groupPendingByGtNet(
        gtNetMessageJpaRepository.findUnansweredRequests(SendReceivedType.RECEIVED.getValue(),
            messageCodeRegistry.requestCodesRequiringResponse()));

    // Get message counts per idGtNet (instead of full messages for lazy loading)
    Map<Integer, Integer> gtNetMessageCountMap = gtNetMessageJpaRepository
        .countMessagesByIdGtNet(visibilitiesForCaller());

    // Check for open discontinued message
    Integer idOpenDiscontinuedMessage = gtNetMessageJpaRepository.findOpenDiscontinuedMessage(
        SendReceivedType.SEND.getValue(), GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C.getValue(),
        GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C.getValue());

    // Check for open maintenance message
    Integer idOpenMaintenanceMessage = gtNetMessageJpaRepository.findOpenMaintenanceMessage(
        SendReceivedType.SEND.getValue(), GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C.getValue(),
        GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C.getValue());

    // Announced maintenance windows per domain, so the panel header can show whether it holds anything
    Map<Integer, Integer> gtNetMaintenanceWindowCountMap = gtNetMaintenanceWindowJpaRepository.countPerGtNet().stream()
        .collect(Collectors.toMap(row -> (Integer) row[0], row -> ((Number) row[1]).intValue()));

    Map<Integer, Integer> gtNetMessageAttemptCountMap = isAdminCaller()
        ? gtNetMessageAttemptJpaRepository.countBySourceIdGtNet()
        : Map.of();

    List<ExchangeKindTypeInfo> exchangeKindTypes = exchangeKindTypeRegistry.getAllKinds().stream()
        .map(ExchangeKindTypeInfo::new).collect(Collectors.toList());

    return new GTNetWithMessages(gtNetJpaRepository.findAll(), gtNetMessageCountMap, outgoingPendingReplies,
        incomingPendingReplies, globalparametersJpaRepository.getGTNetMyEntryID(), idOpenDiscontinuedMessage,
        idOpenMaintenanceMessage, gtNetMaintenanceWindowCountMap, gtNetMessageAttemptCountMap, exchangeKindTypes);
  }

  @Override
  public List<GTNetMaintenanceWindow> getMaintenanceWindowsByIdGtNet(Integer idGtNet) {
    return gtNetMaintenanceWindowJpaRepository.findByIdGtNetOrderByFromDateTimeDesc(idGtNet);
  }

  @Override
  public List<GTNetMessageAttemptView> getMessageAttemptsByIdGtNet(Integer idGtNet) {
    return gtNetMessageAttemptJpaRepository.findViewsBySourceIdGtNet(idGtNet);
  }

  @Override
  public List<GTNetMessage> getMessagesByIdGtNet(Integer idGtNet) {
    List<GTNetMessage> messages = gtNetMessageJpaRepository.findByIdGtNetAndVisibilityInOrderByTimestampDesc(idGtNet,
        visibilitiesForCaller());

    // Fetch pending IDs for canDelete computation
    Set<Integer> outgoingPendingIds = groupPendingByGtNet(gtNetMessageJpaRepository
        .findUnansweredRequests(SendReceivedType.SEND.getValue(), messageCodeRegistry.requestCodesRequiringResponse()))
            .getOrDefault(idGtNet, List.of()).stream().collect(Collectors.toSet());
    Set<Integer> incomingPendingIds = groupPendingByGtNet(gtNetMessageJpaRepository.findUnansweredRequests(
        SendReceivedType.RECEIVED.getValue(), messageCodeRegistry.requestCodesRequiringResponse()))
            .getOrDefault(idGtNet, List.of()).stream().collect(Collectors.toSet());

    // Compute canDelete flags
    gtNetMessageJpaRepository.computeCanDeleteFlags(messages, outgoingPendingIds, incomingPendingIds);

    return messages;
  }

  /**
   * Groups the query result (id_gt_net, id_gt_net_message) into a Map by idGtNet.
   */
  private Map<Integer, List<Integer>> groupPendingByGtNet(List<Object[]> queryResult) {
    return queryResult.stream().collect(Collectors.groupingBy(row -> ((Number) row[0]).intValue(),
        Collectors.mapping(row -> ((Number) row[1]).intValue(), Collectors.toList())));
  }

  @Override
  public GTNet saveOnlyAttributes(final GTNet gtNet, final GTNet existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {

    Integer myInstanceEntry = globalparametersJpaRepository.getGTNetMyEntryID();
    // Validate remote URL is reachable

    if (gtNetJpaRepository.count() == 0) {
      baseDataClient.getActuatorInfo(gtNet.getDomainRemoteName(),
          GTNetTimeoutHelper.resolveTimeout(gtNet, globalparametersJpaRepository));
    }

    // Track settings changes (only for myGTNet)
    boolean isMyEntry = myInstanceEntry != null && myInstanceEntry.equals(gtNet.getIdGtNet());
    boolean settingsChanged = isMyEntry && existingEntity != null && hasSettingsChanged(existingEntity, gtNet);

    // Validate that AC_PUSH_OPEN is only used for entity kinds that support it
    validateEntityPushSupport(gtNet);

    GTNet gtNetNew = RepositoryHelper.saveOnlyAttributes(gtNetJpaRepository, gtNet, existingEntity,
        updatePropertyLevelClasses);
    if (isDomainNameThisMachine(gtNet.getDomainRemoteName())) {
      globalparametersJpaRepository.saveGTNetMyEntryID(gtNetNew.getIdGtNet());
      // Local server is always online - set status if not already set
      if (gtNetNew.getServerOnline() != GTNetServerOnlineStatusTypes.SOS_ONLINE) {
        gtNetNew.setServerOnline(GTNetServerOnlineStatusTypes.SOS_ONLINE);
        gtNetNew = gtNetJpaRepository.save(gtNetNew);
      }
    }

    // If settings changed, schedule background task to notify all connected peers
    if (settingsChanged) {
      taskDataChangeJpaRepository
          .save(new TaskDataChange(TaskTypeBase.GTNET_SETTINGS_BROADCAST, TaskDataExecPriority.PRIO_NORMAL));
      log.info("Scheduled GTNet settings broadcast task for immediate execution");
    }

    return gtNetNew;
  }

  /**
   * Checks if GTNet or GTNetEntity settings have changed that should trigger a broadcast. Compares dailyRequestLimit
   * and entity-level settings (acceptRequest, serverState, maxLimit).
   */
  private boolean hasSettingsChanged(GTNet existing, GTNet updated) {
    // Check dailyRequestLimit
    if (!java.util.Objects.equals(existing.getDailyRequestLimit(), updated.getDailyRequestLimit())) {
      return true;
    }

    // Check serverBusy change
    if (existing.isServerBusy() != updated.isServerBusy()) {
      return true;
    }

    // Check GTNetEntity changes
    for (GTNetEntity updatedEntity : updated.getGtNetEntities()) {
      GTNetEntity existingEntity = findMatchingEntity(existing,
          exchangeKindTypeRegistry.getByValue(updatedEntity.getEntityKindValue()));
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
   * Validates that AC_PUSH_OPEN is only configured for entity kinds that support push.
   *
   * @param gtNet the GTNet to validate
   * @throws DataViolationException if an entity has AC_PUSH_OPEN but the kind doesn't support it
   */
  private void validateEntityPushSupport(GTNet gtNet) {
    for (GTNetEntity entity : gtNet.getGtNetEntities()) {
      if (entity.getAcceptRequest() == AcceptRequestTypes.AC_PUSH_OPEN) {
        IExchangeKindType kind = exchangeKindTypeRegistry.getByValue(entity.getEntityKindValue());
        if (kind != null && !kind.supportsPush()) {
          throw new DataViolationException("accept.request", "g.gtnet.entity.push.not.supported",
              new Object[] { kind.name() });
        }
      }
    }
  }

  /**
   * Finds a GTNetEntity by entity kind within a GTNet.
   */
  private GTNetEntity findMatchingEntity(GTNet gtNet, IExchangeKindType kind) {
    return gtNet.getGtNetEntities().stream().filter(e -> e.getEntityKindValue() == kind.getValue()).findFirst()
        .orElse(null);
  }

  /**
   * Sends settings update notification to all peers with configured exchange. Called when dailyRequestLimit or
   * GTNetEntity settings change for myGTNet.
   */
  private void sendSettingsUpdatedNotification(GTNet myGTNet) {
    GTNetMessageCode messageCode = GNetCoreMessageCode.GT_NET_SETTINGS_UPDATED_ALL_C;

    MsgRequest msgRequest = new MsgRequest();
    msgRequest.messageCode = messageCode.name();
    GTNetProtocolDescriptor descriptor = messageCodeRegistry.getDescriptor(messageCode.getValue());
    List<GTNet> targets = getRemotePeersWithExchange();

    if (!targets.isEmpty()) {
      sendAndSaveMsg(myGTNet, targets, descriptor, msgRequest, messageCode);
    }
    // Save broadcast to own entry for visibility
    saveBroadcastToOwnEntry(myGTNet, msgRequest, messageCode);
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
    // Resolve message code from string to enum
    GTNetMessageCode messageCode = messageCodeRegistry.getByName(msgRequest.messageCode);
    if (messageCode == null) {
      throw new DataViolationException("message.code", "g.gtnet.invalid.message.code",
          new Object[] { msgRequest.messageCode });
    }

    // The shape of the command is checked before anything is written or sent: submitMsg picks its path from whether
    // the code has a payload model, so without this a response code reaches sendResponseMsg and runs the side effects
    // of an answer even when it answers nothing.
    commandValidator.validate(msgRequest, messageCode);

    // Validate that only one GT_NET_OPERATION_DISCONTINUED_ALL_C can be open at a time
    if (messageCode == GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C) {
      Integer existingOpenDiscontinued = gtNetMessageJpaRepository.findOpenDiscontinuedMessage(
          SendReceivedType.SEND.getValue(), GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C.getValue(),
          GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C.getValue());
      if (existingOpenDiscontinued != null) {
        throw new DataViolationException("message.code", "g.gtnet.discontinued.already.open", null);
      }
    }

    // Several maintenance windows may be announced, but they must not overlap: the recipients would not be able to
    // tell which of two overlapping announcements a later cancellation frees them from.
    if (messageCode == GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C) {
      validateMaintenanceWindowFree(msgRequest);
    }

    rejectMessageToRetiredPeer(msgRequest, messageCode);
    rejectAdminMessageWithoutHandshake(msgRequest, messageCode);

    GTNetProtocolDescriptor descriptor = messageCodeRegistry.getDescriptor(messageCode.getValue());

    List<GTNet> gtNetList = getTargetDomains(msgRequest, messageCode);
    GTNet sourceGTNet = gtNetJpaRepository
        .findById(GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository)).orElseThrow();

    // The GTNetMessage rows this request is about to write are charged to today's budget as one unit, before the
    // first of them is saved. This mapping does not pass through UpdateCreate, so the seeded DAY_CUD|GTNetMessage row
    // that bounds POST /api/gtnetmessage would otherwise never be consulted here - a budget an administrator can see
    // in the limits UI but that nothing enforces is worse than no budget at all.
    int messageCost = countMessagesToWrite(gtNetList, messageCode);
    User user = getAuthenticatedUserOrNull();
    if (user != null) {
      dailyLimitService.check(user, GTNetMessage.class.getSimpleName(), messageCost);
    }

    // Future-oriented messages use background delivery via GTNetMessageAttempt
    if (FUTURE_ORIENTED_MESSAGE_CODES.contains(messageCode)) {
      handleFutureOrientedBroadcast(sourceGTNet, gtNetList, descriptor, msgRequest, messageCode);
    } else if (descriptor.category() == MessageCategory.RESPONSE) {
      // An answer to an open request: no model to validate, and the reply is threaded under that request.
      sendResponseMsg(sourceGTNet, gtNetList, msgRequest, messageCode);
    } else {
      // A request or an announcement. Deciding this by category rather than by the presence of a payload model is what
      // keeps a modelless announcement - the server list revoke, the settings update, the admin message - on the path
      // that runs its outgoing side effects.
      sendAndSaveMsg(sourceGTNet, gtNetList, descriptor, msgRequest, messageCode);
    }

    // Future-oriented broadcasts already store their attempt-owning message under the own entry. Other broadcasts
    // still need a visibility copy because their actual messages are stored under the individual targets.
    if (messageCode.isBroadcast() && !FUTURE_ORIENTED_MESSAGE_CODES.contains(messageCode)) {
      saveBroadcastToOwnEntry(sourceGTNet, msgRequest, messageCode);
    }

    if (user != null) {
      dailyLimitService.log(user.getIdUser(), GTNetMessage.class.getSimpleName(), OperationType.ADD, messageCost);
    }
    return this.getAllGTNetsWithMessages();
  }

  /**
   * Counts the {@link GTNetMessage} rows one {@code submitMsg} request writes. A future-oriented broadcast creates a
   * single message and one delivery attempt per target; every other code writes one message per target. A
   * non-future-oriented broadcast additionally keeps a copy under the own entry for visibility.
   *
   * @param gtNetList   the resolved target domains
   * @param messageCode the code being sent
   * @return how many message rows the request costs, at least one
   */
  private int countMessagesToWrite(List<GTNet> gtNetList, GTNetMessageCode messageCode) {
    int cost = FUTURE_ORIENTED_MESSAGE_CODES.contains(messageCode) ? 1 : gtNetList.size();
    if (messageCode.isBroadcast() && !FUTURE_ORIENTED_MESSAGE_CODES.contains(messageCode)) {
      cost++;
    }
    return Math.max(cost, 1);
  }

  /**
   * Returns the user on the security context, or null when there is none. {@code submitMsg} is also reached from
   * {@code GTNetLifecycleListener} during startup and shutdown, where no request and therefore no user exists; such a
   * caller keeps its present behaviour and consumes no budget.
   *
   * @return the authenticated user, or null for a background caller
   */
  private User getAuthenticatedUserOrNull() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getDetails() instanceof User user ? user : null;
  }

  /**
   * The message visibilities the current caller may see. Reading GTNet is open to every authenticated user, so this is
   * the only thing that keeps an {@code ADMIN_ONLY} thread out of a non-administrator's view. A background caller with
   * no security context is treated as a non-administrator; it never consumes the message list.
   *
   * @return the visibility values to filter messages by, never empty
   */
  private List<Byte> visibilitiesForCaller() {
    return MessageVisibility.visibleTo(isAdminCaller());
  }

  private boolean isAdminCaller() {
    User user = getAuthenticatedUserOrNull();
    return user != null && UserAccessHelper.isAdmin(user);
  }

  /**
   * Handles future-oriented broadcast messages (maintenance, discontinuation, and their cancellations). Creates a
   * single GTNetMessage under the sender's own entry and GTNetMessageAttempt entries for each target. Delivery is
   * handled asynchronously by GTNetFutureMessageDeliveryTask.
   *
   * @param sourceGTNet the local GTNet entry (sender)
   * @param gtNetList   list of target GTNet entries
   * @param descriptor  what the protocol says about this code, including its payload model
   * @param msgRequest  the request containing message parameters
   * @param messageCode the resolved message code enum
   */
  private void handleFutureOrientedBroadcast(GTNet sourceGTNet, List<GTNet> gtNetList,
      GTNetProtocolDescriptor descriptor, MsgRequest msgRequest, GTNetMessageCode messageCode) {
    // Validate model if present
    if (descriptor != null && descriptor.model() != null && msgRequest.gtNetMessageParamMap != null
        && !msgRequest.gtNetMessageParamMap.isEmpty()) {
      Object payloadModel = convertMapToTypedModel(descriptor.model(), msgRequest.gtNetMessageParamMap);
      validateModel(payloadModel);
    }

    // Create ONE message under sender's own entry (not per target)
    GTNetMessage gtNetMessage = new GTNetMessage(sourceGTNet.getIdGtNet(), GTNetTime.now(),
        SendReceivedType.SEND.getValue(), msgRequest.replyTo, messageCode.getValue(), msgRequest.message,
        msgRequest.gtNetMessageParamMap);

    // For cancellation messages, set idOriginalMessage to link to the original announcement
    if (messageCode == GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C
        || messageCode == GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C) {
      gtNetMessage.setIdOriginalMessage(msgRequest.idOriginalMessage);
    }

    gtNetMessage = gtNetMessageJpaRepository.saveMsg(gtNetMessage);
    log.info("Created future-oriented broadcast message {} (code: {})", gtNetMessage.getIdGtNetMessage(), messageCode);

    // Create GTNetMessageAttempt entries for each target with completed handshake
    int attemptsCreated = 0;
    for (GTNet targetGTNet : gtNetList) {
      // Skip our own entry
      if (targetGTNet.getIdGtNet().equals(sourceGTNet.getIdGtNet())) {
        continue;
      }

      // Only create attempts for targets with completed handshake
      if (targetGTNet.getGtNetConfig() != null && targetGTNet.getGtNetConfig().getTokenRemote() != null) {
        GTNetMessageAttempt attempt = new GTNetMessageAttempt(targetGTNet.getIdGtNet(),
            gtNetMessage.getIdGtNetMessage());
        gtNetMessageAttemptJpaRepository.save(attempt);
        attemptsCreated++;
      }
    }

    log.info("Created {} GTNetMessageAttempt entries for message {}", attemptsCreated,
        gtNetMessage.getIdGtNetMessage());

    // Schedule immediate delivery task
    taskDataChangeJpaRepository
        .save(new TaskDataChange(TaskTypeBase.GTNET_FUTURE_MESSAGE_DELIVERY, TaskDataExecPriority.PRIO_NORMAL));
    log.info("Scheduled GTNet future message delivery task for immediate execution");
  }

  /**
   * Saves a copy of a broadcast message under the own server's GTNet entry. This allows administrators to see sent
   * broadcast messages in their own server's message list.
   */
  private void saveBroadcastToOwnEntry(GTNet sourceGTNet, MsgRequest msgRequest, GTNetMessageCode messageCode) {
    GTNetMessage gtNetMessage = new GTNetMessage(sourceGTNet.getIdGtNet(), GTNetTime.now(),
        SendReceivedType.SEND.getValue(), null, messageCode.getValue(), msgRequest.message,
        msgRequest.gtNetMessageParamMap);
    // Set visibility from request (for admin messages)
    applyVisibility(gtNetMessage, msgRequest.visibility);
    gtNetMessageJpaRepository.saveMsg(gtNetMessage);
  }

  /**
   * Rejects a point-to-point message aimed at a peer that has gone out of service. A broadcast needs no check here:
   * {@link #getRemotePeersWithExchange()} already leaves such peers out of the target list.
   *
   * @param msgRequest  the submitted request
   * @param messageCode the resolved message code
   */
  private void rejectMessageToRetiredPeer(MsgRequest msgRequest, GTNetMessageCode messageCode) {
    if (messageCode.isBroadcast() || msgRequest.idGTNetTargetDomain == null) {
      return;
    }
    gtNetJpaRepository.findById(msgRequest.idGTNetTargetDomain).filter(GTNet::isOutOfService).ifPresent(peer -> {
      throw new DataViolationException("g.net", "g.gtnet.peer.out.of.service",
          new Object[] { peer.getDomainRemoteName() });
    });
  }

  /**
   * Rejects an admin message aimed at a peer this instance has never shaken hands with.
   *
   * <p>
   * Sending one used to establish the connection as a side effect, because the outgoing path calls
   * {@code hasOrCreateFirstContact} for every code but the handshake itself. A note to an administrator is not a reason
   * to enter into a relationship with their server. The multi-target path has always skipped such peers; this is the
   * same rule for the single-target one, stated rather than skipped so the sender learns why nothing was sent.
   * </p>
   *
   * @param msgRequest  the submitted request
   * @param messageCode the resolved message code
   */
  private void rejectAdminMessageWithoutHandshake(MsgRequest msgRequest, GTNetMessageCode messageCode) {
    if (messageCode != GNetCoreMessageCode.GT_NET_ADMIN_MESSAGE_SEL_C || msgRequest.idGTNetTargetDomain == null) {
      return;
    }
    gtNetJpaRepository.findById(msgRequest.idGTNetTargetDomain)
        .filter(peer -> peer.getGtNetConfig() == null || peer.getGtNetConfig().getTokenRemote() == null)
        .ifPresent(peer -> {
          throw new DataViolationException("g.net", "g.gtnet.admin.message.no.handshake",
              new Object[] { peer.getDomainRemoteName() });
        });
  }

  /**
   * Rejects a maintenance announcement whose window overlaps one this instance has already announced and not cancelled.
   *
   * @param msgRequest the submitted request, carrying {@code fromDateTime} and {@code toDateTime}
   */
  private void validateMaintenanceWindowFree(MsgRequest msgRequest) {
    LocalDateTime from = MessageParamDateParser.parseDateTime(msgRequest.gtNetMessageParamMap, "fromDateTime");
    LocalDateTime to = MessageParamDateParser.parseDateTime(msgRequest.gtNetMessageParamMap, "toDateTime");
    if (from == null || to == null) {
      // Missing or unreadable bounds are the business of the model validation that runs afterwards.
      return;
    }
    List<Integer> openIds = gtNetMessageJpaRepository.findOpenMaintenanceMessages(SendReceivedType.SEND.getValue(),
        GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C.getValue(),
        GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C.getValue());
    for (Integer openId : openIds) {
      GTNetMessage open = gtNetMessageJpaRepository.findById(openId).orElse(null);
      if (open == null) {
        continue;
      }
      LocalDateTime openFrom = MessageParamDateParser.parseDateTime(open.getGtNetMessageParamMap(), "fromDateTime");
      LocalDateTime openTo = MessageParamDateParser.parseDateTime(open.getGtNetMessageParamMap(), "toDateTime");
      if (openFrom != null && openTo != null && from.isBefore(openTo) && openFrom.isBefore(to)) {
        throw new DataViolationException("message.code", "g.gtnet.maintenance.window.overlap",
            new Object[] { openFrom, openTo });
      }
    }
  }

  private List<GTNet> getTargetDomains(MsgRequest msgRequest, GTNetMessageCode messageCode) {
    if (messageCode.isBroadcast()) {
      // All broadcast messages go to remote servers with configured data exchange (excludes own entry)
      if (messageCode == GNetCoreMessageCode.GT_NET_OFFLINE_ALL_C
          || FUTURE_ORIENTED_MESSAGE_CODES.contains(messageCode)) {
        return getRemotePeersWithExchange();
      }
      return List.of();
    } else {
      List<GTNet> gtNetList = new ArrayList<>();
      gtNetJpaRepository.findById(msgRequest.idGTNetTargetDomain).stream().forEach(n -> gtNetList.add(n));
      return gtNetList;
    }
  }

  /**
   * Returns all GTNet entries with configured data exchange, excluding the local server's own entry. This prevents the
   * server from attempting to send messages to itself, which would fail token validation.
   *
   * @return list of remote GTNet entries with configured exchange
   */
  private List<GTNet> getRemotePeersWithExchange() {
    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    List<GTNet> allPeers = gtNetJpaRepository.findWithConfiguredExchange().stream()
        .filter(peer -> !peer.isOutOfService()).toList();
    if (myEntryId == null) {
      return allPeers;
    }
    return allPeers.stream().filter(peer -> !peer.getIdGtNet().equals(myEntryId)).collect(Collectors.toList());
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
   * @param messageCode     the resolved message code enum
   */
  private void sendAndSaveMsg(GTNet sourceGTNet, List<GTNet> gtNetList, GTNetProtocolDescriptor descriptor,
      MsgRequest msgRequest, GTNetMessageCode messageCode) {
    // Convert map to typed model class for validation and payload serialization
    Object payloadModel = null;
    if (descriptor != null && descriptor.model() != null && msgRequest.gtNetMessageParamMap != null
        && !msgRequest.gtNetMessageParamMap.isEmpty()) {
      payloadModel = convertMapToTypedModel(descriptor.model(), msgRequest.gtNetMessageParamMap);
      validateModel(payloadModel);
    }

    // For handshake and token refresh: generate token server-side and set appropriate payload
    if (messageCode == GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S
        || messageCode == GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_SEL_RR_C) {
      String tokenForRemote = DataHelper.generateGUID();
      msgRequest.gtNetMessageParamMap = convertPojoToMap(new FirstHandshakeMsg(tokenForRemote));
      if (messageCode == GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S) {
        payloadModel = sourceGTNet;
      } else {
        // The answerer adopts this token the moment it handles the request and starts calling us with it, while we
        // would otherwise only adopt it when the response arrives. Installing it now, with the token it replaces kept
        // in the overlap window, means neither direction breaks if that response is lost.
        installRefreshedTokenOptimistically(gtNetList, tokenForRemote);
      }
    }

    for (GTNet targetGTNet : gtNetList) {
      GTNetMessage gtNetMessage = new GTNetMessage(targetGTNet.getIdGtNet(), GTNetTime.now(),
          SendReceivedType.SEND.getValue(), msgRequest.replyTo, messageCode.getValue(), msgRequest.message,
          msgRequest.gtNetMessageParamMap);
      // Set visibility from request (for admin messages)
      applyVisibility(gtNetMessage, msgRequest.visibility);
      gtNetMessage = gtNetMessageJpaRepository.saveMsg(gtNetMessage);
      SendResult sendResult = sendMessageWithResult(sourceGTNet, targetGTNet, gtNetMessage, payloadModel);
      MessageEnvelope meResponse = sendResult != null ? sendResult.response() : null;

      // Update deliveryStatus based on send result
      updateDeliveryStatus(gtNetMessage, sendResult);

      // A synchronous body closes the request only when it is a registered answer to it. An acknowledgement, a
      // deferred acknowledgement and an error are outcomes of the transport: filing one as the reply would satisfy
      // the NOT EXISTS clause of GTNetMessage.findUnansweredRequests, drop the request out of the pending map the
      // reply gate reads, lift the delete protection, and leave the real decision arriving later as a second child.
      if (descriptor != null && !descriptor.validResponses().isEmpty() && meResponse != null
          && messageCodeRegistry.isValidResponse(messageCode.getValue(), meResponse.messageCode)) {
        // Save received response with idSourceGtNetMessage from remote and replyTo pointing to our request
        GTNetMessage responseMsg = new GTNetMessage(targetGTNet.getIdGtNet(), meResponse.timestamp,
            SendReceivedType.RECEIVED.getValue(), gtNetMessage.getIdGtNetMessage(), meResponse.messageCode,
            meResponse.message, meResponse.gtNetMessageParamMap);
        responseMsg.setIdSourceGtNetMessage(meResponse.idSourceGtNetMessage);
        responseMsg.setErrorMsgCode(meResponse.errorMsgCode);
        // Store waitDaysApply from the response envelope (cooling-off period set by remote admin)
        if (meResponse.waitDaysApply != null) {
          responseMsg.setWaitDaysApply(meResponse.waitDaysApply);
        }
        gtNetMessageJpaRepository.saveMsg(responseMsg);

        // Process payload from synchronous responses that contain data
        processSynchronousResponsePayload(sourceGTNet, meResponse, targetGTNet, gtNetMessage);
      } else if (meResponse != null && meResponse.errorMsgCode != null) {
        // The reason the peer gave is kept against the message we sent, so a refusal is visible without a reply row.
        gtNetMessage.setErrorMsgCode(meResponse.errorMsgCode);
        gtNetMessageJpaRepository.saveMsg(gtNetMessage);
      }

      // Apply side effects for outgoing announcement messages
      applyOutgoingSideEffects(targetGTNet, msgRequest, messageCode);
    }
  }

  /**
   * Updates the deliveryStatus on a message based on the send result.
   */
  private void updateDeliveryStatus(GTNetMessage message, SendResult sendResult) {
    if (sendResult == null || sendResult.isFailed() || sendResult.isRefused()) {
      // A refusal reached the peer but was not processed by it, so it is a failed delivery and not a delivered one.
      message.setDeliveryStatus(DeliveryStatus.FAILED);
    } else if (sendResult.isAccepted()) {
      message.setDeliveryStatus(DeliveryStatus.DELIVERED);
    }
    // PENDING status remains if result is unclear (e.g., awaiting retry)
    gtNetMessageJpaRepository.save(message);
  }

  /**
   * Processes payloads from synchronous responses that contain data requiring local processing. This method handles
   * responses that would normally be processed by response handlers when received asynchronously via the M2M endpoint,
   * but need special handling when received as immediate HTTP responses.
   *
   * @param myGTNet      the local GTNet entry
   * @param meResponse   the response envelope containing the payload
   * @param targetGTNet  the remote GTNet entry that sent the response
   * @param gtNetMessage the original request message we sent
   */
  private void processSynchronousResponsePayload(GTNet myGTNet, MessageEnvelope meResponse, GTNet targetGTNet,
      GTNetMessage gtNetMessage) {
    GTNetMessageCode responseCode = messageCodeRegistry.getByValue(meResponse.messageCode);

    if (responseCode == GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S) {
      if (meResponse.payload != null && !meResponse.payload.isNull()) {
        processServerListPayload(myGTNet, meResponse);
      }
    } else if (responseCode == GNetCoreMessageCode.GT_NET_DATA_REQUEST_ACCEPT_S) {
      // When they accept our data request, we will RECEIVE data from them
      Set<IExchangeKindType> acceptedKinds = parseEntityKinds(gtNetMessage.getGtNetMessageParamMap());
      for (IExchangeKindType kind : acceptedKinds) {
        updateEntityForReceive(targetGTNet, kind);
      }
      gtNetJpaRepository.save(targetGTNet);
      log.info("Created GTNetConfigEntity with RECEIVE capability for {} entity kinds from {}", acceptedKinds.size(),
          targetGTNet.getDomainRemoteName());
    } else if (responseCode == GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_ACCEPT_S) {
      // User-initiated handshake accepted: store tokens in GTNetConfig
      processHandshakeAcceptResponse(meResponse, targetGTNet, gtNetMessage);
    } else if (responseCode == GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_ACCEPT_S) {
      // Token refresh accepted: update tokens in GTNetConfig
      processTokenRefreshAcceptResponse(meResponse, targetGTNet, gtNetMessage);
    }
  }

  /**
   * Processes a handshake accept response from a user-initiated handshake. Creates GTNetConfig with both tokens.
   */
  private void processHandshakeAcceptResponse(MessageEnvelope meResponse, GTNet targetGTNet, GTNetMessage ourRequest) {
    FirstHandshakeMsg responseMsgData = convertMapToPojo(FirstHandshakeMsg.class, meResponse.gtNetMessageParamMap);
    // Extract our token from the original request
    FirstHandshakeMsg ourRequestData = convertMapToPojo(FirstHandshakeMsg.class, ourRequest.getGtNetMessageParamMap());

    GTNetConfig gtNetConfig = targetGTNet.getGtNetConfig();
    if (gtNetConfig == null) {
      gtNetConfig = new GTNetConfig();
      gtNetConfig.setIdGtNet(targetGTNet.getIdGtNet());
    }
    gtNetConfig.setTokenRemote(responseMsgData.tokenThis);
    gtNetConfig.setTokenThis(ourRequestData.tokenThis);
    gtNetConfig.setHandshakeTimestamp(GTNetTime.now());
    gtNetConfigJpaRepository.save(gtNetConfig);
    targetGTNet.setGtNetConfig(gtNetConfig);

    // Process remote GTNet entity from payload (contains their entity kinds and settings)
    if (meResponse.payload != null && !meResponse.payload.isNull()) {
      try {
        GTNetPublicDTO remoteGTNetInfo = objectMapper.treeToValue(meResponse.payload, GTNetPublicDTO.class);
        if (remoteGTNetInfo.getGtNetEntities() != null) {
          for (var remoteEntity : remoteGTNetInfo.getGtNetEntities()) {
            var localEntity = targetGTNet.getOrCreateEntityByKind(remoteEntity.getEntityKind());
            localEntity.setAcceptRequest(remoteEntity.getAcceptRequest());
            localEntity.setServerState(remoteEntity.getServerState());
          }
          gtNetJpaRepository.save(targetGTNet);
        }
      } catch (Exception e) {
        log.warn("Failed to process remote GTNet payload from handshake accept: {}", e.getMessage());
      }
    }
    log.info("Handshake accepted by {}, tokens stored", targetGTNet.getDomainRemoteName());
  }

  /**
   * Installs the token we are about to offer as our own inbound token before the request leaves.
   *
   * <p>
   * A token refresh has no moment at which both peers agree. The answerer commits our new token while it handles the
   * request and calls us with it from then on; if we waited for its response we would reject exactly those calls when
   * the response was lost. Rotating now instead keeps the token we are replacing acceptable for the overlap window, so
   * both the answerer's new-token calls and our own old-token calls succeed until one reply closes the rotation.
   * </p>
   *
   * @param gtNetList      the targets of this refresh, in practice a single peer
   * @param tokenForRemote the token the peer is to use against us from now on
   */
  private void installRefreshedTokenOptimistically(List<GTNet> gtNetList, String tokenForRemote) {
    for (GTNet targetGTNet : gtNetList) {
      gtNetConfigJpaRepository.findById(targetGTNet.getIdGtNet()).ifPresent(gtNetConfig -> {
        tokenRotationService.rotateTokenThis(gtNetConfig, tokenForRemote);
        gtNetConfigJpaRepository.save(gtNetConfig);
        targetGTNet.setGtNetConfig(gtNetConfig);
      });
    }
  }

  /**
   * Processes a token refresh accept response. The peer's new token is what we did not know yet; our own was already
   * installed before the request went out, and re-writing it here is an idempotent confirmation.
   *
   * <p>
   * The overlap is deliberately not cleared here. Messages the peer sent before it learned the new token may still be
   * in flight, and ending the window on the first reply would reject them. It expires on its own.
   * </p>
   */
  private void processTokenRefreshAcceptResponse(MessageEnvelope meResponse, GTNet targetGTNet,
      GTNetMessage ourRequest) {
    FirstHandshakeMsg responseMsgData = convertMapToPojo(FirstHandshakeMsg.class, meResponse.gtNetMessageParamMap);
    FirstHandshakeMsg ourRequestData = convertMapToPojo(FirstHandshakeMsg.class, ourRequest.getGtNetMessageParamMap());

    GTNetConfig gtNetConfig = gtNetConfigJpaRepository.findById(targetGTNet.getIdGtNet()).orElseThrow();
    gtNetConfig.setTokenRemote(responseMsgData.tokenThis);
    gtNetConfig.setTokenThis(ourRequestData.tokenThis);
    gtNetConfigJpaRepository.save(gtNetConfig);
    targetGTNet.setGtNetConfig(gtNetConfig);
    log.info("Token refresh accepted by {}, tokens updated", targetGTNet.getDomainRemoteName());
  }

  /**
   * Updates a GTNetEntity to add RECEIVE capability for the specified entity kind. When they accept our request, we
   * will RECEIVE data from them.
   */
  private void updateEntityForReceive(GTNet remoteGTNet, IExchangeKindType kind) {
    GTNetEntity entity = remoteGTNet.getOrCreateEntityByKind(kind.getValue());
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);

    if (entity.getGtNetConfigEntity() == null) {
      entity.setGtNetConfigEntity(new GTNetConfigEntity());
    }
    // Config entity defaults to exchange=true, no need to set explicitly
  }

  /**
   * Processes a server list payload from a serverlist accept response. Creates or updates GTNet entries based on the
   * received server list, respecting the allowServerCreation flag on our own GTNet entry.
   *
   * @param myGTNet    the local GTNet entry
   * @param meResponse the response envelope containing the server list payload
   */
  private void processServerListPayload(GTNet myGTNet, MessageEnvelope meResponse) {
    try {
      List<GTNetPublicDTO> serverList = objectMapper.convertValue(meResponse.payload,
          new TypeReference<List<GTNetPublicDTO>>() {
          });

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

      log.info("Processed synchronous server list response: {} new servers added, {} servers updated", newServers,
          updatedServers);

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
   * Applies side effects for outgoing announcement messages (like revokes). When we send a revoke, we disable exchange
   * for the specified entity kinds, or withdraw the server-list access we had granted.
   */
  private void applyOutgoingSideEffects(GTNet targetGTNet, MsgRequest msgRequest, GTNetMessageCode messageCode) {
    if (messageCode == GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_REVOKE_SEL_C) {
      // Sending this used to clear nothing on our side, so UpdateServerlistRequestHandler.checkPriorApproval kept
      // auto-accepting the peer's next request against a grant we had just told it we were withdrawing.
      GTNetConfig config = targetGTNet.getGtNetConfig();
      if (config != null && config.isServerlistAccessGranted()) {
        config.setServerlistAccessGranted(false);
        gtNetConfigJpaRepository.save(config);
        log.info("Withdrew server list access from {}", targetGTNet.getDomainRemoteName());
      }
      return;
    }
    if (messageCode == GNetCoreMessageCode.GT_NET_DATA_REVOKE_SEL_C) {
      Set<IExchangeKindType> revokedKinds = parseEntityKinds(msgRequest.gtNetMessageParamMap);
      for (IExchangeKindType kind : revokedKinds) {
        targetGTNet.getEntityByKind(kind.getValue()).ifPresent(entity -> {
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
   * Parses the entity kinds a stored message names.
   *
   * <p>
   * An unresolvable set yields the empty set, never a default. Substituting the syncable kinds is how a request for one
   * kind used to grant both, in both directions; a message whose kinds cannot be read has to fail rather than be
   * guessed at.
   * </p>
   *
   * @param paramMap the parameter map of a locally stored message
   * @return the kinds it names, possibly empty
   */
  private Set<IExchangeKindType> parseEntityKinds(Map<String, GTNetMessage.GTNetMessageParam> paramMap) {
    if (paramMap == null) {
      return Set.of();
    }
    GTNetMessage.GTNetMessageParam param = paramMap.get(ExchangeKindTypeRegistry.ENTITY_KINDS_PARAM);
    return param == null ? Set.of() : exchangeKindTypeRegistry.parseAll(param.getParamValue());
  }

  /**
   * Sends a response message to the original requester. Response messages don't need model validation as they are
   * simple acknowledgments with an optional message text. The replyTo field links the response to the original request
   * message.
   *
   * @param sourceGTNet the local GTNet entry
   * @param gtNetList   list of target GTNet entries (typically just one - the original requester)
   * @param msgRequest  the request containing the response message code, replyTo, optional message, and waitDaysApply
   * @param messageCode the resolved message code enum
   */
  private void sendResponseMsg(GTNet sourceGTNet, List<GTNet> gtNetList, MsgRequest msgRequest,
      GTNetMessageCode messageCode) {
    // Look up the original request message to get the requester's original message ID
    Integer replyToSourceId = null;
    if (msgRequest.replyTo != null) {
      GTNetMessage originalRequest = gtNetMessageJpaRepository.findById(msgRequest.replyTo).orElse(null);
      if (originalRequest != null) {
        replyToSourceId = originalRequest.getIdSourceGtNetMessage();
      }
    }

    for (GTNet targetGTNet : gtNetList) {
      GTNetMessage gtNetMessage = new GTNetMessage(targetGTNet.getIdGtNet(), GTNetTime.now(),
          SendReceivedType.SEND.getValue(), msgRequest.replyTo, messageCode.getValue(), msgRequest.message,
          msgRequest.gtNetMessageParamMap);
      // Set waitDaysApply if provided by admin
      if (msgRequest.waitDaysApply != null) {
        gtNetMessage.setWaitDaysApply(msgRequest.waitDaysApply);
      }
      // Set visibility from request (for admin messages)
      applyVisibility(gtNetMessage, msgRequest.visibility);
      gtNetMessage = gtNetMessageJpaRepository.saveMsg(gtNetMessage);

      // Send the response with replyToSourceId so the receiver can link it to their original request
      // Include payload for specific response codes
      Object payload = buildManualResponsePayload(sourceGTNet, targetGTNet, messageCode);
      MessageEnvelope peerAnswer = sendResponseMessage(sourceGTNet, targetGTNet, gtNetMessage, replyToSourceId,
          payload);

      // A grant is only real once the peer holds the answer that created it. Applying the side effects first left this
      // side believing an exchange had been agreed while the other side never received the response - and because the
      // grant was committed before the send, it survived even when nothing was delivered at all.
      if (peerAnswer != null) {
        applyManualResponseSideEffects(sourceGTNet, targetGTNet, msgRequest, messageCode);
      }
    }
  }

  /**
   * Applies side effects for manual responses that require state changes.
   */
  private void applyManualResponseSideEffects(GTNet sourceGTNet, GTNet targetGTNet, MsgRequest msgRequest,
      GTNetMessageCode messageCode) {
    GTNetMessageCode responseCode = messageCode;

    if (responseCode == GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S) {
      // Grant server list access to this remote
      GTNetConfig config = targetGTNet.getGtNetConfig();
      if (config != null && !config.isServerlistAccessGranted()) {
        config.setServerlistAccessGranted(true);
        gtNetConfigJpaRepository.save(config);
        log.info("Granted server list access to {} via manual response", targetGTNet.getDomainRemoteName());
      }
    } else if (responseCode == GNetCoreMessageCode.GT_NET_DATA_REQUEST_ACCEPT_S) {
      // When we accept their data request, we will SEND data to them
      // Get the entityKinds from the original request message
      Set<IExchangeKindType> acceptedKinds = getEntityKindsFromOriginalRequest(msgRequest.replyTo);

      // Step 1: Create/update GTNetEntity without config entities to get IDs
      for (IExchangeKindType kind : acceptedKinds) {
        GTNetEntity entity = targetGTNet.getOrCreateEntityByKind(kind.getValue());
        entity.setServerState(GTNetServerStateTypes.SS_OPEN);
      }
      targetGTNet = gtNetJpaRepository.save(targetGTNet);

      // Step 2: Now add GTNetConfigEntity with proper IDs
      for (IExchangeKindType kind : acceptedKinds) {
        GTNetEntity entity = targetGTNet.getEntityByKind(kind.getValue()).orElse(null);
        if (entity != null && entity.getGtNetConfigEntity() == null) {
          entity.setGtNetConfigEntity(new GTNetConfigEntity());
        }
      }
      gtNetJpaRepository.save(targetGTNet);
      log.info("Created GTNetConfigEntity with SEND capability for {} entity kinds to {}", acceptedKinds.size(),
          targetGTNet.getDomainRemoteName());

      // Also update sourceGTNet (myGTNet) to reflect that this server offers these entity kinds.
      // This ensures the serverState is correctly communicated to remote servers via MessageEnvelope.
      for (IExchangeKindType kind : acceptedKinds) {
        updateMyEntityForAccept(sourceGTNet, kind);
      }
      gtNetJpaRepository.save(sourceGTNet);
    } else if (responseCode == GNetCoreMessageCode.GT_NET_DATA_REQUEST_REJECTED_S) {
      // The auto-reject path has always ended the exchange; a rejection an administrator sends by hand persisted only
      // waitDaysApply, so the two ways of saying no left the two sides in different states. Only the grant is written,
      // for the same reason as on the auto path: acceptRequest and serverState on the peer's row are re-synchronised
      // from what the peer publishes about itself and would not survive its next message.
      Set<IExchangeKindType> rejectedKinds = getEntityKindsFromOriginalRequest(msgRequest.replyTo);
      boolean changed = false;
      for (IExchangeKindType kind : rejectedKinds) {
        changed |= grantService.clearGrant(targetGTNet, kind);
      }
      if (changed) {
        gtNetJpaRepository.save(targetGTNet);
        log.info("Ended the exchange grant for {} entity kinds with {} via manual rejection", rejectedKinds.size(),
            targetGTNet.getDomainRemoteName());
      }
    }
  }

  /**
   * The entity kinds the request being answered named.
   *
   * <p>
   * The kinds are read from the locally stored request, whose parameters this instance itself normalized from the
   * authoritative payload when the request arrived. A request that cannot be found yields no kinds, so an answer to a
   * message that is gone grants nothing.
   * </p>
   *
   * @param replyToMessageId the local id of the request being answered
   * @return the kinds it named, possibly empty
   */
  private Set<IExchangeKindType> getEntityKindsFromOriginalRequest(Integer replyToMessageId) {
    if (replyToMessageId == null) {
      return Set.of();
    }
    return gtNetMessageJpaRepository.findById(replyToMessageId).map(GTNetMessage::getGtNetMessageParamMap)
        .map(this::parseEntityKinds).orElse(Set.of());
  }

  /**
   * Updates myGTNet's entity to reflect that this server offers the specified entity kind. This ensures the serverState
   * is correctly communicated to remote servers via MessageEnvelope. Only sets acceptRequest to AC_OPEN if currently
   * closed - preserves AC_PUSH_OPEN if already set.
   */
  private void updateMyEntityForAccept(GTNet myGTNet, IExchangeKindType kind) {
    GTNetEntity entity = myGTNet.getOrCreateEntityByKind(kind.getValue());
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
  private Object buildManualResponsePayload(GTNet sourceGTNet, GTNet targetGTNet, GTNetMessageCode responseCode) {
    if (responseCode == GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S) {
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
      SendResult result = baseDataClient.sendToMsgWithStatus(tokenRemote, targetGTNet.getDomainRemoteName(), meRequest,
          GTNetTimeoutHelper.resolveTimeout(targetGTNet, globalparametersJpaRepository));

      // Update deliveryStatus on the message
      updateDeliveryStatus(gtNetMessage, result);

      // Update target server's status based on response
      if (result.isDelivered()) {
        updateRemoteGTNetFromEnvelope(targetGTNet, result.response());
      } else if (!result.serverReachable()) {
        if (targetGTNet.getServerOnline() != GTNetServerOnlineStatusTypes.SOS_OFFLINE) {
          targetGTNet.setServerOnline(GTNetServerOnlineStatusTypes.SOS_OFFLINE);
          gtNetJpaRepository.save(targetGTNet);
        }
      }
      // HTTP errors: server is reachable but returned error - don't change online status

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
          .saveMsg(new GTNetMessage(targetGTNet.getIdGtNet(), GTNetTime.now(), SendReceivedType.SEND.getValue(), null,
              GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S.getValue(), null, msgMap));
      // Send what we publish about ourselves, not our entity: the receiver builds its row from these fields, and the
      // entity carries local state such as allowServerCreation that a peer has no business seeing or setting.
      SendResult sendResult = sendMessageWithResult(sourceGTNet, targetGTNet, gtNetMessageRequest,
          new GTNetPublicDTO(sourceGTNet));
      MessageEnvelope meResponse = sendResult != null ? sendResult.response() : null;

      // Update deliveryStatus based on send result
      updateDeliveryStatus(gtNetMessageRequest, sendResult);

      if (meResponse != null
          && meResponse.messageCode == GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_ACCEPT_S.getValue()) {
        // Extract the token they gave us from their response
        FirstHandshakeMsg responseMsgData = convertMapToPojo(FirstHandshakeMsg.class, meResponse.gtNetMessageParamMap);
        // Create GTNetConfig to store the tokens
        GTNetConfig gtNetConfig = new GTNetConfig();
        gtNetConfig.setIdGtNet(targetGTNet.getIdGtNet()); // Set FK manually
        // Store their token (what we use to call them) as tokenRemote
        gtNetConfig.setTokenRemote(responseMsgData.tokenThis);
        // Store our token (what they use to call us) as tokenThis
        gtNetConfig.setTokenThis(tokenForRemote);
        gtNetConfigJpaRepository.save(gtNetConfig); // Save config separately
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
        gtNetMessageJpaRepository.saveMsg(gtNetMessageResponse);
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
   * <li>Comma-separated strings → Set&lt;Enum&gt; (e.g., "LAST_PRICE,HISTORICAL_PRICES" →
   * Set&lt;GTNetExchangeKindType&gt;)</li>
   * <li>ISO-8601 strings → LocalDateTime</li>
   * <li>Simple string fields</li>
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
          // Handle Set<Enum> or Set<? extends Interface> - convert comma-separated string
          ParameterizedType paramType = (ParameterizedType) field.getGenericType();
          Type typeArg = paramType.getActualTypeArguments()[0];
          Class<?> elementClass = resolveTypeArg(typeArg);
          if (elementClass != null && elementClass.isEnum()) {
            Set enumSet = EnumSet.noneOf((Class<Enum>) elementClass);
            for (String enumName : value.split(",")) {
              String trimmed = enumName.trim();
              if (!trimmed.isEmpty()) {
                enumSet.add(Enum.valueOf((Class<Enum>) elementClass, trimmed));
              }
            }
            convertedValue = enumSet;
          } else if (elementClass != null && elementClass.isInterface()
              && IExchangeKindType.class.isAssignableFrom(elementClass)) {
            // Interface bound (e.g., Set<? extends IExchangeKindType>) - resolve via registry
            Set<IExchangeKindType> kindSet = new java.util.LinkedHashSet<>();
            for (String enumName : value.split(",")) {
              String trimmed = enumName.trim();
              if (!trimmed.isEmpty()) {
                IExchangeKindType kind = exchangeKindTypeRegistry.getByName(trimmed);
                if (kind != null) {
                  kindSet.add(kind);
                }
              }
            }
            convertedValue = kindSet;
          } else {
            continue;
          }
        } else if (LocalDateTime.class.isAssignableFrom(field.getType())) {
          // Handle LocalDateTime - parse ISO-8601 string (may include 'Z' timezone)
          if (value.endsWith("Z")) {
            // ISO-8601 with UTC timezone indicator (e.g., "2026-01-13T14:24:00.000Z")
            convertedValue = LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
          } else {
            // Plain LocalDateTime format (e.g., "2026-01-13T14:24:00")
            convertedValue = LocalDateTime.parse(value);
          }
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
      throw new IllegalArgumentException("Failed to convert map to " + clazz.getSimpleName() + ": " + e.getMessage(),
          e);
    }
  }

  /**
   * Resolves a generic type argument to a concrete Class, handling WildcardType bounds.
   */
  private Class<?> resolveTypeArg(Type typeArg) {
    if (typeArg instanceof Class) {
      return (Class<?>) typeArg;
    }
    if (typeArg instanceof WildcardType) {
      Type[] upperBounds = ((WildcardType) typeArg).getUpperBounds();
      if (upperBounds.length == 1 && upperBounds[0] instanceof Class) {
        return (Class<?>) upperBounds[0];
      }
    }
    return null;
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

  /**
   * Applies visibility from a string enum name to a GTNetMessage.
   *
   * <p>
   * A value that cannot be parsed is refused. Falling back to the entity default meant that a typo published a message
   * meant to be private, and it did so silently — the only trace was a warning in the log, long after the message had
   * been sent and was visible to every user.
   * </p>
   *
   * @param message    the message to update
   * @param visibility the visibility as enum name string (e.g., "ADMIN_ONLY"), or null to keep the default
   * @throws DataViolationException when the value names no known visibility
   */
  private void applyVisibility(GTNetMessage message, String visibility) {
    if (visibility != null && !visibility.isBlank()) {
      try {
        message.setVisibility(MessageVisibility.valueOf(visibility));
      } catch (IllegalArgumentException e) {
        throw new DataViolationException("visibility", "g.gtnet.invalid.visibility", new Object[] { visibility });
      }
    }
  }

  private SendResult sendMessageWithResult(GTNet sourceGTNet, GTNet targetGTNet, GTNetMessage gtNetMessage,
      Object payLoadObject) {
    if (gtNetMessage.getMessageCodeValue() != GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S.getValue()
        ? hasOrCreateFirstContact(sourceGTNet, targetGTNet)
        : true) {
      return sendMessageWithStatusUpdateResult(sourceGTNet, targetGTNet, gtNetMessage, payLoadObject);
    }
    return null;
  }

  /**
   * Sends a message to a remote GTNet server and returns the full SendResult.
   *
   * @param sourceGTNet   the local GTNet entry (provides serverBusy flag for outgoing envelope)
   * @param targetGTNet   the remote GTNet entry to send to (will be updated with online/busy status)
   * @param gtNetMessage  the message to send
   * @param payLoadObject optional payload object to include
   * @return the SendResult containing delivery status, response, and error info
   */
  private SendResult sendMessageWithStatusUpdateResult(GTNet sourceGTNet, GTNet targetGTNet, GTNetMessage gtNetMessage,
      Object payLoadObject) {
    MessageEnvelope meRequest = new MessageEnvelope(sourceGTNet, gtNetMessage);
    if (payLoadObject != null) {
      meRequest.payload = objectMapper.convertValue(payLoadObject, JsonNode.class);
    }

    if (!requestBudgetService.chargeOutgoing(targetGTNet, gtNetMessage.getMessageCodeValue())) {
      return SendResult.httpError(HttpStatus.TOO_MANY_REQUESTS.value(),
          "Daily request limit of " + targetGTNet.getDomainRemoteName() + " is used up for today");
    }

    String tokenRemote = targetGTNet.getGtNetConfig() != null ? targetGTNet.getGtNetConfig().getTokenRemote() : null;
    SendResult result = baseDataClient.sendToMsgWithStatus(tokenRemote, targetGTNet.getDomainRemoteName(), meRequest,
        GTNetTimeoutHelper.resolveTimeout(targetGTNet, globalparametersJpaRepository));

    // Update target server's status based on response
    if (result.isDelivered()) {
      updateRemoteGTNetFromEnvelope(targetGTNet, result.response());
    } else if (!result.serverReachable()) {
      // Server unreachable - update online status
      if (targetGTNet.getServerOnline() != GTNetServerOnlineStatusTypes.SOS_OFFLINE) {
        targetGTNet.setServerOnline(GTNetServerOnlineStatusTypes.SOS_OFFLINE);
        gtNetJpaRepository.save(targetGTNet);
      }
    }
    // Note: HTTP errors (result.httpError()) mean server is reachable but returned error
    // We don't change online status for HTTP errors - server is technically online

    return result;
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
        .getReferenceById(GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository));

    GTNetMessageCode messageCode = messageCodeRegistry.getByValue(me.messageCode);
    if (messageCode == null) {
      log.warn("Unknown message code received: {}", me.messageCode);
      return buildErrorResponse(myGTNet, "UNKNOWN_MESSAGE_CODE", "Unknown message code: " + me.messageCode);
    }

    // Check if handler is registered
    if (!handlerRegistry.hasHandler(me.messageCode)) {
      log.warn("No handler registered for message code: {}", messageCode);
      return buildErrorResponse(myGTNet, "NO_HANDLER", "No handler for message code: " + messageCode);
    }

    // Look up remote GTNet (may be null for first handshake)
    GTNet remoteGTNet = findPeerByDomain(me.sourceDomain);

    // The envelope is bounded before anything is synchronized, charged or stored, so an over-long text, an over-sized
    // payload, an implausible timestamp or a correlation that points at another peer is a protocol rejection the
    // sender can read rather than a DataException and an HTTP 500 out of the persistence layer.
    var envelopeViolation = envelopeValidator.validate(me, remoteGTNet);
    if (envelopeViolation.isPresent()) {
      var violation = envelopeViolation.get();
      log.warn("Rejected envelope of code {} from peer {}: {}", me.messageCode,
          remoteGTNet != null ? remoteGTNet.getIdGtNet() : null, violation.errorMsgCode());
      return buildErrorResponse(myGTNet, violation.errorMsgCode(), violation.reason());
    }

    // Synchronize the peer's published state only for a message whose token was validated. The first handshake is the
    // one code that reaches this point unauthenticated, and anybody may name an existing domain in it, so its envelope
    // must not be allowed to rewrite that peer's online state, capabilities, limits or entity settings.
    if (remoteGTNet != null && !isUnauthenticatedCode(me.messageCode)) {
      updateRemoteGTNetFromEnvelope(remoteGTNet, me);
    }

    // A delivery we have already processed is answered from what the first one produced. It comes before the
    // cooling-off check and the budget charge on purpose: a repeat must repeat no side effect and no charge, and it
    // must not be judged against a cooling-off period that the original processing itself established. The status
    // synchronization above stays in front of it, because it is idempotent and recording that the peer is reachable is
    // correct for a repeat too.
    boolean duplicateDelivery = false;
    if (!isUnauthenticatedCode(me.messageCode)) {
      var previousDelivery = idempotencyService.findPreviousDelivery(remoteGTNet, me);
      if (previousDelivery.isPresent()) {
        duplicateDelivery = true;
        if (!idempotencyService.prefersReprocessing(me.messageCode)) {
          log.info("Message {} from {} was already delivered, answering from the stored outcome", messageCode,
              me.sourceDomain);
          return addServerBusyToResponse(
              idempotencyService.replayOutcome(myGTNet, previousDelivery.get(), me.messageCode), myGTNet);
        }
        log.info("Message {} from {} was already delivered, re-running it as a side-effect-free query", messageCode,
            me.sourceDomain);
      }
    }

    var coolingOffPeriod = coolingOffService.findActive(remoteGTNet, me.messageCode);
    if (coolingOffPeriod.isPresent()) {
      var period = coolingOffPeriod.get();
      GTNetMessage refusal = new GTNetMessage(remoteGTNet.getIdGtNet(), GTNetTime.now(),
          SendReceivedType.ANSWER.getValue(), null, period.responseCode().getValue(),
          "Cooling-off period active: " + period.remainingDays() + " day(s) remaining", null);
      refusal.setErrorMsgCode("COOLING_OFF_ACTIVE");
      return new MessageEnvelope(myGTNet, refusal);
    }

    // Charge the request against the daily budget we grant this peer, before the rules are evaluated so that an
    // auto-answer rule reading dailyCount sees the request it is deciding about. A repeat of a delivery already
    // charged is not charged again, whether it is replayed or re-run.
    if (!duplicateDelivery && !requestBudgetService.chargeIncoming(remoteGTNet, myGTNet, me.messageCode)) {
      GTNetMessage refusal = new GTNetMessage(remoteGTNet.getIdGtNet(), GTNetTime.now(),
          SendReceivedType.ANSWER.getValue(), null,
          GNetCoreMessageCode.GT_NET_DAILY_REQUEST_LIMIT_EXCEEDED_S.getValue(),
          "Daily request limit of " + myGTNet.getDailyRequestLimit() + " reached", null);
      refusal.setErrorMsgCode("DAILY_LIMIT_EXCEEDED");
      return new MessageEnvelope(myGTNet, refusal);
    }

    // Look up auto-response rules for this message code, ordered by priority
    List<GTNetMessageAnswer> autoResponseRules = gtNetMessageAnswerJpaRepository
        .findByRequestMsgCodeOrderByPriority(me.messageCode);

    // Build context for the handler
    GTNetMessageContext context = new GTNetMessageContext(myGTNet, remoteGTNet, me, autoResponseRules, objectMapper);

    // Get the handler and process the message
    GTNetMessageHandler handler = handlerRegistry.getHandler(me.messageCode);
    HandlerResult<GTNetMessage, MessageEnvelope> result = handler.handle(context);

    // Process the result and include our serverBusy status in responses
    return switch (result) {
    case HandlerResult.ImmediateResponse(var response) -> addServerBusyToResponse((MessageEnvelope) response, myGTNet);
    case HandlerResult.AwaitingManualResponse(var _) -> {
      log.info("Message {} from {} awaiting manual response", messageCode, me.sourceDomain);
      yield buildDeferredResponse(myGTNet);
    }
    case HandlerResult.NoResponseNeeded() -> buildAckResponse(myGTNet);
    case HandlerResult.ProcessingError(var errorCode, var message) -> {
      log.error("Error processing message {}: {} - {}", messageCode, errorCode, message);
      yield buildErrorResponse(myGTNet, errorCode, message);
    }
    };
  }

  /**
   * Updates the local GTNet entry for a remote server based on the sourceGtNet in the received envelope. Synchronizes
   * status flags, timezone, capabilities, and entity exchange settings.
   *
   * @param localRemoteEntry the local GTNet entry representing the remote server (to be updated)
   * @param envelope         the received message envelope containing sourceGtNet
   */
  private void updateRemoteGTNetFromEnvelope(GTNet localRemoteEntry, MessageEnvelope envelope) {
    boolean needsSave = false;

    if (localRemoteEntry.isOutOfService()) {
      // The peer announced that it is gone and the date has passed. It evidently still answers, but reviving it here
      // would silently undo the retirement on the peer's very next message. Only a cancellation of the announcement,
      // or an administrator, brings it back.
      return;
    }

    // Server communicated with us, so it's online - except when the message it just sent is the announcement that it
    // is going offline. Forcing SOS_ONLINE there overwrote the very transition being announced, so a graceful shutdown
    // left every peer recording the instance as online with all entities closed until an outbound send failed.
    if (envelope.messageCode != GNetCoreMessageCode.GT_NET_OFFLINE_ALL_C.getValue()
        && localRemoteEntry.getServerOnline() != GTNetServerOnlineStatusTypes.SOS_ONLINE) {
      localRemoteEntry.setServerOnline(GTNetServerOnlineStatusTypes.SOS_ONLINE);
      needsSave = true;
    }

    // Update busy status from envelope and adjust entity serverState accordingly
    if (localRemoteEntry.isServerBusy() != envelope.serverBusy) {
      localRemoteEntry.setServerBusy(envelope.serverBusy);
      needsSave = true;

      // Update serverState on all entities based on new busy status
      if (envelope.serverBusy) {
        // Server is now busy - mark all entities as CLOSED
        localRemoteEntry.getGtNetEntities().forEach(entity -> entity.setServerState(GTNetServerStateTypes.SS_CLOSED));
      } else {
        // Server is no longer busy - restore OPEN state for accepting entities
        localRemoteEntry.getGtNetEntities().stream().filter(entity -> entity.isAccepting())
            .forEach(entity -> entity.setServerState(GTNetServerStateTypes.SS_OPEN));
      }
    }

    // Sync additional fields from sourceGtNet DTO if present
    GTNetPublicDTO sourceGtNet = envelope.sourceGtNet;
    if (sourceGtNet != null) {
      // Sync timezone
      if (sourceGtNet.getTimeZone() != null && !sourceGtNet.getTimeZone().equals(localRemoteEntry.getTimeZone())) {
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
   * Synchronizes the GTNetEntity list from a remote source DTO into the local entry. Updates existing entities and adds
   * new ones based on entityKind.
   *
   * @param localEntry     the local GTNet entry to update
   * @param sourceEntities the entity DTOs from the remote source
   * @return true if any changes were made
   */
  private boolean syncGtNetEntitiesFromDTO(GTNet localEntry, List<GTNetEntityPublicDTO> sourceEntities) {
    boolean changed = false;

    for (GTNetEntityPublicDTO sourceEntity : sourceEntities) {
      GTNetEntity localEntity = localEntry.getOrCreateEntityByKind(sourceEntity.getEntityKind());

      // Sync acceptRequest, serverState, and maxLimit from remote
      if (localEntity.getAcceptRequest() != sourceEntity.getAcceptRequest()) {
        localEntity.setAcceptRequest(sourceEntity.getAcceptRequest());
        changed = true;
      }
      if (localEntity.getServerState() != sourceEntity.getServerState()) {
        localEntity.setServerState(sourceEntity.getServerState());
        changed = true;
      }
      if (!java.util.Objects.equals(localEntity.getMaxLimit(), sourceEntity.getMaxLimit())) {
        localEntity.setMaxLimit(sourceEntity.getMaxLimit());
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
   * Ensures the response envelope contains the local server's GTNet info. This is a safety check in case the envelope
   * was created without using the standard constructor.
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
   * Builds an error response envelope: the message was not processed, and {@code errorMsgCode} carries the stable
   * reason. Both travel on the envelope, so the sender can tell a malformed envelope from a refusal without reading the
   * free text.
   *
   * @param myGTNet   the local entry, which becomes the envelope's source
   * @param errorCode the stable reason, translated through the NLS bundle on the receiving side
   * @param message   the human-readable detail
   * @return the error envelope
   */
  private MessageEnvelope buildErrorResponse(GTNet myGTNet, String errorCode, String message) {
    GTNetMessage errorMsg = new GTNetMessage(null, GTNetTime.now(), SendReceivedType.ANSWER.getValue(), null,
        GNetCoreMessageCode.GT_NET_ERROR_S.getValue(), message, null);
    errorMsg.setErrorMsgCode(errorCode);
    return new MessageEnvelope(myGTNet, errorMsg);
  }

  /**
   * Builds the acknowledgement for a message that needs no semantic answer — an announcement or a one-way message. The
   * sender learns that it arrived and was dealt with, and nothing more.
   *
   * @param myGTNet the local entry, which becomes the envelope's source
   * @return the acknowledgement envelope
   */
  private MessageEnvelope buildAckResponse(GTNet myGTNet) {
    GTNetMessage ackMsg = new GTNetMessage(null, GTNetTime.now(), SendReceivedType.ANSWER.getValue(), null,
        GNetCoreMessageCode.GT_NET_ACK_S.getValue(), null, null);
    return new MessageEnvelope(myGTNet, ackMsg);
  }

  /**
   * Builds the answer for a request that was accepted but not decided: an administrator has to act, and the real
   * response follows later as a message of its own. Distinct from an acknowledgement, because the sender must keep its
   * request open rather than record this as the reply that closes it.
   *
   * @param myGTNet the local entry, which becomes the envelope's source
   * @return the deferred-acknowledgement envelope
   */
  private MessageEnvelope buildDeferredResponse(GTNet myGTNet) {
    GTNetMessage deferredMsg = new GTNetMessage(null, GTNetTime.now(), SendReceivedType.ANSWER.getValue(), null,
        GNetCoreMessageCode.GT_NET_DEFERRED_S.getValue(), null, null);
    return new MessageEnvelope(myGTNet, deferredMsg);
  }

  /**
   * Whether a message code reaches {@code getMsgResponse} without a validated token. Only the first handshake does,
   * because the caller has no token yet; every other code passed {@code validateIncomingToken} in the M2M resource.
   *
   * @param messageCode the code of the incoming message
   * @return true when the message was not authenticated
   */
  private boolean isUnauthenticatedCode(byte messageCode) {
    return messageCode == GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S.getValue();
  }

  /**
   * Finds the peer entry for a domain supplied by a caller. The stored value is matched as it is first, so entries
   * written before domains were canonicalized keep resolving, and only then by canonical form.
   *
   * @param domain the domain as it arrived from the caller
   * @return the peer entry, or null when no entry names that domain
   */
  private GTNet findPeerByDomain(String domain) {
    if (domain == null || domain.isBlank()) {
      return null;
    }
    GTNet peer = gtNetJpaRepository.findByDomainRemoteName(domain);
    if (peer != null) {
      return peer;
    }
    String canonical = domainService.canonicalize(domain);
    return canonical == null || canonical.equals(domain) ? null : gtNetJpaRepository.findByDomainRemoteName(canonical);
  }

  @Override
  public void validateIncomingToken(String sourceDomain, String authToken) {
    if (authToken == null || authToken.isBlank()) {
      throw new SecurityException("Missing authentication token");
    }

    GTNet remoteGTNet = findPeerByDomain(sourceDomain);
    if (remoteGTNet == null) {
      // The domain is caller-supplied; logging it verbatim would echo arbitrary text into the log.
      throw new SecurityException("Unknown source domain");
    }

    GTNetConfig gtNetConfig = remoteGTNet.getGtNetConfig();
    if (gtNetConfig == null) {
      throw new SecurityException("Invalid authentication token");
    }
    if (tokenMatches(gtNetConfig.getTokenThis(), authToken)) {
      return;
    }
    // A peer whose token refresh response was lost still holds the token we replaced. Accepting it for the bounded
    // overlap window is what lets that peer reach us at all, so that a retry can complete the rotation. The peer is
    // named by its id rather than by the caller-supplied domain, which must not be echoed into the log.
    if (gtNetConfig.isPreviousTokenValid(GTNetTime.now())
        && tokenMatches(gtNetConfig.getTokenThisPrevious(), authToken)) {
      log.warn("Peer {} authenticated with the superseded token; its token refresh has not completed",
          remoteGTNet.getIdGtNet());
      return;
    }
    throw new SecurityException("Invalid authentication token");
  }

  /**
   * Constant-time comparison of a stored token against the one a caller presented.
   *
   * @param expectedToken the token on record, may be null
   * @param authToken     the token the caller presented
   * @return true when both are present and equal
   */
  private static boolean tokenMatches(String expectedToken, String authToken) {
    return expectedToken != null && MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
        authToken.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  @Transactional
  public void broadcastSettingsUpdate() {
    Integer myEntryId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository);
    GTNet myGTNet = gtNetJpaRepository.findById(myEntryId)
        .orElseThrow(() -> new IllegalStateException("My GTNet entry not found with ID: " + myEntryId));

    sendSettingsUpdatedNotification(myGTNet);
  }

  @Override
  @Transactional
  public void deleteMessageBatch(List<Integer> idGtNetMessageList) {
    // Fetch all pending IDs for validation
    Set<Integer> outgoingPendingIds = gtNetMessageJpaRepository
        .findUnansweredRequests(SendReceivedType.SEND.getValue(), messageCodeRegistry.requestCodesRequiringResponse())
        .stream().map(row -> ((Number) row[1]).intValue()).collect(Collectors.toSet());
    Set<Integer> incomingPendingIds = gtNetMessageJpaRepository
        .findUnansweredRequests(SendReceivedType.RECEIVED.getValue(),
            messageCodeRegistry.requestCodesRequiringResponse())
        .stream().map(row -> ((Number) row[1]).intValue()).collect(Collectors.toSet());

    // Delegate to GTNetMessageJpaRepository for actual deletion with validation
    gtNetMessageJpaRepository.deleteBatch(idGtNetMessageList, outgoingPendingIds, incomingPendingIds);
  }

  @Override
  @Transactional
  public GTNetWithMessages submitMsgToMultiple(MultiTargetMsgRequest multiTargetMsgRequest) {
    if (multiTargetMsgRequest.idGTNetTargetDomains == null || multiTargetMsgRequest.idGTNetTargetDomains.isEmpty()) {
      throw new DataViolationException("id.gtnet.target.domains", "g.gtnet.multi.target.empty", null);
    }

    GTNet sourceGTNet = gtNetJpaRepository
        .findById(GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersJpaRepository)).orElseThrow();

    // First pass: collect valid targets (filter out own entry, not found, no handshake)
    List<GTNet> validTargets = new ArrayList<>();
    for (Integer targetIdGtNet : multiTargetMsgRequest.idGTNetTargetDomains) {
      // Skip our own entry
      if (targetIdGtNet.equals(sourceGTNet.getIdGtNet())) {
        continue;
      }

      GTNet targetGTNet = gtNetJpaRepository.findById(targetIdGtNet).orElse(null);
      if (targetGTNet == null) {
        log.warn("Target GTNet {} not found, skipping", targetIdGtNet);
        continue;
      }

      // Only include targets with completed handshake
      if (targetGTNet.getGtNetConfig() == null || targetGTNet.getGtNetConfig().getTokenRemote() == null) {
        log.warn("Target GTNet {} has no completed handshake, skipping", targetIdGtNet);
        continue;
      }

      validTargets.add(targetGTNet);
    }

    if (validTargets.isEmpty()) {
      log.warn("No valid targets found for admin message delivery");
      return this.getAllGTNetsWithMessages();
    }

    // One GTNetMessage row per valid target, charged as one unit before the first is written, so a multi target
    // request that would cross the remaining budget is refused whole rather than delivered halfway.
    User user = getAuthenticatedUserOrNull();
    if (user != null) {
      dailyLimitService.check(user, GTNetMessage.class.getSimpleName(), validTargets.size());
    }

    // Single target: send synchronously (no background job)
    if (validTargets.size() == 1) {
      GTNet targetGTNet = validTargets.get(0);
      GTNetMessage gtNetMessage = new GTNetMessage(targetGTNet.getIdGtNet(), GTNetTime.now(),
          SendReceivedType.SEND.getValue(), null, GNetCoreMessageCode.GT_NET_ADMIN_MESSAGE_SEL_C.getValue(),
          multiTargetMsgRequest.message, multiTargetMsgRequest.gtNetMessageParamMap);
      applyVisibility(gtNetMessage, multiTargetMsgRequest.visibility);
      gtNetMessage = gtNetMessageJpaRepository.saveMsg(gtNetMessage);

      // Send synchronously
      SendResult sendResult = sendMessageWithResult(sourceGTNet, targetGTNet, gtNetMessage, null);
      updateDeliveryStatus(gtNetMessage, sendResult);

      log.info("Sent admin message {} synchronously to {}", gtNetMessage.getIdGtNetMessage(),
          targetGTNet.getDomainRemoteName());
    } else {
      // Multiple targets: use background job for delivery
      int messagesCreated = 0;
      for (GTNet targetGTNet : validTargets) {
        GTNetMessage gtNetMessage = new GTNetMessage(targetGTNet.getIdGtNet(), GTNetTime.now(),
            SendReceivedType.SEND.getValue(), null, GNetCoreMessageCode.GT_NET_ADMIN_MESSAGE_SEL_C.getValue(),
            multiTargetMsgRequest.message, multiTargetMsgRequest.gtNetMessageParamMap);
        applyVisibility(gtNetMessage, multiTargetMsgRequest.visibility);
        gtNetMessage = gtNetMessageJpaRepository.saveMsg(gtNetMessage);

        // Create GTNetMessageAttempt for background delivery
        GTNetMessageAttempt attempt = new GTNetMessageAttempt(targetGTNet.getIdGtNet(),
            gtNetMessage.getIdGtNetMessage());
        gtNetMessageAttemptJpaRepository.save(attempt);
        messagesCreated++;

        log.info("Created admin message {} for target {}", gtNetMessage.getIdGtNetMessage(),
            targetGTNet.getDomainRemoteName());
      }

      log.info("Created {} admin messages for multi-target delivery", messagesCreated);

      // Schedule background delivery task
      taskDataChangeJpaRepository
          .save(new TaskDataChange(TaskTypeBase.GTNET_ADMIN_MESSAGE_DELIVERY, TaskDataExecPriority.PRIO_NORMAL));
      log.info("Scheduled GTNet admin message delivery task for immediate execution");
    }

    if (user != null) {
      dailyLimitService.log(user.getIdUser(), GTNetMessage.class.getSimpleName(), OperationType.ADD,
          validTargets.size());
    }
    return this.getAllGTNetsWithMessages();
  }

  @Override
  @Transactional
  public void deleteGTNet(Integer idGtNet) {
    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (idGtNet.equals(myEntryId)) {
      throw new DataViolationException("g.net", "g.gtnet.cannot.delete.own.entry", null);
    }
    boolean hasPending = gtNetMessageJpaRepository
        .findUnansweredRequests(SendReceivedType.SEND.getValue(), messageCodeRegistry.requestCodesRequiringResponse())
        .stream().anyMatch(row -> ((Number) row[0]).intValue() == idGtNet)
        || gtNetMessageJpaRepository
            .findUnansweredRequests(SendReceivedType.RECEIVED.getValue(),
                messageCodeRegistry.requestCodesRequiringResponse())
            .stream().anyMatch(row -> ((Number) row[0]).intValue() == idGtNet);
    if (hasPending) {
      throw new DataViolationException("g.net", "g.gtnet.pending.messages.exist", null);
    }
    gtNetJpaRepository.deleteById(idGtNet);
  }

  @Override
  @Transactional
  public GTNetWithMessages resetHandshake(Integer idGtNet) {
    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (idGtNet.equals(myEntryId)) {
      throw new DataViolationException("g.net", "g.gtnet.cannot.reset.own.entry", null);
    }
    GTNet peer = gtNetJpaRepository.findById(idGtNet).orElseThrow();
    GTNetConfig gtNetConfig = peer.getGtNetConfig();
    if (gtNetConfig != null) {
      gtNetConfig.setTokenThis(null);
      gtNetConfig.setTokenRemote(null);
      gtNetConfig.setTokenThisPrevious(null);
      gtNetConfig.setTokenThisPreviousValidUntil(null);
      gtNetConfig.setHandshakeTimestamp(null);
      gtNetConfig.setReconnectRequestedTime(null);
      gtNetConfigJpaRepository.save(gtNetConfig);
      log.info("Handshake reset for peer {}; it may handshake again", peer.getDomainRemoteName());
    }
    // Without a token the peer can no longer be probed, so it is put into the state of a peer that was never
    // handshaked rather than left at the status its last successful contact wrote.
    statusCheckService.markUnverifiable(peer);
    return this.getAllGTNetsWithMessages();
  }

  @Override
  public String exportGTNetConfig(String exportHeader, String[] deleteOnlyTables, String[] exportAndDeleteTables) {
    StringBuilder sql = new StringBuilder();
    sql.append(exportHeader).append("\n");
    // Break self-referencing FK (reply_to -> id_gt_net_message) before deleting
    sql.append("UPDATE `").append(GTNetMessage.TABNAME)
        .append("` SET `reply_to` = NULL WHERE `reply_to` IS NOT NULL;\n");
    // Delete from delete-only tables first (children of exported tables)
    for (String table : deleteOnlyTables) {
      sql.append(MySqlInsertStatementGenerator.generateDeleteStatement(table));
    }
    // Delete from exported tables (children first)
    for (String table : exportAndDeleteTables) {
      sql.append(MySqlInsertStatementGenerator.generateDeleteStatement(table));
    }
    // Insert in parent-first order; gt_net_message uses ordered insert (NULL reply_to first, then referencing rows)
    for (int i = exportAndDeleteTables.length - 1; i >= 0; i--) {
      String table = exportAndDeleteTables[i];
      if (GTNetMessage.TABNAME.equals(table)) {
        sql.append(MySqlInsertStatementGenerator.generateInsertStatementsWithSelfRef(jdbcTemplate, table, "reply_to"));
      } else {
        sql.append(MySqlInsertStatementGenerator.generateInsertStatements(jdbcTemplate, table));
      }
    }
    return sql.toString();
  }

  @Override
  @Transactional
  public void importGTNetConfig(String sqlStatements, String expectedHeader) {
    String trimmed = sqlStatements.strip();
    if (!trimmed.startsWith(expectedHeader)) {
      throw new DataViolationException("g.net", "g.gtnet.import.invalid.header", null);
    }
    // Read before the first DELETE: afterwards the row this instance identified itself with is gone. A domain name
    // survives an id renumbering, an id does not, so the domain is what carries the identity across the import.
    String previousOwnDomain = readOwnDomainBeforeImport();
    String[] statements = trimmed.split(";\\s*\n");
    for (String stmt : statements) {
      String cleaned = stmt.strip();
      if (cleaned.isEmpty() || cleaned.startsWith("--")) {
        continue;
      }
      validateImportStatement(cleaned);
      jdbcTemplate.execute(cleaned);
    }
    resolveOwnEntryAfterImport(previousOwnDomain);
  }

  /**
   * Reads the domain name of the entry {@code g.gnet.my.entry.id} names at the moment the import starts.
   *
   * <p>
   * Deliberately a JDBC read rather than {@code gtNetJpaRepository.findById}: the import writes through
   * {@link JdbcTemplate}, so the persistence context of this transaction would keep handing back rows the import has
   * already deleted.
   * </p>
   *
   * @return the own domain name before the import, or null when this instance has no own entry
   */
  private String readOwnDomainBeforeImport() {
    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (myEntryId == null) {
      return null;
    }
    List<String> domains = jdbcTemplate.queryForList(
        "SELECT `domain_remote_name` FROM `" + GTNet.TABNAME + "` WHERE `id_gt_net` = ?", String.class, myEntryId);
    return domains.isEmpty() ? null : domains.get(0);
  }

  /**
   * Re-points {@code g.gnet.my.entry.id} at the imported row that is this instance.
   *
   * <p>
   * The import replaces every GTNet table but must not carry {@code globalparameters} rows in the uploaded file, so the
   * own-entry parameter is resolved here instead. Without it a restore into a database whose parameter was nulled -
   * which every freshly built database is - brings the own entry back into {@code gt_net} while the instance keeps
   * reporting that it has no GTNet identity, and a restore that renumbers ids makes the instance treat a foreign peer
   * as itself.
   * </p>
   *
   * <p>
   * Resolution is by the domain name held before the import first, and only then by
   * {@link #isDomainNameThisMachine(String)} - the same rule {@code saveOnlyAttributes} uses to claim the own entry.
   * That method has a known false negative behind NAT or a reverse proxy, where the public host name resolves to no
   * locally bound address, so failing to resolve leaves the parameter untouched rather than rolling the import back.
   * </p>
   *
   * @param previousOwnDomain the own domain name read before the import, or null when there was none
   */
  private void resolveOwnEntryAfterImport(String previousOwnDomain) {
    Map<Integer, String> importedDomains = readImportedDomains();
    Integer resolved = findIdByDomain(importedDomains, previousOwnDomain);
    if (resolved == null) {
      resolved = findIdByThisMachine(importedDomains);
    }
    Integer currentEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (resolved == null) {
      log.error("GTNet import could not identify the own entry among the imported domains {}; g.gnet.my.entry.id stays "
          + "{}. Set it to the id of this instance's own entry.", importedDomains.values(), currentEntryId);
      return;
    }
    if (!resolved.equals(currentEntryId)) {
      globalparametersJpaRepository.saveGTNetMyEntryID(resolved);
      log.info("GTNet import moved g.gnet.my.entry.id from {} to {} ({})", currentEntryId, resolved,
          importedDomains.get(resolved));
    }
  }

  /**
   * Reads id and domain name of every row the import has just written.
   *
   * @return the imported entries by id, never null
   */
  private Map<Integer, String> readImportedDomains() {
    Map<Integer, String> importedDomains = new HashMap<>();
    jdbcTemplate.queryForList("SELECT `id_gt_net`, `domain_remote_name` FROM `" + GTNet.TABNAME + "`").forEach(
        row -> importedDomains.put(((Number) row.get("id_gt_net")).intValue(), (String) row.get("domain_remote_name")));
    return importedDomains;
  }

  /**
   * Finds the imported row carrying exactly the given domain name.
   *
   * @param importedDomains  the imported entries by id
   * @param domainRemoteName the domain name to look for, may be null
   * @return the id of the matching entry, or null when there is none
   */
  private Integer findIdByDomain(Map<Integer, String> importedDomains, String domainRemoteName) {
    if (domainRemoteName == null) {
      return null;
    }
    return importedDomains.entrySet().stream().filter(entry -> domainRemoteName.equals(entry.getValue()))
        .map(Map.Entry::getKey).findFirst().orElse(null);
  }

  /**
   * Finds the imported row whose domain name resolves to an address of this machine. A domain that cannot be resolved
   * belongs to a peer and is skipped rather than failing the import. Two matching rows leave the decision open, because
   * picking one of them would be a guess about this instance's identity.
   *
   * @param importedDomains the imported entries by id
   * @return the id of the single entry that is this machine, or null when none or more than one matches
   */
  private Integer findIdByThisMachine(Map<Integer, String> importedDomains) {
    List<Integer> matches = new ArrayList<>();
    for (Map.Entry<Integer, String> entry : importedDomains.entrySet()) {
      try {
        if (entry.getValue() != null && isDomainNameThisMachine(entry.getValue())) {
          matches.add(entry.getKey());
        }
      } catch (SocketException | UnknownHostException | URISyntaxException _) {
        log.debug("GTNet import cannot resolve {}, so it is not this machine", entry.getValue());
      }
    }
    if (matches.size() > 1) {
      log.error("GTNet import found {} imported entries pointing at this machine; g.gnet.my.entry.id left unchanged",
          matches.size());
      return null;
    }
    return matches.isEmpty() ? null : matches.get(0);
  }

  private void validateImportStatement(String statement) {
    String upper = statement.toUpperCase().strip();
    if (upper.startsWith("DELETE FROM") || upper.startsWith("INSERT INTO") || upper.startsWith("UPDATE")) {
      if (!statement.contains("gt_net")) {
        throw new DataViolationException("g.net", "g.gtnet.import.invalid.statement", null);
      }
      return;
    }
    throw new DataViolationException("g.net", "g.gtnet.import.invalid.statement", null);
  }

  @Override
  public GTNet checkPeerStatusNow(Integer idGtNet) {
    GTNet peer = gtNetJpaRepository.findById(idGtNet).orElseThrow();

    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (myEntryId == null || idGtNet.equals(myEntryId)) {
      // Own entry or GTNet not fully configured — nothing to probe; return current state.
      return peer;
    }
    GTNet myGTNet = gtNetJpaRepository.findById(myEntryId).orElse(null);
    if (myGTNet == null) {
      return peer;
    }

    if (peer.getGtNetConfig() == null || peer.getGtNetConfig().getTokenRemote() == null) {
      return statusCheckService.markUnverifiable(peer);
    }
    return statusCheckService.checkAndUpdatePeer(peer, myGTNet);
  }

}
