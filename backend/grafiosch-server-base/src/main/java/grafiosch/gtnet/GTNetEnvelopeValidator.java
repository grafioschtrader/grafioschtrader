package grafiosch.gtnet;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetMessageJpaRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * The bounds of an inbound envelope that Bean Validation cannot express.
 *
 * <p>
 * Field-level lengths are declared on {@link MessageEnvelope} itself, but the enforcement lives here: how many
 * parameters the envelope carries, how large the serialized payload is, how far its timestamp lies from our clock,
 * whether the correlation fields match the category of the code, and which fields are mandatory for that code at all.
 * All of it is checked before status synchronization, budget charging and persistence, so a violation is a protocol
 * rejection rather than an HTTP 500 out of the persistence layer.
 * </p>
 */
@Component
public class GTNetEnvelopeValidator {

  /**
   * The announcements whose cancellation refers back to them. Only these two codes carry {@code idOriginalMessage}.
   */
  private static final Set<Byte> CANCELLATION_CODES = Set.of(
      GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C.getValue(),
      GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C.getValue());

  /**
   * The codes a background task redelivers from the database. {@code GNetFutureMessageDeliveryTask} and
   * {@code GNetAdminMessageDeliveryTask} rebuild the envelope from the persisted row, and
   * {@link MessageEnvelope#MessageEnvelope(GTNet, GTNetMessage)} copies that row's creation timestamp, so a retry hours
   * or days after the first attempt still carries the original instant. For these codes only the future bound of the
   * skew check applies; an old timestamp is the normal, correct case.
   */
  private static final Set<Byte> REDELIVERED_CODES = Set.of(GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C.getValue(),
      GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C.getValue(),
      GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C.getValue(),
      GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C.getValue(),
      GNetCoreMessageCode.GT_NET_ADMIN_MESSAGE_SEL_C.getValue());

  /** Column width of {@code gt_net.domain_remote_name}. */
  private static final int MAX_DOMAIN_LENGTH = 255;

  /** Column width of {@code gt_net_message.message}. */
  private static final int MAX_MESSAGE_LENGTH = 1000;

  /** Column width of {@code gt_net_message_param.param_name}. */
  private static final int MAX_PARAM_NAME_LENGTH = 32;

  /** Column width of {@code gt_net_message_param.param_value}. */
  private static final int MAX_PARAM_VALUE_LENGTH = 255;

  private final GTNetProtocolLimits protocolLimits;

  private final GTNetMessageCodeRegistry messageCodeRegistry;

  private final ObjectMapper objectMapper;

  private final GTNetMessageJpaRepository gtNetMessageJpaRepository;

  public GTNetEnvelopeValidator(GTNetProtocolLimits protocolLimits, GTNetMessageCodeRegistry messageCodeRegistry,
      ObjectMapper objectMapper, GTNetMessageJpaRepository gtNetMessageJpaRepository) {
    this.protocolLimits = protocolLimits;
    this.messageCodeRegistry = messageCodeRegistry;
    this.objectMapper = objectMapper;
    this.gtNetMessageJpaRepository = gtNetMessageJpaRepository;
  }

  /**
   * Checks the envelope against the protocol limits and the correlation rules of its code.
   *
   * @param me          the received envelope
   * @param remoteGTNet the local entry of the sending peer, null when the peer is not known yet (first handshake)
   * @return the violation as a stable {@code errorMsgCode} and a human-readable reason, empty when the envelope is
   *         acceptable
   */
  public Optional<EnvelopeViolation> validate(MessageEnvelope me, GTNet remoteGTNet) {
    GTNetProtocolDescriptor descriptor = messageCodeRegistry.getDescriptor(me.messageCode);
    return firstOf(checkFields(me, descriptor), checkParams(me.gtNetMessageParamMap), checkPayloadSize(me),
        checkClockSkew(me), checkCorrelation(me, descriptor, remoteGTNet));
  }

  /**
   * The field-level bounds. They are declared on {@link MessageEnvelope} as Bean Validation constraints too, but that
   * declaration is the OpenAPI contract rather than the enforcement: a {@code @Valid} failure is an HTTP 400, and
   * {@code BaseDataClient} turns every non-2xx into a result whose response envelope is null, so the sender would lose
   * the {@code errorMsgCode} entirely. Enforcing here keeps every refusal the same shape — HTTP 200 with
   * {@link GNetCoreMessageCode#GT_NET_ERROR_S}.
   *
   * <p>
   * The sender-local id is demanded only from a code whose sender keeps a message row for it. The ping and the payload
   * exchanges are built by a service and answered in the same HTTP response; they have no row to name, which is why
   * {@link GTNetIdempotencyService#findPreviousDelivery} and {@code AbstractGTNetMessageHandler.findPreviousDelivery}
   * treat a null id as an ordinary state rather than an error.
   * </p>
   */
  private Optional<EnvelopeViolation> checkFields(MessageEnvelope me, GTNetProtocolDescriptor descriptor) {
    if (me.sourceDomain == null || me.sourceDomain.isBlank() || me.sourceDomain.length() > MAX_DOMAIN_LENGTH) {
      return violation("ENVELOPE_INVALID", "Envelope names no usable source domain");
    }
    if (me.timestamp == null) {
      return violation("ENVELOPE_INVALID", "Envelope carries no timestamp");
    }
    if (me.idSourceGtNetMessage == null && (descriptor == null || descriptor.senderPersists())) {
      return violation("ENVELOPE_INVALID", "Envelope carries no sender-local message id");
    }
    if (me.message != null && me.message.length() > MAX_MESSAGE_LENGTH) {
      return violation("ENVELOPE_INVALID",
          "Message text of " + me.message.length() + " characters exceeds the limit of " + MAX_MESSAGE_LENGTH);
    }
    if (MessageVisibility.getByValue(me.visibility).getValue() != me.visibility) {
      // getByValue falls back to ALL_USERS for an unknown byte, so comparing the round trip is what detects it. A byte
      // outside the range would otherwise be stored verbatim and the message would be invisible to everyone.
      return violation("ENVELOPE_INVALID", "Visibility " + me.visibility + " is outside the declared range");
    }
    return Optional.empty();
  }

