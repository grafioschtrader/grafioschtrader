package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyChangePassword;
import grafiosch.common.PropertyOnlyCreation;
import grafiosch.dto.PasswordRegexProperties;
import grafiosch.dto.TenantLimit;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.dynamic.model.DynamicModelHelper;
import grafiosch.dynamic.model.FieldDescriptorInputAndShow;
import grafiosch.entities.Globalparameters;
import grafiosch.entities.User;
import grafiosch.repository.GlobalparametersJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(RequestMappings.GLOBALPARAMETERS_MAP)
@Tag(name = Globalparameters.TABNAME, description = "Controller for global parameters")
public class GlobalparametersResource {

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Operation(summary = "Returns the password requirements.", description = "", tags = { Globalparameters.TABNAME })
  @GetMapping(value = "/passwordrequirements", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<PasswordRegexProperties> getPasswordRegexProperties() throws Exception {
    return new ResponseEntity<>(globalparametersJpaRepository.getPasswordRegexProperties(), HttpStatus.OK);
  }

  @Operation(summary = "Returns all global parameters", description = "", tags = { Globalparameters.TABNAME })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Globalparameters>> getAllGlobalparameters() {
    return new ResponseEntity<>(globalparametersJpaRepository.findAll(), HttpStatus.OK);
  }

  @Operation(summary = """
      Some information classes have limits for the tenant. The current number of entities and their limit can be queried here.
      This allows the frontend to check this limit before creating an entity.""", description = "", tags = {
      Globalparameters.TABNAME })
  @GetMapping(value = "/tenantlimits", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<TenantLimit>> getMaxTenantLimitsByMsgKey(@RequestParam() final List<String> msgKeys) {
    return new ResponseEntity<>(globalparametersJpaRepository.getMaxTenantLimitsByMsgKeys(msgKeys), HttpStatus.OK);
  }

  @Operation(summary = """
      Provides the metadata of the User information class.
      This gives the frontend the possible definition of the input fields. This is useful for the registration and password dialog.""", description = "", tags = {
      Globalparameters.TABNAME })
  @GetMapping(value = "/userformdefinition", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<FieldDescriptorInputAndShow>> getUserFormDefinitions() {
    return new ResponseEntity<>(
        DynamicModelHelper.getFormDefinitionOfModelClassMembers(User.class,
            Set.of(PropertyAlwaysUpdatable.class, PropertyChangePassword.class, PropertyOnlyCreation.class)),
        HttpStatus.OK);
  }

  @Operation(summary = "Returns the possible countries as it can be used in html option", description = "", tags = {
      Globalparameters.TABNAME })
  @GetMapping(value = "/countries", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getCountriesForSelectBox() {
    return new ResponseEntity<>(globalparametersJpaRepository.getCountriesForSelectBox(), HttpStatus.OK);
  }

  @Operation(summary = "Some language translations are provided by the backend", description = "Properties names are separated by underscore", tags = {
      Globalparameters.TABNAME })
  @GetMapping(value = "/properties/{language}", produces = APPLICATION_JSON_VALUE)
  public String getLanguageProperties(@PathVariable final String language) {
    return globalparametersJpaRepository.getLanguageProperties(language);
  }

  @Operation(summary = "Returns the locales as key value properties", description = "", tags = {
      Globalparameters.TABNAME })
  @GetMapping(value = "/locales", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getSupportedLocalesEnDe() {
    return new ResponseEntity<>(globalparametersJpaRepository.getSupportedLocales(), HttpStatus.OK);
  }

  @Operation(summary = "Returns the country time zones as key value pair", description = "", tags = {
      Globalparameters.TABNAME })
  @GetMapping(value = "/timezones", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getTimezones() {
    return new ResponseEntity<>(globalparametersJpaRepository.getAllZoneIds(), HttpStatus.OK);
  }

  @Operation(summary = "Change a property value of existing global parameter", description = "Only admin can change values of exiting global parameters", tags = {
      Globalparameters.TABNAME })
  @PutMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Globalparameters> replacePropertyValue(
      @Valid @RequestBody final Globalparameters globalparameters) throws Exception {
    return new ResponseEntity<>(globalparametersJpaRepository.saveOnlyAttributes(globalparameters), HttpStatus.OK);
  }

}
