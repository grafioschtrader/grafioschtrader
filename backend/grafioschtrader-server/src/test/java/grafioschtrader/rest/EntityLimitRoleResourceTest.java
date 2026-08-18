package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.ParameterizedTypeReference;

import grafiosch.dto.LimitKey;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.EntityLimit;
import grafiosch.rest.RequestMappings;
import grafiosch.types.CountScope;
import grafiosch.types.LimitType;
import grafiosch.types.OwnerScope;

/**
 * Creates the integration-owned role limits from the fixture shared with Playwright spec 190.
 *
 * <p>
 * This class deliberately runs at the end of {@link ResourceTestSuite_25}: the migration owns only the mandatory MAX
 * defaults, while the two test layers divide the production ROLE_LIMITEDIT rows through the fixture's
 * {@code e2e} routing column.
 * </p>
 */
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class EntityLimitRoleResourceTest extends BaseIntegrationTest {

  private static final String FIXTURE = "/testdata/limit_entity.csv";
  private static final List<String> EXPECTED_HEADER =
      List.of("limitType", "entityName", "relationEntityName", "countScope", "ownerScope", "roleName",
          "limitValue", "validUntil", "e2e");

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Test
  @Order(1)
  @DisplayName("Create integration-owned ROLE_LIMITEDIT limits from the shared CSV")
  void createIntegrationRoleLimits() throws IOException {
    List<LimitFixtureRow> allRows = loadFixture();
    List<LimitFixtureRow> integrationRows = allRows.stream().filter(row -> "i".equals(row.routing())).toList();

    assertThat(allRows).hasSize(32);
    assertThat(integrationRows).hasSize(26);
    assertThat(allRows.stream().filter(row -> "e2e".equals(row.routing()))).hasSize(6);
    assertThat(allRows).extracting(LimitFixtureRow::roleName).containsOnly("ROLE_LIMITEDIT");
    Integer idRole = resolveRoleId(integrationRows.getFirst().roleName());

    deleteExistingRows(integrationRows, idRole);
    for (LimitFixtureRow row : integrationRows) {
      EntityLimit requested = new EntityLimit(row.limitKey(), idRole, null, row.limitValue(), row.validUntil());
      EntityLimit created = authenticatedClient(RestTestHelper.ADMIN).post().uri(RequestMappings.ENTITY_LIMIT_MAP)
          .body(requested).exchange().expectStatus().isOk().expectBody(EntityLimit.class).returnResult()
          .getResponseBody();

      assertThat(created).isNotNull();
      assertThat(created.getLimitKey()).isEqualTo(row.limitKey());
      assertThat(created.getIdRole()).isEqualTo(idRole);
      assertThat(created.getIdUser()).isNull();
      assertThat(created.getLimitValue()).isEqualTo(row.limitValue());
      assertThat(created.getValidUntil()).isEqualTo(row.validUntil());
    }

    EntityLimit[] persisted = readAllLimits();
    for (LimitFixtureRow row : integrationRows) {
      assertThat(persisted).filteredOn(limit -> matches(limit, row, idRole)).singleElement()
          .satisfies(limit -> {
            assertThat(limit.getLimitValue()).isEqualTo(row.limitValue());
            assertThat(limit.getValidUntil()).isEqualTo(row.validUntil());
          });
    }
  }

  private void deleteExistingRows(List<LimitFixtureRow> rows, Integer idRole) {
    for (EntityLimit existing : readAllLimits()) {
      if (rows.stream().anyMatch(row -> matches(existing, row, idRole))) {
        authenticatedClient(RestTestHelper.ADMIN).delete()
            .uri(RequestMappings.ENTITY_LIMIT_MAP + "/" + existing.getIdEntityLimit()).exchange().expectStatus()
            .isNoContent();
      }
    }
  }

  private EntityLimit[] readAllLimits() {
    EntityLimit[] limits = authenticatedClient(RestTestHelper.ADMIN).get().uri(RequestMappings.ENTITY_LIMIT_MAP)
        .exchange().expectStatus().isOk().expectBody(EntityLimit[].class).returnResult().getResponseBody();
    assertThat(limits).isNotNull();
    return limits;
  }

  private Integer resolveRoleId(String roleName) {
    List<ValueKeyHtmlSelectOptions> roles = authenticatedClient(RestTestHelper.ADMIN).get()
        .uri(RequestMappings.ENTITY_LIMIT_MAP + "/roles").exchange().expectStatus().isOk()
        .expectBody(new ParameterizedTypeReference<List<ValueKeyHtmlSelectOptions>>() {
        }).returnResult().getResponseBody();
    assertThat(roles).isNotNull();
    ValueKeyHtmlSelectOptions role = roles.stream().filter(option -> roleName.equals(option.value)).findFirst()
        .orElseThrow(() -> new AssertionError("Role not returned by entity-limit endpoint: " + roleName));
    return Integer.valueOf(role.key);
  }

  private boolean matches(EntityLimit actual, LimitFixtureRow expected, Integer idRole) {
    return idRole.equals(actual.getIdRole()) && actual.getIdUser() == null
        && expected.limitKey().equals(actual.getLimitKey());
  }

  private List<LimitFixtureRow> loadFixture() throws IOException {
    try (InputStream input = EntityLimitRoleResourceTest.class.getResourceAsStream(FIXTURE)) {
      assertThat(input).as("fixture %s must be on the test class path", FIXTURE).isNotNull();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        String header = reader.readLine();
        assertThat(header).as("header of %s", FIXTURE).isNotNull();
        assertThat(List.of(header.split("\\|", -1))).containsExactlyElementsOf(EXPECTED_HEADER);

        List<LimitFixtureRow> rows = new ArrayList<>();
        Set<String> naturalKeys = new HashSet<>();
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
          lineNumber++;
          if (line.isBlank()) {
            continue;
          }
          String[] columns = line.split("\\|", -1);
          assertThat(columns).as("column count in %s line %d", FIXTURE, lineNumber).hasSize(EXPECTED_HEADER.size());

          LimitType limitType = parseLimitType(columns[0], lineNumber);
          assertThat(columns[1]).as("entityName in %s line %d", FIXTURE, lineNumber).isNotBlank();
          String relationEntityName = columns[2].isBlank() ? null : columns[2];
          CountScope countScope = parseNullableEnum(columns[3], CountScope.class, "countScope", lineNumber);
          OwnerScope ownerScope = parseNullableEnum(columns[4], OwnerScope.class, "ownerScope", lineNumber);
          assertThat(columns[5]).as("roleName in %s line %d", FIXTURE, lineNumber)
              .isEqualTo("ROLE_LIMITEDIT");
          int limitValue = parsePositiveInt(columns[6], lineNumber);
          LocalDate validUntil = columns[7].isBlank() ? null : LocalDate.parse(columns[7]);
          assertThat(columns[8]).as("routing value in %s line %d", FIXTURE, lineNumber).isIn("i", "e2e");

          LimitKey key = new LimitKey(limitType, columns[1], relationEntityName, countScope, ownerScope);
          String naturalKey = key.keyId() + "|" + columns[5];
          assertThat(naturalKeys.add(naturalKey)).as("duplicate natural key in %s: %s", FIXTURE, naturalKey)
              .isTrue();
          rows.add(new LimitFixtureRow(key, columns[5], limitValue, validUntil, columns[8]));
        }
        return rows;
      }
    }
  }

  private LimitType parseLimitType(String value, int lineNumber) {
    LimitType limitType;
    try {
      limitType = LimitType.valueOf(value);
    } catch (IllegalArgumentException ex) {
      throw new AssertionError("Invalid limitType in " + FIXTURE + " line " + lineNumber + ": " + value, ex);
    }
    return limitType;
  }

  private <E extends Enum<E>> E parseNullableEnum(String value, Class<E> enumType, String column, int lineNumber) {
    if (value.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException ex) {
      throw new AssertionError("Invalid " + column + " in " + FIXTURE + " line " + lineNumber + ": " + value,
          ex);
    }
  }

  private int parsePositiveInt(String value, int lineNumber) {
    try {
      int parsed = Integer.parseInt(value);
      assertThat(parsed).as("limitValue in %s line %d", FIXTURE, lineNumber).isPositive();
      return parsed;
    } catch (NumberFormatException ex) {
      throw new AssertionError("Invalid limitValue in " + FIXTURE + " line " + lineNumber + ": " + value, ex);
    }
  }

  private record LimitFixtureRow(LimitKey limitKey, String roleName, int limitValue, LocalDate validUntil,
      String routing) {
  }
}
