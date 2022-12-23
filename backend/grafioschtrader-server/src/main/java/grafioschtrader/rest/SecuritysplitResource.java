package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.SecuritysplitDeleteAndCreateMultiple;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(RequestMappings.SECURITYSPLIT_MAP)
@Tag(name = Securitysplit.TABNAME, description = "Controller for security split")
public class SecuritysplitResource {

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Operation(summary = "Returns all security splits of a security which is identified by ID", description = "", tags = {
      Securitysplit.TABNAME })
  @GetMapping(value = "/{idSecuritycurrency}/security", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Securitysplit>> getSecuritysplitsByIdSecuritycurrency(
      @PathVariable final Integer idSecuritycurrency) {
    final Map<Integer, List<Securitysplit>> securitysplitsMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdSecuritycurrency(idSecuritycurrency);
    final List<Securitysplit> securitysplits = (securitysplitsMap.isEmpty()) ? Collections.emptyList()
        : securitysplitsMap.get(idSecuritycurrency);

    return new ResponseEntity<>(securitysplits, HttpStatus.OK);
  }

  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Securitysplit>> deleteAndCreateMultiple(
      @Valid @RequestBody final SecuritysplitDeleteAndCreateMultiple sdacm) {
    log.debug("Create Entities : {}", sdacm);
    return new ResponseEntity<>(securitysplitJpaRepository.deleteAndCreateMultiple(sdacm), HttpStatus.OK);
  }

}
