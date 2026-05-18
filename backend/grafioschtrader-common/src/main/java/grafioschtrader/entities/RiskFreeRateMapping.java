package grafioschtrader.entities;

import grafiosch.entities.Auditable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Maps an ISO currency code to the synthetic Security (NON_INVESTABLE_INDICES) whose historical quotes serve as that
 * currency's risk-free interest rate. Used by RiskFreeRateService to resolve the rate-of-record for risk metrics such
 * as the Sharpe ratio.
 *
 * <p>
 * The Integer surrogate PK is required by the project's CRUD infrastructure (Auditable / BaseID&lt;Integer&gt;); the
 * functional uniqueness key is {@code currency}, enforced by a UNIQUE index in the schema.
 */
@Schema(description = "Currency -> security mapping for the risk-free rate used in risk metrics (e.g. Sharpe ratio).")
@Entity
@Table(name = RiskFreeRateMapping.TABNAME)
public class RiskFreeRateMapping extends Auditable {

  private static final long serialVersionUID = 1L;

  public static final String TABNAME = "risk_free_rate_mapping";

  @Schema(description = "Integer surrogate primary key.")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_risk_free_rate_mapping")
  private Integer idRiskFreeRateMapping;

  @Schema(description = "ISO 4217 currency code (3 letters). Unique across the table.")
  @Basic(optional = false)
  @NotBlank
  @Size(min = 3, max = 3)
  @Column(name = "currency", length = 3)
  private String currency;

  @Schema(description = "Securitycurrency id of the synthetic security carrying the risk-free rate quotes.")
  @Basic(optional = false)
  @NotNull
  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  public RiskFreeRateMapping() {
  }

  public RiskFreeRateMapping(String currency, Integer idSecuritycurrency) {
    this.currency = currency;
    this.idSecuritycurrency = idSecuritycurrency;
  }

  @Override
  public Integer getId() {
    return idRiskFreeRateMapping;
  }

  public Integer getIdRiskFreeRateMapping() {
    return idRiskFreeRateMapping;
  }

  public void setIdRiskFreeRateMapping(Integer idRiskFreeRateMapping) {
    this.idRiskFreeRateMapping = idRiskFreeRateMapping;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }
}
