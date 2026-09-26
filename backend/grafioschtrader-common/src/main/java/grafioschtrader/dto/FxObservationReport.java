package grafioschtrader.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Read-only observations of recorded FX rates against exact-date EOD closes. "
    + "Includes timing deviations; the mean need not converge to the configured percentage tariff.")
public record FxObservationReport(
    @Schema(description = "Groups including those containing only skipped rows") List<FxObservationGroup> groups) {
}
