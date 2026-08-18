package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafioschtrader.entities.UserChartShape;
import grafioschtrader.entities.UserChartShape.UserChartShapeKey;
import grafioschtrader.repository.UserChartShapeJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for managing user chart shapes. Provides endpoints to retrieve, save, and delete
 * Plotly.js drawing shapes associated with a specific security or currency pair.
 */
@RestController
@RequestMapping(RequestGTMappings.USER_CHART_SHAPE_MAP)
@Tag(name = RequestGTMappings.USER_CHART_SHAPE, description = "Controller for user chart drawing shapes")
public class UserChartShapeResource {

  @Autowired
  private UserChartShapeJpaRepository userChartShapeJpaRepository;

  @Operation(summary = "Returns saved chart shapes for a specific security/currency pair.", tags = {
      RequestGTMappings.USER_CHART_SHAPE })
  @GetMapping(value = "/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UserChartShape> getShapes(@PathVariable final Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    UserChartShapeKey key = new UserChartShapeKey(user.getIdUser(), idSecuritycurrency);
    return userChartShapeJpaRepository.findById(key)
        .map(shape -> ResponseEntity.ok().body(shape))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  /**
   * Stores the shapes of one chart for the current user. Ownership, the referenced instrument and the size of the
   * shape data are all settled in {@code UserChartShapeJpaRepositoryImpl.saveWithValidation}, so that the only write
   * path into {@code user_chart_shape} carries the checks.
   */
  @Operation(summary = "Creates or updates chart shapes for a specific security/currency pair.", tags = {
      RequestGTMappings.USER_CHART_SHAPE })
  @PutMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UserChartShape> saveShapes(@RequestBody UserChartShape userChartShape) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(userChartShapeJpaRepository.saveWithValidation(userChartShape, user), HttpStatus.OK);
  }

  @Operation(summary = "Deletes chart shapes for a specific security/currency pair.", tags = {
      RequestGTMappings.USER_CHART_SHAPE })
  @DeleteMapping(value = "/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteShapes(@PathVariable final Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    UserChartShapeKey key = new UserChartShapeKey(user.getIdUser(), idSecuritycurrency);
    userChartShapeJpaRepository.deleteById(key);
    return ResponseEntity.ok().build();
  }
}
