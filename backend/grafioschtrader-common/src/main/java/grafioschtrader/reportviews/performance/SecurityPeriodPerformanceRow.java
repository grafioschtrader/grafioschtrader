package grafioschtrader.reportviews.performance;

import grafioschtrader.types.BenchmarkCoverage;
import grafioschtrader.types.SecurityPeriodRemark;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    One security of the period performance per security. Values are closing values of the start and end date, flows
    are the purchases, sales, income and settled margin results booked after the start date up to the end date. Main
    currency (MC) is the tenant currency for the client and the portfolio currency for a portfolio. A figure that cannot
    be formed is null, never 0; the remark says why.""")
public record SecurityPeriodPerformanceRow(
    @Schema(description = "Identifier of the security") Integer idSecuritycurrency,
    @Schema(description = "Name of the security") String name,
    @Schema(description = "ISIN of the security, may be null") String isin,
    @Schema(description = "Currency in which the security is traded") String currency,
    @Schema(description = "Whether the security is a margin instrument (CFD, Forex), which shows the gain only") boolean marginInstrument,
    @Schema(description = """
        Calendar days after the start date up to the end date on which the security was held at the close, each day
        counted once however many accounts held it""") int holdingDays,
    @Schema(description = "Units held at the close of the start date as traded; null for a margin instrument") Double unitsStart,
    @Schema(description = "Units held at the close of the end date as traded; null for a margin instrument") Double unitsEnd,
    @Schema(description = "Value at the close of the start date in main currency; for a margin instrument the open result") Double valueStartMC,
    @Schema(description = "Value at the close of the end date in main currency; for a margin instrument the open result") Double valueEndMC,
    @Schema(description = """
        Money paid into the position during the period in main currency: purchases with their cost, for a margin
        instrument settled losses and cost. Each flow is converted with the closing rate of its day""") Double inflowMC,
    @Schema(description = """
        Money that came back from the position during the period in main currency, as a positive amount: sales and
        income after cost, for a margin instrument settled gains""") Double outflowMC,
    @Schema(description = "Gain of the period in security currency: end value minus start value minus the net flow") Double gainPeriod,
    @Schema(description = """
        Gain of the period in main currency, valueEndMC - valueStartMC - inflowMC + outflowMC. Contains price gain,
        income, transaction cost and the currency result of the position""") Double gainPeriodMC,
    @Schema(description = """
        Time-weighted return of the position in percent in main currency, from close to close over the days it was
        held. Purchases count from the previous close, sales and income at the close""") Double twrPercent,
    @Schema(description = "Time-weighted return of the position in percent in security currency") Double twrSecurityCurrencyPercent,
    @Schema(description = "Cause of missing figures, null for a complete row") SecurityPeriodRemark remark,
    @Schema(description = """
        Return of the benchmark in percent over exactly the days on which the position held capital; null without a
        benchmark, without twrPercent or when one of those days is not covered by the comparison""") Double benchmarkTwrPercent,
    @Schema(description = "twrPercent minus benchmarkTwrPercent in percentage points") Double excessTwrPercent,
    @Schema(description = "Why a comparison is empty; null without a benchmark") BenchmarkCoverage benchmarkCoverage) {
}
