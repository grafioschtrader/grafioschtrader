package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.dto.MailSendForwardDefaultBase;
import grafiosch.entities.MailSettingForward;
import grafiosch.entities.User;
import grafiosch.repository.MailSettingForwardJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.MAIL_SETTING_FORWARD_MAP)
@Tag(name = RequestMappings.MAIL_SETTING_FORWARD, description = "Controller for  mail forward setting")
public class MailSettingForwardResource extends UpdateCreateDeleteWithUserIdResource<MailSettingForward> {

  private final MailSettingForwardJpaRepository mailSettingForwardJpaRepository;

  public MailSettingForwardResource(MailSettingForwardJpaRepository mailSettingForwardJpaRepository) {
    super(MailSettingForward.class);
    this.mailSettingForwardJpaRepository = mailSettingForwardJpaRepository;
  }

  @Operation(summary = "Return all mail forward setting for a user", description = "", tags = {
      RequestMappings.MAIL_SETTING_FORWARD })
  @GetMapping(value = "/user", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<MailSettingForward>> getMailSettingForwardByUser() {
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(mailSettingForwardJpaRepository.findByIdUser(user.getIdUser()), HttpStatus.OK);
  }

  @Operation(summary = "Returns the default setting and the possible setting values.", description = "", tags = {
      RequestMappings.MAIL_SETTING_FORWARD })
  @GetMapping(value = "/defaultforward", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<MailSendForwardDefaultBase> getSendForwardDefault() {
    return new ResponseEntity<>(mailSettingForwardJpaRepository.getMailSendForwardDefault(), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateDeleteWithUserIdJpaRepository<MailSettingForward> getUpdateCreateJpaRepository() {
    return mailSettingForwardJpaRepository;
  }

}
