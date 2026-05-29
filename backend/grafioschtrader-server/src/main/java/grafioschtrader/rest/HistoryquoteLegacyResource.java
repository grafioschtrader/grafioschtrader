package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import grafiosch.BaseConstants;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.Auditable;
import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.dto.LegacySplitRequest;
import grafioschtrader.dto.SupportedCSVFormat;
import grafioschtrader.dto.UploadHistoryquotesSuccess;
import grafioschtrader.entities.HistoryquoteLegacy;
import grafioschtrader.entities.Security;
import grafioschtrader.priceupdate.historyquote.HistoryquoteLegacyImport;
import grafioschtrader.repository.HistoryquoteLegacyJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for the {@code historyquote_legacy} shadow archive. Individual rows can be edited (PUT, via the
 * propose-change flow inherited from {@link HistoryquoteResourceBase}) and deleted, but not created one-by-one — rows
 * arrive only through automatic connector-change archiving and CSV import. The bulk archive operations (list, forgotten
 * split, delete-all, CSV import) live here too.
 */
@RestController
@RequestMapping(RequestGTMappings.HISTORYQUOTE_LEGACY_MAP)
@Tag(name = HistoryquoteLegacy.TABNAME, description = "Controller for the historyquote_legacy shadow archive")
public class HistoryquoteLegacyResource extends HistoryquoteResourceBase<HistoryquoteLegacy> {

  @Autowired
  private HistoryquoteLegacyJpaRepository historyquoteLegacyJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Operation(summary = "List archived historyquote_legacy rows for a security", tags = { HistoryquoteLegacy.TABNAME })
  @GetMapping(value = "/security/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<HistoryquoteLegacy>> getLegacyForSecurity(
      @Parameter(description = "Id of the security", required = true) @PathVariable Integer idSecuritycurrency) {
    return new ResponseEntity<>(
        historyquoteLegacyJpaRepository.findByIdSecuritycurrencyOrderByDateDesc(idSecuritycurrency), HttpStatus.OK);
  }

  @Operation(summary = "Apply a split factor to every legacy row before the given split date", tags = {
      HistoryquoteLegacy.TABNAME })
  @PostMapping(value = "/security/{idSecuritycurrency}/split", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Integer> applySplitToLegacy(
      @Parameter(description = "Id of the security", required = true) @PathVariable Integer idSecuritycurrency,
      @RequestBody LegacySplitRequest body) {
    requireEditOrDeleteWithPrivileges(idSecuritycurrency);
    int updated = historyquoteLegacyJpaRepository.applySplitToLegacy(idSecuritycurrency, body.fromFactor, body.toFactor,
        body.splitDate);
    return new ResponseEntity<>(updated, HttpStatus.OK);
  }

  @Operation(summary = "Delete every legacy historyquote row for a security", tags = { HistoryquoteLegacy.TABNAME })
  @DeleteMapping(value = "/security/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteAllLegacyForSecurity(
      @Parameter(description = "Id of the security", required = true) @PathVariable Integer idSecuritycurrency) {
    requireEditOrDeleteRights(idSecuritycurrency);
    historyquoteLegacyJpaRepository.deleteLegacyByIdSecuritycurrency(idSecuritycurrency);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Import CSV into historyquote_legacy",
      description = "Round-trip counterpart of the legacy view's CSV export. Inserts rows with INSERT IGNORE on (id_securitycurrency, date); rows already archived are counted as notOverridden. Missing transferDate defaults to today; create_type is fixed to MANUAL_IMPORTED.",
      tags = { HistoryquoteLegacy.TABNAME })
  @PostMapping(value = "/security/{idSecuritycurrency}/uploadhistoryquotes", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UploadHistoryquotesSuccess> uploadHistoryquotesLegacy(
      @PathVariable Integer idSecuritycurrency,
      @RequestParam("file") MultipartFile[] uploadFiles,
      @RequestParam(required = false) char decimalSeparator,
      @RequestParam(required = false) char thousandSeparator,
      @RequestParam(required = false) String dateFormat) throws Exception {
    requireEditOrDeleteRights(idSecuritycurrency);
    HistoryquoteLegacyImport importer = new HistoryquoteLegacyImport(historyquoteLegacyJpaRepository);
    return new ResponseEntity<>(importer.uploadHistoryquotes(idSecuritycurrency, uploadFiles,
        new SupportedCSVFormat(decimalSeparator, thousandSeparator, dateFormat)), HttpStatus.OK);
  }

  @Operation(summary = "Delete a single archived legacy historyquote row", tags = { HistoryquoteLegacy.TABNAME })
  @DeleteMapping(value = "/{idHistoryquoteLegacy}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteHistoryquoteLegacy(@PathVariable final Integer idHistoryquoteLegacy) {
    deleteById(idHistoryquoteLegacy);
    return ResponseEntity.ok().build();
  }

  @Override
  protected ResponseEntity<HistoryquoteLegacy> createEntity(HistoryquoteLegacy entity) throws Exception {
    // Archived rows are never created individually; they arrive via connector-change archiving and CSV import.
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
  }

  private void requireEditOrDeleteRights(Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Security security = securityJpaRepository.findByIdSecuritycurrency(idSecuritycurrency);
    if (security == null || !UserAccessHelper.hasRightsForEditingOrDeleteOnEntity(user, security)) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  private void requireEditOrDeleteWithPrivileges(Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Security security = securityJpaRepository.findByIdSecuritycurrency(idSecuritycurrency);
    if (security == null || !UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, security)) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  @Override
  protected Auditable getParentSecurityCurrency(User user, Integer idSecuritycurrency) {
    return historyquoteLegacyJpaRepository.getParentSecurityCurrency(user, idSecuritycurrency);
  }

  @Override
  protected boolean isImmutableCreateType(HistoryquoteLegacy entity) {
    return false;
  }

  @Override
  protected UpdateCreateJpaRepository<HistoryquoteLegacy> getUpdateCreateJpaRepository() {
    return historyquoteLegacyJpaRepository;
  }

}
