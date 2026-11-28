package grafioschtrader.reportviews.securitycurrency;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.dto.InstrumentLogoRefDto;
import grafioschtrader.types.WatchlistTreemapNodeType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One node of the watchlist market map.
 */
@Schema(description = "One node of the watchlist market map, as a flat list entry with the id of its parent")
public record WatchlistTreemapNode(
    @Schema(description = "Stable node id: 'root', 'grp/<idAssetClass>' or 'sec/<idSecuritycurrency>'") String id,
    @Schema(description = "Id of the parent node, null for the root") String parentId,
    @Schema(description = "Level of the node") WatchlistTreemapNodeType nodeType, @Schema(description = """
        Name of the stock or sub category of the asset class in the language of the user; null for the root and for a
        sub category without text""") String label,
    @Schema(description = "Area in main currency: the market capitalisation of a stock, 0 for other nodes") double valueMC,
    @Schema(description = "Market capitalisation in main currency, for other nodes the sum of their stocks") double totalValueMC,
    @Schema(description = """
        Price change in percent in the currency of the listing; for other nodes the change of their stocks weighted by
        market capitalisation""") double changePercentage,
    @Schema(description = "Stock nodes only") Integer idSecuritycurrency,
    @Schema(description = "Stock nodes only: currency of the listing, used by the price chart of the context menu") String currency,
    @Schema(description = "Stock nodes only: UTC time of the price") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT) LocalDateTime timestamp,
    @Schema(description = "Stock nodes only: backend-resolved company or issuer logo metadata") InstrumentLogoRefDto resolvedLogo,
    @Schema(description = """
        Stock nodes only: whether the tenant ever held the stock, with an open or closed transaction. The context menu
        disables the security position chart when false.""") Boolean hadPosition) {
}
