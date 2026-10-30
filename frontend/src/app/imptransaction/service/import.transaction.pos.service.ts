import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { ImportTransactionPos } from '../../entities/import.transaction.pos';
import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { CombineTemplateAndImpTransPos } from '../../securityaccount/component/combine.template.and.imp.trans.pos';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class ImportTransactionPosService
  extends AuthServiceWithLogout<ImportTransactionPos>
  implements DeleteService, ServiceEntityUpdate<ImportTransactionPos>
{
  getCombineTemplateAndImpTransPosListByTransactionHead(
    idTransactionHead: number
  ): Observable<CombineTemplateAndImpTransPos[]> {
    return this.httpClient
      .get<CombineTemplateAndImpTransPos[]>(
        `${BaseSettings.API_ENDPOINT}` +
          `${AppSettings.IMPORT_TRANSACTION_POS_KEY}/${AppSettings.IMPORT_TRANSACTION_HEAD_KEY}/${idTransactionHead}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idTransactionImportPos: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_POS_KEY}/${idTransactionImportPos}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public adjustQuotation(idTransactionPosList: number[]): Observable<any> {
    return this.httpClient
      .post(
        `${BaseSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_POS_KEY}/adjustquotation`,
        idTransactionPosList,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public adjustCurrencyExRateOrQuotation(idTransactionPosList: number[]): Observable<any> {
    return this.httpClient
      .post(
        `${BaseSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_POS_KEY}/adjustcurrencyexrateorquotation`,
        idTransactionPosList,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public acceptTotalDiff(idTransactionPosList: number[]): Observable<any> {
    return this.httpClient
      .post(
        `${BaseSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_POS_KEY}/accepttotaldiff`,
        idTransactionPosList,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public deleteMultiple(idTransactionPosList: number[]): Observable<any> {
    return this.httpClient
      .post(
        `${BaseSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_POS_KEY}/deletes`,
        idTransactionPosList,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public createAndSaveTransactions(idTransactionPosList: number[]): Observable<any> {
    return this.httpClient
      .post(
        `${BaseSettings.API_ENDPOINT}${AppSettings.IMPORT_TRANSACTION_POS_KEY}/createtransaction`,
        idTransactionPosList,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(importTransactionPos: ImportTransactionPos): Observable<ImportTransactionPos> {
    return this.updateEntity(
      importTransactionPos,
      importTransactionPos.idTransactionPos,
      AppSettings.IMPORT_TRANSACTION_POS_KEY
    );
  }

  setSecurity(idSecuritycurrency: number, idTransactionPosList: number[]): Observable<ImportTransactionPos[]> {
    return this.httpClient
      .put<ImportTransactionPos[]>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.IMPORT_TRANSACTION_POS_KEY}/setsecurity`,
        { idSecuritycurrency, idTransactionPosList },
        { headers: this.prepareHeaders() }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  setCashAccount(idSecuritycashAccount: number, idTransactionPosList: number[]): Observable<ImportTransactionPos[]> {
    return this.httpClient
      .put<ImportTransactionPos[]>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.IMPORT_TRANSACTION_POS_KEY}/setcashaccount`,
        { idSecuritycashAccount, idTransactionPosList },
        { headers: this.prepareHeaders() }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  setIdTransactionMayBe(
    idTransactionMayBe: number | null,
    idTransactionPosList: number[]
  ): Observable<ImportTransactionPos[]> {
    return this.httpClient
      .patch<ImportTransactionPos[]>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.IMPORT_TRANSACTION_POS_KEY}/setidtransactionmaybe`,
        { idTransactionMayBe, idTransactionPosList },
        { headers: this.prepareHeaders() }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
