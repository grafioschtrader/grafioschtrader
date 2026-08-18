package grafioschtrader.repository;

import grafiosch.entities.User;
import grafioschtrader.entities.UserChartShape;

/**
 * Custom repository operations for {@link UserChartShape} beyond standard CRUD.
 */
public interface UserChartShapeJpaRepositoryCustom {

  /**
   * Persists the chart shapes of one user for one instrument after validating the request.
   *
   * <p>
   * The table carries a foreign key on {@code id_user} only, and its {@code shape_data} column is constrained by
   * nothing but {@code json_valid}. Without the checks performed here a caller could write one arbitrarily large row
   * per arbitrary integer, which is why this method rather than a plain {@code save} is the only write path.
   * </p>
   *
   * @param entity the shapes to store; its key must name an instrument that exists and is visible to the user
   * @param user   the owner of the shapes, taken from the authenticated session by the caller
   * @return the persisted shapes
   * @throws grafiosch.exceptions.DataViolationException if the instrument is unknown or not visible to the user, or if
   *                                                     the shape data exceeds the permitted number of shapes or size
   */
  UserChartShape saveWithValidation(UserChartShape entity, User user);
}
