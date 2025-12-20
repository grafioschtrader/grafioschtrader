package grafioschtrader.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyOnlyCreation;
import grafiosch.entities.BaseID;
import grafioschtrader.gtnet.GTNetExchangeStatusTypes;
import grafioschtrader.gtnet.GTNetServerOnlineStatusTypes;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a remote domain configuration in the GT-Network (GTNet) peer-to-peer system.
 *
 * Each GTNet entry defines a connection to another Grafioschtrader instance with capability flags.
 * The local instance maintains one entry per known remote domain, plus an entry representing itself
 * (identified via {@code gtnet.my.entry.id} global parameter). This entity contains basic information
 * about the server. The connection configuration (including authentication tokens) is stored in the
 * associated {@link GTNetConfig} entity.
 */
@Entity
@Table(name = GTNet.TABNAME)

public class GTNet extends BaseID<Integer> {

  public static final String TABNAME = "gt_net";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net")
  private Integer idGtNet;

  @Schema(description = """
      Base URL of the remote domain (e.g., 'https://example.com:8080'). Used as the target for all M2M HTTP requests.
      When this URL resolves to the local machine's network interfaces, the entry is identified as 'my entry' and its
      ID is stored in the global parameter 'gtnet.my.entry.id'.""")
  @NotNull
  // @WebUrl
  @Column(name = "domain_remote_name")
  @PropertyOnlyCreation
  private String domainRemoteName;

