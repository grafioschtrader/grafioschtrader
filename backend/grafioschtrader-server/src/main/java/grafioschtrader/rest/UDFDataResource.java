package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.UDFData;
import grafioschtrader.entities.User;
import grafioschtrader.repository.UDFDataJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.UDF_DATA_MAP)
@Tag(name = RequestMappings.UDFDATA, description = "Controller for the data of user defined fields")
public class UDFDataResource {

  @Autowired
  private UDFDataJpaRepository uDFDataJpaRepository;

  @Operation(summary = "Return of user-defined data for a specific instrument.", description = "", tags = {
      RequestMappings.UDFDATA })
  @GetMapping(value = "/{entity}/{idEntity}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UDFData> getUDFDataByIdUserAndEntityAndIdEntity(@PathVariable final String entity,
      @PathVariable final Integer idEntity) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return uDFDataJpaRepository.getUDFDataByIdUserAndEntityAndIdEntity(user.getIdUser(), entity, idEntity)
        .map(udfData -> ResponseEntity.ok().body(udfData)).orElseGet(() -> ResponseEntity.noContent().build());
  }

  @Operation(summary = "With this entity we have a composite key, so there is a special implementation for creating it.", description = "", tags = {
      RequestMappings.UDFDATA })
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UDFData> create(@RequestBody UDFData uDFData) throws Exception {
    return new ResponseEntity<>(uDFDataJpaRepository.createUpdate(uDFData), HttpStatus.OK);
  }

  @Operation(summary = "For this entity, we have a composite key, so there is a special implementation for the update.", description = "", tags = {
      RequestMappings.UDFDATA })
  @PutMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UDFData> update(@RequestBody UDFData uDFData) throws Exception {
    return new ResponseEntity<>(uDFDataJpaRepository.createUpdate(uDFData), HttpStatus.OK);
  }

}
