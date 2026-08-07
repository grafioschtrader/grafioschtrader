package grafiosch.config;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.User;

/**
 * Supplies the id of the acting user to Spring Data JPA auditing, which fills {@code created_by} and
 * {@code last_modified_by} on every {@code Auditable} entity of {@code grafiosch-base}.
 *
 * <p>
 * User id {@code 0} — the "User Zero" placeholder row — is returned whenever there is no authenticated user, which
 * happens for writes made during startup, from scheduled tasks and from the self-registration endpoint, where the user
 * being created cannot yet be its own auditor.
 *
 * <p>
 * Registering this is mandatory for any application built on these libraries: without a
 * {@code @EnableJpaAuditing(auditorAwareRef = "auditorAware")} configuration every insert into an audited table fails
 * with {@code Column 'created_by' cannot be null}.
 */
public class AuditorAwareImpl implements AuditorAware<Integer> {

  @Override
  public Optional<Integer> getCurrentAuditor() {
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      if (SecurityContextHolder.getContext().getAuthentication().getDetails() instanceof User user) {
        return Optional.ofNullable(user.getIdUser());
      } else {
        return Optional.of(0);
      }
    } else {
      return Optional.of(0);
    }
  }
}
