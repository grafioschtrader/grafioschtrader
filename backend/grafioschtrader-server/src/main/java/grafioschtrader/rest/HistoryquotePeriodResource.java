package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.HistoryquotePeriodDeleteAndCreateMultiple;
import grafioschtrader.entities.HistoryquotePeriod;
import grafioschtrader.entities.User;
import grafioschtrader.repository.HistoryquotePeriodJpaRepository;
import grafioschtrader.types.HistoryquotePeriodCreateType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(RequestMappings.HISTORYQUOTE_PERDIO_MAP)
@Tag(name = RequestMappings.HISTORYQUOTE_PERIOD, description = "Controller for historyquote period")
public class HistoryquotePeriodResource {

  @Autowired
  private HistoryquotePeriodJpaRepository historyquotePeriodJpaRepository;

  @Operation(summary = "Returns historyquote peroid for a spezified security", description = "", tags = {
      RequestMappings.HISTORYQUOTE_PERIOD })
  @GetMapping(value = "/{idSecuritycurrency}/security", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<HistoryquotePeriod>> getHistoryquotePeriodByIdSecuritycurrency(
      @Parameter(description = "Id of security", required = true) @PathVariable final Integer idSecuritycurrency) {
    return new ResponseEntity<>(historyquotePeriodJpaRepository.findByIdSecuritycurrencyAndCreateTypeOrderByFromDate(
        idSecuritycurrency, HistoryquotePeriodCreateType.USER_CREATED.getValue()), HttpStatus.OK);
  }

  @Operation(summary = "Historyquote period are created manually, normally used when one price does not fit the whole lifetime "
      + " of a security", description = "", tags = { RequestMappings.HISTORYQUOTE_PERIOD })
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<HistoryquotePeriod>> deleteAndCreateMultiple(
      @Valid @RequestBody final HistoryquotePeriodDeleteAndCreateMultiple hpdacm) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(historyquotePeriodJpaRepository.deleteAndCreateMultiple(user, hpdacm), HttpStatus.OK);
  }

}
