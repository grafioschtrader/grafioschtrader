package grafiosch.dashboard;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

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
  void defaultsNeverWriteAndDisabledFeatureNeverReads() {
    var storage = mock(UserDashboardJpaRepository.class);
    when(storage.findById(123)).thenReturn(Optional.empty());
    var layouts = new DashboardLayoutRepository(storage, registry, mapper, mock(EntityManager.class),
        mock(DailyLimitService.class));
    var layout = layouts.read(user);
    assertThat(layout.persisted()).isFalse();
    assertThat(layout.revision()).isNull();
    verify(storage).findById(123);
    verifyNoMoreInteractions(storage);
    features.setDashboard(false);
    assertThatThrownBy(() -> layouts.read(user))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    verifyNoMoreInteractions(storage);
  }

  @Test
  void layoutCapsCountUtf8BytesAndHiddenInstances() {
    var layouts = new DashboardLayoutRepository(mock(UserDashboardJpaRepository.class), registry, mapper,
        mock(EntityManager.class), mock(DailyLimitService.class));
    var oversized = mapper.createObjectNode().put("instanceId", "unknown").put("opaque", "ä".repeat(33000));
    assertThatThrownBy(() -> layouts.encode(List.of(oversized)))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThatThrownBy(() -> layouts.encode(List.of(oversized, oversized)))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }
}
