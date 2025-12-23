package grafioschtrader.gtnet.m2m.model;

import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.gtnet.AcceptRequestTypes;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Public DTO for GTNetEntity used in M2M communication.
 * Excludes gtNetConfigEntity which contains local exchange configuration.
 */
@Schema(description = """
    Public representation of a GTNetEntity for M2M communication. Contains the entity kind,
    acceptance status, and server state, but excludes local configuration details.""")
public class GTNetEntityPublicDTO {

  @Schema(description = "What type of information is provided (LAST_PRICE, HISTORICAL_PRICES, etc.)")
  private GTNetExchangeKindType entityKind;

  @Schema(description = """
      Defines how this server handles incoming data exchange requests: AC_CLOSED (no requests),
      AC_OPEN (accepts requests), or AC_PUSH_OPEN (accepts requests and pushed updates).""")
  private AcceptRequestTypes acceptRequest;

  @Schema(description = "Server state for data sharing (NONE, CLOSED, MAINTENANCE, OPEN)")
  private GTNetServerStateTypes serverState;

  public GTNetEntityPublicDTO() {
  }

  /**
   * Creates a public DTO from a GTNetEntity, excluding sensitive configuration.
   */
  public GTNetEntityPublicDTO(GTNetEntity entity) {
    this.entityKind = entity.getEntityKind();
    this.acceptRequest = entity.getAcceptRequest();
    this.serverState = entity.getServerState();
  }

  public GTNetExchangeKindType getEntityKind() {
    return entityKind;
  }

  public void setEntityKind(GTNetExchangeKindType entityKind) {
    this.entityKind = entityKind;
  }

  public AcceptRequestTypes getAcceptRequest() {
    return acceptRequest;
  }

  public void setAcceptRequest(AcceptRequestTypes acceptRequest) {
    this.acceptRequest = acceptRequest;
  }

  /**
   * Checks if this entity accepts incoming data requests.
   *
   * @return true if acceptRequest is AC_OPEN or AC_PUSH_OPEN
   */
  public boolean isAccepting() {
    return acceptRequest != null && acceptRequest.isAccepting();
  }

  public GTNetServerStateTypes getServerState() {
    return serverState;
  }

  public void setServerState(GTNetServerStateTypes serverState) {
    this.serverState = serverState;
  }
}
