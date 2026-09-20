package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithUserIdJpaRepository;
import grafiosch.rest.UpdateCreateDeleteWithUserIdResource;
import grafioschtrader.dto.FieldDescriptorInputAndShowExtendedSecurity;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.UDF_METADATA_SECURITY_MAP)
@Tag(name = RequestGTMappings.UDFMETADATASECURITY, description = "Controller for user defined field of security")
public class UDFMetadataSecurityResource extends UpdateCreateDeleteWithUserIdResource<UDFMetadataSecurity> {

  private final UDFMetadataSecurityJpaRepository uDFMetadataSecurityJpaRepository;

  public UDFMetadataSecurityResource(UDFMetadataSecurityJpaRepository uDFMetadataSecurityJpaRepository) {
    super(UDFMetadataSecurity.class);
    this.uDFMetadataSecurityJpaRepository = uDFMetadataSecurityJpaRepository;
  }

  @Operation(summary = "Return all security UDF metadata for the current user", description = "This is intended for editing the metadata and contains all of them.", tags = {
      RequestGTMappings.UDFMETADATASECURITY })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<UDFMetadataSecurity>> getAllByIdUser() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        uDFMetadataSecurityJpaRepository.getAllByIdUserInAndUiOrderLessThanOrderByUiOrder(
            new int[] { user.getIdUser(), BaseConstants.UDF_ID_USER }, BaseConstants.MAX_USER_UI_ORDER_VALUE),
        HttpStatus.OK);
  }

  @Operation(summary = "Return of the input or output field descriptions for the securities.", description = "The deactivated metadata is excluded here.", tags = {
      RequestGTMappings.UDFMETADATASECURITY })
  @GetMapping(value = "/fielddescriptor", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<FieldDescriptorInputAndShowExtendedSecurity>> getFieldDescriptorByIdUserAndEveryUser() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        uDFMetadataSecurityJpaRepository.getFieldDescriptorByIdUserAndEveryUserExcludeDisabled(user.getIdUser()),
        HttpStatus.OK);
  }

  @Override
  protected UpdateCreateDeleteWithUserIdJpaRepository<UDFMetadataSecurity> getUpdateCreateJpaRepository() {
    return uDFMetadataSecurityJpaRepository;
  }

}
