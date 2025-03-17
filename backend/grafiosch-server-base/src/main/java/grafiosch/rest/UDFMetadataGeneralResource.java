package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.dynamic.model.FieldDescriptorInputAndShowExtendedGeneral;
import grafiosch.entities.UDFMetadataGeneral;
import grafiosch.entities.User;
import grafiosch.repository.UDFMetadataGeneralJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.UDF_METADATA_GENERAL_MAP)
@Tag(name = RequestMappings.UDFMETADATAGENERAL, description = "Controller for the general metadata of the user-defined fields")
public class UDFMetadataGeneralResource extends UpdateCreateDeleteWithUserIdResource<UDFMetadataGeneral> {
  
  private final UDFMetadataGeneralJpaRepository uDFMetadataGeneralJpaRepository;
  
  public UDFMetadataGeneralResource(UDFMetadataGeneralJpaRepository uDFMetadataGeneralJpaRepository) {
    super(UDFMetadataGeneral.class);
    this.uDFMetadataGeneralJpaRepository = uDFMetadataGeneralJpaRepository;
  }

  @Operation(summary = "Return all UDF for the current user", description = "", tags = {
      RequestMappings.UDFMETADATAGENERAL })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<UDFMetadataGeneral>> getAllByIdUser() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        uDFMetadataGeneralJpaRepository.getAllByIdUserInOrderByUiOrder(new int[] { user.getIdUser() }), HttpStatus.OK);
  }

  @Operation(summary = "Return of the input or output field descriptions for the generall user defined fields.", description = "", tags = {
      RequestMappings.UDFMETADATAGENERAL })
  @GetMapping(value = "/fielddescriptor/{entity}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<FieldDescriptorInputAndShowExtendedGeneral>> getFieldDescriptorByIdUserForEntity(
      @Parameter(description = "Name of entity", required = true) @PathVariable final String entity) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        uDFMetadataGeneralJpaRepository.getFieldDescriptorByIdUserAndEveryUserForEntity(user.getIdUser(), entity),
        HttpStatus.OK);
  }

  @Override
  protected UpdateCreateDeleteWithUserIdJpaRepository<UDFMetadataGeneral> getUpdateCreateJpaRepository() {
    return uDFMetadataGeneralJpaRepository;
  }
}
