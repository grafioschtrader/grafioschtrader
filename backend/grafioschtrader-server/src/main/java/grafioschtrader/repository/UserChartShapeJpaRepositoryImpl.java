package grafioschtrader.repository;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.UserChartShape;
import grafioschtrader.entities.UserChartShape.UserChartShapeKey;
import grafioschtrader.service.InstrumentVisibilityService;
import jakarta.transaction.Transactional;

/**
 * Implementation of the custom {@link UserChartShape} operations. It is the single place where the shapes of a chart
 * are validated before they reach the database.
 */
public class UserChartShapeJpaRepositoryImpl implements UserChartShapeJpaRepositoryCustom {

  /** Upper bound on the number of Plotly shapes one user may store for one instrument. */
  private static final int MAX_SHAPES = 50;

  /** Upper bound on the serialized size of the shape data, in characters. */
  private static final int MAX_SHAPE_DATA_CHARS = 16 * 1024;

  @Autowired
  private UserChartShapeJpaRepository userChartShapeJpaRepository;

  @Autowired
  private InstrumentVisibilityService instrumentVisibilityService;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  @Transactional
  public UserChartShape saveWithValidation(UserChartShape entity, User user) {
    UserChartShapeKey key = entity.getUserChartShapeKey();
    if (key == null || key.getIdSecuritycurrency() == null) {
      throw new DataViolationException("id.securitycurrency", "gt.chart.shape.instrument.unknown", null);
    }
    // The owner is always the authenticated user, never whatever the request carried.
    key.setIdUser(user.getIdUser());
    checkInstrumentVisible(key.getIdSecuritycurrency(), user);
    checkShapeData(entity);
    return userChartShapeJpaRepository.save(entity);
  }

  /**
   * Rejects an instrument that does not exist or that the user must not see. The rule itself lives in
   * {@link InstrumentVisibilityService} because more than one write path needs it; only the field and message of this
   * request contract stay here.
   */
  private void checkInstrumentVisible(Integer idSecuritycurrency, User user) {
    if (!instrumentVisibilityService.isVisible(idSecuritycurrency, user)) {
      throw new DataViolationException("id.securitycurrency", "gt.chart.shape.instrument.unknown", null);
    }
  }

  /**
   * Bounds the payload by the number of shapes and by its serialized size. The column has no length limit of its own,
   * so both bounds have to be applied here.
   */
  private void checkShapeData(UserChartShape entity) {
    final List<Map<String, Object>> shapeData = entity.getShapeData();
    if (shapeData == null) {
      throw new DataViolationException("shape.data", "gt.chart.shape.data.required", null);
    }
    if (shapeData.size() > MAX_SHAPES) {
      throw new DataViolationException("shape.data", "gt.chart.shape.data.too.many", new Object[] { MAX_SHAPES });
    }
    final String serialized;
    try {
      serialized = objectMapper.writeValueAsString(shapeData);
    } catch (JsonProcessingException ex) {
      // The list was produced by Jackson from the request body, so it must be serializable again.
      throw new UncheckedIOException(ex);
    }
    if (serialized.length() > MAX_SHAPE_DATA_CHARS) {
      throw new DataViolationException("shape.data", "gt.chart.shape.data.too.large",
          new Object[] { MAX_SHAPE_DATA_CHARS });
    }
  }
}
