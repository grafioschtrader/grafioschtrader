package grafioschtrader.config;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.entities.User;

public class AuditorAwareImpl implements AuditorAware<Integer> {

  @Override
  public Optional<Integer> getCurrentAuditor() {
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      if (SecurityContextHolder.getContext().getAuthentication().getDetails() instanceof User) {
        return Optional
            .ofNullable(((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdUser());
      } else {
        return Optional.ofNullable(0);
      }
    } else {
      return Optional.ofNullable(0);
    }
  }

}
