package grafiosch.repository;

import java.sql.SQLException;
import java.util.List;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.User;

public interface UserJpaRepositoryCustom extends BaseRepositoryCustom<User> {

  //@formatter:off
  /**
   * Fetches all users ordered by user ID (ascending) and attaches any pending
   * logout or limit-change proposals to each user.
   *
   * Implementation notes:
   * - Loads all users via findAllByOrderByIdUserAsc()
   * - Loads all ProposeUserTask entries and matches each to the corresponding user
   * - If a task’s type is RELEASE_LOGOUT, assigns it to userChangePropose;
   *   otherwise adds it to the user’s limitChangeProposals
   *
   * @return a list of User entities, each enriched with any related logout or limit-change proposals
   */
  //@formatter:on
  List<User> connectUserWithUserAndLimitProposals();

  /**
   * Retrieves selectable “ID – Nickname” options for all users except the currently authenticated user.
   *
   * Only administrators may invoke this; non-admins will receive a SecurityException. Each option’s value is the user
   * ID (as String) and its label is “<ID> – <nickname>”.
   *
   * @return a list of ValueKeyHtmlSelectOptions for all users except the caller
   * @throws SecurityException if the current user is not an administrator
   */
  List<ValueKeyHtmlSelectOptions> getIdUserAndNicknameExcludeMe();

  /**
   * Reassigns all database records created by one user to another user.
   *
   * This method determines the current database name from the JDBC connection URL and then performs a bulk update of
   * all “created by” references.
   *
   * @param fromIdUser the ID of the user whose created-by references will be moved
   * @param toIdUser   the ID of the user who will become the new creator
   * @return the number of rows that were updated
   * @throws SQLException if an error occurs while accessing or updating the database
   */
  Integer moveCreatedByUserToOtherUser(Integer fromIdUser, Integer toIdUser) throws SQLException;
}