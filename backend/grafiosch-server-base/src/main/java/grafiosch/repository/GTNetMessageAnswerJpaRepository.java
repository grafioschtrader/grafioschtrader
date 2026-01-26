package grafiosch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import grafiosch.entities.GTNetMessageAnswer;
import grafiosch.rest.UpdateCreateJpaRepository;

public interface GTNetMessageAnswerJpaRepository
    extends GTNetMessageAnswerJpaRepositoryBase, GTNetMessageAnswerJpaRepositoryCustom, UpdateCreateJpaRepository<GTNetMessageAnswer> {

  /**
   * Finds all response rules for a given request message code, ordered by priority (ascending). Lower priority values
   * are evaluated first, forming the condition chain for automatic response resolution.
   *
   * @param requestMsgCode the request message code value to find rules for
   * @return list of matching rules ordered by priority, empty if no rules configured
   */
  @Query("SELECT g FROM GTNetMessageAnswer g WHERE g.requestMsgCode = :requestMsgCode ORDER BY g.priority ASC")
  List<GTNetMessageAnswer> findByRequestMsgCodeOrderByPriority(@Param("requestMsgCode") byte requestMsgCode);

}
