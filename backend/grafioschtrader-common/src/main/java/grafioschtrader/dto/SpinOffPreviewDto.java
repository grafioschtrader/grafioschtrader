package grafioschtrader.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Proposed spin-off bookings for the current tenant")
public record SpinOffPreviewDto(@JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate actionDate,
    @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate childPriceDate, double parentClose,
    String parentCurrency, double childClose, String childCurrency, double fxChildToParent, List<Account> accounts) {

  public enum Decision {
    BOOK, SKIP
  }

  public record Account(Integer idSecurityaccount, String accountName, String cashCurrency, double parentUnits,
      double childUnits, double fraction, double fxParentToCash, double fxChildToCash, double saleAmount,
      double repurchaseAmount, double childAmount, List<Candidate> candidates, Decision decision) {
  }

  public record Candidate(Integer idTransaction,
      @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_ISO) LocalDateTime transactionTime, boolean child,
      TransactionType type, Double units, Double quotation) {
  }
}
