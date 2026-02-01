package grafiosch.gtnet.handler;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.entities.GTNetMessageAnswer;
import grafiosch.gtnet.m2m.model.MessageEnvelope;

/**
 * Context object containing all information needed for GTNet message processing.
 *
 * Provides a clean API for handlers to access message data, entity references, and utility methods without requiring
 * direct injection of multiple dependencies. The context is created once per incoming message and passed to the
 * handler.
 *
 * @see GTNetMessageHandler for the handler interface using this context
 */
public class GTNetMessageContext {

  private final GTNet myGTNet;
  private final GTNet remoteGTNet;
  private final MessageEnvelope request;
  private final List<GTNetMessageAnswer> autoResponseRules;
  private final ObjectMapper objectMapper;

  /**
   * Creates a new message context.
   *
   * @param myGTNet           the local GTNet configuration (this server)
   * @param remoteGTNet       the remote GTNet configuration (may be null for first handshake from unknown domain)
   * @param request           the incoming message envelope
   * @param autoResponseRules list of auto-response rules for this message type, ordered by priority (may be empty)
   * @param objectMapper      Jackson ObjectMapper for payload conversion
   */
  public GTNetMessageContext(GTNet myGTNet, GTNet remoteGTNet, MessageEnvelope request,
      List<GTNetMessageAnswer> autoResponseRules, ObjectMapper objectMapper) {
    this.myGTNet = myGTNet;
    this.remoteGTNet = remoteGTNet;
    this.request = request;
    this.autoResponseRules = autoResponseRules != null ? autoResponseRules : List.of();
    this.objectMapper = objectMapper;
  }

  /**
   * Returns the local GTNet configuration representing this server.
   */
  public GTNet getMyGTNet() {
    return myGTNet;
  }

  /**
   * Returns the remote GTNet configuration.
   *
   * May be null for first handshake requests from unknown domains, where the remote GTNet entity doesn't exist yet in
   * the local database.
   */
  public GTNet getRemoteGTNet() {
    return remoteGTNet;
  }

  /**
   * Returns the incoming message envelope.
   */
  public MessageEnvelope getRequest() {
    return request;
  }

  /**
   * Returns the source domain name from the request.
   */
  public String getSourceDomain() {
    return request.sourceDomain;
  }

  /**
   * Returns the raw message code byte value.
   */
  public byte getMessageCodeValue() {
    return request.messageCode;
  }

  /**
   * Returns the message parameters map.
   */
  public Map<String, GTNetMessageParam> getParams() {
    return request.gtNetMessageParamMap;
  }

  /**
   * Returns the value of a specific parameter by key.
   *
   * @param key the parameter key
   * @return the parameter value as String, or null if the parameter doesn't exist
   */
  public String getParamValue(String key) {
    if (request.gtNetMessageParamMap == null) {
      return null;
    }
    GTNetMessageParam param = request.gtNetMessageParamMap.get(key);
    return param != null ? param.getParamValue() : null;
  }

  /**
   * Returns the optional text message from the request.
   */
  public String getMessage() {
    return request.message;
  }

  /**
   * Returns the request timestamp.
   */
  public java.util.Date getTimestamp() {
    return request.timestamp;
  }

  /**
   * Returns the source message ID from the remote system (for reply correlation).
   */
  public Integer getIdSourceGtNetMessage() {
    return request.idSourceGtNetMessage;
  }

  /**
   * Returns the ID of the local request message that this response is replying to.
   *
   * For response messages only. This allows linking an incoming response to the
   * original request that was sent from this server.
   */
  public Integer getReplyToSourceId() {
    return request.replyToSourceId;
  }

  /**
   * Returns the list of auto-response rules configured for this message type, ordered by priority.
   *
   * @return list of GTNetMessageAnswer rules, empty list if no auto-response is configured
   */
  public List<GTNetMessageAnswer> getAutoResponseRules() {
    return autoResponseRules;
  }

  /**
   * Checks if auto-response rules are configured for this message type.
   */
  public boolean hasAutoResponseRules() {
    return !autoResponseRules.isEmpty();
  }

  /**
   * Returns the raw JSON payload from the request.
   */
  public JsonNode getPayload() {
    return request.payload;
  }

  /**
   * Checks if the request contains a payload.
   */
  public boolean hasPayload() {
    return request.payload != null && !request.payload.isNull();
  }

  /**
   * Converts the JSON payload to the specified class.
   *
   * @param clazz the target class for deserialization
   * @param <T>   the target type
   * @return the deserialized payload object
   * @throws IllegalArgumentException if payload is null or conversion fails
   */
  public <T> T getPayloadAs(Class<T> clazz) {
    if (!hasPayload()) {
      throw new IllegalArgumentException("Request has no payload");
    }
    try {
      return objectMapper.treeToValue(request.payload, clazz);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to convert payload to " + clazz.getSimpleName(), e);
    }
  }

  /**
   * Converts the message parameters to the specified POJO class.
   *
   * @param clazz the target class for conversion
   * @param <T>   the target type
   * @return the converted POJO
   */
  public <T> T getParamsAs(Class<T> clazz) {
    if (request.gtNetMessageParamMap == null || request.gtNetMessageParamMap.isEmpty()) {
      throw new IllegalArgumentException("Request has no parameters");
    }
    Map<String, String> valueMap = request.gtNetMessageParamMap.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getParamValue()));
    return objectMapper.convertValue(valueMap, clazz);
  }

  /**
   * Returns the ObjectMapper for custom serialization needs.
   */
  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  /**
   * Returns the visibility level for the message.
   *
   * @return the visibility byte value (0 = ALL_USERS, 1 = ADMIN_ONLY)
   */
  public byte getVisibility() {
    return request.visibility;
  }
}
