package grafiosch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import grafiosch.common.UpdateQuery;
import grafiosch.entities.User;
import grafiosch.entities.projection.UserOwnProjection;
import grafiosch.rest.UpdateCreateJpaRepository;

public interface UserJpaRepository
    extends JpaRepository<User, Integer>, UserJpaRepositoryCustom, UpdateCreateJpaRepository<User> {
  Optional<User> findByEmail(String username);

  Optional<User> findByNickname(String nickname);

  Optional<User> findByIdTenant(Integer idTenant);

  User findByIdTenantAndIdUser(Integer idTenant, Integer idUser);

  UserOwnProjection findByIdUserAndIdTenant(Integer idUser, Integer idTenant);

  List<User> findAllByOrderByIdUserAsc();

  int countByEnabled(boolean value);

  @Query("SELECT u.idUser AS idUser, u.localeStr AS localeStr FROM User u WHERE u.idUser IN ?1")
  List<IdUserLocale> findIdUserAndLocaleStrByIdUsers(List<Integer> idUsers);

  @UpdateQuery(value = """
      DELETE u FROM user u JOIN verificationtoken v ON u.id_user = v.id_user
      WHERE u.id_tenant IS NULL AND v.expiry_date < NOW()""", nativeQuery = true)
  void removeWithExpiredVerificationToken();

  @Query(value = "SELECT u.id_user AS idUser, u.nickname FROM user u WHERE u.id_user <> ?1", nativeQuery = true)
  List<IdUserAndNickname> getIdUserAndNicknameExcludeUser(Integer idUser);

  @Query(value = "SELECT u.* FROM user u WHERE u.email REGEXP ?1", nativeQuery = true)
  List<User> getUsersByMailPattern(String mailPattern);

  /**
   * Moves shared data from one user to another. It is assumed that shared data has a field 'created_by', whereby tables
   * beginning with "user" are excluded.
   */
  @Query(value = "CALL moveCreatedByUserToOtherUser(:fromIdUser, :toIdUser, :schemaName);", nativeQuery = true)
  Integer moveCreatedByUserToOtherUser(@Param("fromIdUser") Integer fromIdUser, @Param("toIdUser") Integer toIdUser,
      @Param("schemaName") String schemaName);

  /**
   * Fetches all users’ IDs and nicknames in the given role—except for one user.
   *
   * This projection is used when you want to list everyone in a role (e.g. to populate a UI dropdown) but exclude a
   * specific user (typically the current user).
   *
   * @param roleName      the name of the role whose members should be returned
   * @param excludeUserId the user ID to omit from the result set
   * @return a list of projections ({@link IdUserAndNickname}), each containing an enabled user’s ID and nickname
   */
  @Query(nativeQuery = true)
  List<IdUserAndNickname> getIdUserAndNicknameByRoleExcludeUser(String roleName, Integer exludeIdUser);

  /**
   * Retrieves users’ email addresses and locales *only* if they have no forwarding rule for the specified communication
   * and target types.
   *
   * This prevents sending a direct email when a mail‐forwarding setting already applies.
   *
   * @param messageComType    the communication type to check (e.g. system vs. user messages)
   * @param messageTargetType the target type to check (e.g. email vs. internal messaging)
   * @return a list of {@link EMailLocale} projections (email + locale); empty if every user has a matching forwarding
   *         rule
   */
  @Query(nativeQuery = true)
  List<EMailLocale> getEmailExcludeWhenMsgComTypeAndTargetTypeExists(Byte messageComType, Byte messageTargetType);

  public interface EMailLocale {
    String getEmail();

    String getLocale();
  }

  public interface IdUserAndNickname {
    int getIdUser();

    String getNickname();
  }

  public interface IdUserLocale {
    Integer getIdUser();

    String getLocaleStr();
  }
}
