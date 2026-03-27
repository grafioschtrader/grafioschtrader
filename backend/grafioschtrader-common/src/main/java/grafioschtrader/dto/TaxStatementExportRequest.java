package grafioschtrader.dto;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for exporting an eCH-0196 v2.2.0 Swiss electronic tax statement. The user provides institution and client
 * details that are not stored in GT but required by the tax authorities. Also persisted as JSON in the tenant entity to
 * pre-fill the export dialog on subsequent visits.
 */
@Schema(description = """
    Request parameters for generating an eCH-0196 v2.2.0 compliant Swiss electronic tax statement (eSteuerauszug).
    Institution and client details are entered by the user for each export.""")
public class TaxStatementExportRequest implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "Tax year for which to generate the statement", requiredMode = Schema.RequiredMode.REQUIRED)
  private short taxYear;

  @Schema(description = """
      Security account IDs to include in the export. Pass [-1] or empty list to include all security accounts.
      Uses the same account selection as the dividends view.""")
  private List<Integer> idsSecurityaccount;

  @Schema(description = "Swiss canton abbreviation (2 letters, e.g. ZH, BE, LU)", requiredMode = Schema.RequiredMode.REQUIRED)
  private String canton;

  @Schema(description = "Name of the financial institution", requiredMode = Schema.RequiredMode.REQUIRED)
  private String institutionName;

  @Schema(description = "LEI (Legal Entity Identifier) of the institution, exactly 20 characters")
  private String institutionLei;

  @Schema(description = "Client/customer number at the institution", requiredMode = Schema.RequiredMode.REQUIRED)
  private String clientNumber;

  @Schema(description = "Client's first name")
  private String clientFirstName;

  @Schema(description = "Client's last name")
  private String clientLastName;

  @Schema(description = "Client's TIN (Swiss AHV number)")
  private String clientTin;

  public short getTaxYear() { return taxYear; }
  public void setTaxYear(short taxYear) { this.taxYear = taxYear; }

  public String getCanton() { return canton; }
  public void setCanton(String canton) { this.canton = canton; }

  public String getInstitutionName() { return institutionName; }
  public void setInstitutionName(String institutionName) { this.institutionName = institutionName; }

  public String getInstitutionLei() { return institutionLei; }
  public void setInstitutionLei(String institutionLei) { this.institutionLei = institutionLei; }

  public String getClientNumber() { return clientNumber; }
  public void setClientNumber(String clientNumber) { this.clientNumber = clientNumber; }

  public String getClientFirstName() { return clientFirstName; }
  public void setClientFirstName(String clientFirstName) { this.clientFirstName = clientFirstName; }

  public String getClientLastName() { return clientLastName; }
  public void setClientLastName(String clientLastName) { this.clientLastName = clientLastName; }

  public String getClientTin() { return clientTin; }
  public void setClientTin(String clientTin) { this.clientTin = clientTin; }

  public List<Integer> getIdsSecurityaccount() { return idsSecurityaccount; }
  public void setIdsSecurityaccount(List<Integer> idsSecurityaccount) { this.idsSecurityaccount = idsSecurityaccount; }
}
