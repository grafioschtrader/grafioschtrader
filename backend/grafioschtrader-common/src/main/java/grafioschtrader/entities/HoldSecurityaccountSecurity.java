package grafioschtrader.entities;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Entity representing security holding positions over time periods within security accounts.
 * 
 * <p>
 * <strong>Holdings Tracking:</strong>
 * </p>
 * <p>
 * This entity tracks the holdings of securities based on accumulation and reduction transactions, providing information
 * about holding periods, position sizes, and margin-related calculations. Holdings are created whenever buy/sell
 * transactions result in position changes.
 * </p>
 * 
 * <p>
 * <strong>Time-Based Position Records:</strong>
 * </p>
 * <p>
 * Each record represents a specific time period during which a security position remained constant. New records are
 * created when transactions change the position size, and the previous record's end date is set to maintain temporal
 * continuity.
 * </p>
 * 
 * <p>
 * <strong>Margin Position Support:</strong>
 * </p>
 * <p>
 * The entity provides specialized support for margin trading through dedicated fields for leveraged position tracking,
 * average price calculations across multiple margin openings, and real holdings adjustments for leveraged instruments.
 * </p>
 * 
 * <p>
 * <strong>Multi-Currency Considerations:</strong>
 * </p>
 * <p>
 * Holdings support cross-currency analysis by storing currency pair references for conversion between the security's
 * native currency, portfolio currency, and tenant currency.
 * </p>
 * 
 * <p>
 * <strong>Corporate Actions:</strong>
 * </p>
 * <p>
 * Stock splits and other corporate actions are handled through split price factors that maintain historical price
 * accuracy and position continuity across events.
 * </p>
 * 
 * <p>
 * <strong>Data Access:</strong>
 * </p>
 * <p>
 * This table is not exposed via REST API as it represents internal calculation state used for portfolio analysis and
 * performance calculations.
 * </p>
 */
@Entity
@Table(name = "hold_securityaccount_security")
public class HoldSecurityaccountSecurity extends HoldBase {

  /**
   * Composite primary key containing security account, security, and holding start date. This key ensures unique
   * identification of holding periods.
   */
  @EmbeddedId
  private HoldSecurityaccountSecurityKey hssk;

  /**
   * Number of security units held during this time period. Positive values indicate long positions, negative values
   * indicate short positions.
   */
  @Column(name = "holdings")
  private double hodlings;

  /**
   * Currency pair ID for conversion from security currency to portfolio currency. Null if the security and portfolio
   * use the same currency.
   */
  @Column(name = "id_currency_pair_portfolio")
  private Integer idCurrencypairPortoflio;

  /**
   * Currency pair ID for conversion from security currency to tenant currency. Null if the security and tenant use the
   * same currency.
   */
  @Column(name = "id_currency_pair_tenant")
  private Integer idCurrencypairTenant;

  /**
   * Factor for adjusting historical prices after stock splits. Used to maintain price continuity across corporate
   * actions.
   */
  @Column(name = "split_price_factor")
  private double splitPriceFactor;

  /**
   * Real holdings value for leveraged margin positions.
   * 
   * <p>
   * For margin instruments, this field contains the actual position value that differs from the nominal holdings due to
   * leverage. When not null, this value should be used instead of the holdings field for margin position calculations.
   * </p>
   * 
   * <p>
   * This field is null for non-margin securities where holdings and real holdings are identical.
   * </p>
   */
  @Column(name = "margin_real_holdings")
  private Double marginRealHoldings;

  /**
   * Average price of open margin positions for return calculations.
   * 
   * <p>
   * When multiple margin positions are opened at different prices, this field contains the weighted average price of
   * all open positions. This enables accurate return calculations for complex margin strategies.
   * </p>
   * 
   * <p>
   * The average price is recalculated as new margin positions are opened or existing positions are partially closed,
   * maintaining accuracy across the lifetime of the position.
   * </p>
   * 
   * <p>
   * This field is null for non-margin securities and when no margin positions are currently open.
   * </p>
   */
  @Column(name = "margin_average_price")
  private Double marginAveragePrice;

  public HoldSecurityaccountSecurity() {
  }

  /**
   * Creates a new security holding record with all required parameters.
   * 
   * <p>
   * This constructor initializes a complete holding record including margin-specific data, currency conversion
   * references, and stock split adjustments.
   * </p>
   * 
   * @param idTenant                the tenant identifier (inherited from HoldBase)
   * @param idPortfolio             the portfolio identifier (inherited from HoldBase)
   * @param idSecuritycashAccount   the security account identifier
   * @param idSecuritycurrency      the security identifier
   * @param fromHoldDate            the start date of this holding period
   * @param holdings                the number of units held (positive for long, negative for short)
   * @param marginRealHoldings      real holdings for margin positions (null for regular securities)
   * @param marginAveragePrice      average price for margin positions (null for regular securities)
   * @param splitPriceFactor        factor for stock split price adjustments
   * @param idCurrencypairTenant    currency pair for tenant conversion (null if same currency)
   * @param idCurrencypairPortoflio currency pair for portfolio conversion (null if same currency)
   */
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
