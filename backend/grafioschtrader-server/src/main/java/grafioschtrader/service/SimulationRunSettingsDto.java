package grafioschtrader.service;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

@Schema(description = """
    What the latest historical replay of a simulation environment was based on: the strategy hierarchy as it was when the
    run was submitted and the weights the replay actually allocated with. Both are frozen, so editing the shared
    hierarchy afterwards changes neither.""")
public record SimulationRunSettingsDto(@Schema(description = """
    The hierarchy at submit time, in the shape of the hierarchy view: an object with algoTop and algoAssetclassList,
    the instruments and strategies with their parameters nested in it. Empty for a run submitted before the
    hierarchy was captured.""") JsonNode hierarchy, @Schema(description = """
    Weights the replay allocated with, keyed by hierarchy node id: the original weights of the classes and members
    and the effective ones after excluded instruments were removed and their weight redistributed within their
    class. Empty when the run recorded no allocation.""") AlgoReplayAllocation allocation,
    @Schema(description = "Time at which the replay was submitted") @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND) LocalDateTime submittedAt) {
}
