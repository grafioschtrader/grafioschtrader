package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.time.LocalDate;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.MissingQuotesWithSecurities;
import grafioschtrader.reports.PerformanceReport;
import grafioschtrader.reportviews.performance.FirstAndMissingTradingDays;
import grafioschtrader.reportviews.performance.PerformancePeriod;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.HOLDING_MAP)
@Tag(name = RequestMappings.HOLDING, description = "Controller for security holdings and performance report")
public class HoldingResource {

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  private PerformanceReport performanceReport;

  @GetMapping(value = "/getdatesforform", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<FirstAndMissingTradingDays> getFirstAndMissingTradingDays(
      @RequestParam(required = false) final Integer idPortfolio) throws InterruptedException, ExecutionException {
    if (idPortfolio != null) {
      return new ResponseEntity<>(performanceReport.getFirstAndMissingTradingDaysByPortfolio(idPortfolio),
          HttpStatus.OK);
    } else {
      return new ResponseEntity<>(performanceReport.getFirstAndMissingTradingDaysByTenant(), HttpStatus.OK);
    }
  }

  @GetMapping(value = "/{dateFrom}/{dateTo}/{periodSplit}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<PerformancePeriod> getPeriodPerformance(
      @PathVariable() @DateTimeFormat(iso = ISO.DATE) final LocalDate dateFrom,
      @PathVariable() @DateTimeFormat(iso = ISO.DATE) final LocalDate dateTo,
      @PathVariable() final WeekYear periodSplit, @RequestParam(required = false) final Integer idPortfolio)
      throws Exception {
    if (idPortfolio != null) {
      return new ResponseEntity<>(
          performanceReport.getPeriodPerformanceByPortfolio(idPortfolio, dateFrom, dateTo, periodSplit), HttpStatus.OK);
    } else {
      return new ResponseEntity<>(performanceReport.getPeriodPerformanceByTenant(dateFrom, dateTo, periodSplit),
          HttpStatus.OK);
    }
  }

  @Operation(summary = "Returns the missing qoutes for securties during the holding period", description = "", tags = {
      RequestMappings.HOLDING })
  @GetMapping(value = "/missingquotes/{year}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<MissingQuotesWithSecurities> getMissingQuotesWithSecurities(@PathVariable() final Integer year)
      throws InterruptedException, ExecutionException {
    return new ResponseEntity<>(holdSecurityaccountSecurityRepository.getMissingQuotesWithSecurities(year),
        HttpStatus.OK);
  }

}
