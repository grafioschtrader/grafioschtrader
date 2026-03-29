package grafioschtrader.rest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import grafiosch.entities.TaskDataChange;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GTNetJpaRepositoryImpl;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;
import grafioschtrader.entities.GTNetExchangeLog;
import grafioschtrader.entities.GTNetHistoryquote;
import grafioschtrader.entities.GTNetInstrument;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.GTNetLastprice;
import grafioschtrader.entities.GTNetSecurityImpGap;
import grafioschtrader.entities.GTNetSecurityImpHead;
import grafioschtrader.entities.GTNetSecurityImpPos;
import grafioschtrader.entities.GTNetSupplierDetailHist;
import grafioschtrader.entities.GTNetSupplierDetailLast;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for exporting and importing all GTNet data. Combines base tables (from grafiosch-server-base) and
 * app-specific tables (from grafioschtrader) into a single export/import operation.
 */
@RestController
@RequestMapping(RequestGTMappings.GTNETDATAEXPORT_MAP)
@Tag(name = RequestGTMappings.GTNETDATAEXPORT, description = "GTNet data export and import")
public class GTNetDataExportResource {

  private static final String EXPORT_HEADER = "-- GTNET_EXPORT_V1";

  /** Tables that are deleted during import but not exported (data is rebuilt by background jobs). */
  private static final String[] DELETE_ONLY_TABLES = { GTNetSecurityImpGap.TABNAME, GTNetSecurityImpPos.TABNAME,
      GTNetSecurityImpHead.TABNAME, GTNetHistoryquote.TABNAME, GTNetLastprice.TABNAME,
      GTNetInstrumentSecurity.TABNAME, GTNetInstrumentCurrencypair.TABNAME, GTNetInstrument.TABNAME,
      GTNetSupplierDetailHist.TABNAME, GTNetSupplierDetailLast.TABNAME };

  /** App tables that are exported and deleted. Combined with base tables for the full export+delete list. */
  private static final String[] EXPORT_AND_DELETE_TABLES = Stream
      .concat(Stream.of(GTNetExchangeLog.TABNAME), Stream.of(GTNetJpaRepositoryImpl.GTNET_BASE_TABLES_DELETE_ORDER))
      .toArray(String[]::new);

  private static final int POST_IMPORT_DELAY_MINUTES = 5;

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Operation(summary = "Exports all GTNet data as SQL", description = "Admin-only. Returns a single SQL file with DELETE + INSERT statements for all GTNet tables (base and app).", tags = {
      RequestGTMappings.GTNETDATAEXPORT })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping(value = "/export", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> exportGTNetData() {
    String sql = gtNetJpaRepository.exportGTNetConfig(EXPORT_HEADER, DELETE_ONLY_TABLES, EXPORT_AND_DELETE_TABLES);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(
        org.springframework.http.ContentDisposition.attachment().filename("gtnet_export.sql").build());
    return new ResponseEntity<>(sql, headers, HttpStatus.OK);
  }

  @Operation(summary = "Imports all GTNet data from SQL file", description = "Admin-only. Replaces all GTNet data with the uploaded SQL file. Schedules background jobs to run after import.", tags = {
      RequestGTMappings.GTNETDATAEXPORT })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> importGTNetData(@RequestParam("file") MultipartFile file) throws Exception {
    String sqlStatements = new String(file.getBytes(), StandardCharsets.UTF_8);
    gtNetJpaRepository.importGTNetConfig(sqlStatements, EXPORT_HEADER);
    schedulePostImportTasks();
    return ResponseEntity.ok().build();
  }

  /**
   * Schedules background tasks to run 5 minutes after import to update dependent data.
   */
  private void schedulePostImportTasks() {
    LocalDateTime startTime = LocalDateTime.now().plusMinutes(POST_IMPORT_DELAY_MINUTES);
    taskDataChangeJpaRepository
        .save(new TaskDataChange(TaskTypeBase.GTNET_EXCHANGE_SYNC, TaskDataExecPriority.PRIO_NORMAL, startTime));
    taskDataChangeJpaRepository
        .save(new TaskDataChange(TaskTypeBase.GTNET_SERVER_STATUS_CHECK, TaskDataExecPriority.PRIO_NORMAL, startTime));
  }
}
