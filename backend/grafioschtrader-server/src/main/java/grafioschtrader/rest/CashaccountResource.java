package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafiosch.rest.UpdateCreateDeleteWithTenantResource;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.reports.AccountPositionGroupSummaryReport;
import grafioschtrader.reportviews.account.AccountPositionGroupSummary;
import grafioschtrader.repository.CashaccountJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.CASHACCOUNT_MAP)
@Tag(name = Cashaccount.TABNAME, description = "Controller for the cash account")
public class CashaccountResource extends UpdateCreateDeleteWithTenantResource<Cashaccount> {

  @Autowired
  private CashaccountJpaRepository cashaccountJpaRepository;

  @Autowired
  private AccountPositionGroupSummaryReport accountPositionGroupSummaryReport;

  public CashaccountResource() {
    super(Cashaccount.class);
  }

  @Operation(summary = "Returns the performance report over a portfolio with all its cash accounts, it includes securities as well", description = "", tags = {
      Cashaccount.TABNAME })
  @GetMapping(value = "/{idPortfolio}/portfoliocashaccountsummary", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<AccountPositionGroupSummary> getAccountPositionSummaryPortfolio(
      @PathVariable final Integer idPortfolio, @RequestParam() @DateTimeFormat(iso = ISO.DATE) final Date untilDate) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        accountPositionGroupSummaryReport.getAccountGrandSummaryPortfolio(user.getIdTenant(), idPortfolio, untilDate),
        HttpStatus.OK);
  }

  /*
   * 
   * @Operation(summary = "Delete a cash account when possible", description =
   * "A used cash account will not be deleted", tags = { Cashaccount.TABNAME })
   * 
   * @DeleteMapping(value = "/{idSecuritycashaccount}", produces = APPLICATION_JSON_VALUE) public ResponseEntity<Void>
   * deleteCashaccount(@PathVariable final Integer idSecuritycashaccount) { final User user = (User)
   * SecurityContextHolder.getContext().getAuthentication().getDetails();
   * cashaccountJpaRepository.deleteCashaccount(idSecuritycashaccount, user.getIdTenant()); return
   * ResponseEntity.noContent().build(); }
   */
  @Override
  protected UpdateCreateDeleteWithTenantJpaRepository<Cashaccount> getUpdateCreateJpaRepository() {
    return cashaccountJpaRepository;
  }

  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

}
