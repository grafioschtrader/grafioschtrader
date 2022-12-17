package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.MailInbox;
import grafioschtrader.repository.MailInboxJpaRepository;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.MAIL_INBOX_MAP)
@Tag(name = RequestMappings.MAIL_INBOX, description = "Controller for intenal mail inbox")
public class MailInboxResource {

  @Autowired
  private MailInboxJpaRepository mailInboxJpaRepository;

  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<MailInbox>> getMailInboxByUser() {
    return new ResponseEntity<>(mailInboxJpaRepository.getMailInboxByUser(), HttpStatus.OK);
  }

  @PostMapping(value = "/{idMailInOut}/markforread", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<MailInbox> markForRead(@PathVariable final Integer idMailInOut) {
    return new ResponseEntity<>(mailInboxJpaRepository.markForRead(idMailInOut), HttpStatus.OK);
  }

  @DeleteMapping(value = "/{idMailInOut}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteResource(@PathVariable final Integer idMailInOut) {
    mailInboxJpaRepository.deleteByIdMailInOut(idMailInOut);
    return ResponseEntity.noContent().build();
  }

}
