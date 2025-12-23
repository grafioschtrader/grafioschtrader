package grafioschtrader.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.BaseID;
import grafioschtrader.gtnet.AcceptRequestTypes;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = GTNetEntity.TABNAME)
@Schema(description = """
    This defines what can be exchanged with an instance. One entry per exchangeable information object.""")
public class GTNetEntity extends BaseID<Integer> {

  public static final String TABNAME = "gt_net_entity";
  
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net_entity")
  private Integer idGtNetEntity;

  @Schema(description = "Reference to the parent GTNet domain entry")
  @Column(name = "id_gt_net", nullable = false, insertable = false, updatable = false)
  private Integer idGtNet;

  @Schema(description = "What type of information is provided?")
  @Column(name = "entity_kind")
  private byte entityKind;  
    
  @Schema(description = """
      Defines how this server handles incoming data exchange requests: AC_CLOSED (no requests),
      AC_OPEN (accepts requests), or AC_PUSH_OPEN (accepts requests and pushed updates).
      AC_PUSH_OPEN is only available for LAST_PRICE exchange kind.""")
  @Column(name = "accept_request")
  @PropertyAlwaysUpdatable
  private byte acceptRequest;
  
  @Schema(description = """
      Server state for data sharing. Indicates whether the remote domain is available to provide
      this kind of data. Uses GTNetServerStateTypes enum values.""")
  @Column(name = "server_state")
  private byte serverState;

  @Schema(description = "Entity-specific configuration for exchange settings, logging, and consumer usage")
  @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "id_gt_net_entity", referencedColumnName = "id_gt_net_entity", insertable = false, updatable = false)
  private GTNetConfigEntity gtNetConfigEntity;

  public GTNetExchangeKindType getEntityKind() {
    return GTNetExchangeKindType.getGTNetExchangeKindType(entityKind);
  }

  public void setEntityKind(GTNetExchangeKindType entityKind) {
    this.entityKind = entityKind.getValue();
  }

  
  public Integer getIdGtNetEntity() {
    return idGtNetEntity;
  }

  public void setIdGtNetEntity(Integer idGtNetEntity) {
    this.idGtNetEntity = idGtNetEntity;
  }

  public AcceptRequestTypes getAcceptRequest() {
    return AcceptRequestTypes.getAcceptRequestType(acceptRequest);
  }

  public void setAcceptRequest(AcceptRequestTypes acceptRequest) {
    this.acceptRequest = acceptRequest.getValue();
  }

  /**
   * Checks if this entity accepts incoming data requests.
   *
   * @return true if acceptRequest is AC_OPEN or AC_PUSH_OPEN
   */
  public boolean isAccepting() {
    return getAcceptRequest().isAccepting();
  }

  public GTNetServerStateTypes getServerState() {
    return GTNetServerStateTypes.getGTNetServerStateType(serverState);
  }

  public void setServerState(GTNetServerStateTypes serverState) {
    this.serverState = serverState.getValue();
  }

  public Integer getIdGtNet() {
    return idGtNet;
  }

  public void setIdGtNet(Integer idGtNet) {
    this.idGtNet = idGtNet;
  }

  public GTNetConfigEntity getGtNetConfigEntity() {
    return gtNetConfigEntity;
  }

  public void setGtNetConfigEntity(GTNetConfigEntity gtNetConfigEntity) {
    this.gtNetConfigEntity = gtNetConfigEntity;
  }

  @Override
  public Integer getId() {
    return idGtNetEntity;
  }
}
