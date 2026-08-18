package grafioschtrader.limit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.filter.AssignableTypeFilter;

import grafiosch.dto.LimitKey;
import grafiosch.entities.AdminEntity;
import grafiosch.entities.TenantBaseID;
import grafiosch.limit.LimitKeyRegistration;
import grafiosch.limit.LimitKeyRegistry;
import grafiosch.rest.UpdateCreate;
import grafiosch.types.CountScope;
import grafiosch.types.LimitType;
import grafiosch.types.OwnerScope;
import grafioschtrader.config.LimitKeyConfig;

/**
 * Guards the agreement between the limit key registry and the migration that seeds it.
 *
 * <p>
 * The two cannot be derived from each other: a Flyway script cannot read the Java registry, so the fresh-install
 * defaults appear literally in the SQL, and a key registered without a seed statement would be silently unlimited
 * until someone notices. This test is the reason that cannot happen, in the spirit of {@code NlsBundleGuardTest}.
 * </p>
 *
 * <p>
 * It covers both families, because both fail the same silent way. A {@code MAX} key is registered in Java and seeded
 * in SQL, so the two lists have to agree. A daily key is not registered at all - it is derived - so nothing but this
 * test says that an entity a user may write through {@code UpdateCreate} actually has a budget: a missing row resolves
 * to unlimited without a trace.
 * </p>
 *
 * <p>
 * It deliberately parses the migration as text rather than executing it. Running it would need a database and would
 * only prove that the statements are valid SQL, not that they cover every registered key with the right number.
 * </p>
 */
class EntityLimitSeedGuardTest {

  private static final String MIGRATION_RESOURCE = "/db/migration/V0_36_7__Frankfurter_and_entity_limit.sql";
  private static final String E2E_MIGRATION_RESOURCE = "/db/migration/test/V4__seed_entity_limits.sql";

  private static final String ROLE_LIMIT_FIXTURE_RESOURCE = "/testdata/limit_entity.csv";

  /** One seed row of the MAX block: entity name, relation, scopes, property name and default value. */
  private static final Pattern MAX_SEED = Pattern.compile(
      "SELECT\\s+'(?<entity>[^']+)',\\s*(?<relation>NULL|'[^']*'),\\s*(?<countScope>NULL|\\d+),"
          + "\\s*(?<ownerScope>\\d+),\\s*(?<property>NULL|'[^']*'),\\s*(?<value>\\d+)");

  /** The first row of the MAX block, which spells its column aliases out and therefore does not match MAX_SEED. */
  private static final Pattern MAX_SEED_FIRST = Pattern.compile(
      "SELECT\\s+'(?<entity>[^']+)'\\s+AS entity_name.*?(?<ownerScope>\\d+) AS owner_scope,"
          + "\\s*'(?<property>[^']+)' AS property_name,\\s*(?<value>\\d+) AS default_value",
      Pattern.DOTALL);

  /**
   * Marker of the role scoped MAX statement. Everything before it is the block of mandatory default rows, which is the
   * only block {@link #readMaxSeedRows(String)} may see: a role row is an additional, tighter row for a key that
   * already has a default row, and would otherwise be reported as a key seeded twice.
   */
  private static final String ROLE_MAX_MARKER = "-- 4b.";

  private static final String DAILY_MARKER = "-- 5. The daily rows";

  private static final String AFTER_DAILY_MARKER = "-- 6. Carry over";

  /** One row of the role scoped MAX block, which carries a limit value in place of a property name and a default. */
  private static final Pattern ROLE_MAX_SEED = Pattern.compile(
      "UNION ALL SELECT\\s+'(?<entity>[^']+)',\\s*(?<relation>NULL|'[^']*'),\\s*(?<countScope>NULL|\\d+),"
          + "\\s*(?<ownerScope>\\d+),\\s*(?<value>\\d+)");

