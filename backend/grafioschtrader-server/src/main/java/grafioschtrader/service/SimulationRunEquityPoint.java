package grafioschtrader.service;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    One point of the equity curve of a completed historical replay: a day whose closing state could be valued
    completely. Days without a complete valuation are no points; the audit trail names them.""")
public record SimulationRunEquityPoint(
    @Schema(description = "The valued day, the opening date for the first point") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate date,
    @Schema(description = "Closing equity of the environment on that day, in the currency of the environment") double equity,
    @Schema(description = """
        Capital invested up to that day, in the currency of the environment: the equity of the first point plus all
        deposits and withdrawals booked since. The distance between equity and invested capital is what the run
        earned, so a deposit raises both lines and does not appear as a gain.""") double investedCapital) {
}
