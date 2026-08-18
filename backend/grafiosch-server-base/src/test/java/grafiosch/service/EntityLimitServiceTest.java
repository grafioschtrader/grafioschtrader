package grafiosch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import grafiosch.dto.LimitKey;
import grafiosch.entities.EntityLimit;
import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.limit.EntityLimitCache;
import grafiosch.repository.EntityLimitJpaRepository;
import grafiosch.types.LimitType;

/**
 * Unit tests of the limit resolution order, exercised with a mocked repository so the precedence rules are verified
 * without a database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Entity limit resolution")
class EntityLimitServiceTest {

  private static final Integer ID_USER = 11;
  private static final Integer ID_ROLE_ADMIN = 1;
  private static final Integer ID_ROLE_ALL_EDIT = 2;
  private static final Integer ID_ROLE_USER = 3;
  private static final LimitKey KEY = LimitKey.dayCud("Assetclass");

  @Mock
  private EntityLimitJpaRepository entityLimitJpaRepository;

  private EntityLimitCache entityLimitCache;
  private EntityLimitService entityLimitService;

  @BeforeEach
  void setUp() {
    entityLimitCache = new EntityLimitCache();
    entityLimitService = new EntityLimitService(entityLimitJpaRepository, entityLimitCache);
  }

  private void givenRows(EntityLimit... rows) {
    when(entityLimitJpaRepository.findByLimitTypeAndEntityName(LimitType.DAY_CUD.getValue(), "Assetclass"))
        .thenReturn(List.of(rows));
  }

  private EntityLimit row(Integer idRole, Integer idUser, int limitValue, LocalDate validUntil) {
    return new EntityLimit(KEY, idRole, idUser, limitValue, validUntil);
  }

  /**
   * Roles are cumulative: {@code User.setMostPrivilegedRole} materialises ADMIN as {ADMIN, ALLEDIT, USER}, so a user
   * matches several role rows at once.
   */
  private User user(String mostPrivilegedRole, Integer... idRoles) {
    User user = new User();
    user.setIdUser(ID_USER);
    List<Role> roles = new java.util.ArrayList<>();
    for (Integer idRole : idRoles) {
      Role role = new Role();
      role.setIdRole(idRole);
      role.setRolename(idRole.equals(ID_ROLE_ADMIN) ? Role.ROLE_ADMIN
          : idRole.equals(ID_ROLE_ALL_EDIT) ? Role.ROLE_ALL_EDIT : Role.ROLE_USER);
      roles.add(role);
    }
    user.setRoles(roles);
    user.setMostPrivilegedRole(mostPrivilegedRole);
    return user;
  }

  @Nested
  @DisplayName("Precedence")
  class Precedence {

    @Test
    @DisplayName("A row for the user wins over the role row and the default row")
    void userRowWins() {
      givenRows(row(null, null, 10, null), row(ID_ROLE_USER, null, 20, null), row(null, ID_USER, 30, null));
      assertThat(entityLimitService.resolve(user(Role.ROLE_USER, ID_ROLE_USER), KEY)).contains(30);
    }

    @Test
    @DisplayName("Without a user row the role row wins over the default row")
    void roleRowWins() {
      givenRows(row(null, null, 10, null), row(ID_ROLE_USER, null, 20, null));
      assertThat(entityLimitService.resolve(user(Role.ROLE_USER, ID_ROLE_USER), KEY)).contains(20);
    }

    @Test
    @DisplayName("Without a user or role row the default row applies")
    void defaultRowApplies() {
      givenRows(row(null, null, 10, null));
      assertThat(entityLimitService.resolve(user(Role.ROLE_USER, ID_ROLE_USER), KEY)).contains(10);
    }

    @Test
    @DisplayName("A key with no row at all is unlimited")
    void noRowIsUnlimited() {
      givenRows();
      assertThat(entityLimitService.resolve(user(Role.ROLE_USER, ID_ROLE_USER), KEY)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Most privileged role")
  class MostPrivilegedRole {

    /**
     * The point of consulting only the highest ranked role: taking a numeric maximum over the cumulative role set
     * would let a limit written for ROLE_USER throttle every administrator.
     */
    @Test
    @DisplayName("An administrator is not bound by a row written for ROLE_USER")
    void adminIgnoresLowerRoleRow() {
      givenRows(row(ID_ROLE_USER, null, 5, null));
      assertThat(entityLimitService.resolve(user(Role.ROLE_ADMIN, ID_ROLE_ADMIN, ID_ROLE_ALL_EDIT, ID_ROLE_USER), KEY))
          .isEmpty();
    }

    @Test
    @DisplayName("An administrator is bound by a row written for ROLE_ADMIN")
    void adminHonoursOwnRoleRow() {
      givenRows(row(ID_ROLE_USER, null, 5, null), row(ID_ROLE_ADMIN, null, 50, null));
      assertThat(entityLimitService.resolve(user(Role.ROLE_ADMIN, ID_ROLE_ADMIN, ID_ROLE_ALL_EDIT, ID_ROLE_USER), KEY))
          .contains(50);
    }

    @Test
    @DisplayName("An ALLEDIT user is bound by a row written for ROLE_ALLEDIT")
    void allEditHonoursOwnRoleRow() {
      givenRows(row(ID_ROLE_ALL_EDIT, null, 25, null));
      assertThat(entityLimitService.resolve(user(Role.ROLE_ALL_EDIT, ID_ROLE_ALL_EDIT, ID_ROLE_USER), KEY))
          .contains(25);
    }
  }

  @Nested
  @DisplayName("Expiry")
  class Expiry {

    @Test
    @DisplayName("An expired user row falls through to the default row")
    void expiredRowFallsThrough() {
      givenRows(row(null, null, 10, null), row(null, ID_USER, 30, LocalDate.now().minusDays(1)));
      assertThat(entityLimitService.resolve(user(Role.ROLE_USER, ID_ROLE_USER), KEY)).contains(10);
    }

    @Test
    @DisplayName("A row expiring today is still honoured")
    void rowExpiringTodayApplies() {
      givenRows(row(null, null, 10, null), row(null, ID_USER, 30, LocalDate.now()));
      assertThat(entityLimitService.resolve(user(Role.ROLE_USER, ID_ROLE_USER), KEY)).contains(30);
    }
  }

  @Nested
  @DisplayName("Background context")
  class BackgroundContext {

    /**
     * Scheduled work such as the standing order execution runs without an authenticated user. Resolving must then
     * skip the user and role steps rather than fail, otherwise those jobs could not enforce a cap at all.
     */
    @Test
    @DisplayName("Without a user the default row applies and user rows are ignored")
    void nullUserUsesDefaultRow() {
      givenRows(row(null, null, 10, null), row(null, ID_USER, 30, null), row(ID_ROLE_USER, null, 20, null));
      assertThat(entityLimitService.resolve(null, KEY)).contains(10);
    }
  }

  @Nested
  @DisplayName("Cache")
  class Cache {

    @Test
    @DisplayName("Evicting a user drops that user's entry and keeps the others")
    void evictUserDropsOnlyThatUser() {
      givenRows(row(null, ID_USER, 30, null), row(null, null, 10, null));
      assertThat(entityLimitService.resolve(user(Role.ROLE_USER, ID_ROLE_USER), KEY)).contains(30);

      // A role change makes the cached value stale; after eviction the row set is read again.
      givenRows(row(null, null, 10, null));
      assertThat(entityLimitService.resolve(user(Role.ROLE_USER, ID_ROLE_USER), KEY))
          .as("still cached until evicted").contains(30);
      entityLimitService.evictUser(ID_USER);
      assertThat(entityLimitService.resolve(user(Role.ROLE_USER, ID_ROLE_USER), KEY)).contains(10);
    }

    /**
     * A row whose valid_until has passed must stop applying without a restart, which is what the date stamp on the
     * cache is for.
     */
    @Test
    @DisplayName("A date change discards the cache so an expiry takes effect")
    void dateChangeDiscardsCache() {
      givenRows(row(null, ID_USER, 30, null), row(null, null, 10, null));
      assertThat(entityLimitService.resolve(user(Role.ROLE_USER, ID_ROLE_USER), KEY)).contains(30);

      givenRows(row(null, null, 10, null));
      entityLimitCache.setBuiltOnForTest(LocalDate.now().minusDays(1));
      assertThat(entityLimitService.resolve(user(Role.ROLE_USER, ID_ROLE_USER), KEY)).contains(10);
    }
  }
}
