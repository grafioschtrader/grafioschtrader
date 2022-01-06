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

import grafioschtrader.entities.Dividend;
import grafioschtrader.repository.DividendJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.DIVIDEND_MAP)
@Tag(name = Dividend.TABNAME, description = "Controller for security dividend")
public class DividendResource {

  @Autowired
  private DividendJpaRepository dividendJpaRepository;

  @Operation(summary = "Returns all dividends of a security which is identified by ID", description = "", tags = {
      Dividend.TABNAME })
  @GetMapping(value = "/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Dividend>> getDividendsByIdSecuritycurrency(
      @PathVariable final Integer idSecuritycurrency) {
    return new ResponseEntity<>(dividendJpaRepository.findByIdSecuritycurrencyOrderByExDateAsc(idSecuritycurrency),
        HttpStatus.OK);
  }

}
