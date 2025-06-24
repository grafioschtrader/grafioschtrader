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

import grafioschtrader.dto.ImportTransactionPosDTOs.SetCashAccountImport;
import grafioschtrader.dto.ImportTransactionPosDTOs.SetIdTransactionMayBe;
import grafioschtrader.dto.ImportTransactionPosDTOs.SetSecurityImport;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.platformimport.CombineTemplateAndImpTransPos;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


@RestController
@RequestMapping(RequestGTMappings.IMPORTTRANSACTIONPOS_MAP)
@Tag(name = RequestGTMappings.IMPORTTRANSACTIONPOS, description = "Controller for import transaction position")
public class ImportTransactionPosResource {

  @Autowired
  private ImportTransactionPosJpaRepository importTransactionPosJpaRepository;

  @Operation(summary = "Retrieve import transaction positions with template information for a transaction head",
      description = """
          Returns all import transaction positions associated with the specified transaction head, combined with their
          corresponding template information. This provides a complete view of imported transaction data along with the
          parsing templates used to extract the information from the original source files.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONPOS })
  @GetMapping(value = "/importtransactionhead/{idTransactionHead}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<CombineTemplateAndImpTransPos>> getCombineTemplateAndImpTransPosListByTransactionHead(
      @PathVariable final Integer idTransactionHead) {
    return new ResponseEntity<>(
        importTransactionPosJpaRepository.getCombineTemplateAndImpTransPosListByTransactionHead(idTransactionHead),
        HttpStatus.OK);
  }

  @Operation(summary = "Assign a security to multiple import transaction positions",
      description = """
          Updates the specified import transaction positions to reference the given security. This operation is used
          when the automatic security detection during import was unsuccessful or incorrect, allowing manual assignment
          of the correct security to the transaction positions.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONPOS })
  @PutMapping(value = "/setsecurity", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionPos>> setSecurity(@RequestBody SetSecurityImport setSecurityImport) {
    return new ResponseEntity<>(importTransactionPosJpaRepository.setSecurity(setSecurityImport.idSecuritycurrency,
        setSecurityImport.idTransactionPosList), HttpStatus.OK);
  }

  @Operation(summary = "Assign a cash account to multiple import transaction positions",
      description = """
          Updates the specified import transaction positions to reference the given cash account. This operation is
          used when the automatic cash account detection during import was unsuccessful or incorrect, allowing manual
          assignment of the correct cash account to the transaction positions.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONPOS })
  @PutMapping(value = "/setcashaccount", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionPos>> setCashAccount(
      @RequestBody SetCashAccountImport setCashAccountImport) {
    return new ResponseEntity<>(importTransactionPosJpaRepository.setCashAccount(
        setCashAccountImport.idSecuritycashAccount, setCashAccountImport.idTransactionPosList), HttpStatus.OK);
  }

  @Operation(summary = "Adjust currency exchange rates or quotations to match broker calculations",
      description = """
          Corrects currency exchange rates or security quotations when broker rounding causes discrepancies between
          calculated and actual transaction totals. This operation automatically adjusts the rates/quotations to
          match the broker's total values, resolving minor calculation differences due to rounding.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONPOS })
  @PostMapping(value = "/adjustcurrencyexrateorquotation", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionPos>> adjustCurrencyExRateOrQuotation(
      @RequestBody final List<Integer> idTransactionPosList) {
    return new ResponseEntity<>(importTransactionPosJpaRepository.adjustCurrencyExRateOrQuotation(idTransactionPosList),
        HttpStatus.OK);
  }

  @Operation(summary = "Accept calculated total differences for import transactions",
      description = """
          Accepts the calculated total of the import transactions for permanent transaction creation, even when small
          differences exist between calculated and imported totals. This should only be used for minor discrepancies
          caused by rounding differences or data precision limitations.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONPOS })
  @PostMapping(value = "/accepttotaldiff", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionPos>> acceptTotalDiff(
      @RequestBody final List<Integer> idTransactionPosList) {
    return new ResponseEntity<>(importTransactionPosJpaRepository.acceptTotalDiff(idTransactionPosList), HttpStatus.OK);
  }

  @Operation(summary = "Delete multiple import transaction positions",
      description = """
          Permanently removes the specified import transaction positions from the system. This operation is irreversible
          and should be used when import positions are incorrect, duplicated, or no longer needed. Only positions that
          have not been converted to permanent transactions can be deleted.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONPOS })
  @PostMapping(value = "/deletes", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteMultiple(@RequestBody final List<Integer> idTransactionPosList) {
    importTransactionPosJpaRepository.deleteMultiple(idTransactionPosList);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Set duplicate transaction handling for import positions",
      description = """
          Controls duplicate detection behavior for import transaction positions. Set to 0 to mark positions as
          confirmed non-duplicates, or null to reset duplicate detection. This helps prevent creation of duplicate
          transactions when similar transactions already exist in the system.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONPOS })
  @PatchMapping(value = "/setidtransactionmaybe", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> setIdTransactionMayBe(@RequestBody SetIdTransactionMayBe setIdTransactionMayBe) {
    importTransactionPosJpaRepository.setIdTransactionMayBe(setIdTransactionMayBe.idTransactionMayBe,
        setIdTransactionMayBe.idTransactionPosList);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Create permanent transactions from validated import positions",
      description = """
          Converts the specified import transaction positions into permanent transaction records. This operation
          validates all required data, performs final calculations, and creates the permanent transactions while
          updating the import positions with transaction references. Only ready and validated positions can be
          converted.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONPOS })
  @PostMapping(value = "/createtransaction", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionPos>> createTransaction(
      @RequestBody final List<Integer> idTransactionPosList) {
    return new ResponseEntity<>(importTransactionPosJpaRepository.createAndSaveTransactionsByIds(idTransactionPosList),
        HttpStatus.OK);
  }

  
  

}
