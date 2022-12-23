package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.StringResponse;
import grafioschtrader.dynamic.model.DynamicModelHelper;
import grafioschtrader.dynamic.model.FieldDescriptorInputAndShow;
import grafioschtrader.entities.ProposeUserTask;
import grafioschtrader.repository.ProposeUserTaskJpaRepository;
import grafioschtrader.usertask.UserTaskType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;

@RestController
@RequestMapping(RequestMappings.PROPOSEUSER_TASK_MAP)
@Tag(name = RequestMappings.PROPOSEUSER_TASK, description = "Controller for propose user task")
public class ProposeUserTaskResource extends UpdateCreateDeleteAuditResource<ProposeUserTask> {

  @Autowired
  private ProposeUserTaskJpaRepository proposeUserTaskJpaRepository;

  @Operation(summary = "It returns input field defintion for a form which allows the user to apply for some user task", description = "", tags = {
      RequestMappings.PROPOSEUSER_TASK })
  @GetMapping(value = "/form/{userTaskType}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<FieldDescriptorInputAndShow>> getFormDefinitionsByUserTaskType(
      @PathVariable final byte userTaskType) {
    return new ResponseEntity<>(DynamicModelHelper.getFormDefinitionOfModelClassMembers(
        ProposeUserTask.getModelByUserTaskType(UserTaskType.getUserTaskTypeByValue(userTaskType))), HttpStatus.OK);
  }

  @Operation(summary = "The request was denied by the admin.", description = "It send a Email to the user, since he may not have anymore any access to GT", tags = {
      RequestMappings.PROPOSEUSER_TASK })
  @PostMapping(value = "/reject/{idProposeRequest}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<StringResponse> rejectUserTask(@PathVariable final Integer idProposeRequest,
      @RequestBody String rejectNote) throws MessagingException {
    return new ResponseEntity<>(
        new StringResponse(proposeUserTaskJpaRepository.rejectUserTask(idProposeRequest, rejectNote)), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<ProposeUserTask> getUpdateCreateJpaRepository() {
    return proposeUserTaskJpaRepository;
  }

}
