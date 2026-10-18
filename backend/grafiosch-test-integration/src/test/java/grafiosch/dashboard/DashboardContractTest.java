package grafiosch.dashboard;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import grafiosch.config.BaseFeatureConfig;
import grafiosch.entities.User;
import grafiosch.repository.DashboardSummaryJpaRepository;
import grafiosch.repository.UserDashboardJpaRepository;
import grafiosch.service.DailyLimitService;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

/** Pure contract tests: malformed documents and feature gates must fail before persistence or data loading. */
class DashboardContractTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final BaseFeatureConfig features = new BaseFeatureConfig();
  private final DashboardSummaryJpaRepository summaries = mock(DashboardSummaryJpaRepository.class);
  private final UnreadMailDashboardHandler mail = new UnreadMailDashboardHandler(summaries);
  private final DashboardRegistry registry = new DashboardRegistry(List.of(mail), features, mapper);
  private final User user = new User();

  DashboardContractTest() {
    user.setIdUser(123);
  }

  @Test
  void defaultsAreStableAndUserScoped() {
    assertThat(registry.defaults(user)).isEqualTo(registry.defaults(user));
    User other = new User();
    other.setIdUser(456);
    assertThat(registry.defaults(user).getFirst().path("instanceId"))
        .isNotEqualTo(registry.defaults(other).getFirst().path("instanceId"));
    verifyNoInteractions(summaries);
  }

  @Test
  void strictListSettingsRejectUnknownFieldsAndOutOfRangeValues() {
    for (String invalid : List.of("{}", "{\"maxRows\":0}", "{\"maxRows\":21}", "{\"maxRows\":1.5}",
        "{\"maxRows\":5,\"sql\":\"anything\"}", "{\"maxRows\":\"5\"}")) {
      assertThatThrownBy(() -> mail.validate(mapper.readTree(invalid)))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    assertThatCode(() -> mail.validate(mapper.readTree("{\"maxRows\":20}"))).doesNotThrowAnyException();
  }

  @Test
  void layoutSizeLimitCountsUtf8Bytes() {
    var layouts = new DashboardLayoutRepository(mock(UserDashboardJpaRepository.class), registry, mapper,
        mock(EntityManager.class), mock(DailyLimitService.class));
    var oversized = mapper.createObjectNode().put("instanceId", "unknown").put("opaque", "ä".repeat(33000));
    assertThatThrownBy(() -> layouts.encode(List.of(oversized))).isInstanceOfSatisfying(ResponseStatusException.class,
        error -> assertThat(error.getReason()).isEqualTo("DASHBOARD_CAPACITY"));
    oversized.put("opaque", "a".repeat(33000));
    assertThatCode(() -> layouts.encode(List.of(oversized))).doesNotThrowAnyException();
  }

  @Test
  void duplicateInstanceIdsAreRejected() {
    var layouts = new DashboardLayoutRepository(mock(UserDashboardJpaRepository.class), registry, mapper,
        mock(EntityManager.class), mock(DailyLimitService.class));
    var first = mapper.createObjectNode().put("instanceId", "same").put("type", "first");
    var second = mapper.createObjectNode().put("instanceId", "same").put("type", "second");
    assertThatThrownBy(() -> layouts.encode(List.of(first, second))).isInstanceOfSatisfying(
        ResponseStatusException.class, error -> assertThat(error.getReason()).isEqualTo("DASHBOARD_CAPACITY"));
  }
}
