package grafiosch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.common.UpdateQuery;
import grafiosch.entities.ProposeChangeEntity;
import grafiosch.rest.UpdateCreateJpaRepository;

public interface ProposeChangeEntityJpaRepository extends JpaRepository<ProposeChangeEntity, Integer>,
    ProposeChangeEntityJpaRepositoryCustom, UpdateCreateJpaRepository<ProposeChangeEntity> {

  List<ProposeChangeEntity> findByCreatedBy(Integer createdBy);

  List<ProposeChangeEntity> findByDataChangeState(byte dateChangeState);

  List<ProposeChangeEntity> findByIdOwnerEntityAndDataChangeState(Integer idOwnerEntity, byte dateChangeState);

  /**
   * Usage of this delete statement because of cascade delete in database and only unidirectional mapping
   */
  @Override
  @UpdateQuery(value = "DELETE FROM propose_request WHERE id_propose_request = ?1", nativeQuery = true)
  void deleteById(Integer id);

}
