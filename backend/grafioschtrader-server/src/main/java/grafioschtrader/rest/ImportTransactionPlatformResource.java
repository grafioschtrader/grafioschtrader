package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.platform.IPlatformTransactionImport;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
import grafioschtrader.repository.ImportTransactionPlatformJpaRepository;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.IMPORTTRANSACTION_PLATFORM_MAP)
@Tag(name = RequestMappings.IMPORTTRANSACTION_PLATFORM, description = "Controller for import transaction platform")
public class ImportTransactionPlatformResource extends UpdateCreateDeleteAuditResource<ImportTransactionPlatform> {

  @Autowired
  ImportTransactionPlatformJpaRepository importTransactionPlatformJpaRepository;

  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionPlatform>> getAllImportTransactionPlatform() {
    return new ResponseEntity<>(importTransactionPlatformJpaRepository.findAll(), HttpStatus.OK);
  }

  @GetMapping(value = "/platformImports", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<IPlatformTransactionImport>> getPlatformTransactionImport() {
    return new ResponseEntity<>(importTransactionPlatformJpaRepository.getPlatformTransactionImport(), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<ImportTransactionPlatform> getUpdateCreateJpaRepository() {
    return importTransactionPlatformJpaRepository;
  }

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
