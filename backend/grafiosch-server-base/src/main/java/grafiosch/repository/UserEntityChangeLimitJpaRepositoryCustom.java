package grafiosch.repository;

import java.util.List;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.UserEntityChangeLimit;

public interface UserEntityChangeLimitJpaRepositoryCustom extends BaseRepositoryCustom<UserEntityChangeLimit> {

  List<ValueKeyHtmlSelectOptions> getPublicEntitiesAsHtmlSelectOptions(Integer idUser, Integer idUserEntityChangeLimit);
}
