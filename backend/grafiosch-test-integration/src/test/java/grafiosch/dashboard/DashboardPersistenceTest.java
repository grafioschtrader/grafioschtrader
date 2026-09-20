package grafiosch.dashboard;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.GrafioschApplication;
import grafiosch.dto.DashboardDtos.Save;
import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.repository.DashboardProposalRepository;
import grafiosch.repository.DashboardSummaryJpaRepository;
import grafiosch.repository.UserDashboardJpaRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Real MariaDB/JPA checks. Fixture writes and personal layouts roll back after every test. */
@SpringBootTest(classes = GrafioschApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "g.background.worker.enabled=false", "g.use.gtnet=false", "g.gnet.future.message.cron=-",
    "g.gnet.log.aggregation.cron=-" })
@ActiveProfiles("test")
@Transactional
public class DashboardPersistenceTest {
  @Autowired
  DashboardLayoutRepository layouts;
  @Autowired
  DashboardService service;
  @Autowired
  DashboardRegistry registry;
  @Autowired
  DashboardSummaryJpaRepository summaries;
  @Autowired
  DashboardProposalRepository proposals;
  @Autowired
  UserDashboardJpaRepository storage;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  ObjectMapper mapper;
  User user;
  int sender;

  @BeforeEach
  void fixture() {
    assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("grafiosch_t");
    // The normal library user fixture is seeded by ResourceTestSuite, as for the other library resource tests.
    user = new User();
    Map<String, Role> roleMap = new java.util.HashMap<>();
    jdbc.query("SELECT id_role, rolename FROM role", rs -> {
      Role role = new Role(rs.getString("rolename"));
      role.setIdRole(rs.getInt("id_role"));
      roleMap.put(role.getRolename(), role);
    });
    user.setRoleMap(roleMap);
    user.setIdUser(jdbc.queryForObject("SELECT id_user FROM user WHERE nickname = 'user'", Integer.class));
    user.setMostPrivilegedRole(Role.ROLE_USER);
    sender = jdbc.queryForObject("SELECT id_user FROM user WHERE nickname = 'admin'", Integer.class);
    storage.deleteById(user.getIdUser());
    storage.flush();
  }