  /** The first row of the role scoped MAX block, which spells its column aliases out. */
  private static final Pattern ROLE_MAX_SEED_FIRST = Pattern.compile(
      "SELECT\\s+'(?<entity>[^']+)' AS entity_name.*?(?<ownerScope>\\d+) AS owner_scope,"
          + "\\s*(?<value>\\d+) AS limit_value",
      Pattern.DOTALL);

  /** One row of the daily block: the limit type and the entity or pseudo entity name it is written for. */
  private static final Pattern DAILY_SEED = Pattern
      .compile("SELECT\\s+(?<type>[12])(?: AS limit_type)?,\\s*'(?<entity>[^']+)'");

  /** One complete role-scoped daily row: limit type, entity name and seeded value. */
  private static final Pattern ROLE_DAILY_SEED = Pattern.compile(
      "(?:SELECT|UNION ALL SELECT)\\s+(?<type>[12])(?:\\s+AS limit_type)?\\s*,\\s*'(?<entity>[^']+)'"
          + "(?:\\s+AS entity_name)?\\s*,\\s*(?:NULL|'[^']*')(?:\\s+AS property_name)?\\s*,"
          + "\\s*(?<value>\\d+)");

  /** Packages holding the REST controllers that may extend {@link UpdateCreate}. */
  private static final String[] RESOURCE_PACKAGES = { "grafiosch.rest", "grafioschtrader.rest" };

  /**
   * Entities that reach the generic daily check but deliberately carry no budget, with the reason. Anything not listed
   * here has to be seeded; that is what turns the omission of a new entity into a build failure rather than a silent
   * hole.
   */
  private static final Set<String> DAILY_EXEMPT = Set.of(
      // Registration and profile edits of the caller's own account, bounded by the account itself.
      "User",
      // Create returns 405. A proposal is only ever produced by UpdateCreate.createProposaleChange, by
      // SecuritysplitJpaRepositoryImpl.changeSecuritySplit and by
      // HistoryquotePeriodJpaRepositoryImpl.changeHistoryquotePeriods; all three consume the budget of the entity the
      // proposal targets, so a proposal is never free even though this name carries no budget of its own.
      "ProposeChangeEntity",
      // POST, PUT, PATCH and DELETE of TASK_DATA_CHANGE_MAP are restricted to ROLE_ADMIN in
      // SecurityConfig.configureGlobalParameters, and registered before the broad /api/** rule, so the role bounds it.
      "TaskDataChange");

  private static String migration;
  private static String e2eMigration;

  @BeforeAll
  static void loadMigrationAndRegistry() throws IOException {
    migration = loadResource(MIGRATION_RESOURCE);
    e2eMigration = loadResource(E2E_MIGRATION_RESOURCE);
    LimitKeyRegistry.clear();
    LimitKeyConfig.initialize();
  }

  @Test
  @DisplayName("Every registered MAX key is seeded exactly once with its registry default")
  void everyRegisteredMaxKeyIsSeeded() {
    assertEveryRegisteredMaxKeyIsSeeded(migration, "production migration");
  }

  @Test
  @DisplayName("Every registered MAX key is present in the E2E bootstrap")
  void everyRegisteredMaxKeyIsSeededForE2E() {
    assertEveryRegisteredMaxKeyIsSeeded(e2eMigration, "E2E migration");
  }

  @Test
  @DisplayName("The E2E bootstrap seeds only MAX limits")
  void e2eBootstrapSeedsOnlyMaxLimits() {
    assertThat(e2eMigration).doesNotContain("`id_role`").doesNotContain("ROLE_LIMITEDIT");
    assertThat(Pattern.compile("(?:SELECT|UNION ALL SELECT)\\s+[12](?:\\s|,)").matcher(e2eMigration).find())
        .as("role limits must be created from testdata/limit_entity.csv by their owning test")
        .isFalse();
  }

