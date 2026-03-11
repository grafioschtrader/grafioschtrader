package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.SecurityActionApplication;

public interface SecurityActionApplicationJpaRepository extends JpaRepository<SecurityActionApplication, Integer> {

  List<SecurityActionApplication> findByIdTenant(Integer idTenant);

  Optional<SecurityActionApplication> findBySecurityAction_IdSecurityActionAndIdTenant(Integer idSecurityAction,
      Integer idTenant);

  boolean existsBySecurityAction_IdSecurityAction(Integer idSecurityAction);
}
