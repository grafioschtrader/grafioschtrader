package grafioschtrader.reportviews.dashboard;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Wire contract of the card showing how the value of a client or of a single portfolio moved over the last completed
 * sessions.
 *
 * <p>
 * Every figure of a session is a change from the session before it, never a level, with the single exception of
 * {@code totalBalanceMC}. That is why the card reads one session more than it shows.
 * </p>
 */
public final class LastSessionsPerformancePayload {

  private LastSessionsPerformancePayload() {
  }

  @Schema(description = """
      One card. The currency is that of the client, or that of the portfolio when a single portfolio is shown, and every
      amount is expressed in it. The totals are the sums over the listed sessions, so they cover the span the rows
      actually cover and not the one the settings asked for, which is longer whenever the history does not reach that
      far back.""")
  public record LastSessions(String currency,
      @Schema(description = "Identifier of the portfolio shown, null when the whole client is shown") Integer idPortfolio,
      @Schema(description = "Session the first row is measured against; it is not itself a row") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate baseDate,
      @Schema(description = "Sum of the daily results over the listed sessions") double totalGainMC,
      @Schema(description = "Sum of the daily changes of the total value, deposits and withdrawals included") double totalBalanceChangeMC,
      @Schema(description = "Deposits less withdrawals over the listed sessions") double externalCashTransferMC,
      @Schema(description = "Separately booked fees over the listed sessions") double feeRealMC,
      @Schema(description = "Cash account interest over the listed sessions") double interestCashaccountRealMC,
      @Schema(description = """
          Translation key explaining why the card has no rows, null when it has some. A client that holds nothing yet,
          or one whose price history does not reach two usable sessions, is an ordinary state and not a fault; the
          reader is told so rather than being left with a blank card.""") String reasonKey, List<Session> sessions) {
  }

  @Schema(description = """
      One session of the card. Both movements are reported side by side because they answer different questions:
      totalBalanceChangeMC is what the client is worth more or less than on the session before, totalGainMC is what the
      investments earned. The two differ by exactly externalCashTransferMC, because paying money in raises the value
      without being a result.""")
  public record Session(
      @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate date,
      @Schema(description = "Total value at the end of the session: cash, securities and the result of closed margin positions") double totalBalanceMC,
      @Schema(description = "Change of that total value since the session before, deposits and withdrawals included") double totalBalanceChangeMC,
      @Schema(description = """
          Result of the session, that is the change of the total value with deposits and withdrawals removed. This is
          the figure a reader means by the performance of a day.""") double totalGainMC,
      @Schema(description = "Deposits less withdrawals booked on this session") double externalCashTransferMC,
      @Schema(description = """
          Separately booked fees of this session, that is account and depot fees; the trading costs inside a purchase or
          a sale are not here. Delivered as a positive number although it is a cost. It is contained in the result
          rather than added to it: the fee has already left the cash account and is therefore part of the day. For a
          cash account in a foreign currency the figure also carries the revaluation of the accumulated fees at the
          exchange rate of this session, so it is not exactly what was booked on the day.""") double feeRealMC,
      @Schema(description = """
          Cash account interest of this session, contained in the result for the same reason as the fee and carrying the
          same currency revaluation.""") double interestCashaccountRealMC, @Schema(description = """
          True when at least one held instrument had no price of its own on this session and was valued with a filled
          one. The figures of such a session are an estimate rather than a market result.""") boolean substitute) {
  }
}
