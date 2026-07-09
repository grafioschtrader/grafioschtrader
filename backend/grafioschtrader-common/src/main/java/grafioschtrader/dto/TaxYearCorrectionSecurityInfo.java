package grafioschtrader.dto;

import java.util.List;

import grafioschtrader.entities.TaxYearCorrection;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Payload of the tax year correction maintenance dialog for one security: the tenant's existing correction
    records across all tax years plus the tax years for which ICTax data exists for the security's ISIN. Years
    without ICTax data allow only comment-only correction records.""")
public class TaxYearCorrectionSecurityInfo {

  @Schema(description = "All corrections of the tenant for this security, ordered by tax year descending")
  public List<TaxYearCorrection> corrections;

  @Schema(description = "Tax years with ICTax data for the security's ISIN; empty when the security has no ISIN")
  public List<Short> ictaxTaxYears;

  public TaxYearCorrectionSecurityInfo() {
  }

  public TaxYearCorrectionSecurityInfo(List<TaxYearCorrection> corrections, List<Short> ictaxTaxYears) {
    this.corrections = corrections;
    this.ictaxTaxYears = ictaxTaxYears;
  }
}
