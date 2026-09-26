package grafiosch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import grafiosch.entities.TenantAccess;
import grafiosch.entities.User;
import grafiosch.security.UserAuthentication;
import grafiosch.types.TenantAccessLevel;

/** The library's deletion extension and validation order, without application classes or a database. */
class TenantAccountDeletionHookTest {
  private final RecordingRepository target = new RecordingRepository();
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final TenantAccessJpaRepository access = mock(TenantAccessJpaRepository.class);
  private final UserJpaRepository users = mock(UserJpaRepository.class);
  private User owner;

  @BeforeEach
  void fixture() {
    owner = user(10, 100);
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(owner));
    ReflectionTestUtils.setField(target, "jdbcTemplate", jdbc);
    ReflectionTestUtils.setField(target, "tenantAccessJpaRepository", access);
    ReflectionTestUtils.setField(target, "userJpaRepository", users);
    ReflectionTestUtils.setField(target, "demoAccountPatternDE", "demo.*");
    ReflectionTestUtils.setField(target, "demoAccountPatternEN", "demo.*");
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  @DisplayName("A checked cleanup exception rolls back either outer deletion path before SQL deletion")
  void checkedFailureRollsBack(boolean managed) throws Exception {
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    TransactionStatus status = mock(TransactionStatus.class);
    when(manager.getTransaction(any())).thenReturn(status);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(manager);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    ProxyFactory factory = new ProxyFactory(target);
    factory.addAdvice(interceptor);
    TenantBaseCustom repository = (TenantBaseCustom) factory.getProxy();
    User client = user(20, 200);

    assertThatThrownBy(() -> {
      if (managed)
        repository.deleteManagedClientData(client);
      else
        repository.deleteMyDataAndUserAccount();
    }).isInstanceOf(IOException.class).hasMessage("cleanup failed");
    assertThat(target.deletedTenant).isEqualTo(managed ? client.getActualIdTenant() : owner.getActualIdTenant());
    verify(manager).rollback(status);
    verifyNoInteractions(jdbc);
  }

  @Test
  @DisplayName("Switched context is refused before calling the cleanup hook")
  void switchedContextDoesNotClean() {
    owner.setActualIdTenant(200);
    assertGuardRejects();
    verifyNoInteractions(access, users);
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  @DisplayName("Demo-account guards precede cleanup for both deletion paths")
  void demoGuardDoesNotClean(boolean managed) {
    User demo = new User("demo@test.local", "unused", "demo", "en", 0);
    demo.setIdTenant(100);
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(demo));
    assertThatThrownBy(() -> {
      if (managed)
        target.deleteManagedClientData(demo);
      else
        target.deleteMyDataAndUserAccount();
    }).isInstanceOf(grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException.class);
    assertThat(target.deletedTenant).isNull();
    verifyNoInteractions(jdbc, access, users);
  }

  @Test
  @DisplayName("An advisor with clients is refused before the cleanup hook")
  void clientGuardDoesNotClean() {
    when(access.findByIdUser(owner.getIdUser()))
        .thenReturn(List.of(new TenantAccess(owner.getIdUser(), 200, TenantAccessLevel.MANAGE)));
    assertGuardRejects();
  }

  @Test
  @DisplayName("Shared viewers are checked before the cleanup hook")
  void viewerGuardDoesNotClean() {
    when(access.findByIdTenant(owner.getIdTenant()))
        .thenReturn(List.of(new TenantAccess(20, owner.getIdTenant(), TenantAccessLevel.READ)));
    assertGuardRejects();
  }

  private void assertGuardRejects() {
    assertThatThrownBy(target::deleteMyDataAndUserAccount)
        .isInstanceOf(grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException.class);
    assertThat(target.deletedTenant).isNull();
    verifyNoInteractions(jdbc);
  }

  private User user(int id, int tenant) {
    User user = new User("hook-" + id + "@test.local", "unused", "hook" + id, "en", 0);
    user.setIdTenant(tenant);
    user.setIdUser(id);
    return user;
  }

  private static class RecordingRepository extends TenantBaseImpl<Object> {
    private Integer deletedTenant;

    @Override
    protected void beforeDeleteTenantData(Integer idTenant) throws IOException {
      deletedTenant = idTenant;
      throw new IOException("cleanup failed");
    }
  }
}
