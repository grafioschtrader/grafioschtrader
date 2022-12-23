package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sun.istack.NotNull;

import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.platformimport.CombineTemplateAndImpTransPos;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Rest service for one or more import transactions.
 *
 * @author Hugo Graf
 *
 */
@RestController
@RequestMapping(RequestMappings.IMPORTTRANSACTIONPOS_MAP)
@Tag(name = RequestMappings.IMPORTTRANSACTIONPOS, description = "Controller for import transaction position")
public class ImportTransactionPosResource {

  @Autowired
  private ImportTransactionPosJpaRepository importTransactionPosJpaRepository;

  @Operation(summary = "Gets all import transaction position for a specified ID of import transaction head", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONPOS })
  @GetMapping(value = "/importtransactionhead/{idTransactionHead}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<CombineTemplateAndImpTransPos>> getCombineTemplateAndImpTransPosListByTransactionHead(
      @PathVariable final Integer idTransactionHead) {
    return new ResponseEntity<>(
        importTransactionPosJpaRepository.getCombineTemplateAndImpTransPosListByTransactionHead(idTransactionHead),
        HttpStatus.OK);
  }

  @Operation(summary = "Set a security for some specified import transaction positions", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONPOS })
  @PutMapping(value = "/setsecurity", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionPos>> setSecurity(@RequestBody SetSecurityImport setSecurityImport) {
    return new ResponseEntity<>(importTransactionPosJpaRepository.setSecurity(setSecurityImport.idSecuritycurrency,
        setSecurityImport.idTransactionPosList), HttpStatus.OK);
  }

  @Operation(summary = "Set a cash account for some specified import transaction positions", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONPOS })
  @PutMapping(value = "/setcashaccount", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionPos>> setCashAccount(
      @RequestBody SetCashAccountImport setCashAccountImport) {
    return new ResponseEntity<>(importTransactionPosJpaRepository.setCashAccount(
        setCashAccountImport.idSecuritycashAccount, setCashAccountImport.idTransactionPosList), HttpStatus.OK);
  }

  @Operation(summary = "Sometimes the brooker round some value, is corrects the the qouatation to match the total value", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONPOS })
  @PostMapping(value = "/adjustcurrencyexrateorquotation", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionPos>> adjustCurrencyExRateOrQuotation(
      @RequestBody final List<Integer> idTransactionPosList) {
    return new ResponseEntity<>(importTransactionPosJpaRepository.adjustCurrencyExRateOrQuotation(idTransactionPosList),
        HttpStatus.OK);
  }

  @Operation(summary = "The calculated total of the import transactions is taken for the possible transaction.", description = "This should be used only in case of small difference between calculated and imported total.", tags = {
      RequestMappings.IMPORTTRANSACTIONPOS })
  @PostMapping(value = "/accepttotaldiff", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionPos>> acceptTotalDiff(
      @RequestBody final List<Integer> idTransactionPosList) {
    return new ResponseEntity<>(importTransactionPosJpaRepository.acceptTotalDiff(idTransactionPosList), HttpStatus.OK);
  }

  @Operation(summary = "Deletes existing import transaction of the passed ID's..", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONPOS })
  @PostMapping(value = "/deletes", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteMultiple(@RequestBody final List<Integer> idTransactionPosList) {
    importTransactionPosJpaRepository.deleteMultiple(idTransactionPosList);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Check or ignore for possible existing import transaction", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONPOS })
  @PatchMapping(value = "/setidtransactionmaybe", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> setIdTransactionMayBe(@RequestBody SetIdTransactionMayBe setIdTransactionMayBe) {
    importTransactionPosJpaRepository.setIdTransactionMayBe(setIdTransactionMayBe.idTransactionMayBe,
        setIdTransactionMayBe.idTransactionPosList);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Creates the corresponding transaction from the passed IDs of import transactions, updating existing transactions.", description = "", tags = {
      RequestMappings.IMPORTTRANSACTIONPOS })
  @PostMapping(value = "/createtransaction", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionPos>> createTransaction(
      @RequestBody final List<Integer> idTransactionPosList) {
    return new ResponseEntity<>(importTransactionPosJpaRepository.createAndSaveTransactionsByIds(idTransactionPosList),
        HttpStatus.OK);
  }

  static class SetSecurityImport {
    public Integer idSecuritycurrency;
    public List<Integer> idTransactionPosList;
  }

  static class SetCashAccountImport {
    @NotNull
    public Integer idSecuritycashAccount;
    @NotNull
    public List<Integer> idTransactionPosList;
  }

  static class SetIdTransactionMayBe {
    @Min(value = 0)
    @Max(value = 0)
    public Integer idTransactionMayBe;
    public List<Integer> idTransactionPosList;
  }

}
