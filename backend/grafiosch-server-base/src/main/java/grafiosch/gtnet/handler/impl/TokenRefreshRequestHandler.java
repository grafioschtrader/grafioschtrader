package grafiosch.gtnet.handler.impl;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.common.DataHelper;
import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetTokenRotationService;
import grafiosch.gtnet.handler.AbstractRequestHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.ValidationResult;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.gtnet.model.msg.FirstHandshakeMsg;
import grafiosch.repository.GTNetConfigJpaRepository;

/**
 * Handler for GT_NET_TOKEN_REFRESH_SEL_RR_C messages (token refresh requests).
 *
 * Processes requests to regenerate authentication tokens between established peers. Both sides generate new tokens,
 * replacing the existing ones in GTNetConfig. This allows periodic token rotation for security without requiring a full
 * re-handshake.
 *
 * When no GTNetMessageAnswer rules are configured, defaults to accepting the token refresh.
 *
 * <p>
 * A repeated refresh is not rotated a second time. The reply is stored with its parameters, so the inbound
 * deduplication answers a redelivery from that stored row and the peer receives the identical token pair. That is what
 * makes the refresh idempotent; this handler itself needs no repeat detection.
 * </p>
 */
@Component
public class TokenRefreshRequestHandler extends AbstractRequestHandler {

  @Autowired
  private GTNetConfigJpaRepository gtNetConfigJpaRepositoryFull;

  @Autowired
  private GTNetTokenRotationService tokenRotationService;

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_SEL_RR_C;
  }

  @Override
  protected ValidationResult validateRequest(GTNetMessageContext context) {
    if (context.getRemoteGTNet() == null) {
      return ValidationResult.invalid("UNKNOWN_REMOTE", "Token refresh from unknown domain - handshake required first");
    }
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet.getGtNetConfig() == null) {
      return ValidationResult.invalid("NO_CONNECTION",
          "Token refresh requires an established connection (GTNetConfig must exist)");
    }
    if (context.getParams() == null || context.getParams().isEmpty()) {
      return ValidationResult.invalid("MISSING_TOKEN", "Token refresh requires tokenThis parameter");
    }
    return ValidationResult.ok();
  }

  @Override
  protected void processRequestSideEffects(GTNetMessageContext context, GTNetMessage storedRequest) {
    // No side effects before response determination
  }

  @Override
  protected void applyResponseSideEffects(GTNetMessageContext context, GTNetMessageCode responseCode,
      GTNetMessage storedRequest) {
    if (responseCode.getValue() == GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_ACCEPT_S.getValue()) {
      GTNet remoteGTNet = context.getRemoteGTNet();

      // Extract the new token they sent us
      FirstHandshakeMsg refreshMsg = context.getParamsAs(FirstHandshakeMsg.class);
      String theirNewTokenForUs = refreshMsg.tokenThis;

      // Generate our new token for them
      String ourNewTokenForThem = DataHelper.generateGUID();

      // Update GTNetConfig with both new tokens. The token we are replacing stays acceptable for the overlap window,
      // because this commit happens before the response is on the wire: without it a lost response would leave the
      // peer calling us with a token we no longer know, and the refresh that would repair it is itself authenticated.
      GTNetConfig gtNetConfig = gtNetConfigJpaRepositoryFull.findById(remoteGTNet.getIdGtNet()).orElseThrow();
      gtNetConfig.setTokenRemote(theirNewTokenForUs);
      tokenRotationService.rotateTokenThis(gtNetConfig, ourNewTokenForThem);
      gtNetConfigJpaRepositoryFull.save(gtNetConfig);

      // Store the generated token for buildResponse to include in the response
      context.setHandlerData("ourNewTokenForThem", ourNewTokenForThem);
    }
  }

  @Override
  protected MessageEnvelope buildResponse(GTNetMessageContext context, GTNetMessageCode responseCode, String message,
      GTNetMessage originalRequest) {
    if (responseCode.getValue() == GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_ACCEPT_S.getValue()) {
      String ourNewTokenForThem = context.getHandlerData("ourNewTokenForThem", String.class);
      Map<String, GTNetMessageParam> responseParams = convertPojoToParamMap(
          new FirstHandshakeMsg(ourNewTokenForThem != null ? ourNewTokenForThem : ""));
      GTNetMessage responseMsg = storeResponseMessage(context, responseCode, message, responseParams, originalRequest);
      return createResponseEnvelope(context, responseMsg);
    }
    GTNetMessage responseMsg = storeResponseMessage(context, responseCode, message,
        buildResponseParams(context, responseCode), originalRequest);
    return createResponseEnvelope(context, responseMsg);
  }

  @Override
  protected Optional<? extends GTNetMessageCode> getDefaultResponseCode(GTNetMessageContext context) {
    return Optional.of(GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_ACCEPT_S);
  }
}
