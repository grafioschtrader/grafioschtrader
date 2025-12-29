package grafioschtrader.repository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.dto.TaskDataChangeFormConstraints;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.repository.EntityIdOptionsProvider;
import grafioschtrader.connector.instrument.IFeedConnector;

/**
 * Provider that adds feed connector options to task form constraints. This allows users to select a connector by name
 * instead of having to know its numeric ID (hashCode).
 */
@Component
public class FeedConnectorEntityIdOptionsProvider implements EntityIdOptionsProvider {

  @Autowired
  private List<IFeedConnector> feedConnectors;

  @Override
  public void addEntityIdOptions(TaskDataChangeFormConstraints constraints) {
    List<ValueKeyHtmlSelectOptions> connectorOptions = feedConnectors.stream()
        .map(fc -> new ValueKeyHtmlSelectOptions(String.valueOf(fc.getIdNumber()), fc.getReadableName()))
        .sorted(Comparator.comparing(o -> o.value))
        .collect(Collectors.toList());
    constraints.entityIdOptions.put(IFeedConnector.class.getSimpleName(), connectorOptions);
  }
}
