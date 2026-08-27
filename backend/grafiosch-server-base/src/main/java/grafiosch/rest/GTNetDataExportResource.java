package grafiosch.rest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
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
import grafiosch.gtnet.GTNetExportTables;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GTNetJpaRepositoryImpl;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for exporting and importing GTNet data as a single SQL file.
 *
 * <p>
 * The UI that drives it is library code - the GTNet setup table's show menu - so the endpoint belongs to the library as
 * well; a host built on {@code grafiosch-server-base} alone previously answered 404 on both menu entries. Without an
 * application contribution it exports the library tables under {@link GTNetExportTables#BASE_EXPORT_HEADER}. An
 * application that owns further GTNet tables supplies one {@link GTNetExportTables} bean, which replaces marker and
 * table lists so its file format is unchanged.
 * </p>
 */
@RestController
@RequestMapping(RequestMappings.GTNETDATAEXPORT_MAP)
@Tag(name = RequestMappings.GTNETDATAEXPORT, description = "GTNet data export and import")
public class GTNetDataExportResource {

  /** Delay of the jobs that reconcile derived data with the freshly imported GTNet rows. */
  private static final int POST_IMPORT_DELAY_MINUTES = 5;

  private static final String[] NO_DELETE_ONLY_TABLES = {};

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  /** Absent on a host that owns no GTNet tables beyond the library ones. */
  @Autowired(required = false)
  private GTNetExportTables exportTables;

  @Operation(summary = "Exports all GTNet data as SQL", description = "Admin-only. Returns a single SQL file with DELETE + INSERT statements for every GTNet table this instance owns.", tags = {
      RequestMappings.GTNETDATAEXPORT })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping(value = "/export", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> exportGTNetData() {
    String sql = gtNetJpaRepository.exportGTNetConfig(header(), deleteOnlyTables(), exportAndDeleteTables());
    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(ContentDisposition.attachment().filename("gtnet_export.sql").build());
    return new ResponseEntity<>(sql, headers, HttpStatus.OK);
  }

  @Operation(summary = "Imports all GTNet data from SQL file", description = "Admin-only. Replaces all GTNet data with the uploaded SQL file. Schedules background jobs to run after import.", tags = {
      RequestMappings.GTNETDATAEXPORT })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> importGTNetData(@RequestParam() MultipartFile file) throws Exception {
    String sqlStatements = new String(file.getBytes(), StandardCharsets.UTF_8);
    gtNetJpaRepository.importGTNetConfig(sqlStatements, header());
    schedulePostImportTasks();
    return ResponseEntity.ok().build();
  }

  private String header() {
    return exportTables == null ? GTNetExportTables.BASE_EXPORT_HEADER : exportTables.header();
  }

  private String[] deleteOnlyTables() {
    return exportTables == null ? NO_DELETE_ONLY_TABLES : exportTables.deleteOnlyTables();
  }

  private String[] exportAndDeleteTables() {
    return exportTables == null ? GTNetJpaRepositoryImpl.GTNET_BASE_TABLES_DELETE_ORDER
        : exportTables.exportAndDeleteTables();
  }

  /**
   * Schedules the background tasks that reconcile derived data with the imported rows. They run with a delay because
   * both reach out to peers, and the peers named by the import are unknown until it has committed.
   */
  private void schedulePostImportTasks() {
    LocalDateTime startTime = LocalDateTime.now().plusMinutes(POST_IMPORT_DELAY_MINUTES);
    if (!taskDataChangeJpaRepository.existsByIdTaskAndProgressStateType(TaskTypeBase.GTNET_EXCHANGE_SYNC.getValue(),
        ProgressStateType.PROG_WAITING.getValue())) {
      taskDataChangeJpaRepository
          .save(new TaskDataChange(TaskTypeBase.GTNET_EXCHANGE_SYNC, TaskDataExecPriority.PRIO_NORMAL, startTime));
    }
    taskDataChangeJpaRepository
        .save(new TaskDataChange(TaskTypeBase.GTNET_SERVER_STATUS_CHECK, TaskDataExecPriority.PRIO_NORMAL, startTime));
  }
}
