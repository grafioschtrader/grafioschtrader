package grafiosch.repository;

import java.util.List;

import grafiosch.dto.LimitKeyDefinition;
import grafiosch.entities.EntityLimit;

public interface EntityLimitJpaRepositoryCustom extends BaseRepositoryCustom<EntityLimit> {

  /**
   * Returns every limit key an administrator may configure, as the definitions the edit form needs.
   *
   * <p>
   * Two halves are unioned. The registered {@code MAX} keys come from the limit key registry with their counter's
   * scopes, their fresh-install default, their validation rule and their message key. The {@code DAY_CUD} and
   * {@code DAY_READ} keys are derived: every concrete, non-abstract {@code Auditable} entity that is not an
   * {@code AdminEntity}, taken from the JPA metamodel, plus the registered pseudo entity names. Deriving them means an
   * administrator keeps exactly the reach they have today, including entities that have no configured default at all.
   * </p>
   *
   * <p>
   * Keys for which the given user already holds a row are filtered out, so the picker cannot produce a duplicate.
   * </p>
   *
   * @param idUser the user the new or edited row belongs to, or {@code null} for a row that is not user-scoped
   * @param idEntityLimit the row currently being edited, whose own key must stay in the list, or {@code null} on create
   * @return the offered key definitions, registered MAX keys first
   */
  List<LimitKeyDefinition> getLimitKeyDefinitions(Integer idUser, Integer idEntityLimit);
}
