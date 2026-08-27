package grafiosch.gtnet;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.gtnet.m2m.model.MessageEnvelope;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns the envelope a peer answered with into a decided outcome, so that a caller never mistakes a refusal for an
 * empty result.
 *
 * <p>
 * Every client declares the codes it is willing to treat as an answer. Only those are deserialized; anything else is
 * classified and reported. This is deliberately stricter than testing for a payload: a peer that answers
 * {@code GT_NET_DAILY_REQUEST_LIMIT_EXCEEDED_S} sends the correct code and no payload, which is exactly what a peer
 * with nothing to offer also sends.
 * </p>
 */
@Component
public class GTNetResponseValidator {

  private static final Logger log = LoggerFactory.getLogger(GTNetResponseValidator.class);

  @Autowired
  private ObjectMapper objectMapper;

  /**
   * Classifies the answer and deserializes it only when it is one the caller accepts.
   *
   * @param response      the envelope that came back, may be null when nothing arrived
   * @param acceptedCodes the wire values the caller treats as an answer to its request
   * @param payloadClass  the type of the payload, or null when the answer carries none
   * @param context       what the caller was doing, used in the log of a non-success
   * @param <T>           the payload type
   * @return the classified outcome; only {@link GTNetResponseResult.Success} carries a payload
   */
  public <T> GTNetResponseResult<T> validate(MessageEnvelope response, Set<Byte> acceptedCodes, Class<T> payloadClass,
      String context) {
    if (response == null) {
      return new GTNetResponseResult.Failed<>("no response");
    }
    if (!acceptedCodes.contains(response.messageCode)) {
      return classifyUnaccepted(response, context);
    }
    if (payloadClass == null || response.payload == null || response.payload.isNull()) {
      return new GTNetResponseResult.Success<>(null, response.messageCode);
    }
    try {
      return new GTNetResponseResult.Success<>(objectMapper.treeToValue(response.payload, payloadClass),
          response.messageCode);
    } catch (Exception e) {
      log.warn("{}: answer {} carried a payload that could not be read: {}", context, response.messageCode,
          e.getMessage());
      return new GTNetResponseResult.Failed<>("unreadable payload: " + e.getMessage());
    }
  }

  /**
   * Names an answer the caller did not ask for. A refusal is recorded with its code so it is visible why the exchange
   * produced nothing, rather than disappearing into a count of zero rows.
   *
   * @param response the envelope that came back
   * @param context  what the caller was doing
   * @param <T>      the payload type of the caller
   * @return the classified non-success
   */
  private <T> GTNetResponseResult<T> classifyUnaccepted(MessageEnvelope response, String context) {
    if (response.messageCode == GNetCoreMessageCode.GT_NET_DEFERRED_S.getValue()) {
      log.info("{}: the peer deferred the request to an administrator", context);
      return new GTNetResponseResult.Deferred<>();
    }
    if (response.messageCode == GNetCoreMessageCode.GT_NET_ERROR_S.getValue()) {
      log.warn("{}: the peer refused the request with {}", context, response.errorMsgCode);
      return new GTNetResponseResult.Failed<>("error: " + response.errorMsgCode);
    }
    log.warn("{}: the peer answered with {} ({}), which is not an answer to this request", context,
        response.messageCode, response.errorMsgCode);
    return new GTNetResponseResult.Refused<>(response.messageCode, response.errorMsgCode);
  }
}
