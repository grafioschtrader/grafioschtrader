package grafiosch.gtnet.m2m.model;

import grafiosch.entities.GTNetEntity;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.GTNetServerStateTypes;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Public DTO for GTNetEntity used in M2M communication.
 * Excludes gtNetConfigEntity which contains local exchange configuration.
 */
@Schema(description = """
    Public representation of a GTNetEntity for M2M communication. Contains the entity kind,
    acceptance status, and server state, but excludes local configuration details.""")
public class GTNetEntityPublicDTO {

  @Schema(description = "What type of information is provided (byte value)")
  private byte entityKind;

  @Schema(description = """
      Defines how this server handles incoming data exchange requests: AC_CLOSED (no requests),
      AC_OPEN (accepts requests), or AC_PUSH_OPEN (accepts requests and pushed updates).""")
  private AcceptRequestTypes acceptRequest;

  @Schema(description = "Server state for data sharing (NONE, CLOSED, MAINTENANCE, OPEN)")
  private GTNetServerStateTypes serverState;

  @Schema(description = """
      Maximum number of instruments (securities or currency pairs) that can be transferred in a single request.
      For example, 300 for LAST_PRICE means a maximum of 300 instruments per request.""")
  private Short maxLimit;

  public GTNetEntityPublicDTO() {
  }

  /**
   * Creates a public DTO from a GTNetEntity, excluding sensitive configuration.
   */
  public GTNetEntityPublicDTO(GTNetEntity entity) {
    this.entityKind = entity.getEntityKindValue();
    this.acceptRequest = entity.getAcceptRequest();
    this.serverState = entity.getServerState();
    this.maxLimit = entity.getMaxLimit();
  }

  public byte getEntityKind() {
    return entityKind;
  }

  public void setEntityKind(byte entityKind) {
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

  public Short getMaxLimit() {
    return maxLimit;
  }

  public void setMaxLimit(Short maxLimit) {
    this.maxLimit = maxLimit;
  }
}
