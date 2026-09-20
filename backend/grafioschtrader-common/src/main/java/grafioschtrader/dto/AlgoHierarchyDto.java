package grafioschtrader.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoTop;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Algorithmic allocation hierarchy and backend validation for its overview. Field maps are keyed by hierarchy
    node ID and contain the property paths whose values the client should highlight.""")
public record AlgoHierarchyDto(
    @Schema(description = "Root including its calculated child percentage total") AlgoTop algoTop,
    @Schema(description = "Asset classes including their assigned securities and strategies") List<AlgoAssetclass> algoAssetclassList,
    @Schema(description = "Property paths to display in red per hierarchy node ID") Map<Integer, Set<String>> invalidFields,
    @Schema(description = "Property paths to display on a yellow background per hierarchy node ID") Map<Integer, Set<String>> warningFields) {
}
