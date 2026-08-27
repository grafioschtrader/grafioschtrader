package grafiosch.m2m.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.rest.RequestMappings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST endpoint for machine-to-machine (M2M) communication between GTNet instances.
 *
 * This endpoint receives all incoming GTNet messages from remote peers. Authentication is performed via token
 * validation: the remote must include the token we gave them during handshake in the Authorization header. First
 * handshake messages are exempt from token validation since no token exists yet.
 *
 * <p>
 * It lives in the library because everything it touches does — {@link GTNetJpaRepository}, {@link MessageEnvelope} and
 * {@link RequestMappings}. Every consuming application therefore has the endpoint by component scan alone and only has
 * to permit {@code RequestMappings.M2M_API + "**"} in its security configuration, which must happen <em>before</em>
 * {@code SecurityConfig.configureGlobalParameters} registers the broad {@code /api/**} rule.
 * </p>
 */
@RestController
@RequestMapping(RequestMappings.GTNET_M2M_MAP)
@Tag(name = RequestMappings.GTNET_M2M, description = "Controller for GTNet M2M messages between instances")
public class GTNetM2MResource {

  public static final String AUTHORIZATION_HEADER = "Authorization";

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  /**
   * Receives one envelope from a peer.
   *
   * <p>
   * The body deliberately carries no {@code @Valid}. A Bean Validation failure is an HTTP 400 with a Spring error body,
   * and {@code BaseDataClient} turns every non-2xx into a result whose response envelope is null — the sender would
   * learn that something went wrong but not what. The envelope is therefore bounded inside {@code getMsgResponse} by
   * {@code GTNetEnvelopeValidator}, so that a malformed message is refused the same way every other refusal is: HTTP
   * 200 with {@code GT_NET_ERROR_S} and a stable {@code errorMsgCode}. The constraints declared on
   * {@link MessageEnvelope} state the same bounds as the published contract.
   * </p>
   *
   * @param authToken       the token this peer was issued, absent only for the first handshake
   * @param messageEnvelope the received envelope
   * @return the answer envelope, always with HTTP 200
   */
  @Operation(summary = "Receive and process GTNet message from remote instance", description = "Entry point for all M2M communication. Validates token for non-handshake messages.", tags = {
      RequestMappings.GTNET_M2M })
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<MessageEnvelope> receiveMessage(
      @RequestHeader(value = AUTHORIZATION_HEADER, required = false) String authToken,
      @RequestBody MessageEnvelope messageEnvelope) throws Exception {

    // The handshake is the only code whose value has to be recognised here, and it is a core one, so this needs no
    // registry: an unknown value falls through to token validation and is refused there.
    boolean isFirstHandshake = messageEnvelope.messageCode == GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S
        .getValue();

    if (!isFirstHandshake) {
      // Validate token for all other message types
      gtNetJpaRepository.validateIncomingToken(messageEnvelope.sourceDomain, authToken);
    }

    return new ResponseEntity<>(gtNetJpaRepository.getMsgResponse(messageEnvelope), HttpStatus.OK);
  }
}
