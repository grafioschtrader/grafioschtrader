package grafioschtrader.reportviews.securitycurrency;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A stock of the watchlist that the market map cannot draw.
 */
@Schema(description = "A stock of the watchlist that the market map cannot draw, with the reason")
public record WatchlistTreemapExcluded(@Schema(description = "Name of the stock") String label,
    @Schema(description = "Translation key of the reason") String reasonKey) {
}
