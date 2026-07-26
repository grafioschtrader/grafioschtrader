package grafioschtrader.exportcsv;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of the cash transfer relink run over the tenant's unconnected WITHDRAWAL/DEPOSIT transactions.
 *
 * @param checked     number of unconnected withdrawal/deposit transactions that were examined
 * @param linkedPairs number of transfer pairs that were successfully connected
 * @param ambiguous   number of examined transactions that matched a counterpart but not unambiguously (skipped)
 * @param failed      number of matched pairs rejected by the transfer validation (e.g. overdraft or closed period)
 */
@Schema(description = """
    Result of the maintenance run that restores the connection between the two sides of a cash account transfer.
    Only unambiguous one-to-one matches are linked; ambiguous or rejected candidates are reported, never guessed.""")
public record CashTransferRelinkResult(
    @Schema(description = "Number of unconnected withdrawal/deposit transactions examined") int checked,
    @Schema(description = "Number of transfer pairs that were connected") int linkedPairs,
    @Schema(description = "Number of transactions with ambiguous match candidates, skipped for safety") int ambiguous,
    @Schema(description = "Number of matched pairs rejected by the transfer validation") int failed) {
}
