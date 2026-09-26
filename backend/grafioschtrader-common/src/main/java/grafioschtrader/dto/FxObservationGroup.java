package grafioschtrader.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** Statistics of eligible conversions, all deviations and modelled markups expressed in percent. */
@Schema(description = "Observed adverse deviation from the EOD close by pair, conversion kind and FX tariff period. "
    + "Positive values are client costs; timing bias is included and is not a measured broker fee.")
public record FxObservationGroup(@Schema(description = "Stored currency pair identifier") Integer idCurrencypair,
    @Schema(description = "Stored pair direction, from/to") String currencyPair,
    @Schema(description = "TRADE, INCOME or TRANSFER") FxMarkupRequest.Kind kind,
    @Schema(description = "Inclusive tariff period start, ISO date; null for flat/no applicable period") String periodFrom,
    @Schema(description = "Inclusive tariff period end, ISO date; null for open-ended/flat/no period") String periodTo,
    @Schema(description = "Accepted observation count") int count,
    @Schema(description = "Rows without a usable exact-date EOD close") int skippedNoClose,
    @Schema(description = "Rows without a usable recorded exchange rate; checked before the close") int skippedNoRate,
    @Schema(description = "Rows whose absolute signed deviation is at least 8 percent") int skippedOutlier,
    @Schema(description = "Arithmetic mean adverse deviation in percent, null for no observations") Double mean,
    @Schema(description = "Median adverse deviation in percent, null for no observations") Double median,
    @Schema(description = "Sample standard deviation (n-1), null for fewer than two observations") Double stdDev,
    @Schema(description = "Mean weighted by absolute converted volume in pair to-currency at EOD mid") Double volumeWeightedMean,
    @Schema(description = "Covered observation count, including explicit zero; null without an FX section") Integer modelledCount,
    @Schema(description = "Mean modelled percent over covered observations only; null when none are covered") Double modelledMean,
    @Schema(description = "Accepted observations without a matched tariff, including missing section and invalid configuration") int uncoveredCount,
    @Schema(description = "Counts of each coverage outcome for accepted observations; INVALID is never zero markup") Map<FxOutcome, Integer> outcomes,
    @Schema(description = "Configuration error, if any; observation statistics remain available") String error) {
}
