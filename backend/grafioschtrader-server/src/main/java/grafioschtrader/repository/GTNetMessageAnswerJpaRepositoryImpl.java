package grafioschtrader.repository;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.GTNetMessageAnswer;

public class GTNetMessageAnswerJpaRepositoryImpl extends BaseRepositoryImpl<GTNetMessageAnswer>
 implements GTNetMessageAnswerJpaRepositoryCustom {

  @Autowired
  private GTNetMessageAnswerJpaRepository gtNetMessageAnswerJpaRepository;
}
