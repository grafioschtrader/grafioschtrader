package grafiosch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.User;
import grafiosch.repository.UserJpaRepository;
import grafiosch.rest.GrafioschIntegrationTestContext;
import grafiosch.types.UserRightLimitCounter;

/** Exercises violation accounting against MariaDB, including concurrent and rejected tenant-context requests. */
@GrafioschIntegrationTestContext
class UserRightsLimitConcurrencyTest {
  @Autowired
  private UserService service;
  @Autowired
  private UserJpaRepository users;
  @Autowired
  private PlatformTransactionManager transactionManager;
  private User original;

  @BeforeEach
  void createUser() {
    original = users.saveAndFlush(new User("counter-" + UUID.randomUUID() + "@test.local", "unused",
        "counter-" + UUID.randomUUID().toString().substring(0, 12), "en", 0));
  }

  @AfterEach
  void deleteUser() {
    if (original != null) {
      users.deleteById(original.getIdUser());
    }
  }

  @ParameterizedTest
  @EnumSource(UserRightLimitCounter.class)
  @DisplayName("Simultaneous violations all commit without optimistic-lock failures or lost increments")
  void concurrentViolations(UserRightLimitCounter counter) throws Exception {
    int requests = 24;
    CountDownLatch ready = new CountDownLatch(requests);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<User>> futures = new ArrayList<>();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < requests; i++) {
        futures.add(executor.submit(() -> {
          ready.countDown();
          if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent requests did not start");
          }
          return service.incrementRightsLimitCount(original.getIdUser(), counter);
        }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<User> future : futures) {
        User updated = future.get(30, TimeUnit.SECONDS);
        assertThat(count(updated, counter)).isPositive();
        assertProfileUnchanged(updated);
      }
    }
    User persisted = users.findById(original.getIdUser()).orElseThrow();
    assertThat(count(persisted, counter)).isEqualTo(requests);
    assertThat(count(persisted,
        counter == UserRightLimitCounter.SECURITY_BREACH ? UserRightLimitCounter.LIMIT_EXCEEDED_TENANT_DATA
            : UserRightLimitCounter.SECURITY_BREACH)).isZero();
    assertThat(persisted.getVersion()).isEqualTo(original.getVersion() + requests);
    assertProfileUnchanged(persisted);
  }

  @Test
  @DisplayName("A rejected switched-tenant request retains its violation but cannot persist the temporary tenant")
  void violationSurvivesRollbackWithoutFlushingRequestUser() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      User requestUser = users.findById(original.getIdUser()).orElseThrow();
      requestUser.setIdTenant(-123);
      requestUser.setNickname("uncommitted change");
      User updated = service.incrementRightsLimitCount(original.getIdUser(), UserRightLimitCounter.SECURITY_BREACH);
      assertProfileUnchanged(updated);
      assertThat(updated.getSecurityBreachCount()).isEqualTo((short) 1);
      status.setRollbackOnly();
    });
    User persisted = users.findById(original.getIdUser()).orElseThrow();
    assertProfileUnchanged(persisted);
    assertThat(persisted.getSecurityBreachCount()).isEqualTo((short) 1);
  }

  @Test
  @DisplayName("Saving an older profile cannot erase a newly recorded violation")
  void staleProfileCannotOverwriteCounter() {
    service.incrementRightsLimitCount(original.getIdUser(), UserRightLimitCounter.LIMIT_EXCEEDED_TENANT_DATA);
    original.setNickname("stale profile edit");
    assertThatThrownBy(() -> users.saveAndFlush(original)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    assertThat(users.findById(original.getIdUser()).orElseThrow().getLimitRequestExceedCount()).isEqualTo((short) 1);
  }

  private int count(User user, UserRightLimitCounter counter) {
    return counter == UserRightLimitCounter.SECURITY_BREACH ? user.getSecurityBreachCount()
        : user.getLimitRequestExceedCount();
  }

  private void assertProfileUnchanged(User user) {
    assertThat(user.getIdTenant()).isEqualTo(original.getIdTenant());
    assertThat(user.getNickname()).isEqualTo(original.getNickname());
    assertThat(user.getUsername()).isEqualTo(original.getUsername());
    assertThat(user.getPassword()).isEqualTo(original.getPassword());
  }
}
