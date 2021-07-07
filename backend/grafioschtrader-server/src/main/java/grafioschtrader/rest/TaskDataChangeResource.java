package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.repository.TaskDataChangeJpaRepositoryCustom.TaskDataChangeFormConstraints;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.TASK_DATA_CHANGE_MAP)
@Tag(name = RequestMappings.TASK_DATA_CHANGE, description = "Controller for task data change")
public class TaskDataChangeResource extends UpdateCreateDeleteAuditResource<TaskDataChange> {

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @GetMapping(value = "/", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<TaskDataChange>> getAllTaskDataChange() {
    return new ResponseEntity<>(taskDataChangeJpaRepository.findAll(), HttpStatus.OK);
  }

  @GetMapping(value = "/taskdatachangeconstraints", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<TaskDataChangeFormConstraints> getFormConstraints() {
    return new ResponseEntity<>(taskDataChangeJpaRepository.getFormConstraints(), HttpStatus.OK);
  }

  @Override
  @DeleteMapping(value = "/{idTaskDataChange}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteResource(@PathVariable final Integer idTaskDataChange) {
    taskDataChangeJpaRepository.deleteById(idTaskDataChange);
    return ResponseEntity.noContent().build();
  }

  @Override
  protected UpdateCreateJpaRepository<TaskDataChange> getUpdateCreateJpaRepository() {
    return taskDataChangeJpaRepository;
  }

}
