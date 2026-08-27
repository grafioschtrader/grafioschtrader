package grafiosch.gtnet;

import java.util.List;
import java.util.Objects;

/**
 * Everything the system knows about one GTNet message code.
 *
 * <p>
 * The protocol used to be described by six overlapping registries — a code registry, a response map on the application
 * enum, a model map, two frontend constants and a hardcoded list of "codes that await a reply" — none of them complete
 * and no two of them in agreement. A descriptor is the single statement about a code: which category it belongs to,
 * whether a person may send it, which codes answer it, what payload it carries, whether it needs a handler and whether
 * it may appear in an auto-answer rule.
 * </p>
 *
 * <p>
 * Nothing is derived from the name of the code. The naming convention lies often enough to be unusable:
 * {@code GT_NET_FIRST_HANDSHAKE_SEL_RR_S} is user-initiated yet ends in {@code _S}, and the {@code _RR_} marker is
 * absent from codes that do wait for an answer. Where a fact matters, it is declared.
 * </p>
 *
 * @param code               the message code this describes
 * @param category           request, response or announcement — this decides the send path and the handler contract
 * @param userInitiable      an administrator may submit this code from the message dialog
 * @param requiresResponse   the request stays open until one of {@link #validResponses()} arrives; such a request is
 *                           delete-protected and blocks the deletion of its peer
 * @param validResponses     the codes that legitimately answer this one; empty for everything but a request and the
 *                           threadable admin message
 * @param model              the payload model class, {@code null} when the code carries no parameters
 * @param formEligible       the model fields are filled in by the user, so the code belongs in
 *                           {@code /msgformdefinition}. False for a payload the server builds itself, such as the
 *                           handshake token
 * @param repeatSendAsMany   how often a delivery of this code may be attempted
 * @param reprocessable      a repeated delivery may simply be processed again instead of being answered from the stored
 *                           outcome — true only for a query that mutates nothing
 * @param inboundDispatch    the code can arrive as an inbound message of its own and therefore needs a handler. False
 *                           for an answer that is only ever read out of the synchronous reply envelope
 * @param threadable         the code may carry a {@code replyTo} without being a registered response of anything; this
 *                           is what lets two administrators hold a conversation in admin messages
 * @param autoAnswerRequest  the code may be the {@code requestMsgCode} of a {@code GTNetMessageAnswer} rule
 * @param autoAnswerResponse the code may be the {@code responseMsgCode} of a rule. False for a refusal the server
 *                           issues on its own, which an administrator must not be able to configure as an answer
 * @param senderPersists     the sender writes a {@code gt_net_message} row before it sends, so its envelope can name
 *                           that row in {@code idSourceGtNetMessage}. False for a machine-to-machine code whose sender
 *                           keeps no row at all — the ping and the payload exchanges — whose envelope therefore carries
 *                           no sender-local id and whose delivery is outside the idempotency mechanism
 */
