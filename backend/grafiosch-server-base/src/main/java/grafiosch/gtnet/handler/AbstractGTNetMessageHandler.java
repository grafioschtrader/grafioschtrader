package grafiosch.gtnet.handler;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.SendReceivedType;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetConfigJpaRepositoryBase;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GTNetMessageJpaRepositoryBase;

/**
 * Abstract base class providing common functionality for GTNet message handlers.
 *
 * Provides utility methods for:
 * <ul>
 *   <li>Storing incoming messages</li>
 *   <li>Building response envelopes</li>
 *   <li>Converting between POJOs and parameter maps</li>
 * </ul>
 *
 * Subclasses should typically extend the more specialized {@link AbstractRequestHandler} or
 * {@link AbstractAnnouncementHandler} instead of this class directly.
 */
public abstract class AbstractGTNetMessageHandler implements GTNetMessageHandler {

  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  protected GTNetConfigJpaRepositoryBase gtNetConfigJpaRepository;

  @Autowired
  protected GTNetMessageJpaRepositoryBase gtNetMessageJpaRepository;

  /**
   * Stores the incoming message in the database.
   *
   * @param context the message context
   * @return the persisted GTNetMessage entity
   */
  protected GTNetMessage storeIncomingMessage(GTNetMessageContext context) {
    Integer idGtNet = context.getRemoteGTNet() != null ? context.getRemoteGTNet().getIdGtNet() : null;

    GTNetMessage message = new GTNetMessage(idGtNet, context.getTimestamp(), SendReceivedType.RECEIVED.getValue(), null,
        context.getMessageCodeValue(), context.getMessage(), context.getParams());
    message.setIdSourceGtNetMessage(context.getIdSourceGtNetMessage());

    return gtNetMessageJpaRepository.saveMsg(message);
  }

  /**
   * Stores a response message in the database.
   *
   * @param context         the message context
   * @param responseCode    the response message code
   * @param message         optional text message
   * @param params          response parameters
   * @param replyToMessage  the original request message this responds to
   * @return the persisted response GTNetMessage entity
   */
  protected GTNetMessage storeResponseMessage(GTNetMessageContext context, GTNetMessageCode responseCode,
      String message, Map<String, GTNetMessageParam> params, GTNetMessage replyToMessage) {
    Integer idGtNet = context.getRemoteGTNet() != null ? context.getRemoteGTNet().getIdGtNet() : null;

    GTNetMessage responseMsg = new GTNetMessage(idGtNet, new Date(), SendReceivedType.SEND.getValue(),
        replyToMessage != null ? replyToMessage.getIdGtNetMessage() : null, responseCode.getValue(), message, params);

    return gtNetMessageJpaRepository.saveMsg(responseMsg);
  }

  /**
   * Creates a response envelope for sending back to the caller.
   *
   * @param context      the message context
   * @param responseMsg  the response message entity
   * @return the MessageEnvelope ready for transmission
   */
  protected MessageEnvelope createResponseEnvelope(GTNetMessageContext context, GTNetMessage responseMsg) {
    return new MessageEnvelope(context.getMyGTNet(), responseMsg);
  }

  /**
   * Creates a response envelope with a JSON payload.
   *
   * @param context      the message context
   * @param responseMsg  the response message entity
   * @param payload      object to serialize as JSON payload
   * @return the MessageEnvelope with payload
   */
  protected MessageEnvelope createResponseEnvelopeWithPayload(GTNetMessageContext context, GTNetMessage responseMsg,
      Object payload) {
    MessageEnvelope envelope = createResponseEnvelope(context, responseMsg);
    envelope.payload = objectMapper.convertValue(payload, JsonNode.class);
    return envelope;
  }

  /**
   * Converts a POJO to a parameter map for message storage.
   *
   * @param pojo the object to convert
   * @return map of parameter names to GTNetMessageParam values
   */
  protected Map<String, GTNetMessageParam> convertPojoToParamMap(Object pojo) {
    Map<String, String> stringMap = objectMapper.convertValue(pojo, new TypeReference<Map<String, String>>() {
    });
    Map<String, GTNetMessageParam> paramMap = new HashMap<>();
    for (Map.Entry<String, String> entry : stringMap.entrySet()) {
      paramMap.put(entry.getKey(), new GTNetMessageParam(entry.getValue()));
    }
    return paramMap;
  }

  /**
   * Updates the GTNet entity for the remote server.
   *
   * @param remoteGTNet the remote GTNet entity to update
   * @return the updated entity
   */
  protected GTNet saveRemoteGTNet(GTNet remoteGTNet) {
    return gtNetJpaRepository.save(remoteGTNet);
  }

  /**
   * Updates the GTNetConfig entity for the remote server.
   *
   * @param gtNetConfig the GTNetConfig entity to update
   * @return the updated entity
   */
  protected GTNetConfig saveGTNetConfig(GTNetConfig gtNetConfig) {
    return gtNetConfigJpaRepository.save(gtNetConfig);
  }

  /**
   * Hook method for triggering post-acceptance tasks.
   * Override in application-specific handlers to trigger tasks like exchange sync.
   *
   * This should be called after mutual acceptance of data exchange to ensure both sides synchronize their
   * configurations.
   */
  protected void triggerExchangeSyncTask() {
    // Default implementation does nothing - override in application-specific handlers
  }
}
