package grafiosch.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.UserChartShape;
import grafiosch.entities.UserChartShape.UserChartShapeKey;

/**
 * Repository for managing user chart shape persistence. Provides CRUD operations for chart drawing shapes
 * associated with a specific user and security/currency pair.
 */
public interface UserChartShapeJpaRepository extends JpaRepository<UserChartShape, UserChartShapeKey> {

  /**
   * Finds chart shapes for a given user and security/currency pair.
   *
   * @param key composite key containing user ID and security/currency pair ID
   * @return Optional containing the chart shape data, or empty if none exist
   */
  Optional<UserChartShape> findById(UserChartShapeKey key);
}
