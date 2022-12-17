package grafioschtrader.entities;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * It tracks the holdings of securities, which depends accumulation and
 * reduction transactions. This entity delivers information over the holding
 * periods for securities.
 *
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = "hold_securityaccount_security")
public class HoldSecurityaccountSecurity extends HoldBase {

  @EmbeddedId
  private HoldSecurityaccountSecurityKey hssk;

  @Column(name = "holdings")
  private double hodlings;

  @Column(name = "id_currency_pair_portfolio")
  private Integer idCurrencypairPortoflio;

  @Column(name = "id_currency_pair_tenant")
  private Integer idCurrencypairTenant;

  @Column(name = "split_price_factor ")
  private double splitPriceFactor;

  @Column(name = "margin_real_holdings")
  private Double marginRealHoldings;

  @Column(name = "margin_average_price")
  private Double marginAveragePrice;

  public HoldSecurityaccountSecurity() {
  }

  public HoldSecurityaccountSecurity(Integer idTenant, Integer idPortfolio, Integer idSecuritycashAccount,
      Integer idSecuritycurrency, LocalDate fromHoldDate, double holdings, Double marginRealHoldings,
      Double marginAveragePrice, double splitPriceFactor, Integer idCurrencypairTenant,
      Integer idCurrencypairPortoflio) {
    super(idTenant, idPortfolio);
    hssk = new HoldSecurityaccountSecurityKey(idSecuritycashAccount, idSecuritycurrency, fromHoldDate);
    this.hodlings = holdings;
    this.marginRealHoldings = marginRealHoldings;
    this.marginAveragePrice = marginAveragePrice;
    this.splitPriceFactor = splitPriceFactor;
    this.idCurrencypairTenant = idCurrencypairTenant;
    this.idCurrencypairPortoflio = idCurrencypairPortoflio;
  }

  public double getHodlings() {
    return hodlings;
  }

  public void setHodlings(double hodlings) {
    this.hodlings = hodlings;
  }

  public Double getSplitPriceFactor() {
    return splitPriceFactor;
  }

  public void setSplitPriceFactor(Double splitPriceFactor) {
    this.splitPriceFactor = splitPriceFactor;
  }

  public HoldSecurityaccountSecurityKey getHssk() {
    return hssk;
  }

  public Integer getIdCurrencypairTenant() {
    return idCurrencypairTenant;
  }

  public Integer getIdCurrencypairPortoflio() {
    return idCurrencypairPortoflio;
  }

  public Double getMarginAveragePrice() {
    return marginAveragePrice;
  }

  public void setMarginAveragePrice(Double marginAveragePrice) {
    this.marginAveragePrice = marginAveragePrice;
  }

  @Override
  public String toString() {
    return "HoldSecurityaccountSecurity [hssk=" + hssk + ", hodlings=" + hodlings + ", idCurrencypairPortoflio="
        + idCurrencypairPortoflio + ", idCurrencypairTenant=" + idCurrencypairTenant + ", splitPriceFactor="
        + splitPriceFactor + ", marginRealHoldings=" + marginRealHoldings + ", marginBasePrice=" + marginAveragePrice
        + ", idTenant=" + idTenant + ", idPortfolio=" + idPortfolio + ", toHoldDate=" + toHoldDate + "]";
  }

}