  /**
   * A role scoped MAX row is a tighter row for a key that already has a mandatory default row. It must therefore name a
   * registered key, and it must not be looser than that key's default: the default row is the outer bound the code
   * enforces and cannot be deleted, so a role row above it would be silently pointless.
   */
  @Test
  @DisplayName("Every role scoped MAX row names a registered key and stays at or below its default")
  void roleScopedMaxRowsAreTighterThanTheirDefault() {
    List<String> violations = new ArrayList<>();

    for (SeedRow row : readRoleMaxSeedRows()) {
      List<LimitKeyRegistration> matches = LimitKeyRegistry.getMaxRegistrations().stream().filter(row::matches)
          .toList();
      if (matches.isEmpty()) {
        violations.add("role scoped MAX row for '" + row.entityName + "' names no registered limit key");
      } else if (row.defaultValue > matches.get(0).defaultValue()) {
        violations.add("role scoped MAX row for limit key " + matches.get(0).limitKey().keyId() + " is "
            + row.defaultValue + ", which is above the default of " + matches.get(0).defaultValue());
      }
    }
    assertThat(violations)
        .as("role scoped MAX seed violations:%n%s", String.join(System.lineSeparator(), violations)).isEmpty();
  }

  @Test
  @DisplayName("Every role scoped migration row is routed through the shared test fixture")
  void everyRoleScopedMigrationRowIsInFixture() throws IOException {
    List<RoleSeedRow> migrationRows = readRoleScopedMigrationRows();
    List<FixtureRoleSeedRow> fixtureRows = readRoleLimitFixture();
    List<RoleSeedRow> fixtureSeedRows = fixtureRows.stream().map(FixtureRoleSeedRow::seedRow).toList();

    assertThat(migrationRows).hasSize(32).doesNotHaveDuplicates();
    assertThat(fixtureSeedRows).containsExactlyInAnyOrderElementsOf(migrationRows).doesNotHaveDuplicates();
    assertThat(fixtureRows).filteredOn(row -> "i".equals(row.routing())).hasSize(26);
    assertThat(fixtureRows).filteredOn(row -> "e2e".equals(row.routing())).hasSize(6);
  }

  /**
   * Every entity a user can write through {@link UpdateCreate} without landing in the tenant or admin branch passes
   * through {@code checkDailyLimitOnCRUDOperations}, so a missing seed row makes it unlimited without any trace. This
   * is what would have caught {@code ProposeChangeEntity} and what keeps the next entity from repeating it.
   */
  @Test
  @DisplayName("Every entity reachable through UpdateCreate has a daily CUD seed")
  void everyCudCapableEntityIsSeeded() {
    Set<String> seeded = readDailySeededEntityNames();
    List<String> violations = new ArrayList<>();

    for (Class<?> entityClass : cudCapableEntityClasses()) {
      String entityName = entityClass.getSimpleName();
      if (!seeded.contains(entityName) && !DAILY_EXEMPT.contains(entityName)) {
        violations.add(entityName + " is created or updated through UpdateCreate but has no DAY_CUD seed row, so its "
            + "daily budget resolves to unlimited. Seed it, or list it in DAILY_EXEMPT with the reason.");
      }
    }
    assertThat(violations).as("daily CUD seed violations:%n%s", String.join(System.lineSeparator(), violations))
        .isEmpty();
  }

  /**
   * A pseudo entity name exists precisely because the derived key space cannot find it - either it has no entity behind
   * it at all, or its entity is tenant private and therefore excluded. Registering one without seeding it leaves an
   * editable key with no budget behind it.
   */
  @Test
  @DisplayName("Every registered CUD pseudo entity name is seeded")
  void everyCudPseudoEntityNameIsSeeded() {
    Set<String> seeded = readDailySeededEntityNames();
    List<String> violations = new ArrayList<>();

    for (String pseudoName : LimitKeyRegistry.getCudPseudoEntityNames()) {
      if (!seeded.contains(pseudoName)) {
        violations.add("pseudo entity name " + pseudoName + " is registered for a daily CUD limit but never seeded");
      }
    }
    assertThat(violations)
        .as("daily CUD pseudo name violations:%n%s", String.join(System.lineSeparator(), violations)).isEmpty();
  }

