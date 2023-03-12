package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.dto.MailInboxWithSend;
import grafioschtrader.entities.MailSendRecv;
import grafioschtrader.repository.MailSendRecvJpaRepository;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.MAIL_SEMD_RECV_READ_MAP)
@Tag(name = RequestMappings.MAIL_SEMD_RECV, description = "Controller for mail system")
public class MailSendRecvReadResource extends UpdateCreateResource<MailSendRecv> {

  @Autowired
  private MailSendRecvJpaRepository mailSendRecvJpaRepository;
  
 
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<MailInboxWithSend> getMailsByUserOrRole() {
    return new ResponseEntity<>(mailSendRecvJpaRepository.getMailsByUserOrRole(), HttpStatus.OK);
  }

  @PostMapping(value = "/{idMailSendRecv}/markforread", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<MailSendRecv> markForRead(@PathVariable final Integer idMailSendRecv) {
    return new ResponseEntity<>(mailSendRecvJpaRepository.markForRead(idMailSendRecv), HttpStatus.OK);
  }

  @DeleteMapping(value = "/{idMailSendRecv}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> hideDeleteResource(@PathVariable final Integer idMailSendRecv) {
    mailSendRecvJpaRepository.hideDeleteResource(idMailSendRecv);
    return ResponseEntity.noContent().build();
  }

  @Override
  protected UpdateCreateJpaRepository<MailSendRecv> getUpdateCreateJpaRepository() {
    return mailSendRecvJpaRepository;
  }

}
