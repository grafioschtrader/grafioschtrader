package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.StockexchangeHasSecurity;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.repository.StockexchangeJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


@RestController
@RequestMapping(RequestMappings.STOCKEXCHANGE_MAP)
@Tag(name = RequestMappings.STOCKEXCHANGE, description = "Controller for stockexchange")
public class StockexchangeResource extends UpdateCreateDeleteAuditResource<Stockexchange> {

  @Autowired
  StockexchangeJpaRepository stockexchangeJpaRepository;

  @Operation(summary = "Returns all stock exchanges sorted by name", description = "", 
      tags = { RequestMappings.STOCKEXCHANGE })
  @GetMapping(value = "/", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Stockexchange>> getAllStockexchanges() {
    return new ResponseEntity<>(stockexchangeJpaRepository.findAllByOrderByNameAsc(), HttpStatus.OK);
  }

  @Operation(summary = "Returns if specified stock exchange has a depending security", description = "1 has a security", 
      tags = { RequestMappings.STOCKEXCHANGE })
  @GetMapping(value = "/{idStockexchange}/hassecurity", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Boolean> stockexchangeHasSecurity(@PathVariable final Integer idStockexchange) {
    return new ResponseEntity<>(stockexchangeJpaRepository.stockexchangeHasSecurity(idStockexchange) > 0,
        HttpStatus.OK);
  }

  @Operation(summary = "Returns if stock exchanges has a at least one depending security", description = "1 has a security", 
      tags = { RequestMappings.STOCKEXCHANGE })
  @GetMapping(value = "/hassecurity", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<StockexchangeHasSecurity>> stockexchangesHasSecurity() {
    return new ResponseEntity<>(stockexchangeJpaRepository.stockexchangesHasSecurity(), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<Stockexchange> getUpdateCreateJpaRepository() {
    return stockexchangeJpaRepository;
  }

}
