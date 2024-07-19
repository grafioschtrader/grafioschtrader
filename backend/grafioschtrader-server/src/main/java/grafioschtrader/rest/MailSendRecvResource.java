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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.MAIL_SEMD_RECV_MAP)
@Tag(name = RequestMappings.MAIL_SEMD_RECV, description = "Controller for mail system")
public class MailSendRecvResource extends UpdateCreateResource<MailSendRecv> {

  @Autowired
  private MailSendRecvJpaRepository mailSendRecvJpaRepository;

  @Operation(summary = "Returning the messages for the current user and his user role", description = "", tags = {
      RequestMappings.MAIL_SEMD_RECV })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<MailInboxWithSend> getMailsByUserOrRole() {
    return new ResponseEntity<>(mailSendRecvJpaRepository.getMailsByUserOrRole(), HttpStatus.OK);
  }

  @Operation(summary = "Received messages can be marked as read", description = "", tags = {
      RequestMappings.MAIL_SEMD_RECV })
  @PostMapping(value = "/{idMailSendRecv}/markforread", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<MailSendRecv> markForRead(@PathVariable final Integer idMailSendRecv) {
    return new ResponseEntity<>(mailSendRecvJpaRepository.markForRead(idMailSendRecv), HttpStatus.OK);
  }

  @Operation(summary = "Delete or mark as deleted a single message or a message topic.",
      description = "The message topic or message will no longer appear to the user.", tags = {
      RequestMappings.MAIL_SEMD_RECV })
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
