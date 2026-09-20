package grafiosch.integration.repository;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafiosch.integration.entities.Tenant;
import grafiosch.repository.TenantBaseImpl;
import grafiosch.repository.UserJpaRepository;

/** Supplies the reusable tenant operations required by the integration host. */
public class TenantJpaRepositoryImpl extends TenantBaseImpl<Tenant> implements TenantJpaRepositoryCustom {

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  /**
   * Exports the standalone host's current schema, including Flyway history. Its baseline contains seed rows and is
   * amended by later migrations, so it cannot be used as the schema of a personal-data export. Reading only table
   * definitions avoids both duplicate seed data on restore and a dependency on Grafioschtrader's DDL resource.
   */
  @Override
  protected InputStream openExportDdl() {
    String ddl = jdbcTemplate.execute((ConnectionCallback<String>) connection -> {
      List<String> tables = new ArrayList<>();
      try (ResultSet metadata = connection.getMetaData().getTables(connection.getCatalog(), null, "%",
          new String[] { "TABLE" })) {
        while (metadata.next()) {
          tables.add(metadata.getString("TABLE_NAME"));
        }
      }
      tables.sort(String::compareTo);
      StringBuilder schema = new StringBuilder(
          "SET @old_foreign_key_checks = @@SESSION.foreign_key_checks;\n" + "SET SESSION foreign_key_checks = 0;\n");
      try (Statement statement = connection.createStatement()) {
        for (String table : tables) {
          // Identifiers come from database metadata; quote them for SHOW CREATE, which cannot bind a table name.
          try (ResultSet definition = statement.executeQuery("SHOW CREATE TABLE `" + table.replace("`", "``") + "`")) {
            definition.next();
            schema.append(definition.getString(2)).append(";\n");
          }
        }
      }
      return schema.append("SET SESSION foreign_key_checks = @old_foreign_key_checks;\n").toString();
    });
    return new ByteArrayInputStream(ddl.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  @Transactional
  public Tenant saveOnlyAttributes(Tenant tenant, Tenant existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Tenant target = tenant;
    if (tenant.getIdTenant() == null) {
      target.setCreateIdUser(user.getIdUser());
    } else {
      target = tenantJpaRepository.getReferenceById(tenant.getIdTenant());
      target.setTenantName(tenant.getTenantName());
    }
    target = tenantJpaRepository.save(target);
    if (user.getIdTenant() == null) {
      user.setIdTenant(target.getIdTenant());
      userJpaRepository.save(user);
    }
    return target;
  }
}
