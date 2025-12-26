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

import grafiosch.rest.UpdateCreateJpaRepository;
import grafiosch.rest.UpdateCreateResource;
import grafioschtrader.entities.GTNetMessageAnswer;
import grafioschtrader.repository.GTNetMessageAnswerJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.GTNET_MESSAGE_ANSWER_MAP)
@Tag(name = RequestGTMappings.GTNET_MESSAGE_ANSWER, description = "Controller for GTNet message answer auto-response rules")
public class GTNetMessageAnswerResource extends UpdateCreateResource<GTNetMessageAnswer> {

  @Autowired
  private GTNetMessageAnswerJpaRepository gtNetMessageAnswerJpaRepository;

  @Override
  protected UpdateCreateJpaRepository<GTNetMessageAnswer> getUpdateCreateJpaRepository() {
    return gtNetMessageAnswerJpaRepository;
  }

  @Operation(summary = "Returns all GTNet message answer rules", description = "Retrieves all configured auto-response rules for GTNet messages")
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<GTNetMessageAnswer>> getAllGTNetMessageAnswers() {
    return new ResponseEntity<>(gtNetMessageAnswerJpaRepository.findAll(), HttpStatus.OK);
  }

  @Operation(summary = "Deletes a GTNet message answer rule", description = "Removes an auto-response rule by its ID")
  @DeleteMapping(value = "/{idGtNetMessageAnswer}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteGTNetMessageAnswer(@PathVariable final Integer idGtNetMessageAnswer) {
    gtNetMessageAnswerJpaRepository.deleteById(idGtNetMessageAnswer);
    return ResponseEntity.noContent().build();
  }
}
