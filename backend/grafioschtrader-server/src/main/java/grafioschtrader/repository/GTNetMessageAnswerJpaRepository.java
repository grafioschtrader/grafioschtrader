package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNetMessageAnswer;


public interface GTNetMessageAnswerJpaRepository extends JpaRepository<GTNetMessageAnswer, Byte>,
 GTNetMessageAnswerJpaRepositoryCustom {
  
 
}
