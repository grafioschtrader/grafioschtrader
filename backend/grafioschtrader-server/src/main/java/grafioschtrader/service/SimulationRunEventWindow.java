package grafioschtrader.service;

import java.util.List;

import grafioschtrader.entities.AlgoEventLog;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    A bounded window of the audit trail of a historical replay around an anchor day. A long run can write far more
    entries than one response can carry, so the reader picks a day and receives the entries nearest to it on both
    sides; the total tells how much of the trail the window covers.""")
public record SimulationRunEventWindow(@Schema(description = """
    The entries of the window, newest day first: up to the window half size before the anchor day and as many from
    the anchor day on. Entries of the same day keep the order in which the replay wrote them, reversed.""") List<AlgoEventLog> events,
    @Schema(description = "Number of entries the run wrote in total, of which the window holds a part") long totalElements) {
}
