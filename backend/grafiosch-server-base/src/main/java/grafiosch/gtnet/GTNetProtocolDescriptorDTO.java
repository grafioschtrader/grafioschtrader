package grafiosch.gtnet;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the client is told about one message code.
 *
 * <p>
 * The browser used to keep its own copies of this — a response map next to the message enum, a second one next to the
 * auto-answer rule model, and a list of the codes that may carry an auto-answer rule. All three had drifted from the
 * server and from each other: one offered a reply the backend refuses, another omitted every application code. The
 * client now receives the protocol instead of restating it.
 * </p>
 *
 * @param name               the enum constant name, which is also the NLS key the client renders
 * @param value              the wire value, which is what a stored message row carries
 * @param category           REQUEST, RESPONSE or ANNOUNCEMENT
 * @param userInitiable      an administrator may send this code
 * @param requiresResponse   a sent instance stays open until one of its answers arrives
 * @param validResponses     the names of the codes that answer this one
 * @param hasModel           the code carries a payload
 * @param formEligible       the payload is filled in by the user, so {@code /msgformdefinition} describes it
 * @param threadable         the code may carry a replyTo without being an answer, as an admin message does
 * @param autoAnswerRequest  the code may be the request of an auto-answer rule
 * @param autoAnswerResponse the code may be the answer of an auto-answer rule
 */
@Schema(description = """
    Everything the client needs to know about one GTNet message code: its category, the answers it accepts, whether a
    person may send it, whether it carries a form, and whether it may appear in an auto-answer rule.""")
public record GTNetProtocolDescriptorDTO(String name, byte value, MessageCategory category, boolean userInitiable,
    boolean requiresResponse, List<String> validResponses, boolean hasModel, boolean formEligible, boolean threadable,
    boolean autoAnswerRequest, boolean autoAnswerResponse) {

  /**
   * Projects a descriptor for transport.
   *
   * @param descriptor the registered descriptor
   * @return the client view of it
   */
  public static GTNetProtocolDescriptorDTO of(GTNetProtocolDescriptor descriptor) {
    return new GTNetProtocolDescriptorDTO(descriptor.name(), descriptor.value(), descriptor.category(),
        descriptor.userInitiable(), descriptor.requiresResponse(),
        descriptor.validResponses().stream().map(GTNetMessageCode::name).toList(), descriptor.model() != null,
        descriptor.formEligible(), descriptor.threadable(), descriptor.autoAnswerRequest(),
        descriptor.autoAnswerResponse());
  }
}
