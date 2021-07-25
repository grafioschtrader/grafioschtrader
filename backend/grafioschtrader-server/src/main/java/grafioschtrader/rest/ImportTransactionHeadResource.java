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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.User;
import grafioschtrader.repository.ImportTransactionHeadJpaRepository;
import grafioschtrader.repository.ImportTransactionHeadJpaRepositoryImpl.SuccessFailedDirectImportTransaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Rest service for head of import transactions.
 *
 * @author Hugo Graf
 *
 */
@RestController
@RequestMapping(RequestMappings.IMPORTTRANSACTIONHEAD_MAP)
@Tag(name = RequestMappings.IMPORTTRANSACTIONHEAD, description = "Controller for Import transaction head")
public class ImportTransactionHeadResource extends UpdateCreateDeleteWithTenantResource<ImportTransactionHead> {

  @Autowired
  private ImportTransactionHeadJpaRepository importTransactionHeadJpaRepository;

  public ImportTransactionHeadResource() {
    super(ImportTransactionHead.class);
  }

  @Operation(summary = "Return all import transaction head for a specified security account", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONHEAD })
  @GetMapping(value = "/securityaccount/{idSecuritycashaccount}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionHead>> getImportTransactionHeadBySecurityaccount(
      @PathVariable final Integer idSecuritycashaccount) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(importTransactionHeadJpaRepository
        .findBySecurityaccount_idSecuritycashAccountAndIdTenant(idSecuritycashaccount, user.getIdTenant()),
        HttpStatus.OK);
  }

  @Operation(summary = "Upload one or more PDF files, each for a single transaction.", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONHEAD })
  @PostMapping(value = "/{idSecuritycashaccount}/uploadpdftransactions", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SuccessFailedDirectImportTransaction> uploadPdfTransactions(
      @PathVariable() Integer idSecuritycashaccount, @RequestParam("file") MultipartFile[] uploadFiles)
      throws Exception {
    return new ResponseEntity<>(
        importTransactionHeadJpaRepository.uploadPdfFileSecurityAccountTransactions(idSecuritycashaccount, uploadFiles),
        HttpStatus.OK);
  }

  @Operation(summary = "Upload different kind of transaction files with a existing transaction head record.", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONHEAD })
  @PostMapping(value = "/{idTransactionHead}/uploadtransaction", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> uploadCsvFileSecurityAccountTransactions(@PathVariable() Integer idTransactionHead,
      @RequestParam("file") MultipartFile[] uploadFiles,
      @RequestParam(required = false) Integer idTransactionImportTemplate) throws Exception {
    importTransactionHeadJpaRepository.uploadCsvPdfTxtFileSecurityAccountTransactions(idTransactionHead, uploadFiles,
        idTransactionImportTemplate);
    return ResponseEntity.noContent().build();
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<ImportTransactionHead> getUpdateCreateJpaRepository() {
    return importTransactionHeadJpaRepository;
  }

}
