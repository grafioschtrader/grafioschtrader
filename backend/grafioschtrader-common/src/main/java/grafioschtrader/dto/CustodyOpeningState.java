package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * As-of-opening state of one account; accrued fees and year-to-date billed fees exclude VAT. Every field is optional
 * when the user states it: a missing balance counts as zero, a missing assumption is replaced by a note saying so.
 */
@Schema(description = "Custody opening state in fee currency. Account ids refer to the simulation environment.")
public record CustodyOpeningState(Integer cashaccount, Double accruedFees, Double remainingCredits,
    Double billedThisYear, String assumption) {
}
