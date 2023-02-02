package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import grafioschtrader.entities.User;
import grafioschtrader.entities.projection.UserOwnProjection;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface UserJpaRepository
    extends JpaRepository<User, Integer>, UserJpaRepositoryCustom, UpdateCreateJpaRepository<User> {
  Optional<User> findByEmail(String username);

  Optional<User> findByNickname(String nickname);

  User findByIdTenantAndIdUser(Integer idTenant, Integer idUser);

  UserOwnProjection findByIdUserAndIdTenant(Integer idUser, Integer idTenant);

  List<User> findAllByOrderByIdUserAsc();

  int countByEnabled(boolean value);

  @Query(value = """
      DELETE u FROM user u JOIN verificationtoken v ON u.id_user = v.id_user
      WHERE u.id_tenant IS NULL AND v.expiry_date < NOW()""", nativeQuery = true)
  void removeWithExpiredVerificationToken();

  @Query(value = "SELECT id_Tenant FROM user WHERE email REGEXP ?1", nativeQuery = true)
  Integer[] findIdTenantByMailPattern(String mailPattern);

  @Query(value = "CALL moveCreatedByUserToOtherUser(:fromIdUser, :toIdUser, :schemaName);", nativeQuery = true)
  Integer moveCreatedByUserToOtherUser(@Param("fromIdUser") Integer fromIdUser, @Param("toIdUser") Integer toIdUser,
      @Param("schemaName") String schemaName);
}
