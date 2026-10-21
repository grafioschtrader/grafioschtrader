package grafiosch.m2m.rest;

import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.gtnet.GTNetInstanceIdentity;
import grafiosch.rest.RequestMappings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Answers the identity challenge of {@link GTNetInstanceIdentity}, with which an instance finds out whether a URL leads
 * back to itself.
 *
 * <p>
 * It sits under {@code RequestMappings.M2M_API} because that prefix is already open without authentication in every
 * consuming application and already forwarded by the reverse proxies of the installations — the caller is this very
 * instance going out through its public address, so the request takes exactly the path a peer would take. The answer
 * reveals nothing but the HMAC of the challenge under a key drawn at start-up, and the database is never touched, so
 * the request also succeeds while the asking instance holds a transaction open.
 * </p>
 */
@RestController
@RequestMapping(RequestMappings.GTNET_IDENTITY_M2M_MAP)
@Tag(name = RequestMappings.GTNET_M2M, description = "Controller for GTNet M2M messages between instances")
public class GTNetIdentityM2MResource {

  @Autowired
  private GTNetInstanceIdentity instanceIdentity;

  @Operation(summary = "Answer the challenge with which an instance recognizes its own URL", description = """
      Returns the HMAC of the challenge under a key that exists only in the running process. HTTP 400 for a challenge
      that is not Base64url of 32 to 128 characters.""", tags = { RequestMappings.GTNET_M2M })
  @GetMapping(produces = TEXT_PLAIN_VALUE)
  public ResponseEntity<String> answerChallenge(@RequestParam(required = false) String challenge) {
    if (!instanceIdentity.isWellFormedChallenge(challenge)) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(instanceIdentity.answer(challenge));
  }
}