  @Test
  void atomicSaveEmptyLayoutAndRevisionConflict() {
    assertThat(layouts.read(user).persisted()).isFalse();
    var first = layouts.save(user, new Save(null, 1, List.of(), false));
    assertThat(first.all()).isEmpty();
    assertThat(layouts.read(user).persisted()).isTrue();
    var second = layouts.save(user, new Save(first.revision(), 1, List.of(), false));
    assertThat(second.revision()).isGreaterThan(first.revision());
    assertThatThrownBy(() -> layouts.save(user, new Save(first.revision(), 1, List.of(), false)))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void hidesAdminLayoutPreservesItOnSaveAndResetRemovesIt() {
    user.setMostPrivilegedRole(Role.ROLE_ADMIN);
    var first = layouts.save(user, new Save(null, 1, registry.defaults(user), false));
    assertThat(first.all()).hasSize(3);
    user.setMostPrivilegedRole(Role.ROLE_USER);
    assertThat(layouts.eligible(first, user)).hasSize(2);
    var second = layouts.save(user, new Save(first.revision(), 1, List.of(), false));
    assertThat(second.all()).hasSize(1);
    assertThat(second.all().getFirst().path("type").asString()).isEqualTo("USER_LIMIT_REQUESTS");
    var reset = layouts.save(user, new Save(second.revision(), 1, List.of(), true));
    assertThat(reset.all()).hasSize(2);
  }

  @Test
  void oneInstancePerTypeIsRejectedOnSave() {
    var first = registry.defaults(user).getFirst();
    var duplicate = ((ObjectNode) first).deepCopy().put("instanceId", UUID.randomUUID().toString());
    assertThatThrownBy(() -> layouts.save(user, new Save(null, 1, List.of(first, duplicate), false)))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(layouts.read(user).persisted()).isFalse();
  }

  @Test
  void missingTargetAndClosedProposalsNeverCountOrGetDeleted() {
    long before = proposals.proposals(user, true, 20).count();
    int target = user.getIdUser();
    int visible = propose("E", "User", 0);
    jdbc.update("INSERT INTO propose_change_entity (id_propose_request,id_entity,id_owner_entity) VALUES (?,?,?)",
        visible, target, target);
    int orphan = propose("E", "User", 0);
    jdbc.update("INSERT INTO propose_change_entity (id_propose_request,id_entity,id_owner_entity) VALUES (?,?,?)",
        orphan, Integer.MAX_VALUE, target);
    int closed = propose("E", "User", 2);
    jdbc.update("INSERT INTO propose_change_entity (id_propose_request,id_entity,id_owner_entity) VALUES (?,?,?)",
        closed, target, target);
    assertThat(proposals.proposals(user, true, 20).count()).isEqualTo(before + 1);
    assertThat(proposals.proposals(user, false, 20).rows()).extracting(r -> r.id()).contains(visible)
        .doesNotContain(orphan, closed);
    assertThat(
        jdbc.queryForObject("SELECT COUNT(*) FROM propose_request WHERE id_propose_request = ?", Integer.class, orphan))
            .isEqualTo(1);
  }

  @Test
  void inboxUsesReadAndHideStateWithoutWritingAndCountsBeforeLimit() {
    var before = summaries.unreadMail(user.getIdUser(), 1);
    long count = before.isEmpty() ? 0 : before.getFirst().getTotalCount();
    int first = mail(sender, user.getIdUser(), "R");
    mail(sender, user.getIdUser(), "R");
    int read = mail(sender, user.getIdUser(), "R");
    int hidden = mail(sender, user.getIdUser(), "R");
    mail(user.getIdUser(), sender, "S");
    jdbc.update("INSERT INTO mail_send_recv_read_del VALUES (?,?,1,0)", read, user.getIdUser());
    jdbc.update("INSERT INTO mail_send_recv_read_del VALUES (?,?,0,1)", hidden, user.getIdUser());
    var rows = summaries.unreadMail(user.getIdUser(), 1);
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getTotalCount()).isEqualTo(count + 2);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_send_recv_read_del WHERE id_mail_send_recv = ?",
        Integer.class, first)).isZero();
  }

  @Test
  void adminSummaryMatchesLimitRequestsAndExcludesUnlocks() {
    var before = summaries.limitRequests(1);
    long count = before.isEmpty() ? 0 : before.getFirst().getTotalCount();
    int request = propose("U", "EntityLimit", 0);
    int adminRole = jdbc.queryForObject("SELECT id_role FROM role WHERE rolename = 'ROLE_ADMIN'", Integer.class);
    jdbc.update(
        "INSERT INTO propose_user_task (id_propose_request,id_target_user,user_task_type,id_role_to) VALUES (?,?,1,?)",
        request, user.getIdUser(), adminRole);
    int unlock = propose("U", "User", 0);
    jdbc.update(
        "INSERT INTO propose_user_task (id_propose_request,id_target_user,user_task_type,id_role_to) VALUES (?,?,0,?)",
        unlock, user.getIdUser(), adminRole);
    assertThat(summaries.limitRequests(1).getFirst().getTotalCount()).isEqualTo(count + 1);
    assertThat(service.get(user, true).results())
        .allMatch(r -> !r.type().equals("USER_LIMIT_REQUESTS") && !r.status().equals("ERROR"));
    user.setMostPrivilegedRole(Role.ROLE_ADMIN);
    assertThat(service.get(user, true).results()).hasSize(3).allMatch(r -> !r.status().equals("ERROR"));
  }

  private int propose(String dtype, String entity, int state) {
    jdbc.update(
        "INSERT INTO propose_request (dtype,entity,data_change_state,created_by,last_modified_by,version) VALUES (?,?,?,?,?,0)",
        dtype, entity, state, user.getIdUser(), user.getIdUser());
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
  }

  private int mail(int from, int to, String direction) {
    jdbc.update(
        "INSERT INTO mail_send_recv (send_recv,id_user_from,id_user_to,subject,message) VALUES (?,?,?,'Dashboard fixture','Dashboard fixture')",
        direction, from, to);
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
  }
}
