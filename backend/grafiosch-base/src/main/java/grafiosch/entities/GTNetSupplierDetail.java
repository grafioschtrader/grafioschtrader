package grafiosch.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

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
@Table(name = GTNetSupplierDetail.TABNAME)
@Schema(description = """
    Records which GT-Net supplier can provide which price data for a specific security or currency pair.
    This information serves two purposes:
    1. **Display**: Shows users which suppliers offer data for each instrument
    2. **Request filtering**: For AC_OPEN suppliers, only instruments with a matching GTNetSupplierDetail entry
       are included in price data requests. AC_PUSH_OPEN suppliers receive all instruments regardless of
       these entries.

    Entries are populated automatically by the exchange synchronization background task (job 23), which queries
    each supplier's available instruments and updates these records accordingly.""")
public class GTNetSupplierDetail extends BaseID<Integer> {

  public static final String TABNAME = "gt_net_supplier_detail";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net_supplier_detail")
  private Integer idGtNetSupplierDetail;

  @Schema(description = "Reference to the GTNet configuration for this supplier")
  @JsonIgnore
  @JoinColumn(name = "id_gt_net")
  @ManyToOne
  private GTNetConfig gtNetConfig;

  @Schema(description = "The ID of the security or currency pair to which this setting applies.")
  @Column(name = "id_entity")
  private Integer idEntity;

  @Schema(description = """
      Indicates the type of exchange data: LAST_PRICE (0) for intraday prices or HISTORICAL_PRICES (1) for historical
      price data. Filtering is applied separately for each entity kind during the respective exchange process.""")
  @Column(name = "entity_kind")
  private byte entityKind;

  public Integer getIdGtNetSupplierDetail() {
    return idGtNetSupplierDetail;
  }

  public void setIdGtNetSupplierDetail(Integer idGtNetSupplierDetail) {
    this.idGtNetSupplierDetail = idGtNetSupplierDetail;
  }

  public GTNetConfig getGtNetConfig() {
    return gtNetConfig;
  }

  public void setGtNetConfig(GTNetConfig gtNetConfig) {
    this.gtNetConfig = gtNetConfig;
  }

  public Integer getIdGtNet() {
    return gtNetConfig != null ? gtNetConfig.getIdGtNet() : null;
  }

  public Integer getIdEntity() {
    return idEntity;
  }

  public void setIdEntity(Integer idEntity) {
    this.idEntity = idEntity;
  }

  @JsonProperty("entityKind")
  public byte getEntityKind() {
    return entityKind;
  }

  @JsonProperty("entityKind")
  public void setEntityKind(byte entityKind) {
    this.entityKind = entityKind;
  }

  @Override
  public Integer getId() {
    return idGtNetSupplierDetail;
  }

}