public record GTNetProtocolDescriptor(GTNetMessageCode code, MessageCategory category, boolean userInitiable,
    boolean requiresResponse, List<GTNetMessageCode> validResponses, Class<?> model, boolean formEligible,
    byte repeatSendAsMany, boolean reprocessable, boolean inboundDispatch, boolean threadable,
    boolean autoAnswerRequest, boolean autoAnswerResponse, boolean senderPersists) {

  public GTNetProtocolDescriptor {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(category, "category");
    validResponses = validResponses == null ? List.of() : List.copyOf(validResponses);
  }

  /**
   * The wire value of the described code.
   *
   * @return the byte value used on the wire and in the database
   */
  public byte value() {
    return code.getValue();
  }

  /**
   * The enum constant name, which is also the NLS key of the code.
   *
   * @return the constant name
   */
  public String name() {
    return code.name();
  }

  /**
   * Whether a still unanswered instance of this code prevents the deletion of the message and of the peer it belongs
   * to. A request whose answer is outstanding must not be removable, or the conversation loses the row the answer would
   * attach to.
   *
   * @return true when a pending instance is delete-protected
   */
  public boolean blocksDeletion() {
    return requiresResponse;
  }

  /**
   * Whether the given code is a registered answer to this one.
   *
   * @param responseValue the wire value of the answer that came back
   * @return true when that answer belongs to this request
   */
  public boolean isValidResponse(byte responseValue) {
    return validResponses.stream().anyMatch(response -> response.getValue() == responseValue);
  }

  /**
   * Starts a request descriptor. A request is dispatched to a handler and answered.
   *
   * @param code the message code
   * @return the builder
   */
  public static Builder request(GTNetMessageCode code) {
    return new Builder(code, MessageCategory.REQUEST);
  }

  /**
   * Starts a response descriptor. A response is not dispatched unless {@link Builder#inboundDispatch()} says so,
   * because most answers are read straight out of the synchronous reply envelope.
   *
   * @param code the message code
   * @return the builder
   */
  public static Builder response(GTNetMessageCode code) {
    return new Builder(code, MessageCategory.RESPONSE).noInboundDispatch().autoAnswerResponse();
  }

  /**
   * Starts an announcement descriptor. An announcement is one-way and is dispatched to a handler.
   *
   * @param code the message code
   * @return the builder
   */
  public static Builder announcement(GTNetMessageCode code) {
    return new Builder(code, MessageCategory.ANNOUNCEMENT);
  }

  /** Fluent builder; every fact that is not declared keeps the conservative default of its category. */
  public static final class Builder {

    private final GTNetMessageCode code;
    private final MessageCategory category;
    private boolean userInitiable;
    private boolean requiresResponse;
    private List<GTNetMessageCode> validResponses = List.of();
    private Class<?> model;
    private boolean formEligible;
    private byte repeatSendAsMany = 1;
    private boolean reprocessable;
    private boolean inboundDispatch = true;
    private boolean threadable;
    private boolean autoAnswerRequest;
    private boolean autoAnswerResponse;
    private boolean senderPersists = true;

    private Builder(GTNetMessageCode code, MessageCategory category) {
      this.code = code;
      this.category = category;
    }

    /**
     * An administrator may send this code from the message dialog.
     *
     * @return this builder
     */
    public Builder userInitiable() {
      this.userInitiable = true;
      return this;
    }

    /**
     * The request stays open until one of its valid responses arrives.
     *
     * @return this builder
     */
    public Builder requiresResponse() {
      this.requiresResponse = true;
      return this;
    }

    /**
     * Declares the answers to this code.
     *
     * @param responses the codes that legitimately answer it
     * @return this builder
     */
    public Builder responses(GTNetMessageCode... responses) {
      this.validResponses = List.of(responses);
      return this;
    }

    /**
     * Declares a payload the user fills in, so the code appears in {@code /msgformdefinition}.
     *
     * @param model the payload model class
     * @return this builder
     */
    public Builder formModel(Class<?> model) {
      this.model = model;
      this.formEligible = true;
      return this;
    }

    /**
     * Declares a payload the server builds itself. It is still used to deserialize and validate the parameters, but it
     * is never offered as a form.
     *
     * @param model the payload model class
     * @return this builder
     */
    public Builder internalModel(Class<?> model) {
      this.model = model;
      this.formEligible = false;
      return this;
    }

    /**
     * Sets how often a delivery may be attempted.
     *
     * @param repeatSendAsMany the attempt count
     * @return this builder
     */
    public Builder repeat(int repeatSendAsMany) {
      this.repeatSendAsMany = (byte) repeatSendAsMany;
      return this;
    }

    /**
     * The code mutates nothing, so a repeated delivery may simply be run again.
     *
     * @return this builder
     */
    public Builder reprocessable() {
      this.reprocessable = true;
      return this;
    }

    /**
     * The code never arrives as an inbound message of its own and therefore needs no handler.
     *
     * @return this builder
     */
    public Builder noInboundDispatch() {
      this.inboundDispatch = false;
      return this;
    }

    /**
     * The code may arrive as an inbound message of its own and therefore needs a handler.
     *
     * @return this builder
     */
    public Builder inboundDispatch() {
      this.inboundDispatch = true;
      return this;
    }

    /**
     * The code may carry a replyTo without being a registered response of anything.
     *
     * @return this builder
     */
    public Builder threadable() {
      this.threadable = true;
      return this;
    }

    /**
     * The code may be the requestMsgCode of an auto-answer rule.
     *
     * @return this builder
     */
    public Builder autoAnswerRequest() {
      this.autoAnswerRequest = true;
      return this;
    }

    /**
     * The code may be the responseMsgCode of an auto-answer rule.
     *
     * @return this builder
     */
    public Builder autoAnswerResponse() {
      this.autoAnswerResponse = true;
      return this;
    }

    /**
     * The sender keeps no local message row for this code, so its envelope names none. Declared by the ping and by the
     * payload exchanges, which are built by a service and answered in the same HTTP response; without it the receiver
     * refuses the envelope for carrying no sender-local id.
     *
     * @return this builder
     */
    public Builder transientSend() {
      this.senderPersists = false;
      return this;
    }

    /**
     * The code is issued by the server alone and must not be configurable as the answer of a rule.
     *
     * @return this builder
     */
    public Builder systemOnlyAnswer() {
      this.autoAnswerResponse = false;
      return this;
    }

    /**
     * Builds the descriptor.
     *
     * @return the immutable descriptor
     */
    public GTNetProtocolDescriptor build() {
      return new GTNetProtocolDescriptor(code, category, userInitiable, requiresResponse, validResponses, model,
          formEligible, repeatSendAsMany, reprocessable, inboundDispatch, threadable, autoAnswerRequest,
          autoAnswerResponse, senderPersists);
    }
  }
}
