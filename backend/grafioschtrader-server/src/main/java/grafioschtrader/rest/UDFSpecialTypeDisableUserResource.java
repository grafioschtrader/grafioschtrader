package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.entities.UDFSpecialTypeDisableUser;
import grafioschtrader.entities.UDFSpecialTypeDisableUser.UDFSpecialTypeDisableUserId;
import grafioschtrader.entities.User;
import grafioschtrader.repository.UDFSpecialTypeDisableUserRepository;
import grafioschtrader.types.UDFSpecialType;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(RequestMappings.UDF_SEPCIAL_TYPE_DISABLE_USER_MAP)
public class UDFSpecialTypeDisableUserResource {

  @Autowired
  private UDFSpecialTypeDisableUserRepository uDFSpecialTypeDisUserRep;

  @Operation(summary = "Return global disabled specaial types.", description = "", tags = {
      RequestMappings.UDFSPECIALTYPEDISABLEUSER })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Set<UDFSpecialType>> getDisabledSpecialTypes() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(uDFSpecialTypeDisUserRep.findByIdIdUser(user.getIdUser()).stream()
        .map(s -> UDFSpecialType.getUDFSpecialTypeByValue(s)).collect(Collectors.toSet()), HttpStatus.OK);
  }

  @Operation(summary = "With this entity we have a composite key, so there is a special implementation for creating it.", description = "", tags = {
      RequestMappings.UDFSPECIALTYPEDISABLEUSER })
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UDFSpecialTypeDisableUser> create(@RequestBody Byte udfSpecialTypeValue) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        uDFSpecialTypeDisUserRep
            .save(new UDFSpecialTypeDisableUser(new UDFSpecialTypeDisableUserId(user.getIdUser(), 
                udfSpecialTypeValue))),
        HttpStatus.OK);
  }

  @DeleteMapping("/{udfSpecialType}")
  public ResponseEntity<Void> delete(@PathVariable Byte udfSpecialType) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    UDFSpecialTypeDisableUserId id = new UDFSpecialTypeDisableUserId(user.getIdUser(), udfSpecialType);
    uDFSpecialTypeDisUserRep.deleteById(id);
    return ResponseEntity.noContent().build();
  }

}
