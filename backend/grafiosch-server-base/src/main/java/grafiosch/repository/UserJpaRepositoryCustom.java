package grafiosch.repository;

import java.sql.SQLException;
import java.util.List;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.User;

public interface UserJpaRepositoryCustom extends BaseRepositoryCustom<User> {

  List<User> connectUserWithUserAndLimitProposals();

  List<ValueKeyHtmlSelectOptions> getIdUserAndNicknameExcludeMe();

  Integer moveCreatedByUserToOtherUser(Integer fromIdUser, Integer toIdUser) throws SQLException;
}