package grafiosch.gtnet.handler;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.gtnet.ExchangeKindTypeRegistry;
import grafiosch.gtnet.IExchangeKindType;

/**
 * Shared base for the answers to a data exchange request, whether they accept it or refuse it.
 *
 * <p>
 * Both need the same thing first: the entity kinds the request they answer named. The peer echoes our own local message
 * id back as {@code replyToSourceId}, so the kinds are read from our own stored request rather than from anything the
 * peer sent — the answer itself carries no kinds.
 * </p>
 */
public abstract class AbstractDataResponseHandler extends AbstractResponseHandler {

  private static final Logger log = LoggerFactory.getLogger(AbstractDataResponseHandler.class);

  @Autowired
  protected ExchangeKindTypeRegistry exchangeKindRegistry;

  /**
   * The entity kinds of the request this answer replies to.
   *
   * <p>
   * An unresolvable request yields no kinds, and the caller must do nothing rather than proceed. Substituting the
   * syncable kinds here is how a lost or deleted request used to widen a grant to everything, in both directions — the
   * opposite of what a missing decision should do.
   * </p>
   *
   * @param context the message context
   * @return the entity kinds the original request named, empty when it cannot be resolved
   */
  protected Set<IExchangeKindType> getRespondedEntityKinds(GTNetMessageContext context) {
    Integer originalRequestId = context.getReplyToSourceId();
    if (originalRequestId == null) {
      log.warn("No replyToSourceId in the response, cannot find the original request");
      return Set.of();
    }

    GTNetMessage originalRequest = gtNetMessageJpaRepository.findById(originalRequestId).orElse(null);
    if (originalRequest == null) {
      log.warn("Original request message {} not found", originalRequestId);
      return Set.of();
    }

    return parseEntityKindsFromParams(originalRequest.getGtNetMessageParamMap());
  }

  /**
   * Parses entity kinds from the parameter map of a locally stored message.
   *
   * @param paramMap the parameter map from a message
   * @return the entity kinds it names, possibly empty
   */
  protected Set<IExchangeKindType> parseEntityKindsFromParams(Map<String, GTNetMessageParam> paramMap) {
    if (paramMap == null) {
      return Set.of();
    }
    GTNetMessageParam param = paramMap.get(ExchangeKindTypeRegistry.ENTITY_KINDS_PARAM);
    return param == null ? Set.of() : exchangeKindRegistry.parseAll(param.getParamValue());
  }
}
