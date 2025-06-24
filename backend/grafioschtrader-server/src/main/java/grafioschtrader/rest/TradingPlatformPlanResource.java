package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.rest.UpdateCreateDeleteAuditResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.repository.TradingPlatformPlanJpaRepository;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.TRADINGPLATFORMPLAND_MAP)
@Tag(name = RequestGTMappings.TRADINGPLATFORMPLAND, description = "Controller for trading platform plan")
public class TradingPlatformPlanResource extends UpdateCreateDeleteAuditResource<TradingPlatformPlan> {

  @Autowired
  private TradingPlatformPlanJpaRepository tradingPlatformPlanJpaRepository;

  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<TradingPlatformPlan>> getAllTradingPlatform() {
    return new ResponseEntity<>(tradingPlatformPlanJpaRepository.findAll(), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<TradingPlatformPlan> getUpdateCreateJpaRepository() {
    return tradingPlatformPlanJpaRepository;
  }

  
  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

}
