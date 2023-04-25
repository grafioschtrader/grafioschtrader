package grafioschtrader.repository;

import java.sql.SQLException;
import java.util.List;

import grafioschtrader.entities.User;

public interface UserJpaRepositoryCustom extends BaseRepositoryCustom<User> {

  List<User> connectUserWithUserAndLimitProposals();
 
  Integer moveCreatedByUserToOtherUser(Integer fromIdUser, Integer toIdUser) throws SQLException;
}