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

import grafioschtrader.dto.TradingDaysWithDateBoundaries;
import grafioschtrader.repository.TradingDaysBase.SaveTradingDays;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(RequestMappings.TRADINGDAYSPLUS_MAP)
@Tag(name = RequestMappings.TRADINGDAYSPLUS, description = "Controller for trading days plus")
public class TradingDaysPlusResource {

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Operation(summary = "Returns all possible tranding days of year", description = "", tags = {
      RequestMappings.TRADINGDAYSPLUS })
  @GetMapping(value = "/{year}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<TradingDaysWithDateBoundaries> getTradingDaysByYear(@PathVariable final int year) {
    return new ResponseEntity<>(tradingDaysPlusJpaRepository.getTradingDaysByYear(year), HttpStatus.OK);
  }

  @Operation(summary = "Save trading days of year", description = "Only admin can change this calendar", tags = {
      RequestMappings.TRADINGDAYSPLUS })
  @PutMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<TradingDaysWithDateBoundaries> deleteAndCreateMultiple(
      @Valid @RequestBody final SaveTradingDays saveTradingDaysPlus) {
    return new ResponseEntity<>(tradingDaysPlusJpaRepository.save(saveTradingDaysPlus), HttpStatus.OK);
  }

}
