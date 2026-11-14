package grafioschtrader.dto;

import java.util.List;

import grafioschtrader.types.ChartPdfPaper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = """
    Request for the PDF of a time-series chart of one security. The browser sends the chart as it is displayed, drawn
    as SVG, and says which transactions its markers stand for. The server places the chart on the page and adds the
    table of those transactions, which it reads itself in the same scope as the chart.""")
public class SecurityChartPdfRequest {

  @Schema(description = "Marker of a transaction in the chart and the number printed at it and in the table")
  public static class TransactionNumber {
    @NotNull
    @Schema(description = "Transaction the marker stands for")
    public Integer idTransaction;

    @NotNull
    @Schema(description = "Number shown at the marker; the same number leads the row of the table")
    public Integer number;
  }

  @NotNull
  @Schema(description = "Security of the chart")
  public Integer idSecuritycurrency;

  @Schema(description = """
      Portfolio the chart was opened for. Together with idsSecurityaccount empty, the transactions of the whole tenant
      are used. Both set is refused.""")
  public Integer idPortfolio;

  @Size(max = 50)
  @Schema(description = "Securities accounts the chart was opened for, empty for the portfolio or the whole tenant")
  public List<Integer> idsSecurityaccount;

  @NotBlank
  @Size(max = 8_000_000)
  @Schema(description = "The chart as SVG, from Plotly.toImage with format svg; checked strictly before rendering")
  public String svg;

  @NotNull
  @Schema(description = "Paper format of the document")
  public ChartPdfPaper paper;

  @NotNull
  @Schema(description = "Language of the texts, validated against the supported language codes")
  public String language;

  @NotNull
  @Schema(description = "Locale tag for dates and numbers, one of the report number formats")
  public String numberFormat;

  @Schema(description = "Whether the table of the numbered transactions follows the chart")
  public boolean includeTransactions = true;

  @Valid
  @Size(max = 2000)
  @Schema(description = """
      Markers shown in the chart. Only these transactions are listed, in the order of their booking; a transaction the
      user may not see is never listed, whatever is sent here.""")
  public List<TransactionNumber> transactionNumbers;
}
