package grafioschtrader.entities;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.entities.GTNetSupplierDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Stores historical price data quality settings for a GTNet supplier detail entry.
 * Uses a shared primary key with {@link GTNetSupplierDetail} (one-to-one relationship).
 * Cleanup is handled by ON DELETE CASCADE at the database level.
 */
@Entity
@Table(name = GTNetSupplierDetailHist.TABNAME)
@Schema(description = """
    Historical price data quality metrics for a supplier detail entry. Contains retry counters,
    date range boundaries, and OHL quality percentage. Linked 1:1 to gt_net_supplier_detail via shared PK.""")
public class GTNetSupplierDetailHist {

  public static final String TABNAME = "gt_net_supplier_detail_hist";

  @Id
  @Column(name = "id_gt_net_supplier_detail")
  private Integer idGtNetSupplierDetail;

  @JsonIgnore
  @OneToOne
  @JoinColumn(name = "id_gt_net_supplier_detail", insertable = false, updatable = false)
  private GTNetSupplierDetail gtNetSupplierDetail;

  @Schema(description = "Retry counter for failed historical price data downloads.")
  @Column(name = "retry_history_load")
  private Short retryHistoryLoad = 0;

  @Schema(description = "Earliest available history date.")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "history_min_date")
  private LocalDate historyMinDate;

  @Schema(description = "Latest available history date.")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "history_max_date")
  private LocalDate historyMaxDate;

  @Schema(description = "Percentage of quotes with valid open, high, and low values (0-100).")
  @Column(name = "ohl_percentage")
  private Double ohlPercentage;

  public GTNetSupplierDetailHist() {
  }

  public GTNetSupplierDetailHist(Integer idGtNetSupplierDetail) {
    this.idGtNetSupplierDetail = idGtNetSupplierDetail;
  }

  public Integer getIdGtNetSupplierDetail() {
    return idGtNetSupplierDetail;
  }

  public void setIdGtNetSupplierDetail(Integer idGtNetSupplierDetail) {
    this.idGtNetSupplierDetail = idGtNetSupplierDetail;
  }

  public Short getRetryHistoryLoad() {
    return retryHistoryLoad;
  }

  public void setRetryHistoryLoad(Short retryHistoryLoad) {
    this.retryHistoryLoad = retryHistoryLoad;
  }

  public LocalDate getHistoryMinDate() {
    return historyMinDate;
  }

  public void setHistoryMinDate(LocalDate historyMinDate) {
    this.historyMinDate = historyMinDate;
  }

  public LocalDate getHistoryMaxDate() {
    return historyMaxDate;
  }

  public void setHistoryMaxDate(LocalDate historyMaxDate) {
    this.historyMaxDate = historyMaxDate;
  }

  public Double getOhlPercentage() {
    return ohlPercentage;
  }

  public void setOhlPercentage(Double ohlPercentage) {
    this.ohlPercentage = ohlPercentage;
  }
}
