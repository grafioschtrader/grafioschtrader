package grafioschtrader.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = GTNetwok.TABNAME)
@Schema(description = "Contains the domain configuration for the GT-Network")
public class GTNetwok {

  public static final String TABNAME = "gt_network";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_network")
  private Integer idGtNetwrok;

  @Schema(description = "Name of the domain")
  @Column(name = "domain_remote_name")
  private String domainRemoteName;

  @Schema(description = "Give data to this domain")
  @Column(name = "allow_give_away")
  private boolean allowGiveAway;

  @Schema(description = "Can it used to request data")
  @Column(name = "accept_request")
  private boolean acceptRequest;

  @Schema(description = "The daily request limit which the remote domain can request data on this instance")
  @Column(name = "daily_req_limit")
  private Integer dailyRequestLimit;

  @Schema(description = "The daily request counter which counts the remote domains request for data on this instance. This counter is set to null at UTC 00:00")
  @Column(name = "daily_req_limit_count")
  private Integer dailyRequestLimitCount;

  @Schema(description = "The daily request limit which this instance can request data on the remote domain")
  @Column(name = "daily_req_limit_remote")
  private Integer dailyRequestLimitRemote;

  @Schema(description = "Counts the number of request which were done on the remote system. This counter is set to null at UTC 00:00")
  @Column(name = "daily_req_limit_remote_count")
  private Integer dailyRequestLimitRemoteCount;

}
