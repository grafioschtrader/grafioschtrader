package grafioschtrader.gtnet.m2m.model;

import java.util.ArrayList;
import java.util.List;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.gtnet.GTNetServerOnlineStatusTypes;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Public DTO for GTNet used in M2M communication via MessageEnvelope.
 * Excludes gtNetConfig which contains authentication tokens.
 */
@Schema(description = """
    Public representation of a GTNet entry for M2M communication. Contains domain information,
    status flags, and capabilities, but excludes authentication tokens and sensitive configuration.""")
public class GTNetPublicDTO {

  @Schema(description = "Unique identifier of the GTNet entry")
  private Integer idGtNet;

  @Schema(description = "Base URL of the domain (e.g., 'https://example.com:8080')")
  private String domainRemoteName;

  @Schema(description = "Java timezone identifier (e.g., 'Europe/Zurich')")
  private String timeZone;

  @Schema(description = "Whether this domain's server list may be redistributed to other domains")
  private boolean spreadCapability;

  @Schema(description = "Maximum data requests this server accepts per day (null = unlimited)")
  private Integer dailyRequestLimit;

  @Schema(description = "Whether this server is currently busy")
  private boolean serverBusy;

  @Schema(description = "Current online status (UNKNOWN, ONLINE, OFFLINE)")
  private GTNetServerOnlineStatusTypes serverOnline;

  @Schema(description = """
      Controls whether unknown servers can be automatically added during first handshake.
      When false, only pre-existing servers in the GTNet table can complete handshake.""")
  private boolean allowServerCreation;

  @Schema(description = "Data exchange capabilities for different entity kinds")
  private List<GTNetEntityPublicDTO> gtNetEntities = new ArrayList<>();

  public GTNetPublicDTO() {
  }

  /**
   * Creates a public DTO from a GTNet entity, excluding sensitive configuration.
   */
  public GTNetPublicDTO(GTNet gtNet) {
    this.idGtNet = gtNet.getIdGtNet();
    this.domainRemoteName = gtNet.getDomainRemoteName();
    this.timeZone = gtNet.getTimeZone();
    this.spreadCapability = gtNet.isSpreadCapability();
    this.dailyRequestLimit = gtNet.getDailyRequestLimit();
    this.serverBusy = gtNet.isServerBusy();
    this.serverOnline = gtNet.getServerOnline();
    this.allowServerCreation = gtNet.isAllowServerCreation();

    if (gtNet.getGtNetEntities() != null) {
      for (GTNetEntity entity : gtNet.getGtNetEntities()) {
        this.gtNetEntities.add(new GTNetEntityPublicDTO(entity));
      }
    }
  }

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

  public boolean isServerBusy() {
    return serverBusy;
  }

  public void setServerBusy(boolean serverBusy) {
    this.serverBusy = serverBusy;
  }

  public GTNetServerOnlineStatusTypes getServerOnline() {
    return serverOnline;
  }

  public void setServerOnline(GTNetServerOnlineStatusTypes serverOnline) {
    this.serverOnline = serverOnline;
  }

  public boolean isAllowServerCreation() {
    return allowServerCreation;
  }

  public void setAllowServerCreation(boolean allowServerCreation) {
    this.allowServerCreation = allowServerCreation;
  }

  public List<GTNetEntityPublicDTO> getGtNetEntities() {
    return gtNetEntities;
  }

  public void setGtNetEntities(List<GTNetEntityPublicDTO> gtNetEntities) {
    this.gtNetEntities = gtNetEntities;
  }
}