  private Optional<EnvelopeViolation> checkParams(Map<String, GTNetMessageParam> paramMap) {
    if (paramMap != null && paramMap.entrySet().stream()
        .anyMatch(e -> e.getKey() == null || e.getKey().length() > MAX_PARAM_NAME_LENGTH || e.getValue() == null
            || e.getValue().getParamValue() == null
            || e.getValue().getParamValue().length() > MAX_PARAM_VALUE_LENGTH)) {
      return violation("ENVELOPE_INVALID", "A parameter name or value is missing or exceeds its column width");
    }
    if (paramMap != null && paramMap.size() > protocolLimits.getMaxParams()) {
      return violation("ENVELOPE_INVALID",
          "Message carries " + paramMap.size() + " parameters, the limit is " + protocolLimits.getMaxParams());
    }
    return Optional.empty();
  }

  private Optional<EnvelopeViolation> checkPayloadSize(MessageEnvelope me) {
    if (me.payload == null || me.payload.isNull()) {
      return Optional.empty();
    }
    int size = objectMapper.writeValueAsBytes(me.payload).length;
    if (size > protocolLimits.getMaxPayloadBytes()) {
      return violation("PAYLOAD_TOO_LARGE",
          "Payload of " + size + " bytes exceeds the limit of " + protocolLimits.getMaxPayloadBytes());
    }
    return Optional.empty();
  }

  /**
   * A message must be stamped close to our own clock. A message from the future is refused for every code, because no
   * legitimate sender produces one; a message from the past is refused only for the codes that are delivered once, as a
   * redelivered announcement legitimately carries its original creation time.
   */
  private Optional<EnvelopeViolation> checkClockSkew(MessageEnvelope me) {
    if (me.timestamp == null) {
      return Optional.empty();
    }
    long minutes = Duration.between(me.timestamp, GTNetTime.now()).toMinutes();
    int tolerance = protocolLimits.getMaxClockSkewMinutes();
    if (minutes < -tolerance) {
      return violation("CLOCK_SKEW_EXCEEDED",
          "Envelope timestamp lies " + (-minutes) + " minutes in the future, the tolerance is " + tolerance);
    }
    if (minutes > tolerance && !REDELIVERED_CODES.contains(me.messageCode)) {
      return violation("CLOCK_SKEW_EXCEEDED",
          "Envelope timestamp lies " + minutes + " minutes in the past, the tolerance is " + tolerance);
    }
    return Optional.empty();
  }

  /**
   * The correlation fields must match the category of the code, and a correlation a peer supplies must resolve to a
   * message of that same peer. The category is read from the protocol descriptor rather than from the code name,
   * because {@code GT_NET_FIRST_HANDSHAKE_SEL_RR_S} is a user-initiated request whose name ends in {@code _S}.
   *
   * <p>
   * A present {@code replyToSourceId} is not restricted to registered response codes: an admin message threads its
   * replies under the same code {@code GT_NET_ADMIN_MESSAGE_SEL_C}, which answers no request. What is restricted is
   * what the value may point at — {@code AbstractResponseHandler} writes it straight into the local {@code reply_to}
   * foreign key, so an unchecked value would let one peer thread its message under another peer's conversation.
   * </p>
   */
  private Optional<EnvelopeViolation> checkCorrelation(MessageEnvelope me, GTNetProtocolDescriptor descriptor,
      GTNet remoteGTNet) {
    boolean isResponse = descriptor != null && descriptor.category() == MessageCategory.RESPONSE;
    if (isResponse && me.replyToSourceId == null) {
      return violation("ENVELOPE_INVALID", "A response must name the request it answers");
    }
    if (me.replyToSourceId != null && remoteGTNet != null) {
      GTNetMessage referenced = gtNetMessageJpaRepository.findByIdGtNetMessage(me.replyToSourceId);
      if (referenced == null || !remoteGTNet.getIdGtNet().equals(referenced.getIdGtNet())) {
        return violation("ENVELOPE_INVALID", "The referenced message does not belong to this peer");
      }
    }
    boolean isCancellation = CANCELLATION_CODES.contains(me.messageCode);
    if (isCancellation && me.idOriginalMessage == null) {
      return violation("ENVELOPE_INVALID", "A cancellation must name the announcement it cancels");
    }
    if (!isCancellation && me.idOriginalMessage != null) {
      return violation("ENVELOPE_INVALID", "Only a cancellation may name an original announcement");
    }
    return Optional.empty();
  }

  private static Optional<EnvelopeViolation> violation(String errorMsgCode, String reason) {
    return Optional.of(new EnvelopeViolation(errorMsgCode, reason));
  }

  @SafeVarargs
  private static Optional<EnvelopeViolation> firstOf(Optional<EnvelopeViolation>... checks) {
    for (Optional<EnvelopeViolation> check : checks) {
      if (check.isPresent()) {
        return check;
      }
    }
    return Optional.empty();
  }

  /**
   * A rejected envelope, described the way the answer needs it.
   *
   * @param errorMsgCode stable machine-readable reason, resolved to a text through the NLS bundle
   * @param reason       human-readable detail for the free-text message of the answer
   */
  public record EnvelopeViolation(String errorMsgCode, String reason) {
  }
}
