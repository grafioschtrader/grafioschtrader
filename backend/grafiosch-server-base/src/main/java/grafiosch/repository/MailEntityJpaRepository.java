package grafiosch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.MailEntity;

public interface MailEntityJpaRepository extends JpaRepository<MailEntity, Integer> {

}
