/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import static jakarta.persistence.InheritanceType.JOINED;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import grafiosch.BaseConstants;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.Auditable;
import grafiosch.validation.WebUrl;
import grafioschtrader.common.DataBusinessHelper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.Size;

/**
 * It is not mapped to transaction, because the right way goes from security account -> Transaction -> Security.
 */
@Entity
@Table(name = Securitycurrency.TABNAME)
@Inheritance(strategy = JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
public abstract class Securitycurrency<S> extends Auditable implements Serializable {

  public static final String TABNAME = "securitycurrency";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_securitycurrency")
  protected Integer idSecuritycurrency;

  @Schema(description = "This is the reference to the corresponding connector of the historical price data.")
  @Column(name = "id_connector_history")
  @PropertyAlwaysUpdatable
  protected String idConnectorHistory;

  @Schema(description = "This is a comment on the instrument that any user can view.")
  @Column(name = "note")
  @Size(max = BaseConstants.FID_MAX_LETTERS)
  @PropertyAlwaysUpdatable
  protected String note;

  @Schema(description = "All historical price data was loaded for the last time on this date.")
  @Basic(optional = false)
  @Column(name = "full_load_timestamp")
  protected Date fullLoadTimestamp;

  @Schema(description = "This is the reference to the corresponding connector of the intraday price data.")
  @Column(name = "id_connector_intra")
  @PropertyAlwaysUpdatable
  protected String idConnectorIntra;

  @Schema(description = "Retry counter of failed attempts to download historical price data from the data source.")
  @Column(name = "retry_history_load")
  @PropertyAlwaysUpdatable
  protected Short retryHistoryLoad = 0;

  @Schema(description = "Repeat counter of failed attempts to download intraday price data from the data source.")
  @Column(name = "retry_intra_load")
  @PropertyAlwaysUpdatable
  protected Short retryIntraLoad = 0;

  @Schema(title = """
      The URL with access to the historical data differs for each security.
      Therefore, depending on the data source, additional information must be provided for the creation of the URL.""")
  @Column(name = "url_history_extend")
  @Size(min = 1, max = 254)
  @PropertyAlwaysUpdatable
  private String urlHistoryExtend;

  @Schema(title = """
      The URL with access to the intraday data differs for each security.
      Therefore, depending on the data source, additional information must be provided for the creation of the URL.""")
  @Column(name = "url_intra_extend")
  @Size(min = 1, max = 254)
  @PropertyAlwaysUpdatable
  private String urlIntraExtend;

  @Schema(description = "Link to trading exchange, probably not needed for currency pairs. But possibly for cryptocurrency pairs.")
  @Column(name = "stockexchange_link")
  @PropertyAlwaysUpdatable
  @Size(max = BaseConstants.FIELD_SIZE_MAX_G_WEB_URL)
  @WebUrl
  protected String stockexchangeLink;

  // @Max(value=?) @Min(value=?)//if you know range of your decimal fields
  // consider using these annotations to enforce
  // field validation
  @Schema(description = "Last closing price of the previous trading day. But can also be on the “after hours” price.")
  @Column(name = "s_prev_close")
  protected Double sPrevClose;

  @Schema(title = """
      The percentage daily change in shares is usually calculated on the basis
      of the last closing price of the previous trading day. However, it can also
      be based on the after-hours price or the opening price.""")
  @Column(name = "s_change_percentage")
  protected Double sChangePercentage;

  @Schema(description = "Time of the last instraday price update")
  @Basic(optional = false)
  @Column(name = "s_timestamp")
  // @NotNull
  @Temporal(TemporalType.TIMESTAMP)
  protected Date sTimestamp;

  @Schema(description = "Opening price for the last or current trading day")
  @Column(name = "s_open")
  protected Double sOpen;

  @Schema(description = "The most current price - possibly with after hour trade.")
  @Column(name = "s_last")
  protected Double sLast;

  @Schema(description = "Lowest price for the last or current trading day.")
  @Column(name = "s_low")
  protected Double sLow;

  @Schema(description = "Higest price for the last or current trading day.")
  @Column(name = "s_high")
  protected Double sHigh;

  @JsonIgnore
  @JoinColumn(name = "id_securitycurrency")
  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @OrderBy("date ASC")
  protected List<Historyquote> historyquoteList;

  public abstract String getName();

  @JsonIgnore
  public abstract boolean expectVolume();

  public Securitycurrency() {
  }

  public Securitycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public Securitycurrency(Integer idSecuritycurrency, Date sTimestamp) {
    this.idSecuritycurrency = idSecuritycurrency;
    this.sTimestamp = sTimestamp;
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idSecuritycurrency;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public String getIdConnectorHistory() {
    return idConnectorHistory;
  }

  public void setIdConnectorHistory(String idConnectorHistory) {
    this.idConnectorHistory = (idConnectorHistory != null && idConnectorHistory.trim().length() > 0)
        ? idConnectorHistory
        : null;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public String getIdConnectorIntra() {
    return idConnectorIntra;
  }

  public void setIdConnectorIntra(String idConnectorIntra) {
    this.idConnectorIntra = (idConnectorIntra != null && idConnectorIntra.trim().length() > 0) ? idConnectorIntra
        : null;
  }

  @JsonProperty("sLow")
  public Double getSLow() {
    return sLow;
  }

  public void setSLow(Double sLow) {
    this.sLow = sLow;
  }

  @JsonProperty("sHigh")
  public Double getSHigh() {
    return sHigh;
  }

  public void setSHigh(Double sHigh) {
    this.sHigh = sHigh;
  }

  @JsonProperty("sOpen")
  public Double getSOpen() {
    return sOpen;
  }

  public void setSOpen(Double sOpen) {
    this.sOpen = sOpen;
  }

  public Short getRetryHistoryLoad() {
    return retryHistoryLoad;
  }

  public void setRetryHistoryLoad(Short retryHistoryLoad) {
    this.retryHistoryLoad = retryHistoryLoad;
  }

  public Short getRetryIntraLoad() {
    return retryIntraLoad;
  }

  public void setRetryIntraLoad(Short retryIntraLoad) {
    this.retryIntraLoad = retryIntraLoad;
  }

  public String getStockexchangeLink() {
    return stockexchangeLink;
  }

  public void setStockexchangeLink(String stockexchangeLink) {
    this.stockexchangeLink = stockexchangeLink;
  }

  @JsonProperty("sPrevClose")
  public Double getSPrevClose() {
    return sPrevClose;
  }

  public void setSPrevClose(Double sPrevClose) {
    this.sPrevClose = sPrevClose;
  }

  @JsonProperty("sChangePercentage")
  public Double getSChangePercentage() {
    return sChangePercentage == null ? null : DataBusinessHelper.roundStandard(sChangePercentage);
  }

  public Date getFullLoadTimestamp() {
    return fullLoadTimestamp;
  }

  public void setFullLoadTimestamp(Date fullLoadTimestamp) {
    this.fullLoadTimestamp = fullLoadTimestamp;
  }

  public void setSChangePercentage(Double sChangePercentage) {
    this.sChangePercentage = sChangePercentage;
  }

  @JsonProperty("sTimestamp")
  public Date getSTimestamp() {
    return sTimestamp;
  }

  public void setSTimestamp(Date sTimestamp) {
    this.sTimestamp = sTimestamp;
  }

  @JsonProperty("sLast")
  public Double getSLast() {
    return sLast;
  }

  public void setSLast(Double sLast) {
    this.sLast = sLast;
  }

  /*
   * public Integer getCreateUserId() { return createUserId; }
   *
   * public void setCreateUserId(Integer createUserId) { this.createUserId = createUserId; }
   */
  public List<Historyquote> getHistoryquoteList() {
    return historyquoteList;
  }

  public void setHistoryquoteList(List<Historyquote> historyquoteList) {
    this.historyquoteList = historyquoteList;
  }

  public void sortHistoryquoteASC() {
    Comparator<Historyquote> dateComprator = (h1, h2) -> h1.getDate().compareTo(h2.getDate());
    historyquoteList.sort(dateComprator);
  }

  public String getUrlIntraExtend() {
    return urlIntraExtend;
  }

  public void setUrlIntraExtend(String urlIntraExtend) {
    this.urlIntraExtend = urlIntraExtend;
  }

  public String getUrlHistoryExtend() {
    return urlHistoryExtend;
  }

  public void setUrlHistoryExtend(String urlUrlHistoryExtend) {
    this.urlHistoryExtend = urlUrlHistoryExtend;
  }

  public void clearUnusedFields() {
    urlHistoryExtend = idConnectorHistory != null ? urlHistoryExtend : null;
    urlIntraExtend = idConnectorIntra != null ? urlIntraExtend : null;
  }

  public boolean isActiveForIntradayUpdate(Date now) {
    return true;
  }

}
