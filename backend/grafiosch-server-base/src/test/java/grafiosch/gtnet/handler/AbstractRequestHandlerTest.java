package grafiosch.gtnet.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetMessageJpaRepository;
import tools.jackson.databind.ObjectMapper;

class AbstractRequestHandlerTest {

  private final GTNetResponseResolver resolver = mock(GTNetResponseResolver.class);
  private final GTNetMessageJpaRepository messageRepository = mock(GTNetMessageJpaRepository.class);

  @Test
  void automaticResponsePersistsAndTransfersWaitDays() throws Exception {
    MessageEnvelope response = handleWithAutoResponse(GNetCoreMessageCode.GT_NET_DATA_REQUEST_REJECTED_S, "No");

    assertThat(response.waitDaysApply).isEqualTo((short) 3);
    verify(messageRepository).save(any(GTNetMessage.class));
  }

  @Test
  void automaticAcceptanceDropsWaitDays() throws Exception {
    MessageEnvelope response = handleWithAutoResponse(GNetCoreMessageCode.GT_NET_DATA_REQUEST_ACCEPT_S, "Yes");

    assertThat(response.waitDaysApply).isNotEqualTo((short) 3);
    verify(messageRepository, never()).save(any(GTNetMessage.class));
  }

  /** Runs a data request through a handler whose auto-answer rule returns the given code with three waiting days. */
  private MessageEnvelope handleWithAutoResponse(GTNetMessageCode responseCode, String text) throws Exception {
    TestRequestHandler handler = new TestRequestHandler();
    ReflectionTestUtils.setField(handler, "responseResolver", resolver);
    ReflectionTestUtils.setField(handler, "gtNetMessageJpaRepository", messageRepository);
    ReflectionTestUtils.setField(handler, "messageCodeRegistry", new GTNetMessageCodeRegistry());
    AtomicInteger ids = new AtomicInteger(10);
    when(messageRepository.saveMsg(any())).thenAnswer(invocation -> {
      GTNetMessage message = invocation.getArgument(0);
      message.setIdGtNetMessage(ids.incrementAndGet());
      return message;
    });
    when(messageRepository.findById(any())).thenAnswer(_ -> java.util.Optional.of(new GTNetMessage()));

    GTNet remote = gtNet(2, "https://remote");
    MessageEnvelope request = new MessageEnvelope();
    request.messageCode = GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C.getValue();
    request.timestamp = LocalDateTime.now();
    request.gtNetMessageParamMap = Map.of("kind", new GTNetMessageParam("0"));
    GTNetMessageContext context = new GTNetMessageContext(gtNet(1, "https://local"), remote, request, List.of(),
        new ObjectMapper());
    when(resolver.resolveAutoResponse(anyList(), eq(remote), anyMap()))
        .thenReturn(java.util.Optional.of(new GTNetResponseResolver.ResolvedResponse(responseCode, text, (short) 3)));

    var result = handler.handle(context);

    return ((HandlerResult.ImmediateResponse<GTNetMessage, MessageEnvelope>) result).response();
  }

  private GTNet gtNet(Integer id, String domain) {
    GTNet gtNet = new GTNet();
    gtNet.setIdGtNet(id);
    gtNet.setDomainRemoteName(domain);
    return gtNet;
  }

  private static class TestRequestHandler extends AbstractRequestHandler {

    @Override
    public GTNetMessageCode getSupportedMessageCode() {
      return GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C;
    }

    @Override
    protected ValidationResult validateRequest(GTNetMessageContext context) {
      return ValidationResult.ok();
    }

    @Override
    protected void processRequestSideEffects(GTNetMessageContext context, GTNetMessage storedRequest) {
    }
  }
}