  @Test
  @DisplayName("Every globalparameters key a seed statement reads is also deleted")
  void everyMigratedGlobalparametersKeyIsDeleted() {
    String deleteBlock = migration.substring(migration.indexOf("DELETE FROM `globalparameters`"));
    List<String> violations = new ArrayList<>();

    Matcher property = Pattern.compile("property_name = x.property_name").matcher(migration);
    assertThat(property.find()).as("the seed statements must resolve their value through globalparameters").isTrue();

    for (String propertyName : readSeededPropertyNames()) {
      if (!deleteBlock.contains("'" + propertyName + "'")) {
        violations.add("globalparameters key " + propertyName
            + " is read by a seed statement but never deleted, so it would linger as dead configuration");
      }
    }
    assertThat(violations).as("globalparameters cleanup violations:%n%s",
        String.join(System.lineSeparator(), violations)).isEmpty();
  }

  @Test
  @DisplayName("The user lockout thresholds are not migrated")
  void lockoutThresholdsSurvive() {
    String deleteBlock = migration.substring(migration.indexOf("DELETE FROM `globalparameters`"));
    assertThat(deleteBlock)
        .as("g.max.limit.request.exceeded.count and g.max.security.breach.count are lockout thresholds, not entity "
            + "caps, and must stay in globalparameters")
        .doesNotContain("g.max.limit.request.exceeded.count").doesNotContain("g.max.security.breach.count");
  }

  private void assertEveryRegisteredMaxKeyIsSeeded(String seedSql, String source) {
    List<String> violations = new ArrayList<>();

    for (LimitKeyRegistration registration : LimitKeyRegistry.getMaxRegistrations()) {
      List<SeedRow> matches = findSeedRows(registration, seedSql);
      if (matches.isEmpty()) {
        violations.add("no seed statement for registered limit key " + registration.limitKey().keyId());
      } else if (matches.size() > 1) {
        violations.add("limit key " + registration.limitKey().keyId() + " is seeded " + matches.size() + " times");
      } else if (matches.get(0).defaultValue != registration.defaultValue()) {
        violations.add("limit key " + registration.limitKey().keyId() + " is seeded with "
            + matches.get(0).defaultValue + " but the registry default is " + registration.defaultValue());
      }
    }
    assertThat(violations).as("entity_limit seed violations in %s:%n%s", source,
        String.join(System.lineSeparator(), violations)).isEmpty();
  }

  private List<SeedRow> findSeedRows(LimitKeyRegistration registration, String seedSql) {
    List<SeedRow> rows = new ArrayList<>();
    for (SeedRow row : readMaxSeedRows(defaultMaxBlockOf(seedSql))) {
      if (row.matches(registration)) {
        rows.add(row);
      }
    }
    return rows;
  }

  /** The block of mandatory default rows, that is everything before the role scoped statement. */
  private String defaultMaxBlockOf(String seedSql) {
    int roleBlock = seedSql.indexOf(ROLE_MAX_MARKER);
    return roleBlock < 0 ? seedSql : seedSql.substring(0, roleBlock);
  }

  private String roleMaxBlock() {
    return migration.substring(migration.indexOf(ROLE_MAX_MARKER), migration.indexOf(DAILY_MARKER));
  }

  private String dailyBlock() {
    return migration.substring(migration.indexOf(DAILY_MARKER), migration.indexOf(AFTER_DAILY_MARKER));
  }

