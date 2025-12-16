package grafioschtrader.entities;

import java.time.LocalDateTime;

import grafiosch.entities.BaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = GTNetSupplier.TABNAME)
@Schema(description = """
    Storage of which supplier can provide which price data for securities and currency pairs.
    This supplier must have given approval for their price data to be used on the local server.
    After that, a request is used to determine which securities and currency pairs the price data is accessible for.""")
public class GTNetSupplier extends BaseID<Integer> {

  public static final String TABNAME = "gt_net_supplier";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net_supplier")
  private Integer idGtNetSupplier;

  @Schema(description = "Which suppliers these settings apply to.")
  @JoinColumn(name = "id_gt_net")
  @ManyToOne
  private GTNet gtNet;

  @Schema(description = "This entity is updated through periodic polling or manual triggering. This is the timestamp of the update.")
  @Column(name = "last_update")
  private LocalDateTime lastUpdate;

  public Integer getIdGtNetSupplier() {
    return idGtNetSupplier;
  }

  public void setIdGtNetSupplier(Integer idGtNetSupplier) {
    this.idGtNetSupplier = idGtNetSupplier;
  }

  public GTNet getGtNet() {
    return gtNet;
  }

  public void setGtNet(GTNet gtNet) {
    this.gtNet = gtNet;
  }

  public LocalDateTime getLastUpdate() {
    return lastUpdate;
  }

  public void setLastUpdate(LocalDateTime lastUpdate) {
    this.lastUpdate = lastUpdate;
  }

  @Override
  public Integer getId() {
    return idGtNetSupplier;
  }

}
