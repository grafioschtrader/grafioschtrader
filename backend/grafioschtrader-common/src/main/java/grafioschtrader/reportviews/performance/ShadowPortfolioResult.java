package grafioschtrader.reportviews.performance;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.MoneyWeightedReturnStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    The shadow portfolio: what the deposits and withdrawals of the portfolio would be worth if they had gone into the
    benchmark instead, with fractional units, no trading costs and the flow timing of the report. It starts with the
    value of the portfolio on the first day of the comparison. A withdrawal the shadow portfolio cannot fund ends it;
    the terminal figures are then null and shortfallDate names that day. Amounts are in main currency.""")
public record ShadowPortfolioResult(
    @Schema(description = "Value on the first day of the comparison, equal to the portfolio value") Double valueStartMC,
    @Schema(description = "Value on the last day of the comparison; null after a shortfall") Double valueEndMC,
    @Schema(description = "End value minus start value minus the net flows; null after a shortfall") Double gainMC,
    @Schema(description = "Portfolio value minus shadow value at the end; positive means the portfolio did better") Double differenceMC,
    @Schema(description = "Money-weighted return of the shadow portfolio in percent; null after a shortfall or for a non-positive end value") Double mwrPercent,
    @Schema(description = "Money-weighted return p.a. in percent; null below 360 calendar days") Double mwrAnnualizedPercent,
    @Schema(description = "Outcome of the internal rate of return search; null when it was not run") MoneyWeightedReturnStatus mwrStatus,
    @Schema(description = "Day of the first withdrawal the shadow portfolio could not fund; null without shortfall") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate shortfallDate) {
}
