package grafioschtrader.gtnet.model.msg;

import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.m2m.model.IMsgDetails;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for requesting a list of known GTNet servers (GT_NET_UPDATE_SERVERLIST_SEL_C).
 *
 * This message enables network discovery beyond direct connections. When a domain has spreadCapability
 * enabled, it can share its list of known GTNet peers with requesters, expanding the network's reach.
 *
 * The request includes filter criteria to help the responder return only relevant servers. For example,
 * a requester interested only in intraday prices can set lastpriceServerState to SS_OPEN to receive
 * only providers currently offering that service.
 *
 * @see grafioschtrader.entities.GTNet#spreadCapability for permission to share server lists
 */
@Schema(description = """
    Payload for requesting a list of known GTNet servers from a peer. Enables network discovery beyond direct
    connections when the responder has spreadCapability enabled. Includes filter criteria to return only
    servers matching the requester's needs (e.g., only providers with SS_OPEN state for specific services).""")
public class UpdateServerlistRequestMsg implements IMsgDetails {

  private static final long serialVersionUID = 1L;

  @Schema(description = """
      Filter for servers that themselves allow spreading. When true, only servers with spreadCapability=true
      are returned, enabling multi-hop discovery.""")
  public boolean spreadCapability;

  @Schema(description = """
      Filter for entity data availability. Limits results to servers with matching entityServerState.
      Set to SS_OPEN to find active entity data providers.""")
  public GTNetServerStateTypes entityServerState;

  @Schema(description = """
      Filter for daily request limit. Limits results to servers offering at least this many daily requests.
      Null means no minimum requirement.""")
  public Integer dailyRequestLimit;

  @Schema(description = """
      Filter for intraday price availability. Limits results to servers with matching lastpriceServerState.
      Set to SS_OPEN to find active intraday price providers.""")
  public GTNetServerStateTypes lastpriceServerState;
}
