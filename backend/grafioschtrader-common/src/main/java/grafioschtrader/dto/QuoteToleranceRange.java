package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Bounds an administrator allows for the quote tolerance of a standing order. The tolerance says how far the price or
    exchange rate may deviate from the execution date; its magnitude is the window in days on both sides and its sign
    decides which side is tried first, a negative value preferring the past. Both bounds are inclusive and are derived
    from the global parameter gt.standing.order.quote.tolerance, which is written as 'min:max'.""")
public record QuoteToleranceRange(
    @Schema(description = "Smallest admissible tolerance; negative values allow reaching into the past first") byte min,
    @Schema(description = "Largest admissible tolerance; positive values allow reaching into the future first") byte max) {
}
