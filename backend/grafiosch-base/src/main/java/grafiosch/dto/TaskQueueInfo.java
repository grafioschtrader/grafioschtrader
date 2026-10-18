package grafiosch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A queue of background tasks with its own workers, shown as a tab of the task monitor.")
public record TaskQueueInfo(@Schema(description = "Name of the queue; MAIN is the main queue") String name,
    @Schema(description = "How many tasks of this queue run at the same time") int parallel,
    @Schema(description = "Text key of the tab of this queue") String labelKey) {
}
