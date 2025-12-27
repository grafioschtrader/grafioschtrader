package grafioschtrader.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import grafiosch.entities.BaseID;
import grafioschtrader.gtnet.PriceType;
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
    Here are the details of which supplier can provide which price data for a single security or currency pair.
    This supplier must have given approval for their price data to be used on the local server.
    A request to the supplier determines which price data and which type of price data they provide.
    The entities are created here based on this query.""")
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

  @Schema(description = "The security or currency pair to which this setting applies.")
  @JoinColumn(name = "id_securitycurrency")
  @ManyToOne
  private Securitycurrency<?> securitycurrency;

  @Schema(description = "A distinction must be made between whether this setting refers to intraday price data or historical price data.")
  @Column(name = "price_type")
  private byte priceType;

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

  public Securitycurrency<?> getSecuritycurrency() {
    return securitycurrency;
  }

  public void setSecuritycurrency(Securitycurrency<?> securitycurrency) {
    this.securitycurrency = securitycurrency;
  }

  @JsonProperty("priceType")
  public PriceType getPriceType() {
    return PriceType.getPriceType(priceType);
  }

  @JsonProperty("priceType")
  public void setPriceType(PriceType priceType) {
    this.priceType = priceType.getValue();
  }

  @Override
  public Integer getId() {
    return idGtNetSupplierDetail;
  }

}
