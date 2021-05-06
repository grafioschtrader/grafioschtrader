import {Portfolio} from '../../entities/portfolio';
import {Cashaccount} from '../../entities/cashaccount';
import {AppHelper} from '../../shared/helper/app.helper';
import {TranslateService} from '@ngx-translate/core';
import {AppSettings} from '../../shared/app.settings';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';

export abstract class TransactionBaseOperations {

  constructor(public translateService: TranslateService, protected gps: GlobalparameterService) {
  }

  abstract isVisibleDialog(): boolean;

  getCashaccountByIdCashaccountFormPortfolios(portfolios: Portfolio[], idSecuritycashaccount: number): {
    cashaccount: Cashaccount,
    portfolio: Portfolio
  } {
    if (idSecuritycashaccount != null) {
      for (const portfolio of portfolios) {
        const found: { cashaccount: Cashaccount, portfolio: Portfolio } = this.getCashaccountByIdCashaccountFromPortfolio(portfolio,
          idSecuritycashaccount);
        if (found) {
          return found;
        }
      }
    }
    return null;
  }

  getCashaccountByIdCashaccountFromPortfolios(portfolios: Portfolio[], idSecuritycashaccount: number): {
    cashaccount: Cashaccount, portfolio: Portfolio
  } {
    for (const portfolio of portfolios) {
      const found = this.getCashaccountByIdCashaccountFromPortfolio(portfolio, idSecuritycashaccount);
      if (found) {
        return found;
      }
    }
    return null;
  }



  getCashaccountByIdCashaccountFromPortfolio(portfolio: Portfolio, idSecuritycashaccount: number): {
    cashaccount: Cashaccount, portfolio: Portfolio
  } {
    for (const cashaccount of portfolio.cashaccountList) {
      if (cashaccount.idSecuritycashAccount === idSecuritycashaccount) {
        return {cashaccount: cashaccount, portfolio: portfolio};
      }
    }
    return null;
  }




}

