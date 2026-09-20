package grafioschtrader.reportviews.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Read-only wire contracts of the dashboard cards that rank the instruments a tenant holds by how far they moved.
 *
 * <p>
 * A card shows the same set of instruments twice: once ordered by the move of the instrument itself, once by what that
 * move did to the portfolio. Both orderings therefore come from one row set and never disagree about a figure. Every
 * amount is in the tenant currency named on the payload.
 * </p>
 */
public final class HoldingMoversPayload {

  private HoldingMoversPayload() {
  }

  @Schema(description = """
      One card: the tenant currency every amount is expressed in, the number of rows each branch was asked for, and the
      branches themselves. Branch order is the display order.""")
  public record Movers(String currency, int topN, List<Branch> branches) {
  }

  @Schema(description = """
      One period of the card. A branch reports what it measured over, how many instruments it could rank, and how many it
      had to describe with a substitute or older price, so a short list is never mistaken for a quiet market.""")
  public record Branch(BranchType branch,
      @Schema(description = "Session the branch measures; null for the intraday branch, which has no closing date yet") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate date,
      @Schema(description = "Session the branch measures against") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate previousDate,
      @Schema(description = """
          Oldest intraday snapshot among the ranked rows, so the card can date what it shows. Null outside the intraday
          branch, where the closing dates already say it.""") @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND) LocalDateTime asOf,
      @Schema(description = "Instruments that could be ranked at all") int rankedCount,
      @Schema(description = "Ranked instruments valued with a price from another date than the branch names") int substituteCount,
      @Schema(description = "Held instruments left out because no usable price pair could be formed") int omittedCount,
      @Schema(description = "Held margin instruments, which this ranking does not cover") int marginCount,
      @Schema(description = """
          Translation key explaining why the branch is empty, null when it has something to rank. A branch can be empty
          because no market holds a session on the day it covers, which is an ordinary weekend or holiday and not a
          fault; the reader is told so rather than being left with a blank list.""") String reasonKey,
      List<Row> byPercentage, List<Row> byAmount) {
  }

  @Schema(description = """
      One instrument within a branch. The dates are the ones actually used, which differ from the branch dates when a
      market was shut or a quote had not arrived; a row that carries such a pair is telling the reader that its move
      spans more than the branch does.""")
  public record Row(Integer idSecuritycurrency, String name,
      @Schema(description = "Currency of the instrument, in which the two prices are quoted") String currency,
      @Schema(description = "Split adjusted units held over all security accounts; negative for a short position") double units,
      double price, double previousPrice,
      @Schema(description = "Move of the instrument itself, in percent") double changePercentage,
      @Schema(description = "What the move did to the portfolio, in the tenant currency") double amountMC,
      @Schema(description = "Date of the price used; equals the branch date unless a substitute was needed") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate usedDate,
      @Schema(description = "Date of the price compared against") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate usedPreviousDate,
      @Schema(description = """
          True when at least one of the two prices does not belong to the date the branch names, so the row covers a
          longer span than its branch.""") boolean substitute) {
  }

  /** The three periods a card offers. Their codes are part of the wire contract and are translated by the client. */
  public enum BranchType {
    /** Move since the previous close, from the last intraday snapshot the application happens to hold. */
    INTRADAY,
    /** Close-to-close move of the most recent completed session. */
    LAST_TRADING_DAY,
    /** Close-to-close move of a session the reader chooses; defaults to the one before the last. */
    CHOSEN_DATE
  }
}
