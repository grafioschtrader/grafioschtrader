package grafiosch.entities;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One announced maintenance window of a remote GTNet instance.
 *
 * <p>
 * A row is written when a {@code GT_NET_MAINTENANCE_ALL_C} announcement of that remote is received. A peer may announce
 * several windows, so this is a list per remote rather than a pair of columns on {@link GTNet}. The window is never
 * turned into a persisted server state: every place that would contact the peer asks instead whether the current time
 * falls into one of its windows. That way the state can be neither late nor stale, which it would be if a background
 * job had to flip it — {@code GNetFutureMessageDeliveryTask} runs every five hours and could not open or close a window
 * of a few minutes on time.
 * </p>
 *
 * <p>
 * The row belongs to the announcement it came from. A {@code GT_NET_MAINTENANCE_CANCEL_ALL_C} referencing that
 * announcement deletes its windows again, and the foreign keys to {@code gt_net} and {@code gt_net_message} both
 * cascade on delete, so removing the peer or its message leaves nothing behind.
 * </p>
 *
 * <p>
 * <b>No {@code ExportDefinition} and no entity limit.</b> Like every other {@code gt_net*} table this is instance-wide
 * administrative data owned by no tenant and no user, so it is absent from "export my data" and from the deletion of an
 * account by design. It is likewise unbounded on purpose: rows are written only by
 * {@code MaintenanceAnnouncementHandler} while processing an authenticated peer message, never through a user-reachable
 * write endpoint, and the unique key over the peer and the two window bounds makes a re-delivered announcement an
 * upsert rather than a new row.
 * </p>
 */
@Entity
@Table(name = GTNetMaintenanceWindow.TABNAME)
@Schema(description = """
    An announced maintenance window of a remote GTNet instance. While the current time lies inside one of a remote's
    windows that remote is not contacted: it is skipped as a data supplier and receives no outgoing messages.""")
public class GTNetMaintenanceWindow extends BaseID<Integer> {

  public static final String TABNAME = "gt_net_maintenance_window";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net_maintenance_window")
  private Integer idGtNetMaintenanceWindow;

  @Schema(description = "The remote GTNet instance whose services are unavailable during this window.")
  @Column(name = "id_gt_net", nullable = false)
  private Integer idGtNet;

  @Schema(description = """
      The received GT_NET_MAINTENANCE_ALL_C announcement this window was read from. A cancellation referencing that
      announcement removes the windows it created.""")
  @Column(name = "id_gt_net_message", nullable = false)
  private Integer idGtNetMessage;

  @Schema(description = "UTC start of the maintenance window.")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  @Column(name = "from_date_time", nullable = false)
  private LocalDateTime fromDateTime;

  @Schema(description = "UTC end of the maintenance window. From this moment the remote is contacted again.")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  @Column(name = "to_date_time", nullable = false)
  private LocalDateTime toDateTime;

  public GTNetMaintenanceWindow() {
  }

  public GTNetMaintenanceWindow(Integer idGtNet, Integer idGtNetMessage, LocalDateTime fromDateTime,
      LocalDateTime toDateTime) {
    this.idGtNet = idGtNet;
    this.idGtNetMessage = idGtNetMessage;
    this.fromDateTime = fromDateTime;
    this.toDateTime = toDateTime;
  }

  @Override
  public Integer getId() {
    return idGtNetMaintenanceWindow;
  }

  public Integer getIdGtNetMaintenanceWindow() {
    return idGtNetMaintenanceWindow;
  }

  public void setIdGtNetMaintenanceWindow(Integer idGtNetMaintenanceWindow) {
    this.idGtNetMaintenanceWindow = idGtNetMaintenanceWindow;
  }

  public Integer getIdGtNet() {
    return idGtNet;
  }

  public void setIdGtNet(Integer idGtNet) {
    this.idGtNet = idGtNet;
  }

  public Integer getIdGtNetMessage() {
    return idGtNetMessage;
  }

  public void setIdGtNetMessage(Integer idGtNetMessage) {
    this.idGtNetMessage = idGtNetMessage;
  }

  public LocalDateTime getFromDateTime() {
    return fromDateTime;
  }

  public void setFromDateTime(LocalDateTime fromDateTime) {
    this.fromDateTime = fromDateTime;
  }

  public LocalDateTime getToDateTime() {
    return toDateTime;
  }

  public void setToDateTime(LocalDateTime toDateTime) {
    this.toDateTime = toDateTime;
  }

}
