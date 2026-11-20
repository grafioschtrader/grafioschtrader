package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.common.ClientClock;
import grafioschtrader.entities.Dividend;
import grafioschtrader.reports.SecurityDividendMetricsReport;
import grafioschtrader.reportviews.securitydividends.SecurityDividendChart;
import grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics;
import grafioschtrader.repository.DividendJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.DIVIDEND_MAP)
@Tag(name = Dividend.TABNAME, description = "Controller for security dividend")
public class DividendResource {

  @Autowired
  private DividendJpaRepository dividendJpaRepository;

  @Autowired
  private SecurityDividendMetricsReport securityDividendMetricsReport;

  @Operation(summary = "Returns all dividends of a security which is identified by ID", description = "", tags = {
      Dividend.TABNAME })
  @GetMapping(value = "/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Dividend>> getDividendsByIdSecuritycurrency(
      @PathVariable final Integer idSecuritycurrency) {
    return new ResponseEntity<>(dividendJpaRepository.findByIdSecuritycurrencyOrderByExDateAsc(idSecuritycurrency),
        HttpStatus.OK);
  }

  @Operation(summary = "Returns the dividend key figures of a security computed from its dividends and prices",
      description = """
      The figures describe the instrument as the shared dividend table records it, not the distributions of the
      tenant: trailing and forward yield, growth rates, years without cut, payout ratio and the series of the
      dividend chart.""", tags = { Dividend.TABNAME })
  @GetMapping(value = "/{idSecuritycurrency}/metrics", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityDividendMetrics> getDividendMetrics(@PathVariable final Integer idSecuritycurrency) {
    return new ResponseEntity<>(securityDividendMetricsReport.getMetrics(idSecuritycurrency, ClientClock.today()),
        HttpStatus.OK);
  }

  @Operation(summary = "Returns the series of the dividend chart of one or several securities", description = """
      With one security the chart shows its annual dividends, trailing yield and splits; with several it compares the
      trailing yield and the annual dividend indexed to a common base year. At most ten securities are taken.""",
      tags = { Dividend.TABNAME })
  @GetMapping(value = "/chart", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SecurityDividendChart> getDividendChart(@RequestParam final List<Integer> ids) {
    return new ResponseEntity<>(securityDividendMetricsReport.getChart(ids, ClientClock.today()), HttpStatus.OK);
  }

}
