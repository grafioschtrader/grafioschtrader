package grafioschtrader.service;

import java.util.List;

import grafiosch.common.DataHelper;
import grafioschtrader.dto.SpinOffPreviewDto.Account;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.Transaction;
import grafioschtrader.types.SecurityActionRole;
import grafioschtrader.types.TransactionType;

/** Builds the three cash-neutral security bookings from one account preview. */
final class SpinOffLegs {

  record Rate(Integer pairId, double multiplier) {
  }

  record Amounts(double sale, double repurchase, double child) {
  }

  private SpinOffLegs() {
  }

  static double theoreticalParentClose(double parentClose, double childClose, double ratio, double fxChildToParent) {
    return parentClose + ratio * childClose * fxChildToParent;
  }

  static Amounts amounts(double parentUnits, double parentClose, double salePrice, double parentRate, int precision) {
    double sale = DataHelper.round(parentUnits * salePrice * parentRate, precision);
    double repurchase = DataHelper.round(-parentUnits * parentClose * parentRate, precision);
    return new Amounts(sale, repurchase, -(sale + repurchase));
  }

  static List<Transaction> book(SecurityAction action, Integer tenant, Integer application, Cashaccount cash,
      Account account, double parentClose, double childClose, double childToParent, Rate parentRate, Rate childRate,
      int precision) {
    double ratio = (double) action.getToFactor() / action.getFromFactor();
    Transaction sale = leg(action, tenant, application, cash, account, action.getSecurityOld(), account.parentUnits(),
        theoreticalParentClose(parentClose, childClose, ratio, childToParent), account.saleAmount(), parentRate,
        TransactionType.REDUCE, SecurityActionRole.SPIN_OFF_SALE);
    Transaction repurchase = leg(action, tenant, application, cash, account, action.getSecurityOld(),
        account.parentUnits(), parentClose, account.repurchaseAmount(), parentRate, TransactionType.ACCUMULATE,
        SecurityActionRole.SPIN_OFF_REPURCHASE);
    Transaction child = leg(action, tenant, application, cash, account, action.getSecurityNew(), account.childUnits(),
        childClose, account.childAmount(), childRate, TransactionType.ACCUMULATE, SecurityActionRole.SPIN_OFF_CHILD);
    double diff = DataHelper.round(account.childAmount()
        - DataHelper.round(-account.childUnits() * childClose * childRate.multiplier(), precision), precision);
    child.setCashaccountRoundingDiff(diff == 0 ? null : diff);
    return List.of(sale, repurchase, child);
  }

  private static Transaction leg(SecurityAction action, Integer tenant, Integer application, Cashaccount cash,
      Account account, Security security, double units, double quote, double amount, Rate rate, TransactionType type,
      SecurityActionRole role) {
    Transaction transaction = new Transaction(account.idSecurityaccount(), cash, security, amount, units, quote, type,
        null, null, null, action.getActionDate().atStartOfDay(), rate.pairId() == null ? null : rate.multiplier(),
        rate.pairId(), null, null);
    transaction.setIdTenant(tenant);
    transaction.setIdSecurityActionApp(application);
    transaction.setSecurityActionRole(role);
    transaction.setNote("System-Created");
    transaction.setSkipClosedUntilCheck(true);
    return transaction;
  }
}
