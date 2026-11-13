package grafioschtrader.reportviews.securitycurrency;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Market map of a watchlist: the area of a tile is the market capitalisation of a stock in the main currency of the
 * tenant, its colour the current price change. Built by the backend as a flat node list; the client only maps it to a
 * Plotly treemap trace.
 */
@Schema(description = "Market map of the stocks of a watchlist, as a flat node list")
public record WatchlistTreemap(
    @Schema(description = "Main currency of the tenant, the currency of every area") String mainCurrency,
    @Schema(description = "Number of instruments of the watchlist that are no active direct stocks")
    int notApplicableCount,
    @Schema(description = "Nodes with the root first; empty when no stock can be drawn")
    List<WatchlistTreemapNode> nodes,
    @Schema(description = "Stocks that cannot be drawn") List<WatchlistTreemapExcluded> excluded) {
}
