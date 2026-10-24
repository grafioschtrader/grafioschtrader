package grafiosch.exportdelete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafiosch.integration.entities.Tenant;
import grafiosch.integration.repository.TenantJpaRepository;
import grafiosch.rest.GrafioschIntegrationTestContext;
import grafiosch.security.UserAuthentication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Deletes an account that took part in a private and in a role conversation and checks which message copies survive.
 * The graph rolls back after the test.
 */
@GrafioschIntegrationTestContext
@Transactional
class MailAccountDeletionIntegrationTest {
  private static final String PREFIX = "Mail deletion ";

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private TenantJpaRepository tenants;

  private User owner;
  private User other;

  @BeforeEach
  void fixture() {
    owner = user("owner");
    other = user("other");
    em.flush();
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(owner));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Account deletion removes the user's own message copies and keeps those of the other party")
  void deleteOwnCopies() throws Exception {
    Integer privateRoot = mail("S", owner, other, null, null, "private");
    mail("R", owner, other, null, privateRoot, "private");
    mail("S", other, owner, null, privateRoot, "private reply");
    mail("R", other, owner, null, privateRoot, "private reply");
    Integer idRoleAdmin = jdbc.queryForObject("SELECT id_role FROM role WHERE rolename = 'ROLE_ADMIN'", Integer.class);
    Integer roleRoot = mail("S", owner, null, idRoleAdmin, null, "role");
    Integer roleReceived = mail("R", owner, null, idRoleAdmin, roleRoot, "role");
    mail("S", other, owner, null, roleRoot, "role reply");
    mail("R", other, owner, null, roleRoot, "role reply");
    jdbc.update("INSERT INTO mail_send_recv_read_del (id_mail_send_recv, id_user, has_been_read) VALUES (?, ?, 1)",
        roleReceived, owner.getIdUser());

    tenants.deleteMyDataAndUserAccount();

    List<Map<String, Object>> remaining = jdbc.queryForList(
        "SELECT send_recv, id_user_from, subject FROM mail_send_recv WHERE subject LIKE ? ORDER BY id_mail_send_recv",
        PREFIX + "%");
    assertThat(remaining)
        .extracting(row -> row.get("send_recv"), row -> row.get("id_user_from"), row -> row.get("subject"))
        .containsExactly(
            // The other party keeps its received copy and its sent reply
            tuple("R", owner.getIdUser(), PREFIX + "private"), tuple("S", other.getIdUser(), PREFIX + "private reply"),
            // The role thread stays for the members of the role, only the sender's root row is gone
            tuple("R", owner.getIdUser(), PREFIX + "role"), tuple("S", other.getIdUser(), PREFIX + "role reply"),
            tuple("R", other.getIdUser(), PREFIX + "role reply"));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_send_recv_read_del WHERE id_user = ?", Integer.class,
        owner.getIdUser())).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id_user = ?", Integer.class, other.getIdUser()))
        .isOne();
  }

  private Integer mail(String sendRecv, User from, User to, Integer idRoleTo, Integer idReplyToLocal, String subject) {
    jdbc.update(
        """
            INSERT INTO mail_send_recv (send_recv, id_user_from, id_user_to, id_role_to, id_reply_to_local, subject, message)
            VALUES (?, ?, ?, ?, ?, ?, 'text')""",
        sendRecv, from.getIdUser(), to == null ? null : to.getIdUser(), idRoleTo, idReplyToLocal, PREFIX + subject);
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
  }

  private User user(String suffix) {
    Tenant tenant = new Tenant();
    tenant.setTenantName(PREFIX + suffix);
    tenant.setCreateIdUser(0);
    em.persist(tenant);
    User user = new User("mail-deletion-" + suffix + "@test.local", "unused", PREFIX + suffix, "en", 0);
    user.setIdTenant(tenant.getIdTenant());
    em.persist(user);
    return user;
  }
}
