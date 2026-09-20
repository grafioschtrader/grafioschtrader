package grafiosch.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Repository;

import grafiosch.common.UserAccessHelper;
import grafiosch.dto.DashboardDtos.Row;
import grafiosch.dto.DashboardDtos.SummaryList;
import grafiosch.entities.User;
import grafiosch.types.ProposeDataChangeState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;

/**
 * Read-only proposal summaries. Target existence is checked in bulk per mapped entity type, never by loading or
 * repairing proposals.
 */
@Repository
public class DashboardProposalRepository {
  private final EntityManager em;

  public DashboardProposalRepository(EntityManager em) {
    this.em = em;
  }

  public SummaryList proposals(User user, boolean own, int maxRows) {
    List<Row> rows = new ArrayList<>();
    long count = 0;
    String scope = own ? "p.createdBy = :user"
        : UserAccessHelper.hasHigherPrivileges(user) ? "1 = 1" : "p.idOwnerEntity = :user";
    var types = em.createQuery("SELECT DISTINCT p.entity FROM ProposeChangeEntity p WHERE p.dataChangeState = :state",
        String.class).setParameter("state", ProposeDataChangeState.OPEN.getValue()).getResultList();
    for (String type : types) {
      EntityType<?> target = em.getMetamodel().getEntities().stream()
          .filter(t -> t.getJavaType().getSimpleName().equals(type)).findFirst().orElse(null);
      if (target == null || !target.hasSingleIdAttribute())
        continue;
      String id = target.getSingularAttributes().stream().filter(a -> a.isId()).findFirst().orElseThrow().getName();
      // Only metamodel names enter the JPQL; proposal strings and request inputs are bound parameters.
      String predicate = " FROM ProposeChangeEntity p WHERE p.dataChangeState = :state AND p.entity = :type AND "
          + scope + " AND EXISTS (SELECT e FROM " + target.getName() + " e WHERE e." + id + " = p.idEntity)";
      var countQuery = em.createQuery("SELECT COUNT(p)" + predicate, Long.class);
      var listQuery = em.createQuery("SELECT p.idProposeRequest, p.entity, p.noteRequest, p.creationTime" + predicate
          + " ORDER BY p.creationTime DESC, p.idProposeRequest DESC", Object[].class).setMaxResults(maxRows);
      for (var query : List.of(countQuery, listQuery)) {
        query.setParameter("state", ProposeDataChangeState.OPEN.getValue()).setParameter("type", type);
        if (scope.contains(":user"))
          query.setParameter("user", user.getIdUser());
      }
      count += countQuery.getSingleResult();
      listQuery.getResultList().forEach(r -> rows
          .add(new Row((Integer) r[0], null, null, (String) r[1], (String) r[2], (LocalDateTime) r[3], null, null)));
    }
    rows.sort(Comparator.comparing(Row::creationTime, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(Row::id, Comparator.reverseOrder()));
    return new SummaryList(own ? "DASHBOARD_SUBMITTED_BY_YOU" : "DASHBOARD_FOR_YOU", count, null,
        "/mainview/proposeChangeTabMenu/" + (own ? "proposeyourproposal" : "proposerequestforyou"),
        rows.stream().limit(maxRows).toList());
  }
}