  private List<SeedRow> readMaxSeedRows(String seedSql) {
    List<SeedRow> rows = new ArrayList<>();
    Matcher first = MAX_SEED_FIRST.matcher(seedSql);
    if (first.find()) {
      rows.add(new SeedRow(first.group("entity"), null, null, Integer.parseInt(first.group("ownerScope")),
          Integer.parseInt(first.group("value"))));
    }
    Matcher matcher = MAX_SEED.matcher(seedSql);
    while (matcher.find()) {
      rows.add(new SeedRow(matcher.group("entity"), unquote(matcher.group("relation")),
          parseNullableInt(matcher.group("countScope")), Integer.parseInt(matcher.group("ownerScope")),
          Integer.parseInt(matcher.group("value"))));
    }
    return rows;
  }

  private List<SeedRow> readRoleMaxSeedRows() {
    String block = roleMaxBlock();
    List<SeedRow> rows = new ArrayList<>();
    Matcher first = ROLE_MAX_SEED_FIRST.matcher(block);
    if (first.find()) {
      rows.add(new SeedRow(first.group("entity"), null, null, Integer.parseInt(first.group("ownerScope")),
          Integer.parseInt(first.group("value"))));
    }
    Matcher matcher = ROLE_MAX_SEED.matcher(block);
    while (matcher.find()) {
      rows.add(new SeedRow(matcher.group("entity"), unquote(matcher.group("relation")),
          parseNullableInt(matcher.group("countScope")), Integer.parseInt(matcher.group("ownerScope")),
          Integer.parseInt(matcher.group("value"))));
    }
    return rows;
  }

  private List<RoleSeedRow> readRoleScopedMigrationRows() {
    List<RoleSeedRow> rows = new ArrayList<>();
    for (SeedRow row : readRoleMaxSeedRows()) {
      CountScope countScope = row.countScope == null ? null
          : CountScope.getCountScopeByValue(row.countScope.byteValue());
      OwnerScope ownerScope = OwnerScope.getOwnerScopeByValue((byte) row.ownerScope);
      rows.add(new RoleSeedRow(
          new LimitKey(LimitType.MAX, row.entityName, row.relationEntityName, countScope, ownerScope),
          "ROLE_LIMITEDIT", row.defaultValue));
    }

    Matcher matcher = ROLE_DAILY_SEED.matcher(dailyBlock());
    while (matcher.find()) {
      LimitType limitType = "1".equals(matcher.group("type")) ? LimitType.DAY_CUD : LimitType.DAY_READ;
      rows.add(new RoleSeedRow(new LimitKey(limitType, matcher.group("entity"), null, null, null),
          "ROLE_LIMITEDIT", Integer.parseInt(matcher.group("value"))));
    }
    return rows;
  }

  private List<FixtureRoleSeedRow> readRoleLimitFixture() throws IOException {
    String[] lines = loadResource(ROLE_LIMIT_FIXTURE_RESOURCE).split("\\R");
    assertThat(lines).isNotEmpty();
    assertThat(lines[0]).isEqualTo(
        "limitType|entityName|relationEntityName|countScope|ownerScope|roleName|limitValue|validUntil|e2e");

    List<FixtureRoleSeedRow> rows = new ArrayList<>();
    for (int index = 1; index < lines.length; index++) {
      if (lines[index].isBlank()) {
        continue;
      }
      String[] columns = lines[index].split("\\|", -1);
      assertThat(columns).as("column count in %s line %d", ROLE_LIMIT_FIXTURE_RESOURCE, index + 1).hasSize(9);
      CountScope countScope = columns[3].isBlank() ? null : CountScope.valueOf(columns[3]);
      OwnerScope ownerScope = columns[4].isBlank() ? null : OwnerScope.valueOf(columns[4]);
      var limitKey = new LimitKey(LimitType.valueOf(columns[0]), columns[1],
          columns[2].isBlank() ? null : columns[2], countScope, ownerScope);
      assertThat(columns[5]).as("role name in %s line %d", ROLE_LIMIT_FIXTURE_RESOURCE, index + 1)
          .isEqualTo("ROLE_LIMITEDIT");
      assertThat(columns[7]).as("validUntil in migration-owned fixture row %d", index + 1).isEmpty();
      assertThat(columns[8]).as("routing in %s line %d", ROLE_LIMIT_FIXTURE_RESOURCE, index + 1).isIn("i", "e2e");
      rows.add(new FixtureRoleSeedRow(
          new RoleSeedRow(limitKey, columns[5], Integer.parseInt(columns[6])), columns[8]));
    }
    return rows;
  }

