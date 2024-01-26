package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.MailSendForwardDefault;
import grafioschtrader.entities.MailSettingForward;
import grafioschtrader.entities.User;
import grafioschtrader.repository.MailSettingForwardJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.MAIL_SETTING_FORWARD_MAP)
@Tag(name = RequestMappings.MAIL_SETTING_FORWARD, description = "Controller for  mail forward setting")
public class MailSettingForwardResource extends UpdateCreateResource<MailSettingForward> {

  @Autowired
  private MailSettingForwardJpaRepository mailSettingForwardJpaRepository;

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
  public ResponseEntity<MailSendForwardDefault> getSendForwardDefault() {
    return new ResponseEntity<>(mailSettingForwardJpaRepository.getMailSendForwardDefault(), HttpStatus.OK);
  }

  @Operation(summary = "Delete single forward setting", description = "", tags = { RequestMappings.MAIL_SETTING_FORWARD })
  @DeleteMapping(value = "/{idForwardSetting}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteHistoryquote(@PathVariable final Integer idForwardSetting) {
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    int countDel = mailSettingForwardJpaRepository.deleteByIdUserAndIdMailSettingForward(user.getIdUser(), idForwardSetting);
    if(countDel != 1) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
    return ResponseEntity.ok().build();
  }

  @Override
  protected UpdateCreateJpaRepository<MailSettingForward> getUpdateCreateJpaRepository() {
    return mailSettingForwardJpaRepository;
  }



}
