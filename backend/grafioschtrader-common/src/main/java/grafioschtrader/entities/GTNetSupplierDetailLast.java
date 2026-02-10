package grafioschtrader.entities;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.entities.GTNetSupplierDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * Stores intraday (last price) settings for a GTNet supplier detail entry.
 * Uses a shared primary key with {@link GTNetSupplierDetail} (one-to-one relationship).
 * Cleanup is handled by ON DELETE CASCADE at the database level.
 */
@Entity
@Table(name = GTNetSupplierDetailLast.TABNAME)
@Schema(description = """
    Intraday price settings for a supplier detail entry. Contains retry counter and
    last intraday update timestamp. Linked 1:1 to gt_net_supplier_detail via shared PK.""")
public class GTNetSupplierDetailLast {

  public static final String TABNAME = "gt_net_supplier_detail_last";

  @Id
  @Column(name = "id_gt_net_supplier_detail")
  private Integer idGtNetSupplierDetail;

  @JsonIgnore
  @OneToOne
  @JoinColumn(name = "id_gt_net_supplier_detail", insertable = false, updatable = false)
  private GTNetSupplierDetail gtNetSupplierDetail;

  @Schema(description = "Retry counter for failed intraday price data downloads.")
  @Column(name = "retry_intra_load")
  private Short retryIntraLoad = 0;

  @Schema(description = "Timestamp of the last intraday price update.")
  @Column(name = "s_timestamp")
  @Temporal(TemporalType.TIMESTAMP)
  private Date sTimestamp;

  public GTNetSupplierDetailLast() {
  }

  public GTNetSupplierDetailLast(Integer idGtNetSupplierDetail) {
    this.idGtNetSupplierDetail = idGtNetSupplierDetail;
  }

  public Integer getIdGtNetSupplierDetail() {
    return idGtNetSupplierDetail;
  }

  public void setIdGtNetSupplierDetail(Integer idGtNetSupplierDetail) {
    this.idGtNetSupplierDetail = idGtNetSupplierDetail;
  }

  public Short getRetryIntraLoad() {
    return retryIntraLoad;
  }

  public void setRetryIntraLoad(Short retryIntraLoad) {
    this.retryIntraLoad = retryIntraLoad;
  }

  public Date getSTimestamp() {
    return sTimestamp;
  }

  public void setSTimestamp(Date sTimestamp) {
    this.sTimestamp = sTimestamp;
  }
}
