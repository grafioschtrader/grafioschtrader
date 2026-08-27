package grafioschtrader.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Official year-end and annual-mean foreign exchange rate of one currency for one tax year, taken from the
 * {@code <exchangeRateYearEnd>} elements of an ICTax Kursliste XML file.
 *
 * <p>
 * The Swiss Federal Tax Administration bases these rates on the closing spot rates of the last stock exchange trading
 * day in December, supplied by SIX Financial Information, and declares them the tax value as of 31 December. They
 * therefore deviate slightly from the quotes any price connector delivers for a currency pair, which is why the Swiss
 * tax report prefers them over the year-end {@code historyquote} close.
 * </p>
 *
 * <p>
 * A row is keyed by tax year and currency, not by the upload it arrived with. An upload can be re-imported and a
 * differential Kursliste carries no exchange rates at all, so an upload-scoped row would lose the manual override or
 * disappear entirely. The import upserts {@code yearEndRate}, {@code annualMeanRate} and {@code denomination} and never
 * touches the two override columns.
 * </p>
 *
 * <p>
 * No entity limit applies: there is no user-writable path to this table. It is filled by the Kursliste import and the
 * overrides are editable by an administrator only, one row per tax year and currency.
 * </p>
 */
@Entity
@Table(name = IctaxExchangeRate.TABNAME)
@Schema(description = """
    Official Swiss tax exchange rate of one currency for one tax year, imported from the ICTax Kursliste. Both the
    year-end rate used for wealth valuation and the annual mean rate are kept, each with an optional manually entered
    override that survives a re-import.""")
public class IctaxExchangeRate {

  public static final String TABNAME = "ictax_exchange_rate";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_ictax_exchange_rate")
  @Schema(description = "Unique identifier of this exchange rate row")
  private Integer idIctaxExchangeRate;

  @Column(name = "id_tax_year", nullable = false)
  @Schema(description = "Tax year this rate belongs to")
  private Integer idTaxYear;

  @Column(name = "currency", nullable = false, length = 3)
  @Schema(description = "ISO code of the foreign currency, always quoted against CHF")
  private String currency;

  @Column(name = "denomination", nullable = false)
  @Schema(description = """
      Number of foreign currency units the published rates refer to. The Kursliste quotes low value currencies such as
      JPY or DKK per 100 units, so a rate must be divided by this number before it can be used per unit.""")
  private Integer denomination = 1;

  @Column(name = "year_end_rate")
  @Schema(description = "Official year-end rate in CHF for 'denomination' units, the tax value as of 31 December")
  private Double yearEndRate;

  @Column(name = "annual_mean_rate")
  @Schema(description = """
      Official annual mean rate in CHF for 'denomination' units. Published for converting income for which no specific
      transaction date applies; nothing in Grafioschtrader consumes it yet.""")
  private Double annualMeanRate;

  @Column(name = "year_end_rate_override")
  @Schema(description = """
      Manually entered year-end rate that replaces the imported one, for example when the published rate is rounded too
      coarsely. Null means the imported rate applies. Never written by an import.""")
  private Double yearEndRateOverride;

  @Column(name = "annual_mean_rate_override")
  @Schema(description = "Manually entered annual mean rate that replaces the imported one, or null")
  private Double annualMeanRateOverride;

  public IctaxExchangeRate() {
  }

  public IctaxExchangeRate(Integer idTaxYear, String currency, Integer denomination, Double yearEndRate,
      Double annualMeanRate) {
    this.idTaxYear = idTaxYear;
    this.currency = currency;
    this.denomination = denomination;
    this.yearEndRate = yearEndRate;
    this.annualMeanRate = annualMeanRate;
  }

  /**
   * Returns the year-end rate that applies, in CHF for a single unit of the foreign currency. The manual override wins
   * over the imported rate, and the denomination is divided out. This is the only place where both rules are applied.
   *
   * @return CHF per one unit of {@link #getCurrency()}, or null when neither rate is set
   */
  @Transient
  @JsonIgnore
  public Double getEffectiveYearEndRateChfPerUnit() {
    Double rate = yearEndRateOverride != null ? yearEndRateOverride : yearEndRate;
    if (rate == null) {
      return null;
    }
    return rate / (denomination == null || denomination == 0 ? 1 : denomination);
  }

  public Integer getIdIctaxExchangeRate() {
    return idIctaxExchangeRate;
  }

  public void setIdIctaxExchangeRate(Integer idIctaxExchangeRate) {
    this.idIctaxExchangeRate = idIctaxExchangeRate;
  }

  public Integer getIdTaxYear() {
    return idTaxYear;
  }

  public void setIdTaxYear(Integer idTaxYear) {
    this.idTaxYear = idTaxYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getDenomination() {
    return denomination;
  }

  public void setDenomination(Integer denomination) {
    this.denomination = denomination;
  }

  public Double getYearEndRate() {
    return yearEndRate;
  }

  public void setYearEndRate(Double yearEndRate) {
    this.yearEndRate = yearEndRate;
  }

  public Double getAnnualMeanRate() {
    return annualMeanRate;
  }

  public void setAnnualMeanRate(Double annualMeanRate) {
    this.annualMeanRate = annualMeanRate;
  }

  public Double getYearEndRateOverride() {
    return yearEndRateOverride;
  }

  public void setYearEndRateOverride(Double yearEndRateOverride) {
    this.yearEndRateOverride = yearEndRateOverride;
  }

  public Double getAnnualMeanRateOverride() {
    return annualMeanRateOverride;
  }

  public void setAnnualMeanRateOverride(Double annualMeanRateOverride) {
    this.annualMeanRateOverride = annualMeanRateOverride;
  }
}
