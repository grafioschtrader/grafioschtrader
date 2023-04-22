package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.MailEntity;

public interface MailEntityJpaRepository extends JpaRepository<MailEntity, Integer> {

}
