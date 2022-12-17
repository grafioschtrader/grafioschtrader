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

import grafioschtrader.entities.MailSendbox;
import grafioschtrader.repository.MailSendboxJpaRepository;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.MAIL_SENDBOX_MAP)
@Tag(name = RequestMappings.MAIL_SENDBOX, description = "Controller for mail outbox")
public class MailSendboxResource extends UpdateCreateResource<MailSendbox> {

  @Autowired
  private MailSendboxJpaRepository mailSendboxJpaRepository;

  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<MailSendbox>> getAllInboxByUser() {
    return new ResponseEntity<>(mailSendboxJpaRepository.getMailSendboxByUser(), HttpStatus.OK);
  }

  @DeleteMapping(value = "/{idMailInOut}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteResource(@PathVariable final Integer idMailInOut) {
    mailSendboxJpaRepository.deleteByIdMailInOut(idMailInOut);
    return ResponseEntity.noContent().build();
  }

  @Override
  protected UpdateCreateJpaRepository<MailSendbox> getUpdateCreateJpaRepository() {
    return mailSendboxJpaRepository;
  }

}
