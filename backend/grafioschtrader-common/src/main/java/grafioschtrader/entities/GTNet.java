package grafioschtrader.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.types.LastpriceRightsCapabilityType;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = GTNet.TABNAME)
@Schema(description = "Contains the domain configuration for the GT-Network")
public class GTNet extends BaseID {

  public static final String TABNAME = "gt_net";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net")
  private Integer idGtNet;

  @Schema(description = "Name or ip address of this remote domain")
  @Column(name = "domain_remote_name")
  private String domainRemoteName;

  @JsonIgnore
  @Schema(description = "Access token to this server, is generated by this server.")
  @Column(name = "token_this")
  private String tokenThis;
  
  @JsonIgnore
  @Schema(description = "Access token to the remote domain, is generated by remote domain.")
  @Column(name = "token_remote")
  private String tokenRemote;
  
  @Schema(description = "May the capability as distributors of this domoain be passed to other ddomains?")
  @Column(name = "spread_capability")
  private boolean spreadCapability;
  
  @Schema(description = "Give data from this server to this remote domain")
  @Column(name = "allow_give_away")
  private boolean allowGiveAway;

  @Schema(description = "That this remote domain accepts requests from this server.")
  @Column(name = "accept_request")
  private boolean acceptRequest;

  @Schema(description = "The daily request limit which the remote domain can request data on this server")
  @Column(name = "daily_req_limit")
  private Integer dailyRequestLimit;

  @Schema(description = "The daily request counter which counts the remote domains request for data on this server. This counter is set to null at UTC 00:00")
  @Column(name = "daily_req_limit_count")
  private Integer dailyRequestLimitCount;

  @Schema(description = "The daily request limit which this server can request data on the remote domain")
  @Column(name = "daily_req_limit_remote")
  private Integer dailyRequestLimitRemote;

  @Schema(description = "Counts the number of request which were done on the remote system thru this server. This counter is set to null at UTC 00:00")
  @Column(name = "daily_req_limit_remote_count")
  private Integer dailyRequestLimitRemoteCount;

  @Schema(description = "That this remote domain supports the distribution of intraday data.")
  @Column(name = "lastprice_supplier_capability")
  private byte lastpriceSupplierCapability;

  @Schema(description = "Should this provider be used by this server.")
  @Column(name = "lastprice_consumer_usage")
  private byte lastpriceConsumerUsage;

  @Schema(description = "Shall be used the last price detail log.")
  @Column(name = "lastprice_use_detail_log")
  private byte lastpriceUseDetailLog;



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

  public String getTokenThis() {
    return tokenThis;
  }

  public void setTokenThis(String tokenThis) {
    this.tokenThis = tokenThis;
  }

  public String getTokenRemote() {
    return tokenRemote;
  }

  public void setTokenRemote(String tokenRemote) {
    this.tokenRemote = tokenRemote;
  }

  public boolean isSpreadCapability() {
    return spreadCapability;
  }

  public void setSpreadCapability(boolean spreadCapability) {
    this.spreadCapability = spreadCapability;
  }

  public boolean isAllowGiveAway() {
    return allowGiveAway;
  }

  public void setAllowGiveAway(boolean allowGiveAway) {
    this.allowGiveAway = allowGiveAway;
  }

  public boolean isAcceptRequest() {
    return acceptRequest;
  }

  public void setAcceptRequest(boolean acceptRequest) {
    this.acceptRequest = acceptRequest;
  }

  public Integer getDailyRequestLimit() {
    return dailyRequestLimit;
  }

  public void setDailyRequestLimit(Integer dailyRequestLimit) {
    this.dailyRequestLimit = dailyRequestLimit;
  }

  public Integer getDailyRequestLimitCount() {
    return dailyRequestLimitCount;
  }

  public void setDailyRequestLimitCount(Integer dailyRequestLimitCount) {
    this.dailyRequestLimitCount = dailyRequestLimitCount;
  }

  public Integer getDailyRequestLimitRemote() {
    return dailyRequestLimitRemote;
  }

  public void setDailyRequestLimitRemote(Integer dailyRequestLimitRemote) {
    this.dailyRequestLimitRemote = dailyRequestLimitRemote;
  }

  public Integer getDailyRequestLimitRemoteCount() {
    return dailyRequestLimitRemoteCount;
  }

  public void setDailyRequestLimitRemoteCount(Integer dailyRequestLimitRemoteCount) {
    this.dailyRequestLimitRemoteCount = dailyRequestLimitRemoteCount;
  }

  public byte getLastpriceUseDetailLog() {
    return lastpriceUseDetailLog;
  }

  public void setLastpriceUseDetailLog(byte lastpriceUseDetailLog) {
    this.lastpriceUseDetailLog = lastpriceUseDetailLog;
  }

  public LastpriceRightsCapabilityType getLastpriceSupplierCapability() {
    return LastpriceRightsCapabilityType.getOpenClosedType(lastpriceConsumerUsage);
  }

  public void setLastpriceSupplierCapability(LastpriceRightsCapabilityType lastpriceSupplierCapability) {
    this.lastpriceSupplierCapability = lastpriceSupplierCapability.getValue();
  }

  public LastpriceRightsCapabilityType getLastpriceConsumerUsage() {
    return LastpriceRightsCapabilityType.getOpenClosedType(lastpriceConsumerUsage);
  }

  public void setLastpriceConsumerUsage(LastpriceRightsCapabilityType lastpriceConsumerUsage) {
    this.lastpriceConsumerUsage = lastpriceConsumerUsage.getValue();
  }

  

  @Override
  public Integer getId() {
    return idGtNet;
  }

}