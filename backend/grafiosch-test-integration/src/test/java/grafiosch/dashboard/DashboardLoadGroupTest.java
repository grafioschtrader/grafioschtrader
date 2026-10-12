package grafiosch.dashboard;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.config.BaseFeatureConfig;
import grafiosch.dto.DashboardDtos.Descriptor;
import grafiosch.dto.DashboardDtos.Payload;
import grafiosch.dto.DashboardDtos.Result;
import grafiosch.entities.User;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pure tests of the load order: the dashboard document carries only the fastest group present, every slower group is
 * read by its own request, and no group is ever loaded on behalf of another.
 */
class DashboardLoadGroupTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final List<String> loaded = new ArrayList<>();
  private final GroupedHandler fast = new GroupedHandler("FAST_CARD", 1);
  private final GroupedHandler slow = new GroupedHandler("SLOW_CARD", 2);
  private final DashboardRegistry registry = new DashboardRegistry(List.of(fast, slow), new BaseFeatureConfig(),
      mapper);
  private final DashboardLayoutRepository layouts = mock(DashboardLayoutRepository.class);
  private final DashboardService service = new DashboardService(layouts, registry);
  private final User user = new User();

  DashboardLoadGroupTest() {
    user.setIdUser(123);
  }

  @Test
  @DisplayName("The dashboard document loads the fast group and lists the slow one as pending")
  void documentCarriesFastestGroupOnly() {
    layout(widget("s", "SLOW_CARD"), widget("f", "FAST_CARD"));
    var dashboard = service.get(user, true);
    assertThat(dashboard.results()).extracting(Result::instanceId).containsExactly("f");
    assertThat(dashboard.pendingGroups()).containsExactly(2);
    assertThat(dashboard.widgets()).hasSize(2);
    assertThat(loaded).containsExactly("FAST_CARD");
  }

  @Test
  @DisplayName("A pending group is read on its own, without loading the other groups again")
  void groupLoadsOnlyItsWidgets() {
    layout(widget("s", "SLOW_CARD"), widget("f", "FAST_CARD"));
    assertThat(service.group(user, 2)).extracting(Result::instanceId).containsExactly("s");
    assertThat(service.group(user, 3)).isEmpty();
    assertThat(loaded).containsExactly("SLOW_CARD");
  }

  private JsonNode widget(String instanceId, String type) {
    return mapper.valueToTree(Map.of("instanceId", instanceId, "type", type, "width", "HALF", "config", Map.of()));
  }

  private void layout(JsonNode... widgets) {
    var layout = new DashboardLayoutRepository.Layout(true, 1L, 1, List.of(widgets));
    when(layouts.read(user)).thenReturn(layout);
    when(layouts.eligible(layout, user)).thenReturn(List.of(widgets));
  }

  /** A handler that accepts any configuration and records that it was loaded. */
  private final class GroupedHandler implements DashboardWidgetHandler {
    private final String type;
    private final int group;

    private GroupedHandler(String type, int group) {
      this.type = type;
      this.group = group;
    }

    @Override
    public Descriptor descriptor() {
      return new Descriptor(type, type, type, "HALF", Map.of(), null);
    }

    @Override
    public boolean available(User user) {
      return true;
    }

    @Override
    public void validate(JsonNode config) {
    }

    @Override
    public int loadGroup() {
      return group;
    }

    @Override
    public Payload load(User user, JsonNode config) {
      loaded.add(type);
      return new Payload(List.of(), mapper.valueToTree(Map.of("value", 1)));
    }
  }
}
