package grafioschtrader.reportviews.securityaccount;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The wealth of the tenant at the report date distributed over the individual holdings, valued on a hypothetical sale
 * of every position. Open CFD/Forex positions have no tile of their own; their unrealized gain/loss belongs to the cash
 * of the currency they settle into.
 */
@Schema(description = """
    Holdings treemap of the report "security asset classes with cash": the value on a hypothetical sale at the report
    date, per security and per cash currency. Unrealized gain/loss of open CFD/Forex positions is added to the cash of
    the currency the position settles into.""")
public record HoldingsTreemap(

    @Schema(description = "Flat node list, root first, asset classes in the order of AssetclassType") List<HoldingsTreemapNode> nodes,

    @Schema(description = "Values that cannot be drawn, for example negative ones") List<HoldingsTreemapExcluded> excluded) {
}
