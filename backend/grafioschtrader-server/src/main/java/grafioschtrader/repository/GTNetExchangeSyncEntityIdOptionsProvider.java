package grafioschtrader.repository;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import grafiosch.dto.TaskDataChangeFormConstraints;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.repository.EntityIdOptionsProvider;
import grafioschtrader.task.exec.GTNetExchangeSyncTask;

/**
 * Provider that adds sync mode options to task form constraints for GTNetExchangeSyncTask.
 * Allows administrators to choose between incremental (timestamp-based) and full recreation modes.
 */
@Component
public class GTNetExchangeSyncEntityIdOptionsProvider implements EntityIdOptionsProvider {

  @Override
  public void addEntityIdOptions(TaskDataChangeFormConstraints constraints) {
    List<ValueKeyHtmlSelectOptions> syncModeOptions = Arrays.asList(
        new ValueKeyHtmlSelectOptions(
            String.valueOf(GTNetExchangeSyncTask.INCREMENTAL_MODE),
            "GTNET_SYNC_MODE_INCREMENTAL"),
        new ValueKeyHtmlSelectOptions(
            String.valueOf(GTNetExchangeSyncTask.FULL_RECREATION_MODE),
            "GTNET_SYNC_MODE_FULL_RECREATION")
    );
    constraints.entityIdOptions.put(GTNetExchangeSyncTask.SYNC_MODE_ENTITY, syncModeOptions);
  }
}
