package grafioschtrader.repository;

import grafioschtrader.entities.ProposeUserTask;
import jakarta.mail.MessagingException;

public interface ProposeUserTaskJpaRepositoryCustom extends BaseRepositoryCustom<ProposeUserTask> {

  void createReleaseLougout(Integer idTargetUser, String field, String note) throws Exception;

  String rejectUserTask(Integer idProposeRequest, String rejectNote) throws MessagingException;
}
