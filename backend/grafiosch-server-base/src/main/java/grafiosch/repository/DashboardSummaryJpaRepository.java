package grafiosch.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import grafiosch.entities.MailSendRecv;
import grafiosch.entities.ProposeUserTask;

/** Bounded projections over the existing inbox and administrator request populations. */
public interface DashboardSummaryJpaRepository extends Repository<MailSendRecv, Integer> {
  interface MailRow {
    Integer getId();

    String getNickname();

    String getSubject();

    LocalDateTime getCreationTime();

    Long getTotalCount();
  }

  interface LimitRow {
    Integer getId();

    String getNickname();

    LocalDateTime getCreationTime();

    Long getTotalCount();

    Long getUserCount();
  }

  /** Same visibility union as MailSendRecv.findByUserOrGroup, filtered by the UI unread rule; total before LIMIT. */
  @Query(name = "DashboardSummary.unreadMail", nativeQuery = true)
  List<MailRow> unreadMail(Integer idUser, int maxRows);

  /**
   * Existing LIMIT_CUD_CHANGE requests for existing users, including the exact useradmin population; total before
   * LIMIT.
   */
  @Query(name = "DashboardSummary.limitRequests", nativeQuery = true)
  List<LimitRow> limitRequests(int maxRows);

  /** Fetches fields for only the bounded selected request IDs in one query. */
  @Query("SELECT DISTINCT p FROM ProposeUserTask p LEFT JOIN FETCH p.proposeChangeFieldList WHERE p.idProposeRequest IN ?1")
  List<ProposeUserTask> limitRequestDetails(List<Integer> ids);
}
