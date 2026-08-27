package grafioschtrader.gtnet;

import java.util.List;

import grafiosch.gtnet.GTNetProtocolDescriptor;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageQueryMsg;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageResponseMsg;
import grafioschtrader.gtnet.model.msg.SecurityBatchLookupMsg;
import grafioschtrader.gtnet.model.msg.SecurityBatchLookupResponseMsg;
import grafioschtrader.gtnet.model.msg.SecurityLookupMsg;

/**
 * The descriptors of the Grafioschtrader payload protocol, codes 60 and above.
 *
 * <p>
 * These codes are machine-to-machine throughout: a background task or a service sends them, the answer comes back in
 * the same HTTP response, and none of them is offered in the message dialog. Only the exchange sync stays open across
 * requests, which is why it is the one application code that carries {@code requiresResponse}.
 * </p>
 *
 * <p>
 * The list is static so that a test can build the whole protocol without a Spring context; {@code GTStartUp} registers
 * it into the shared registry at start-up.
 * </p>
 *
 * @see grafiosch.gtnet.CoreProtocolDescriptors for the codes 0 to 54
 */
public abstract class GTProtocolDescriptors {

  private GTProtocolDescriptors() {
  }

  /**
   * The descriptor of every application message code, in code order.
   *
   * @return the immutable list of application descriptors
   */
  public static List<GTNetProtocolDescriptor> all() {
    return List.of(

        // Lastprice exchange (60-64). Reading prices changes nothing on this side, so a redelivery of the query is
        // simply answered again; the push does write and is therefore replayed from its stored outcome.
        GTNetProtocolDescriptor.request(GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C).reprocessable()
            .responses(GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S,
                GTNetMessageCodeType.GT_NET_LASTPRICE_MAX_LIMIT_EXCEEDED_S)
            .build(),
        GTNetProtocolDescriptor.response(GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S).systemOnlyAnswer()
            .build(),
        GTNetProtocolDescriptor.request(GTNetMessageCodeType.GT_NET_LASTPRICE_PUSH_SEL_C)
            .responses(GTNetMessageCodeType.GT_NET_LASTPRICE_PUSH_ACK_S).build(),
        GTNetProtocolDescriptor.response(GTNetMessageCodeType.GT_NET_LASTPRICE_PUSH_ACK_S).inboundDispatch()
            .systemOnlyAnswer().build(),
        GTNetProtocolDescriptor.response(GTNetMessageCodeType.GT_NET_LASTPRICE_MAX_LIMIT_EXCEEDED_S).systemOnlyAnswer()
            .build(),

        // Exchange configuration sync (70-71). The only application request that stays open until its answer arrives.
        GTNetProtocolDescriptor.request(GTNetMessageCodeType.GT_NET_EXCHANGE_SYNC_SEL_RR_C).requiresResponse()
            .reprocessable().responses(GTNetMessageCodeType.GT_NET_EXCHANGE_SYNC_RESPONSE_S).build(),
        GTNetProtocolDescriptor.response(GTNetMessageCodeType.GT_NET_EXCHANGE_SYNC_RESPONSE_S).systemOnlyAnswer()
            .build(),

        // Historyquote exchange (80-86).
        GTNetProtocolDescriptor.request(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C).reprocessable()
            .responses(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_RESPONSE_S,
                GTNetMessageCodeType.GT_NET_HISTORYQUOTE_MAX_LIMIT_EXCEEDED_S)
            .build(),
        GTNetProtocolDescriptor.response(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_RESPONSE_S)
            .systemOnlyAnswer().build(),
        GTNetProtocolDescriptor.request(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_PUSH_SEL_C)
            .responses(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_PUSH_ACK_S).build(),
        GTNetProtocolDescriptor.response(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_PUSH_ACK_S).inboundDispatch()
            .systemOnlyAnswer().build(),
        GTNetProtocolDescriptor.response(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_MAX_LIMIT_EXCEEDED_S)
            .systemOnlyAnswer().build(),
        GTNetProtocolDescriptor.request(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_COVERAGE_SEL_C).reprocessable()
            .internalModel(HistoryquoteCoverageQueryMsg.class)
            .responses(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_COVERAGE_RESPONSE_S,
                GTNetMessageCodeType.GT_NET_HISTORYQUOTE_MAX_LIMIT_EXCEEDED_S)
            .build(),
        GTNetProtocolDescriptor.response(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_COVERAGE_RESPONSE_S)
            .internalModel(HistoryquoteCoverageResponseMsg.class).systemOnlyAnswer().build(),

        // Security metadata lookup (90-95).
        GTNetProtocolDescriptor.request(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_SEL_C).reprocessable()
            .formModel(SecurityLookupMsg.class)
            .responses(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_RESPONSE_S,
                GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_NOT_FOUND_S,
                GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_REJECTED_S)
            .build(),
        GTNetProtocolDescriptor.response(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_RESPONSE_S).systemOnlyAnswer()
            .build(),
        GTNetProtocolDescriptor.response(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_NOT_FOUND_S).systemOnlyAnswer()
            .build(),
        GTNetProtocolDescriptor.response(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_REJECTED_S).systemOnlyAnswer()
            .build(),
        GTNetProtocolDescriptor.request(GTNetMessageCodeType.GT_NET_SECURITY_BATCH_LOOKUP_SEL_C).reprocessable()
            .internalModel(SecurityBatchLookupMsg.class)
            .responses(GTNetMessageCodeType.GT_NET_SECURITY_BATCH_LOOKUP_RESPONSE_S).build(),
        GTNetProtocolDescriptor.response(GTNetMessageCodeType.GT_NET_SECURITY_BATCH_LOOKUP_RESPONSE_S)
            .internalModel(SecurityBatchLookupResponseMsg.class).systemOnlyAnswer().build());
  }
}
