package grafiosch.gtnet;

import java.util.List;

import grafiosch.gtnet.model.msg.DataRequestMsg;
import grafiosch.gtnet.model.msg.DiscontinuedMsg;
import grafiosch.gtnet.model.msg.FirstHandshakeMsg;
import grafiosch.gtnet.model.msg.MaintenanceMsg;
import grafiosch.gtnet.model.msg.RevokeMsg;
import grafiosch.gtnet.model.msg.UpdateServerlistRequestMsg;

/**
 * The descriptors of the core protocol, codes 0 to 54.
 *
 * <p>
 * This is a plain static list rather than part of the registry constructor so that a test can build the whole protocol
 * — core plus the application extension — without a Spring context.
 * </p>
 *
 * @see GTNetProtocolDescriptor for what each fact means
 */
public abstract class CoreProtocolDescriptors {

  private CoreProtocolDescriptors() {
  }

  /**
   * The descriptor of every core message code, in code order.
   *
   * @return the immutable list of core descriptors
   */
  public static List<GTNetProtocolDescriptor> all() {
    return List.of(

        // Ping (0) - a health check answered with an acknowledgement, never with a semantic response. Neither side
        // stores it, so its envelope names no sender-local message.
        GTNetProtocolDescriptor.request(GNetCoreMessageCode.GT_NET_PING).transientSend().build(),

        // Handshake (1-4). The payload carries the token this side generates, which sendAndSaveMsg overwrites on the
        // way out, so the model is internal even though an administrator starts the handshake.
        GTNetProtocolDescriptor.request(GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S).userInitiable()
            .requiresResponse().autoAnswerRequest().internalModel(FirstHandshakeMsg.class)
            .responses(GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_ACCEPT_S,
                GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_REJECT_S,
                GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_REJECT_NOT_IN_LIST_S)
            .build(),
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_ACCEPT_S).userInitiable().build(),
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_REJECT_S).userInitiable().build(),
        // A refusal for a domain this server does not know. The handler issues it on its own; offered as a rule answer
        // it would tell an already stored peer that it is not in the list.
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_REJECT_NOT_IN_LIST_S)
            .systemOnlyAnswer().build(),

        // Token refresh (5-7). The token is generated server-side, so the form is never shown.
        GTNetProtocolDescriptor.request(GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_SEL_RR_C).userInitiable()
            .requiresResponse().autoAnswerRequest().internalModel(FirstHandshakeMsg.class)
            .responses(GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_ACCEPT_S,
                GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_REJECTED_S)
            .build(),
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_ACCEPT_S).userInitiable().build(),
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_REJECTED_S).userInitiable().build(),

        // Server list (10-13). Reading the list changes nothing, so a redelivery is simply answered again.
        GTNetProtocolDescriptor.request(GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_SEL_RR_C).userInitiable()
            .requiresResponse().autoAnswerRequest().reprocessable().formModel(UpdateServerlistRequestMsg.class)
            .responses(GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S,
                GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_REJECTED_S)
            .build(),
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S).userInitiable()
            .inboundDispatch().build(),
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_REJECTED_S).userInitiable()
            .inboundDispatch().build(),
        GTNetProtocolDescriptor.announcement(GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_REVOKE_SEL_C).userInitiable()
            .build(),

        // Server status announcements (20, 24-28).
        GTNetProtocolDescriptor.announcement(GNetCoreMessageCode.GT_NET_OFFLINE_ALL_C).userInitiable().build(),
        GTNetProtocolDescriptor.announcement(GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C).userInitiable().repeat(10)
            .formModel(MaintenanceMsg.class).build(),
        GTNetProtocolDescriptor.announcement(GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C).userInitiable()
            .formModel(DiscontinuedMsg.class).build(),
        GTNetProtocolDescriptor.announcement(GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C).userInitiable()
            .build(),
        GTNetProtocolDescriptor.announcement(GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C)
            .userInitiable().build(),
        // Broadcast by the server itself whenever the local limits or exchange settings change.
        GTNetProtocolDescriptor.announcement(GNetCoreMessageCode.GT_NET_SETTINGS_UPDATED_ALL_C).build(),

        // Admin messages (30). An announcement rather than a request - nothing is left open by it - but a threadable
        // one, because two administrators answer each other with the same code.
        GTNetProtocolDescriptor.announcement(GNetCoreMessageCode.GT_NET_ADMIN_MESSAGE_SEL_C).userInitiable()
            .threadable().responses(GNetCoreMessageCode.GT_NET_ADMIN_MESSAGE_SEL_C).build(),

        // Protocol outcomes (21-23) and the rate-limit refusal (29). They are read out of the synchronous reply
        // envelope and say nothing about the request, so they never close one and are never a rule answer.
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_ACK_S).systemOnlyAnswer().build(),
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_DEFERRED_S).systemOnlyAnswer().build(),
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_ERROR_S).systemOnlyAnswer().build(),
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_DAILY_REQUEST_LIMIT_EXCEEDED_S).systemOnlyAnswer()
            .build(),

        // Data exchange negotiation (50-54). Slot 51 is free: a data request is accepted or rejected as a whole.
        GTNetProtocolDescriptor.request(GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C).userInitiable()
            .requiresResponse().autoAnswerRequest().formModel(DataRequestMsg.class)
            .responses(GNetCoreMessageCode.GT_NET_DATA_REQUEST_ACCEPT_S,
                GNetCoreMessageCode.GT_NET_DATA_REQUEST_REJECTED_S)
            .build(),
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_DATA_REQUEST_ACCEPT_S).userInitiable()
            .inboundDispatch().build(),
        GTNetProtocolDescriptor.response(GNetCoreMessageCode.GT_NET_DATA_REQUEST_REJECTED_S).userInitiable()
            .inboundDispatch().build(),
        GTNetProtocolDescriptor.announcement(GNetCoreMessageCode.GT_NET_DATA_REVOKE_SEL_C).userInitiable()
            .formModel(RevokeMsg.class).build());
  }
}
