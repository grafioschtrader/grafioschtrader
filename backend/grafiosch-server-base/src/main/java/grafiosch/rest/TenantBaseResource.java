package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

import grafiosch.entities.BaseID;
import grafiosch.entities.TenantBase;
import grafiosch.repository.TenantBaseCustom;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;


public abstract class TenantBaseResource<T extends BaseID<Integer>> extends UpdateCreateResource<T> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());
  
  protected abstract TenantBaseCustom getTenantRepository();
  

  @Operation(summary = "Export the data of a client with it private ond public data", description = "The created zip file will cotains two files one with ddl and the 2nd with dml statements", tags = {
      TenantBase.TABNAME })
  @GetMapping(value = "/exportpersonaldataaszip", produces = "application/zip")
  public void getExportPersonalDataAsZip(HttpServletResponse response) throws Exception {
    getTenantRepository().getExportPersonalDataAsZip(response);
  }
  
  @Operation(summary = "Delete the private data the main tenant of the user. It als removes the user from this application", description = "", tags = {
      TenantBase.TABNAME })
  @DeleteMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteMyDataAndUserAccount() throws Exception {
    log.debug("Delete all data of a user");
    getTenantRepository().deleteMyDataAndUserAccount();
    return ResponseEntity.noContent().build();
  }
}
