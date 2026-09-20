package grafioschtrader.dto;

import grafiosch.common.DynamicFormField;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Saved settings of the two cards that rank held instruments by how far they moved, and the source of their settings
 * form.
 *
 * <p>
 * The session of the third branch is not here on purpose: the reader changes it on the card itself, so it reaches the
 * server as a setting of one read and is never written into the layout.
 * </p>
 */
public class HoldingMoversConfig {

  /** Rows shown per ordering per branch. A card that lists more than a handful stops being a summary. */
  @DynamicFormField(uiOrder = "1.1", labelKey = "DASHBOARD_MOVERS_TOP_N")
  @NotNull
  @Min(1)
  @Max(5)
  private Integer topN = 3;

  public Integer getTopN() {
    return topN;
  }

  public void setTopN(Integer topN) {
    this.topN = topN;
  }
}
