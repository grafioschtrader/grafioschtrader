package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.TaskDataChangeFormConstraints;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.task.BackgroundWorker;
import grafioschtrader.types.ProgressStateType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.TASK_DATA_CHANGE_MAP)
@Tag(name = RequestMappings.TASK_DATA_CHANGE, description = "Controller for task data change")
public class TaskDataChangeResource extends UpdateCreateDeleteAuditResource<TaskDataChange> {

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private BackgroundWorker backgroundWorker;

  @Operation(summary = "Returns all existing background jobs.", description = "", tags = {
      RequestMappings.TASK_DATA_CHANGE })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<TaskDataChange>> getAllTaskDataChange() {
    return new ResponseEntity<>(taskDataChangeJpaRepository.findAll(), HttpStatus.OK);
  }

  @Operation(summary = "Return the constraints for the user interface, these relate to specific editing options..", description = "", tags = {
      RequestMappings.TASK_DATA_CHANGE })
  @GetMapping(value = "/taskdatachangeconstraints", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<TaskDataChangeFormConstraints> getFormConstraints() {
    return new ResponseEntity<>(taskDataChangeJpaRepository.getFormConstraints(), HttpStatus.OK);
  }

  @Override
  @Operation(summary = "Delete background task. A running background job cannot be deleted.", description = "Requires administrator rights", tags = {
      RequestMappings.TASK_DATA_CHANGE })
  @DeleteMapping(value = "/{idTaskDataChange}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteResource(@PathVariable final Integer idTaskDataChange) {
    taskDataChangeJpaRepository.removeByIdTaskDataChangeAndProgressStateTypeNot(idTaskDataChange,
        ProgressStateType.PROG_RUNNING.getValue());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Cancel a running background task if it can be canceled by definition", description = "Requires administrator rights", tags = {
      RequestMappings.TASK_DATA_CHANGE })
  @PatchMapping(path = "/interruptingrunningjob/{idTaskDataChange}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Boolean> interruptingRunningJob(@PathVariable final Integer idTaskDataChange) {
    return new ResponseEntity<>(backgroundWorker.interruptingRunningJob(idTaskDataChange), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<TaskDataChange> getUpdateCreateJpaRepository() {
    return taskDataChangeJpaRepository;
  }

}
