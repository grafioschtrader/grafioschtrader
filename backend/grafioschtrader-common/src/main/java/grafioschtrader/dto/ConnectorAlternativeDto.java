package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.ConnectorProbeStatus;
import grafioschtrader.types.DistributionFrequency;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    One connector proposed for one feed (history, intraday, dividend or split) of a looked-up instrument, together with
    the URL extension the connector derived itself and the outcome of a trial request.""")
public record ConnectorAlternativeDto(
    @Schema(description = "Id of the connector as it is stored in the security, e.g. 'gt.datafeed.six'") String idConnector,
    @Schema(description = "Readable name of the connector") String readableName,
    @Schema(description = "URL extension derived by the connector; null when the connector needs none") String urlExtension,
    @Schema(description = "Outcome of the trial request") ConnectorProbeStatus probeStatus,
    @Schema(description = "Date of the sample value: last closing price, last price or most recent dividend") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate sampleDate,
    @Schema(description = "Sample value delivered by the probe: closing price, last price or dividend amount") Double sampleValue,
    @Schema(description = "Number of data records the probe received, e.g. closing prices or dividends") Integer sampleCount,
    @Schema(description = """
        Distribution frequency derived from the dividends this connector delivered; only set for a dividend connector.
        It is taken over into the security when the user chooses this connector and the master data has none.""") DistributionFrequency distributionFrequency,
    @Schema(description = "Short technical explanation of a failed probe; null on success") String probeDetail) {
}
