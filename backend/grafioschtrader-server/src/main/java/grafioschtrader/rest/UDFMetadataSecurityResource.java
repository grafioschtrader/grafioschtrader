package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.User;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.UDF_METADATA_SECURITY_MAP)
@Tag(name = RequestMappings.UDFMETADATASECURITY, description = "Controller for user defined field of security")
public class UDFMetadataSecurityResource extends UpdateCreateDeleteAuditResource<UDFMetadataSecurity> {
  @Autowired
  private UDFMetadataSecurityJpaRepository uDFMetadataSecurityJpaRepository;


  @Operation(summary = "Return all UDF for the current user", description = "", tags = {
      RequestMappings.UDFMETADATASECURITY })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<UDFMetadataSecurity>> getAllByIdUser() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(uDFMetadataSecurityJpaRepository.getAllByIdUser(user.getIdUser()), HttpStatus.OK);
  }



  @Override
  protected UpdateCreateJpaRepository<UDFMetadataSecurity> getUpdateCreateJpaRepository() {
    return uDFMetadataSecurityJpaRepository;
  }



}
