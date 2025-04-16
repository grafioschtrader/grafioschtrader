package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.CopyTradingDaysFromSourceToTarget;
import grafioschtrader.dto.TradingDaysWithDateBoundaries;
import grafioschtrader.repository.TradingDaysBase.SaveTradingDays;
import grafioschtrader.repository.TradingDaysMinusJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(RequestGTMappings.TRADINGDAYSMINUS_MAP)
@Tag(name = RequestGTMappings.TRADINGDAYSMINUS, description = "Controller for stock exchange calendar")
public class TradingDaysMinusResource {

  @Autowired
  private TradingDaysMinusJpaRepository tradingDaysMinusJpaRepository;

  @Operation(summary = "Return of the non-trading days of a specific stock exchange and a specific year.", description = "", tags = {
      RequestGTMappings.TRADINGDAYSMINUS })
  @GetMapping(value = "/{idStockexchange}/{year}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<TradingDaysWithDateBoundaries> getTradingDaysMinusByStockexchangeAndYear(
      @PathVariable final int idStockexchange, @PathVariable final int year) {
    return new ResponseEntity<>(
        tradingDaysMinusJpaRepository.getTradingDaysByIdStockexchangeAndYear(idStockexchange, year), HttpStatus.OK);
  }

  @Operation(summary = "Change in the trading calendar of a particular stock exchange in a particular year.", description = "", tags = {
      RequestGTMappings.TRADINGDAYSMINUS })
  @PutMapping(value = "/{idStockexchange}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<TradingDaysWithDateBoundaries> deleteAndCreateMultiple(@PathVariable final int idStockexchange,
      @Valid @RequestBody final SaveTradingDays saveTradingDays) {
    return new ResponseEntity<>(tradingDaysMinusJpaRepository.save(idStockexchange, saveTradingDays), HttpStatus.OK);
  }

  @Operation(summary = """
      This allows a trading calendar from one stock exchange to be copied to another.
      It is possible to copy a single year or all years whose date range GT supports.""", description = "", tags = {
      RequestGTMappings.TRADINGDAYSMINUS })
  @PutMapping(value = "/copytradingdays", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<TradingDaysWithDateBoundaries> copyAllTradingDaysMinusToOtherStockexchange(
      @Valid @RequestBody final CopyTradingDaysFromSourceToTarget copyTradingDaysFromSourceToTarget) {
    return new ResponseEntity<>(tradingDaysMinusJpaRepository
        .copyTradingDaysMinusToOtherStockexchange(copyTradingDaysFromSourceToTarget, false), HttpStatus.OK);
  }

}
