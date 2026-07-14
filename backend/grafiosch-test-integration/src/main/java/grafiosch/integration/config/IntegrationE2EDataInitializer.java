package grafiosch.integration.config;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Deterministic, application-independent users for the portable browser suite. */
@Component
@Profile("e2e")
public class IntegrationE2EDataInitializer implements ApplicationRunner {

  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;

  public IntegrationE2EDataInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
    this.jdbcTemplate = jdbcTemplate;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    insertRole(5, "ROLE_ADMIN");
    insertRole(6, "ROLE_ALLEDIT");
    insertRole(7, "ROLE_USER");
    insertRole(8, "ROLE_LIMITEDIT");
    insertTenant(1, "Grafiosch Lib Admin", 1);
    insertTenant(2, "Grafiosch Lib User", 2);
    insertTenant(3, "Grafiosch Lib Limited", 3);
    insertUser(1, 1, "admin@test.local", "admin", 5);
    insertUser(2, 2, "user@test.local", "user", 7);
    insertUser(3, 3, "limited@test.local", "limited", 8);
  }

  private void insertRole(int idRole, String roleName) {
    jdbcTemplate.update("""
        INSERT INTO role (id_role, rolename) VALUES (?, ?)
        ON DUPLICATE KEY UPDATE rolename = VALUES(rolename)
        """, idRole, roleName);
  }

  private void insertTenant(int idTenant, String tenantName, int creator) {
    jdbcTemplate.update("""
        INSERT INTO tenant (id_tenant, tenant_name, create_id_user) VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE tenant_name = VALUES(tenant_name), create_id_user = VALUES(create_id_user)
        """, idTenant, tenantName, creator);
  }

  private void insertUser(int idUser, int idTenant, String email, String nickname, int idRole) {
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    jdbcTemplate.update("""
        INSERT INTO user (id_user, id_tenant, nickname, email, password, locale, timezone_offset, enabled,
          ui_show_my_property, home_tenant_read_only, security_breach_count, limit_request_exceed_count,
          last_role_modified_time, created_by, creation_time, last_modified_by, last_modified_time, version)
        VALUES (?, ?, ?, ?, ?, 'en-US', 0, true, true, false, 0, 0, ?, ?, ?, ?, ?, 0)
        ON DUPLICATE KEY UPDATE id_tenant = VALUES(id_tenant), nickname = VALUES(nickname),
          password = VALUES(password), enabled = true, home_tenant_read_only = false
        """, idUser, idTenant, nickname, email, passwordEncoder.encode("Test1234"), now, idUser, now, idUser, now);
    jdbcTemplate.update("INSERT IGNORE INTO user_role (id_user, id_role) VALUES (?, ?)", idUser, idRole);
    if (idRole == 5) {
      jdbcTemplate.update("INSERT IGNORE INTO user_role (id_user, id_role) VALUES (?, 6)", idUser);
      jdbcTemplate.update("INSERT IGNORE INTO user_role (id_user, id_role) VALUES (?, 7)", idUser);
    } else if (idRole == 6) {
      jdbcTemplate.update("INSERT IGNORE INTO user_role (id_user, id_role) VALUES (?, 7)", idUser);
    }
  }
}
