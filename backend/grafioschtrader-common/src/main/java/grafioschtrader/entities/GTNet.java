package grafioschtrader.entities;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyOnlyCreation;
import grafiosch.entities.BaseID;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetExchangeStatusTypes;
import grafioschtrader.gtnet.GTNetServerOnlineStatusTypes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a remote domain configuration in the GT-Network (GTNet) peer-to-peer system.
 *
 * Each GTNet entry defines a connection to another Grafioschtrader instance with capability flags. The local instance
 * maintains one entry per known remote domain, plus an entry representing itself (identified via
 * {@code gtnet.my.entry.id} global parameter). This entity contains basic information about the server. The connection
 * configuration (including authentication tokens) is stored in the associated {@link GTNetConfig} entity.
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

  @Schema(description = """
      Collection of data type configurations for this remote domain. Each entry defines
      the exchange capability for a specific data type (LAST_PRICE, HISTORICAL_PRICES, etc.).""")
  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "id_gt_net", nullable = false)
  private List<GTNetEntity> gtNetEntities = new ArrayList<>();

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

  public boolean isServerBusy() {
    return serverBusy;
  }

  public void setServerBusy(boolean serverBusy) {
    this.serverBusy = serverBusy;
  }

  public List<GTNetEntity> getGtNetEntities() {
    return gtNetEntities;
  }

  public void setGtNetEntities(List<GTNetEntity> gtNetEntities) {
    this.gtNetEntities = gtNetEntities;
  }

  /**
   * Gets the GTNetEntity for a specific data type.
   *
   * @param kind the entity kind to search for
   * @return Optional containing the matching GTNetEntity, or empty if not found
   */
  public Optional<GTNetEntity> getEntity(GTNetExchangeKindType kind) {
    return gtNetEntities.stream().filter(e -> e.getEntityKind() == kind).findFirst();
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
   * Returns whether this GTNet entry is authorized (tokens have been exchanged). An entry is considered authorized if
   * it has a GTNetConfig with a tokenRemote set.
   *
   * @return true if authorized, false otherwise
   */
  @JsonProperty("authorized")
  public boolean isAuthorized() {
    return gtNetConfig != null && gtNetConfig.getTokenRemote() != null;
  }

  /**
   * Returns the lastprice exchange status from the LAST_PRICE entity's GTNetConfigEntity. Returns ES_NO_EXCHANGE if no
   * matching entity or config exists.
   *
   * @return the lastprice exchange status
   */
  @JsonProperty("lastpriceExchange")
  public GTNetExchangeStatusTypes getLastpriceExchange() {
    return getEntity(GTNetExchangeKindType.LAST_PRICE).map(GTNetEntity::getGtNetConfigEntity)
        .map(GTNetConfigEntity::getExchange).orElse(GTNetExchangeStatusTypes.ES_NO_EXCHANGE);
  }

  /**
   * Returns the entity exchange status from the HISTORICAL_PRICES entity's GTNetConfigEntity. Returns ES_NO_EXCHANGE if
   * no matching entity or config exists.
   *
   * @return the entity exchange status
   */
  @JsonProperty("entityExchange")
  public GTNetExchangeStatusTypes getEntityExchange() {
    return getEntity(GTNetExchangeKindType.HISTORICAL_PRICES).map(GTNetEntity::getGtNetConfigEntity)
        .map(GTNetConfigEntity::getExchange).orElse(GTNetExchangeStatusTypes.ES_NO_EXCHANGE);
  }

  /**
   * Gets or creates a GTNetEntity for the specified kind. If the entity doesn't exist, creates a new one and adds it to
   * the collection.
   *
   * @param kind the entity kind
   * @return the existing or newly created GTNetEntity
   */
  public GTNetEntity getOrCreateEntity(GTNetExchangeKindType kind) {
    return getEntity(kind).orElseGet(() -> {
      GTNetEntity newEntity = new GTNetEntity();
      newEntity.setIdGtNet(this.idGtNet);
      newEntity.setEntityKind(kind);
      gtNetEntities.add(newEntity);
      return newEntity;
    });
  }

 

  @Override
  public Integer getId() {
    return idGtNet;
  }

}