  /** Entity and pseudo entity names carrying a DAY_CUD row, that is limit type 1. */
  private Set<String> readDailySeededEntityNames() {
    Set<String> names = new LinkedHashSet<>();
    Matcher matcher = DAILY_SEED.matcher(dailyBlock());
    while (matcher.find()) {
      if ("1".equals(matcher.group("type"))) {
        names.add(matcher.group("entity"));
      }
    }
    return names;
  }

  /**
   * Every entity that passes through the generic daily check: the type argument of a concrete {@link UpdateCreate}
   * controller that is neither tenant private nor an {@link AdminEntity}, mirroring the branches of
   * {@code UpdateCreate.createEntity}.
   */
  private Set<Class<?>> cudCapableEntityClasses() {
    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(UpdateCreate.class));

    Set<Class<?>> entityClasses = new LinkedHashSet<>();
    for (String resourcePackage : RESOURCE_PACKAGES) {
      for (BeanDefinition definition : scanner.findCandidateComponents(resourcePackage)) {
        Class<?> resourceClass = resolve(definition.getBeanClassName());
        Class<?> entityClass = resourceClass == null ? null
            : ResolvableType.forClass(UpdateCreate.class, resourceClass).getGeneric(0).resolve();
        if (entityClass != null && !TenantBaseID.class.isAssignableFrom(entityClass)
            && !AdminEntity.class.isAssignableFrom(entityClass)) {
          entityClasses.add(entityClass);
        }
      }
    }
    assertThat(entityClasses).as("the controller scan must find something, otherwise the guard passes vacuously")
        .isNotEmpty();
    return entityClasses;
  }

  private Class<?> resolve(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      return null;
    }
  }

  private static String loadResource(String resource) throws IOException {
    try (InputStream in = EntityLimitSeedGuardTest.class.getResourceAsStream(resource)) {
      assertThat(in).as("migration resource %s must be on the test class path", resource).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private List<String> readSeededPropertyNames() {
    List<String> propertyNames = new ArrayList<>();
    Matcher matcher = Pattern.compile("'(?<property>g[t]?\\.(?:max|limit\\.day)\\.[A-Za-z.]+)'(?=,\\s*\\d+)")
        .matcher(migration.substring(0, migration.indexOf("DELETE FROM `globalparameters`")));
    while (matcher.find()) {
      propertyNames.add(matcher.group("property"));
    }
    return propertyNames;
  }

  private static String unquote(String value) {
    return "NULL".equals(value) ? null : value.substring(1, value.length() - 1);
  }

  private static Integer parseNullableInt(String value) {
    return "NULL".equals(value) ? null : Integer.valueOf(value);
  }

  private record RoleSeedRow(LimitKey limitKey, String roleName, int limitValue) {
  }

  private record FixtureRoleSeedRow(RoleSeedRow seedRow, String routing) {
  }

  private record SeedRow(String entityName, String relationEntityName, Integer countScope, int ownerScope,
      int defaultValue) {

    boolean matches(LimitKeyRegistration registration) {
      var key = registration.limitKey();
      return entityName.equals(key.entityName())
          && java.util.Objects.equals(relationEntityName, key.relationEntityName())
          && java.util.Objects.equals(countScope,
              key.countScope() == null ? null : Integer.valueOf(key.countScope().getValue()))
          && key.ownerScope() != null && ownerScope == key.ownerScope().getValue();
    }
  }
}
