package grafiosch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.User;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.security.UserAuthentication;
import jakarta.servlet.http.HttpServletResponse;

/** Checks the destructive account operation without ever opening a database connection. */
class TenantAccountContextTest {
  private final TenantBaseImpl<Object> repository = new TenantBaseImpl<>() {
  };

  @BeforeEach
  void switchTenant() {
    User user = new User(20);
    user.setActualIdTenant(10);
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Account deletion rejects a switched tenant before accessing any dependencies")
  void deletionRequiresHomeTenant() {
    assertThatExceptionOfType(GeneralNotTranslatedWithArgumentsException.class)
        .isThrownBy(repository::deleteMyDataAndUserAccount)
        .satisfies(error -> assertThat(error.getMessageKey()).isEqualTo("g.account.operation.home.tenant.only"));
  }

  @Test
  @DisplayName("Export rejects a switched tenant before writing the response or accessing data")
  void exportRequiresHomeTenant() {
    HttpServletResponse response = mock(HttpServletResponse.class);
    assertThatExceptionOfType(GeneralNotTranslatedWithArgumentsException.class)
        .isThrownBy(() -> repository.getExportPersonalDataAsZip(response))
        .satisfies(error -> assertThat(error.getMessageKey()).isEqualTo("g.account.operation.home.tenant.only"));
    verifyNoInteractions(response);
  }
}
