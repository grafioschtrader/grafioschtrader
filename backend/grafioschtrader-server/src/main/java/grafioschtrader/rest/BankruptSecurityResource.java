package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.rest.UpdateCreateDeleteAuditResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.dto.IBankruptSecurityWithName;
import grafioschtrader.entities.BankruptSecurity;
import grafioschtrader.repository.BankruptSecurityJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for the instruments whose issuer no longer supplies price data. Create, update and delete are the
 * inherited endpoints of {@link UpdateCreateDeleteAuditResource}.
 *
 * <p>
 * Reading is open to every authenticated user, because the marker explains where the prices of a shared instrument come
 * from and that concerns everyone holding it. Writing requires the right to edit the instrument itself, which for a
 * user holding ROLE_LIMIT_EDIT means the instruments that user created; the rule is enforced in
 * {@link grafioschtrader.repository.BankruptSecurityJpaRepositoryImpl#saveOnlyAttributes} so no write path can bypass
 * it.
 * </p>
 */
@RestController
@RequestMapping(RequestGTMappings.BANKRUPTSECURITY_MAP)
@Tag(name = RequestGTMappings.BANKRUPTSECURITY, description = "Controller for instruments without further price data")
public class BankruptSecurityResource extends UpdateCreateDeleteAuditResource<BankruptSecurity> {

  @Autowired
  private BankruptSecurityJpaRepository bankruptSecurityJpaRepository;

  @Operation(summary = "List every marked instrument", description = """
      Returns one row per marked instrument, with the name, ISIN and currency of the instrument and the two newest
      closing dates: the newest price a provider delivered and the newest price of any kind. The distance between the
      two is how far the automatic filling currently reaches.""", tags = { RequestGTMappings.BANKRUPTSECURITY })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<IBankruptSecurityWithName>> getAll() {
    return new ResponseEntity<>(bankruptSecurityJpaRepository.findAllWithSecurityName(), HttpStatus.OK);
  }

  @Operation(summary = "Marker of a single instrument", description = """
      Returns the marker of the given instrument, or an empty body when it is not marked. Used by the instrument
      context menu to decide whether it offers to mark or to unmark.""", tags = { RequestGTMappings.BANKRUPTSECURITY })
  @GetMapping(value = "/security/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<BankruptSecurity> getByIdSecuritycurrency(
      @Parameter(description = "Identifier of the instrument", required = true) @PathVariable final Integer idSecuritycurrency) {
    return bankruptSecurityJpaRepository.findByIdSecuritycurrency(idSecuritycurrency)
        .map(bankruptSecurity -> new ResponseEntity<>(bankruptSecurity, HttpStatus.OK))
        .orElseGet(() -> new ResponseEntity<>(HttpStatus.NO_CONTENT));
  }

  @Override
  protected UpdateCreateJpaRepository<BankruptSecurity> getUpdateCreateJpaRepository() {
    return bankruptSecurityJpaRepository;
  }
}
