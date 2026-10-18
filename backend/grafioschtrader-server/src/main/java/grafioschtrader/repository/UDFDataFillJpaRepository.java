package grafioschtrader.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.UDFDataFill;
import grafioschtrader.entities.UDFDataFill.UDFDataFillKey;

/**
 * Repository of the fill state of the cells of user-defined security fields. Rows are written only by
 * {@code UDFFillWriter}; the queries here read them and the values and scope they are compared with.
 */
public interface UDFDataFillJpaRepository extends JpaRepository<UDFDataFill, UDFDataFillKey> {

  /**
   * Returns every fill row of one field.
   *
   * @param idUDFMetadata the field
   * @return the fill rows of the field, in no particular order
   */
  List<UDFDataFill> findById_IdUDFMetadata(Integer idUDFMetadata);

  /**
   * Returns the fill rows of one field for the given securities.
   *
   * @param idUDFMetadata the field
   * @param idEntities    the securities
   * @return the existing fill rows
   */
  List<UDFDataFill> findById_IdUDFMetadataAndId_IdEntityIn(Integer idUDFMetadata, Collection<Integer> idEntities);

  /**
   * Deletes every fill row of a field, used when its fill source is switched to manual. The values stay in
   * {@code udf_data} and are manual from then on.
   *
   * @param idUDFMetadata the field
   * @return the number of deleted rows
   */
  @Transactional
  @Modifying
  @Query(value = "DELETE FROM udf_data_fill WHERE id_udf_metadata = ?1", nativeQuery = true)
  int deleteByIdUDFMetadata(Integer idUDFMetadata);

  /**
   * Deletes the fill rows of a field in the given states, used to ask a language model again for rejected, discarded
   * and not found cells.
   *
   * @param idUDFMetadata the field
   * @param fillStates    the byte values of the states
   * @return the number of deleted rows
   */
  @Transactional
  @Modifying
  @Query(value = "DELETE FROM udf_data_fill WHERE id_udf_metadata = ?1 AND fill_state IN (?2)", nativeQuery = true)
  int deleteByIdUDFMetadataAndFillStateIn(Integer idUDFMetadata, Collection<Byte> fillStates);

  /**
   * Returns the fill rows of all fields of a user for the given securities, used for the fill marks of the watchlist.
   *
   * Named query: UDFDataFill.getByIdUserAndIdEntityIn
   *
   * @param idUser     the owner of the fields
   * @param idEntities the securities of the watchlist
   * @return the fill rows of the user's fields for these securities
   */
  @Query(nativeQuery = true)
  List<UDFDataFill> getByIdUserAndIdEntityIn(Integer idUser, Collection<Integer> idEntities);

  /**
   * Returns the IDs of the securities of the tenant scope: every security in a watchlist of the tenant and every
   * security referenced by a transaction of the tenant, including closed positions and inactive instruments. Private
   * securities of other tenants are excluded. The asset classes of a field are not applied here.
   *
   * Named query: UDFDataFill.getTenantScopeSecurityIds
   *
   * @param idTenant the home tenant of the owner of the field
   * @return the distinct security IDs, ascending
   */
  @Query(nativeQuery = true)
  List<Integer> getTenantScopeSecurityIds(Integer idTenant);

  /**
   * Returns the non-null values of one field of a user, read with {@code JSON_VALUE} from the {@code udf_data} rows of
   * the entity {@code Security}. With user 0 this reads a system-wide field such as the persisted Yahoo symbol.
   *
   * Named query: UDFDataFill.getFieldValuesOfUser
   *
   * @param idUser        the owner of the {@code udf_data} rows
   * @param idUDFMetadata the field, read as JSON key {@code f<idUDFMetadata>}
   * @return per security the value as text
   */
  @Query(nativeQuery = true)
  List<IdEntityValue> getFieldValuesOfUser(Integer idUser, Integer idUDFMetadata);

  /**
   * One value of a field for one security.
   */
  interface IdEntityValue {
    Integer getIdEntity();

    String getJsonValue();
  }
}
