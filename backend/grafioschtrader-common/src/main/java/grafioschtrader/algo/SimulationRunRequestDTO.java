package grafioschtrader.algo;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.common.DynamicFormField;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;

/**
 * Request body of a historical replay of a simulation environment.
 *
 * <p>
 * The end date is the only thing a run may choose. Where it starts is the opening date of the environment, which is
 * immutable by design: a run that could move its own beginning would be replaying a different portfolio than the one
 * the user established, and the recorded result would no longer say what it was calculated from.
 * </p>
 */
@Schema(description = """
    Request body of a historical replay. Only the end date is supplied; the run begins at the immutable opening date
    of the simulation environment.""")
public class SimulationRunRequestDTO {
  @Schema(description = "YAML map keyed by simulation securities account name: cashaccount (id), accruedFees, remainingCredits, billedThisYear and assumption. Monetary values are in custody fee currency, before VAT.")
  @jakarta.validation.constraints.Size(max = 20000)
  @DynamicFormField(uiOrder = "1.4")
  private String custodyOpeningYaml;

  public String getCustodyOpeningYaml() {
    return custodyOpeningYaml;
  }

  public void setCustodyOpeningYaml(String value) {
    custodyOpeningYaml = value;
  }

  @DynamicFormField(uiOrder = "1.2")
  private boolean applyTaxModels;
  @DynamicFormField(uiOrder = "1.3")
  private boolean generateBondCoupons;

  public boolean isApplyTaxModels() {
    return applyTaxModels;
  }

  public void setApplyTaxModels(boolean value) {
    applyTaxModels = value;
  }

  public boolean isGenerateBondCoupons() {
    return generateBondCoupons;
  }

  public void setGenerateBondCoupons(boolean value) {
    generateBondCoupons = value;
  }

  @Schema(requiredMode = RequiredMode.REQUIRED, description = """
      Last completed end-of-day date the replay evaluates. It must be after the opening date of the environment and
      before the current day, because an unfinished day has no closing price to decide against.""")
  @NotNull
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @DynamicFormField(uiOrder = "1.1")
  private LocalDate endDate;

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }
}
