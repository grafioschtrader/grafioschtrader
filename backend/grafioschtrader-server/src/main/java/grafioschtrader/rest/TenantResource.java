package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.User;
import grafioschtrader.repository.TenantJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


@RestController
@RequestMapping(RequestMappings.TENANT_MAP)
@Tag(name = RequestMappings.TENANT, description = "Controller for tenant")
public class TenantResource extends UpdateCreateResource<Tenant> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  TenantJpaRepository tenantJpaRepository;

  @Autowired
  private ResourceLoader resourceLoader;

  @GetMapping(value = "/", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Tenant> getTenantAndPortfolio() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(tenantJpaRepository.getOne(user.getIdTenant()), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<Tenant> getUpdateCreateJpaRepository() {
    return tenantJpaRepository;
  }

  @Operation(summary = "Chance tenants currency and also in its each protfolio", description = "", tags = {
      RequestMappings.TENANT })
  @PatchMapping("/watchlistforperformance/{idWatchlist}")
  public ResponseEntity<Tenant> setWatchlistForPerformance (
      @Parameter(description = "ID of watchlist", required = true) @PathVariable Integer idWatchlist) {
    return new ResponseEntity<>(tenantJpaRepository.setWatchlistForPerformance(idWatchlist), HttpStatus.OK);
  }

    
  @Operation(summary = "Chance tenants currency and also in its each protfolio", description = "", tags = {
      RequestMappings.TENANT })
  @PatchMapping("{currency}")
  public ResponseEntity<Tenant> changeCurrencyTenantAndPortfolios(
      @Parameter(description = "New currency", required = true) @PathVariable String currency) {
    return new ResponseEntity<>(tenantJpaRepository.changeCurrencyTenantAndPortfolios(currency), HttpStatus.OK);
  }

  @Operation(summary = "Export the data of a client with it private ond public data", 
      description = "The created zip file will cotains two files one with ddl and the 2nd with dml statements", tags = {
      RequestMappings.TENANT })
  @GetMapping(value = "/exportpersonaldataaszip", produces = "application/zip")
  public void zipFiles(HttpServletResponse response) throws Exception {

    Resource resource = resourceLoader.getResource("classpath:/db/migration/gt_ddl.sql");

    StringBuilder sqlStatement = tenantJpaRepository.exportPersonalData();

    File tempSqlStatement = File.createTempFile("gt_data", ".sql");

    // Delete temp file when program exits.
    tempSqlStatement.deleteOnExit();

    // Write to temp file
    BufferedWriter out = new BufferedWriter(new FileWriter(tempSqlStatement));
    out.write(sqlStatement.toString());
    out.close();

    // setting headers
    response.setStatus(HttpServletResponse.SC_OK);
    response.addHeader("Content-Disposition", "attachment; filename=\"gt.zip\"");

    ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream());

    // create a list to add files to be zipped
    ArrayList<File> files = new ArrayList<>(1);
    files.add(resource.getFile());
    files.add(tempSqlStatement);

    // package files
    for (File file : files) {
      // new zip entry and copying inputstream with file to zipOutputStream, after all
      // closing streams
      zipOutputStream.putNextEntry(new ZipEntry(file.getName()));
      FileInputStream fileInputStream = new FileInputStream(file);

      IOUtils.copy(fileInputStream, zipOutputStream);

      fileInputStream.close();
      zipOutputStream.closeEntry();
    }

    zipOutputStream.close();
    tempSqlStatement.delete();
  }

  
  @Operation(summary = "Delete the private data the main tenant of the user. It als removes the user from this application", 
      description = "", tags = {
      RequestMappings.TENANT })
  @DeleteMapping(value = "/", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteMyDataAndUserAccount() throws Exception {
    log.debug("Delete all data of a user");
    tenantJpaRepository.deleteMyDataAndUserAccount();
    return ResponseEntity.noContent().build();
  }

}
