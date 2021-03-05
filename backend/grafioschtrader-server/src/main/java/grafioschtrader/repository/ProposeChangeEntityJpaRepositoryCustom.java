package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.ProposeChangeEntity;
import grafioschtrader.repository.ProposeChangeEntityJpaRepositoryImpl.ProposeChangeEntityWithEntity;

public interface ProposeChangeEntityJpaRepositoryCustom extends BaseRepositoryCustom<ProposeChangeEntity> {
  List<ProposeChangeEntityWithEntity> getProposeChangeEntityWithEntity() throws Exception;
}
