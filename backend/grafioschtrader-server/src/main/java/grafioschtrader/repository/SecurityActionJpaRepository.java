package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.SecurityAction;

public interface SecurityActionJpaRepository extends JpaRepository<SecurityAction, Integer> {

  List<SecurityAction> findAllByOrderByActionDateDesc();
}
