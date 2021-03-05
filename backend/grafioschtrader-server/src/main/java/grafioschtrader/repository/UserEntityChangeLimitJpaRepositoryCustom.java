package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.UserEntityChangeLimit;

public interface UserEntityChangeLimitJpaRepositoryCustom extends BaseRepositoryCustom<UserEntityChangeLimit> {

  List<ValueKeyHtmlSelectOptions> getPublicEntitiesAsHtmlSelectOptions(Integer idUser, Integer idUserEntityChangeLimit);
}
