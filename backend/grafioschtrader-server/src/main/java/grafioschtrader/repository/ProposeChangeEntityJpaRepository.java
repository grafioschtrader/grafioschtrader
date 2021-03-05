package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.ProposeChangeEntity;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface ProposeChangeEntityJpaRepository extends JpaRepository<ProposeChangeEntity, Integer>,
    ProposeChangeEntityJpaRepositoryCustom, UpdateCreateJpaRepository<ProposeChangeEntity> {

  List<ProposeChangeEntity> findByCreatedBy(Integer createdBy);

  List<ProposeChangeEntity> findByDataChangeState(byte dateChangeState);

  List<ProposeChangeEntity> findByIdOwnerEntityAndDataChangeState(Integer idOwnerEntity, byte dateChangeState);

  /**
   * Usage of this delete statement because of cascade delete in database and only
   * unidirectional mapping
   */
  @Override
  @Transactional
  @Modifying
  @Query(value = "DELETE FROM propose_request WHERE id_propose_request = ?1", nativeQuery = true)
  void deleteById(Integer id);

}
