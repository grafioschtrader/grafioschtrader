package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.rest.UpdateCreateDeleteAuditResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.platform.IPlatformTransactionImport;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
import grafioschtrader.repository.ImportTransactionPlatformJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.IMPORTTRANSACTION_PLATFORM_MAP)
@Tag(name = RequestGTMappings.IMPORTTRANSACTION_PLATFORM, description = "Controller for import transaction platform")
public class ImportTransactionPlatformResource extends UpdateCreateDeleteAuditResource<ImportTransactionPlatform> {

  @Autowired
  private ImportTransactionPlatformJpaRepository importTransactionPlatformJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Operation(summary = "Return of all template groups", description = "", tags = {
      RequestGTMappings.IMPORTTRANSACTION_PLATFORM })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionPlatform>> getAllImportTransactionPlatform() {
    return new ResponseEntity<>(importTransactionPlatformJpaRepository.findAll(), HttpStatus.OK);
  }

  @Operation(summary = "Return of the special import transaction Implementation of trading platforms that have such an implementation", description = "", tags = {
      RequestGTMappings.IMPORTTRANSACTION_PLATFORM })
  @GetMapping(value = "/platformImports", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<IPlatformTransactionImport>> getPlatformTransactionImport() {
    return new ResponseEntity<>(importTransactionPlatformJpaRepository.getPlatformTransactionImport(), HttpStatus.OK);
  }

  @Operation(summary = "Returns the import platform of this instance holding the Grafioschtrader authored import templates", description = """
      Null when no administrator has chosen a platform yet, in which case a tenant cannot use those templates
      regardless of its own opt-in.""", tags = {
      RequestGTMappings.IMPORTTRANSACTION_PLATFORM })
  @GetMapping(value = "/gtplatform", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Integer> getGtImportPlatform() {
    return new ResponseEntity<>(globalparametersService.getGtImportPlatformId(), HttpStatus.OK);
  }

  @Operation(summary = "Chooses the import platform holding the Grafioschtrader authored import templates", description = """
      Instance wide setting, reserved for an administrator. The tenants only opt in to the templates; which platform
      carries them is not their choice. Pass no platform to clear the setting.""", tags = {
      RequestGTMappings.IMPORTTRANSACTION_PLATFORM })
  @PutMapping(value = "/gtplatform", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Integer> setGtImportPlatform(
      @RequestParam(required = false) Integer idTransactionImportPlatform) {
    checkAdmin();
    if (idTransactionImportPlatform != null
        && !importTransactionPlatformJpaRepository.existsById(idTransactionImportPlatform)) {
      throw new DataViolationException("id.gt.import.platform", "gt.tenant.gtplatform.notexists", null);
    }
    globalparametersService.setGtImportPlatformId(idTransactionImportPlatform);
    return new ResponseEntity<>(idTransactionImportPlatform, HttpStatus.OK);
  }

  private void checkAdmin() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (user.getMostPrivilegedRole() != Role.ROLE_ADMIN) {
      throw new SecurityException("Admin access required");
    }
  }

  @Override
  protected UpdateCreateJpaRepository<ImportTransactionPlatform> getUpdateCreateJpaRepository() {
    return importTransactionPlatformJpaRepository;
  }

  @Operation(summary = "Receives PDF as an upload and converts it into a text string and returns it.", description = "", tags = {
      RequestGTMappings.IMPORTTRANSACTION_PLATFORM })
  @PostMapping(value = "/transformpdftotxt")
  public ResponseEntity<String> uploadAndTransformPDFToTxt(@RequestParam("file") MultipartFile uploadPDFFile)
      throws IOException {
    String text = null;
    if (!uploadPDFFile.isEmpty()) {
      try (InputStream is = uploadPDFFile.getInputStream()) {
        text = ImportTransactionHelperPdf.transFormPDFToTxt(is);
      }
    }
    return new ResponseEntity<>(text, HttpStatus.OK);
  }

}
