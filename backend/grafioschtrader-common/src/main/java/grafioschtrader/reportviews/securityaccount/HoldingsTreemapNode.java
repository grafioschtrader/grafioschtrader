package grafioschtrader.reportviews.securityaccount;

import java.util.List;

import grafioschtrader.types.HoldingsTreemapNodeType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One node of the holdings treemap. The nodes form a flat list in which every node except the root names its parent,
 * which is exactly the shape a Plotly treemap trace expects.
 */
@Schema(description = """
    One node of the holdings treemap of the report "security asset classes with cash". The nodes are a flat list linked
    by parentId; the client maps them to a Plotly treemap trace without any aggregation of its own.""")
public record HoldingsTreemapNode(

    @Schema(description = """
        Stable node id: 'root', 'ac/<AssetclassType>', 'sec/<idSecuritycurrency>' or 'cash/<AssetclassType>/<ISO
        currency>'""") String id,

    @Schema(description = "Id of the parent node, null for the root") String parentId,

    @Schema(description = "Level of the node in the hierarchy") HoldingsTreemapNodeType nodeType,

    @Schema(description = """
        ASSETCLASS: the AssetclassType name, translated by the client; SECURITY: the security name; CASH_CURRENCY: the
        ISO currency code; ROOT: null""") String label,

    @Schema(description = """
        Area of the node in main currency. Leaves carry their value, root and asset class nodes 0, because the treemap
        is drawn with branchvalues 'remainder' and sums the children itself.""") double valueMC,

    @Schema(description = """
        Value to display in main currency: the value of a leaf, or the sum of all leaves below a root or asset class
        node. Exists so that the client never has to sum.""") double totalValueMC,

    @Schema(description = """
        Share of totalValueMC in the report total grandAccountValueSecurityMC, in percentage points. Same definition as
        shareOfTotalPercentage of a position, so a security tile shows the number of its table row. Null when the report
        total is not positive.""") Double shareOfTotalPercentage,

    @Schema(description = "SECURITY only: id of the security") Integer idSecuritycurrency,

    @Schema(description = "SECURITY only: gain/loss of the position in percent") Double positionGainLossPercentage,

    @Schema(description = """
        CASH_CURRENCY only: the part of valueMC that comes from the unrealized gain/loss of open CFD/Forex positions
        settling into a cash account of this currency; null when there are none.""") Double marginGainLossMC,

    @Schema(description = "CASH_CURRENCY only: names of the margin securities contributing to marginGainLossMC") List<String> marginPositionNames) {
}