  @OneToOne(fetch = FetchType.EAGER)
  @JoinColumn(nullable = true, name = "id_gt_net")
  @PropertyAlwaysUpdatable
  private GTNetConfig gtNetConfig;
  
  
  @Schema(description = """
      Java timezone identifier (e.g., 'Europe/Zurich', 'America/New_York'). Helps users understand the operating
      hours of the remote server and is used for maintenance window announcements.""")
  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 50)
  @Column(name = "time_zone")
  @PropertyAlwaysUpdatable
  private String timeZone;

  @Schema(description = """
      Controls whether this domain's server list may be redistributed to other domains. When true, a third party
      can request and receive information about this server's known peers, enabling network discovery beyond
      direct connections.""")
  @PropertyAlwaysUpdatable
  @Column(name = "spread_capability")
  private boolean spreadCapability;

  @Schema(description = """
      Server state for entity/historical data sharing. Indicates whether this server is available to provide entity
      data (e.g., historical quotes) to the remote domain. Uses GTNetServerStateTypes enum values.""")
  @JsonIgnore
  @Column(name = "entity_server_state")
  private byte entityServerState;

  @Schema(description = """
      Enables bidirectional entity data exchange. When true, this server accepts entity data requests from the
      remote domain, allowing mutual data sharing. When false, entity exchange is unidirectional or disabled.""")
  @Column(name = "accept_entity_request")
  private boolean acceptEntityRequest;

  @Schema(description = """
      Maximum number of data requests the remote domain can make to this server per day. This limit protects the
      local server from excessive load. Set to null for unlimited requests. The counter (dailyRequestLimitCount)
      tracks usage and resets at UTC midnight.""")
  @Column(name = "daily_req_limit")
  private Integer dailyRequestLimit;
  

  @Schema(description = """
      Maximum number of data requests this server can make to the remote domain per day. This value is typically
      communicated by the remote domain during handshake or negotiation. Set to null if no limit was specified.""")
  @Column(name = "daily_req_limit_remote")
  private Integer dailyRequestLimitRemote;

 

  @Schema(description = """
      Server state for intraday price sharing. Indicates whether the remote domain is available to provide
      intraday/last price data. Uses GTNetServerStateTypes enum values.""")
  @JsonIgnore
  @Column(name = "lastprice_server_state")
  private byte lastpriceServerState;

  @Schema(description = """
      Enables acceptance of intraday price requests from the remote domain. When true, this server will respond
      to last price queries from the remote.""")
  @Column(name = "accept_lastprice_request")
  @PropertyAlwaysUpdatable
  private boolean acceptLastpriceRequest;

  

  @Schema(description = """
      This flag can be used to control which messages should still be communicated to the recipient. 
      If it is true, only server status changes can be communicated to the target system.  
      For your own server, this change can be set via the UI. 
      Any change to this flag should be communicated to the other server immediately.""")
  @Column(name = "server_busy")
  @PropertyAlwaysUpdatable
  private boolean serverBusy;
  
  @Schema(description = """
      This should reflect the current status of the system. No communication will take place with a system that is offline.
      This status is communicated to the other servers by starting and stopping the server. However,
      it can also be changed via the user interface. Values: 0=Unknown, 1=Online, 2=Offline.""")
  @Column(name = "server_online")
  @PropertyAlwaysUpdatable
  private byte serverOnline;
  
  
  public Integer getIdGtNet() {
    return idGtNet;
  }

  public void setIdGtNet(Integer idGtNet) {
    this.idGtNet = idGtNet;
  }

  public String getDomainRemoteName() {
    return domainRemoteName;
  }

  public void setDomainRemoteName(String domainRemoteName) {
    this.domainRemoteName = domainRemoteName;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public boolean isSpreadCapability() {
    return spreadCapability;
  }

  public void setSpreadCapability(boolean spreadCapability) {
    this.spreadCapability = spreadCapability;
  }

  @JsonProperty("entityServerState")
  public GTNetServerStateTypes getEntityServerState() {
    return GTNetServerStateTypes.getGTNetServerStateType(entityServerState);
  }

  @JsonProperty("entityServerState")
  public void setEntityServerState(GTNetServerStateTypes entityServerState) {
    this.entityServerState = entityServerState.getValue();
  }

  public boolean isAcceptEntityRequest() {
    return acceptEntityRequest;
  }

  public void setAcceptEntityRequest(boolean acceptEntityRequest) {
    this.acceptEntityRequest = acceptEntityRequest;
  }

  public Integer getDailyRequestLimit() {
    return dailyRequestLimit;
  }

  public void setDailyRequestLimit(Integer dailyRequestLimit) {
    this.dailyRequestLimit = dailyRequestLimit;
  }

  
  public GTNetConfig getGtNetConfig() {
    return gtNetConfig;
  }

  public void setGtNetConfig(GTNetConfig gtNetConfig) {
    this.gtNetConfig = gtNetConfig;
  }

  public Integer getDailyRequestLimitRemote() {
    return dailyRequestLimitRemote;
  }

  public void setDailyRequestLimitRemote(Integer dailyRequestLimitRemote) {
    this.dailyRequestLimitRemote = dailyRequestLimitRemote;
  }

  
  @JsonProperty("lastpriceServerState")
  public GTNetServerStateTypes getLastpriceServerState() {
    return GTNetServerStateTypes.getGTNetServerStateType(lastpriceServerState);
  }

  @JsonProperty("lastpriceServerState")
  public void setLastpriceServerState(GTNetServerStateTypes lastpriceServerState) {
    this.lastpriceServerState = lastpriceServerState.getValue();
  }

  public boolean isAcceptLastpriceRequest() {
    return acceptLastpriceRequest;
  }

  public void setAcceptLastpriceRequest(boolean acceptLastpriceRequest) {
    this.acceptLastpriceRequest = acceptLastpriceRequest;
  }

  

  public boolean isServerBusy() {
    return serverBusy;
  }

  public void setServerBusy(boolean serverBusy) {
    this.serverBusy = serverBusy;
  }

  @JsonProperty("serverOnline")
  public GTNetServerOnlineStatusTypes getServerOnline() {
    return GTNetServerOnlineStatusTypes.getGTNetServerOnlineStatusType(serverOnline);
  }

  @JsonProperty("serverOnline")
  public void setServerOnline(GTNetServerOnlineStatusTypes serverOnline) {
    this.serverOnline = serverOnline.getValue();
  }

  // Computed properties for JSON serialization (read-only)

  /**
   * Returns whether this GTNet entry is authorized (tokens have been exchanged).
   * An entry is considered authorized if it has a GTNetConfig with a tokenRemote set.
   *
   * @return true if authorized, false otherwise
   */
  @JsonProperty("authorized")
  public boolean isAuthorized() {
    return gtNetConfig != null && gtNetConfig.getTokenRemote() != null;
  }

  /**
   * Returns the lastprice exchange status from GTNetConfig.
   * Returns ES_NO_EXCHANGE if no GTNetConfig exists.
   *
   * @return the lastprice exchange status
   */
  @JsonProperty("lastpriceExchange")
  public GTNetExchangeStatusTypes getLastpriceExchange() {
    return gtNetConfig != null ? gtNetConfig.getLastpriceExchange() : GTNetExchangeStatusTypes.ES_NO_EXCHANGE;
  }

  /**
   * Returns the entity exchange status from GTNetConfig.
   * Returns ES_NO_EXCHANGE if no GTNetConfig exists.
   *
   * @return the entity exchange status
   */
  @JsonProperty("entityExchange")
  public GTNetExchangeStatusTypes getEntityExchange() {
    return gtNetConfig != null ? gtNetConfig.getEntityExchange() : GTNetExchangeStatusTypes.ES_NO_EXCHANGE;
  }

  @Override
  public Integer getId() {
    return idGtNet;
  }

}
