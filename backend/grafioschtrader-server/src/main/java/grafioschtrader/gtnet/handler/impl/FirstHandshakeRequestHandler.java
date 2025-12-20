package grafioschtrader.gtnet.handler.impl;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.common.DataHelper;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetConfig;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.MessageCategory;
import grafioschtrader.gtnet.SendReceivedType;
import grafioschtrader.gtnet.handler.AbstractGTNetMessageHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.HandlerResult;
import grafioschtrader.gtnet.handler.ValidationResult;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.msg.FirstHandshakeMsg;
import grafioschtrader.repository.GTNetConfigJpaRepository;
import grafioschtrader.repository.GTNetJpaRepository;

/**
 * Handler for GT_NET_FIRST_HANDSHAKE_S messages (incoming handshake requests).
 *
 * Processes the first handshake from a remote server that wants to establish a connection. The flow:
 * <ol>
 *   <li>Receive handshake request with remote's GTNet entity and their token for us</li>
 *   <li>Store or update the remote GTNet entity</li>
 *   <li>Generate our token for them to use when calling us</li>
 *   <li>Send accept response with our token</li>
 * </ol>
 *
 * The handshake always auto-accepts (no GTNetMessageAnswer rules needed) to allow initial connectivity. Further access
 * control is handled by subsequent entity/lastprice request messages.
 *
 * Note: This handler extends AbstractGTNetMessageHandler directly (not AbstractRequestHandler) because handshake has
 * special logic that always auto-accepts without using GTNetMessageAnswer rules.
 */
@Component
public class FirstHandshakeRequestHandler extends AbstractGTNetMessageHandler {

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetConfigJpaRepository gtNetConfigJpaRepository;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_S;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public HandlerResult handle(GTNetMessageContext context) throws Exception {
    // 1. Validate the request
    ValidationResult validation = validateRequest(context);
    if (!validation.valid()) {
      return new HandlerResult.ProcessingError(validation.errorCode(), validation.message());
    }

    // 2. Extract the token they sent us (what they will use to call us)
    FirstHandshakeMsg handshakeMsg = context.getParamsAs(FirstHandshakeMsg.class);
    String theirTokenForUs = handshakeMsg.tokenThis;

    // 3. Extract their GTNet entity from payload
    GTNet remoteGTNet = context.getPayloadAs(GTNet.class);

    // 4. Store or update the remote GTNet entry and its config
    GTNet processedRemoteGTNet = addOrUpdateRemoteGTNet(remoteGTNet, theirTokenForUs);

    // 5. Generate our token for them and store in GTNetConfig
    String ourTokenForThem = DataHelper.generateGUID();
    GTNetConfig gtNetConfig = processedRemoteGTNet.getGtNetConfig();
    gtNetConfig.setTokenThis(ourTokenForThem);
    gtNetConfigJpaRepository.save(gtNetConfig);

    // 6. Store the incoming handshake message
    GTNetMessage storedRequest = storeHandshakeRequest(context, processedRemoteGTNet);

    // 7. Build and store the response message
    Map<String, GTNetMessageParam> responseParams = convertPojoToParamMap(new FirstHandshakeMsg(ourTokenForThem));
    GTNetMessage responseMsg = new GTNetMessage(processedRemoteGTNet.getIdGtNet(), new java.util.Date(),
        SendReceivedType.SEND.getValue(), storedRequest.getIdGtNetMessage(),
        GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_ACCEPT_S.getValue(), null, responseParams);
    responseMsg = gtNetMessageJpaRepository.saveMsg(responseMsg);

    // 8. Return response with our GTNet info in payload
    MessageEnvelope response = createResponseEnvelopeWithPayload(context, responseMsg, context.getMyGTNet());
    return new HandlerResult.ImmediateResponse(response);
  }

  private ValidationResult validateRequest(GTNetMessageContext context) {
    // Check that payload contains the remote GTNet entity
    if (!context.hasPayload()) {
      return ValidationResult.invalid("HANDSHAKE_MISSING_PAYLOAD", "First handshake requires GTNet payload");
    }

    // Check that params contain the token
    if (context.getParams() == null || context.getParams().isEmpty()) {
      return ValidationResult.invalid("HANDSHAKE_MISSING_TOKEN", "First handshake requires tokenThis parameter");
    }

    return ValidationResult.ok();
  }

  private GTNet addOrUpdateRemoteGTNet(GTNet remoteGTNet, String theirTokenForUs) {
    GTNet existing = gtNetJpaRepository.findByDomainRemoteName(remoteGTNet.getDomainRemoteName());
    if (existing == null) {
      existing = remoteGTNet;
      existing.setIdGtNet(null); // Ensure new entity
    }
    // Create or get GTNetConfig and store their token (what we use to authenticate to them)
    GTNetConfig gtNetConfig = existing.getGtNetConfig();
    if (gtNetConfig == null) {
      gtNetConfig = new GTNetConfig();
    }
    gtNetConfig.setTokenRemote(theirTokenForUs);
    gtNetConfig = gtNetConfigJpaRepository.save(gtNetConfig);
    existing.setGtNetConfig(gtNetConfig);
    return gtNetJpaRepository.save(existing);
  }

  private GTNetMessage storeHandshakeRequest(GTNetMessageContext context, GTNet processedRemoteGTNet) {
    GTNetMessage message = new GTNetMessage(processedRemoteGTNet.getIdGtNet(), context.getTimestamp(),
        SendReceivedType.RECEIVED.getValue(), null, context.getMessageCodeValue(), context.getMessage(),
        context.getParams());
    message.setIdSourceGtNetMessage(context.getIdSourceGtNetMessage());
    return gtNetMessageJpaRepository.saveMsg(message);
  }
}
