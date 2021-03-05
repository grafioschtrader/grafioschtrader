package grafioschtrader.repository;

import javax.mail.MessagingException;

import grafioschtrader.entities.ProposeUserTask;

public interface ProposeUserTaskJpaRepositoryCustom extends BaseRepositoryCustom<ProposeUserTask> {

  void createReleaseLougout(Integer idTargetUser, String field, String note) throws Exception;

  String rejectUserTask(Integer idProposeRequest, String rejectNote) throws MessagingException;
}
