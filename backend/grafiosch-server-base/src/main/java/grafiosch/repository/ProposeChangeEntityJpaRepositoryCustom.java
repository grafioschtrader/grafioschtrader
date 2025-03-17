package grafiosch.repository;

import java.util.List;

import grafiosch.entities.ProposeChangeEntity;
import grafiosch.repository.ProposeChangeEntityJpaRepositoryImpl.ProposeChangeEntityWithEntity;

public interface ProposeChangeEntityJpaRepositoryCustom extends BaseRepositoryCustom<ProposeChangeEntity> {
  List<ProposeChangeEntityWithEntity> getProposeChangeEntityWithEntity() throws Exception;
}
