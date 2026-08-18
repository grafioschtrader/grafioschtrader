package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafiosch.rest.UpdateCreateDeleteWithTenantResource;
import grafioschtrader.dto.TaxYearCorrectionSecurityInfo;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.TaxYearCorrection;
import grafioschtrader.repository.IctaxSecurityTaxDataJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TaxYearCorrectionJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.TAXYEARCORRECTION_MAP)
@Tag(name = RequestGTMappings.TAXYEARCORRECTION, description = "Controller for tax year corrections")
public class TaxYearCorrectionResource extends UpdateCreateDeleteWithTenantResource<TaxYearCorrection> {

  @Autowired
  private TaxYearCorrectionJpaRepository taxYearCorrectionJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private IctaxSecurityTaxDataJpaRepository ictaxSecurityTaxDataJpaRepository;

  public TaxYearCorrectionResource() {
    super(TaxYearCorrection.class);
  }

  @Operation(summary = "Return all tax year corrections of the tenant for one security plus the tax years with ICTax data", description = "", tags = {
      RequestGTMappings.TAXYEARCORRECTION })
  @GetMapping(value = "/security/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<TaxYearCorrectionSecurityInfo> getBySecurity(@PathVariable final Integer idSecuritycurrency) {
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<TaxYearCorrection> corrections = taxYearCorrectionJpaRepository
        .findByIdTenantAndIdSecuritycurrencyOrderByTaxYearDesc(user.getIdTenant(), idSecuritycurrency);
    Security security = securityJpaRepository.findById(idSecuritycurrency).orElse(null);
    List<Short> ictaxTaxYears = security == null || security.getIsin() == null || security.getIsin().isEmpty()
        ? Collections.emptyList()
        : ictaxSecurityTaxDataJpaRepository.findTaxYearsByIsin(security.getIsin());
    return new ResponseEntity<>(new TaxYearCorrectionSecurityInfo(corrections, ictaxTaxYears), HttpStatus.OK);
  }

  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<TaxYearCorrection> getUpdateCreateJpaRepository() {
    return taxYearCorrectionJpaRepository;
  }

}
