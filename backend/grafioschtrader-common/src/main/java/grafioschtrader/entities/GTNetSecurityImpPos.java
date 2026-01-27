package grafioschtrader.entities;

import grafiosch.entities.BaseID;
import grafioschtrader.validation.ValidCurrencyCode;
import grafioschtrader.validation.ValidISIN;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Position entity representing a single security to be imported via GTNet. Each position contains
 * identification data (ISIN, ticker symbol) and currency for looking up security metadata from
 * GTNet peers.
 */
@Schema(description = """
    Position entity for GTNet security import. Represents a single security to be looked up from
    GTNet peers. Contains identification data (ISIN, ticker symbol) and currency. After successful
    lookup, the idSecuritycurrency field links to the matched or created security.
    """)
@Entity
@Table(name = GTNetSecurityImpPos.TABNAME)
public class GTNetSecurityImpPos extends BaseID<Integer> {

  public static final String TABNAME = "gt_net_security_imp_pos";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_gt_net_security_imp_pos")
  private Integer idGtNetSecurityImpPos;

  @Schema(description = "Reference to the parent import header.")
  @NotNull
  @Column(name = "id_gt_net_security_imp_head")
  private Integer idGtNetSecurityImpHead;

  @Schema(description = """
      International Securities Identification Number (ISIN) for the security.
      Either ISIN or ticker symbol (or both) should be provided for lookup.""")
  @ValidISIN
  @Size(max = 12)
  @Column(name = "isin")
  private String isin;

  @Schema(description = """
      Stock ticker symbol for the security. Either ISIN or ticker symbol (or both)
      should be provided for lookup. Maximum 6 characters.""")
  @Size(max = 6)
  @Column(name = "ticker_symbol")
  private String tickerSymbol;

  @Schema(description = "ISO 4217 currency code for the security (3 characters, e.g., 'USD', 'EUR').")
  @NotNull
  @ValidCurrencyCode
  @Size(min = 3, max = 3)
  @Column(name = "currency")
  private String currency;

  @Schema(description = """
      Reference to the matched or created security after successful GTNet lookup.
      Null until a security is successfully matched or created from the lookup results.""")
  @OneToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "id_securitycurrency")
  private Security security;

  public GTNetSecurityImpPos() {
    super();
  }

  public GTNetSecurityImpPos(Integer idGtNetSecurityImpHead, String isin, String tickerSymbol, String currency) {
    super();
    this.idGtNetSecurityImpHead = idGtNetSecurityImpHead;
    this.isin = isin;
    this.tickerSymbol = tickerSymbol;
    this.currency = currency;
  }

  public Integer getIdGtNetSecurityImpPos() {
    return idGtNetSecurityImpPos;
  }

  public void setIdGtNetSecurityImpPos(Integer idGtNetSecurityImpPos) {
    this.idGtNetSecurityImpPos = idGtNetSecurityImpPos;
  }

  public Integer getIdGtNetSecurityImpHead() {
    return idGtNetSecurityImpHead;
  }

  public void setIdGtNetSecurityImpHead(Integer idGtNetSecurityImpHead) {
    this.idGtNetSecurityImpHead = idGtNetSecurityImpHead;
  }

  public String getIsin() {
    return isin;
  }

  public void setIsin(String isin) {
    this.isin = isin;
  }

  public String getTickerSymbol() {
    return tickerSymbol;
  }

  public void setTickerSymbol(String tickerSymbol) {
    this.tickerSymbol = tickerSymbol;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Security getSecurity() {
    return security;
  }

  public void setSecurity(Security security) {
    this.security = security;
  }

  @Override
  public Integer getId() {
    return this.idGtNetSecurityImpPos;
  }

  @Override
  public String toString() {
    return "GTNetSecurityImpPos [idGtNetSecurityImpPos=" + idGtNetSecurityImpPos + ", idGtNetSecurityImpHead="
        + idGtNetSecurityImpHead + ", isin=" + isin + ", tickerSymbol=" + tickerSymbol + ", currency=" + currency
        + ", security=" + (security != null ? security.getIdSecuritycurrency() : null) + "]";
  }
}
