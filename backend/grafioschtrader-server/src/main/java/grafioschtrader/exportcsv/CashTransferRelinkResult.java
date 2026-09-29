package grafioschtrader.exportcsv;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of the cash transfer relink run over the tenant's unconnected WITHDRAWAL/DEPOSIT transactions.
 *
 * <p>
 * The counts use two units: {@code checked} and {@code ambiguous} count transactions, {@code linkedPairs} and
 * {@code failed} count pairs of two transactions each. The transactions that found no counterpart at all — genuine
 * deposits and withdrawals, or a side whose counterpart was not imported — appear only in {@code checked}; their number
 * is {@code checked - 2 * linkedPairs - ambiguous - 2 * failed}.
 * </p>
 *
 * @param checked     number of unconnected withdrawal/deposit transactions that were examined
 * @param linkedPairs number of transfer pairs that were successfully connected
 * @param ambiguous   number of examined transactions that matched a counterpart but not unambiguously (skipped)
 * @param failed      number of matched pairs rejected by the transfer validation (e.g. overdraft, closed period or an
 *                    exchange rate too far from the close of that day)
 */
@Schema(description = """
    Result of the maintenance run that restores the connection between the two sides of a cash account transfer.
    Only unambiguous one-to-one matches are linked; ambiguous or rejected candidates are reported, never guessed.
    'checked' and 'ambiguous' count transactions, 'linkedPairs' and 'failed' count pairs. Transactions without any
    counterpart appear only in 'checked': checked - 2 * linkedPairs - ambiguous - 2 * failed.""")
public record CashTransferRelinkResult(@Schema(description = """
    Number of unconnected withdrawal/deposit transactions examined, including those without any counterpart, \
    such as genuine deposits""") int checked,
    @Schema(description = "Number of transfer pairs that were connected; each pair consists of two transactions") int linkedPairs,
    @Schema(description = """
        Number of transactions with more than one possible counterpart in the same minute, skipped for safety. \
        They have to be connected manually.""") int ambiguous,
    @Schema(description = """
        Number of matched pairs rejected by the transfer validation, e.g. an overdraft, a closed period or an \
        exchange rate too far from the close of that day. The server log names the transactions and the reason.""") int failed) {
}
