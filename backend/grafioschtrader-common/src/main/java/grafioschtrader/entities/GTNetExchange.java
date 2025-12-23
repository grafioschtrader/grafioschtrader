package grafioschtrader.entities;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

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
import jakarta.persistence.Transient;

@Entity
@Table(name = GTNetExchange.TABNAME)
@Schema(description = """
    Contains the configuration of securities and currency pairs for which you want to update prices via GTNet
    or for which you want to offer prices yourself. This includes intraday and historical price data. There is one such entity per
    currency pair or security.""")
public class GTNetExchange extends BaseID<Integer> {

  public static final String TABNAME = "gt_net_exchange";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net_exchange")
  private Integer idGtNetExchange;

  @Schema(description = "Which security or currency pair these settings apply to.")
  @JoinColumn(name = "id_securitycurrency")
  @ManyToOne
  private Securitycurrency<?> securitycurrency;

  @Schema(description = "I would like to receive the intraday price for this security or currency pair.")
  @Column(name = "lastprice_recv")
  private boolean lastpriceRecv;

  @Schema(description = "I would like to receive historical price data for this security or currency pair.")
  @Column(name = "historical_recv")
  private boolean historicalRecv;

  @Schema(description = "This instance is willing to share the intraday price of this security or currency pair with others.")
  @Column(name = "lastprice_send")
  private boolean lastpriceSend;

  @Schema(description = "This instance is willing to share the historical price data of this security or currency pair with others.")
  @Column(name = "historical_send")
  private boolean historicalSend;

  @Transient
  private Long detailCount;

  /**
   * Transient field to hold the idSecuritycurrency from JSON deserialization.
   * Used when creating new GTNetExchange entries via batchUpdate.
   */
  @Transient
  private Integer idSecuritycurrencyForNew;

  public Integer getIdGtNetExchange() {
    return idGtNetExchange;
  }

  public void setIdGtNetExchange(Integer idGtNetExchange) {
    this.idGtNetExchange = idGtNetExchange;
  }

  public Securitycurrency<?> getSecuritycurrency() {
    return securitycurrency;
  }

  public void setSecuritycurrency(Securitycurrency<?> securitycurrency) {
    this.securitycurrency = securitycurrency;
  }

  /**
   * JSON deserialization setter that extracts the idSecuritycurrency from the incoming JSON object.
   * This avoids the need for @JsonTypeInfo annotations on Securitycurrency by accepting
   * the object as a Map and extracting only the ID needed for processing.
   */
  @JsonProperty("securitycurrency")
  public void setSecuritycurrencyFromJson(Map<String, Object> scMap) {
    if (scMap != null && scMap.containsKey("idSecuritycurrency")) {
      this.idSecuritycurrencyForNew = (Integer) scMap.get("idSecuritycurrency");
    }
  }

  public Integer getIdSecuritycurrencyForNew() {
    return idSecuritycurrencyForNew;
  }

  public boolean isLastpriceRecv() {
    return lastpriceRecv;
  }

  public void setLastpriceRecv(boolean lastpriceRecv) {
    this.lastpriceRecv = lastpriceRecv;
  }

  public boolean isHistoricalRecv() {
    return historicalRecv;
  }

  public void setHistoricalRecv(boolean historicalRecv) {
    this.historicalRecv = historicalRecv;
  }

  public boolean isLastpriceSend() {
    return lastpriceSend;
  }

  public void setLastpriceSend(boolean lastpriceSend) {
    this.lastpriceSend = lastpriceSend;
  }

  public boolean isHistoricalSend() {
    return historicalSend;
  }

  public void setHistoricalSend(boolean historicalSend) {
    this.historicalSend = historicalSend;
  }

  public Long getDetailCount() {
    return detailCount;
  }

  public void setDetailCount(Long detailCount) {
    this.detailCount = detailCount;
  }

  @Override
  public Integer getId() {
    return idGtNetExchange;
  }

}
