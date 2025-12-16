package grafioschtrader.m2m.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.rest.RequestGTMappings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * REST endpoint for machine-to-machine (M2M) communication between GTNet instances.
 *
 * This endpoint receives all incoming GTNet messages from remote peers. Authentication is performed
 * via token validation: the remote must include the token we gave them during handshake in the
 * Authorization header. First handshake messages are exempt from token validation since no token
 * exists yet.
 */
@RestController
@RequestMapping(RequestGTMappings.GTNET_M2M_MAP)
@Tag(name = RequestGTMappings.GTNET_M2M, description = "Controller for GTNet M2M messages between instances")
public class GTNetM2MResource {

  public static final String AUTHORIZATION_HEADER = "Authorization";

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Operation(summary = "Receive and process GTNet message from remote instance",
      description = "Entry point for all M2M communication. Validates token for non-handshake messages.",
      tags = { RequestGTMappings.GTNET_M2M })
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<MessageEnvelope> receiveMessage(
      @RequestHeader(value = AUTHORIZATION_HEADER, required = false) String authToken,
      @Valid @RequestBody MessageEnvelope messageEnvelope) throws Exception {

    GTNetMessageCodeType messageCode = GTNetMessageCodeType.getGTNetMessageCodeTypeByValue(messageEnvelope.messageCode);

    // First handshake doesn't require token validation (no token exists yet)
    boolean isFirstHandshake = messageCode == GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_S;

    if (!isFirstHandshake) {
      // Validate token for all other message types
      gtNetJpaRepository.validateIncomingToken(messageEnvelope.sourceDomain, authToken);
    }

    return new ResponseEntity<>(gtNetJpaRepository.getMsgResponse(messageEnvelope), HttpStatus.OK);
  }
}
