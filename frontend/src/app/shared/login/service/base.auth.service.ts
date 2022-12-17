import {MessageToastService} from '../../message/message.toast.service';
import {Observable, throwError} from 'rxjs';
import {AppSettings} from '../../app.settings';
import {ValidationError} from './validation.error';
import {SingleNativeMsgError} from './single.native.msg.error';
import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {InfoLevelType} from '../../message/info.leve.type';
import {catchError} from 'rxjs/operators';
import {ErrorWrapper} from './error.wrapper';
import {LimitEntityTransactionError} from './limit.entity.transaction.error';
import {TransformedError} from './transformed.error';
import {GetTransformedError} from './get.transformed.error';
import {BaseService} from './base.service';


export abstract class BaseAuthService<T> extends BaseService {

  private static readonly classNameClassMap = {
    LimitEntityTransactionError,
    SingleNativeMsgError,
    ValidationError
  };

  constructor(protected httpClient: HttpClient, protected messageToastService: MessageToastService) {
    super();
  }

  /**
   * Creates a create/put or update/post request depending on the id of the entity.
   *
   * @param entity Entity to be saved or created
   * @param id Id of existing entity or null
   * @param pathApi Resource path.
   */
  updateEntity(entity: T, id: number | string, pathApi: string): Observable<T> {
    if (id != null) {
      return <Observable<T>>this.httpClient.put(`${AppSettings.API_ENDPOINT}${pathApi}`, entity,
        {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
    } else {
      return <Observable<T>>this.httpClient.post(`${AppSettings.API_ENDPOINT}${pathApi}`, entity,
        {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
    }
  }

  protected handleError(error: HttpErrorResponse | any): Observable<TransformedError> {
    let transformedError: TransformedError = new TransformedError();
    if (error instanceof HttpErrorResponse) {
      const body = error.error;

      if (typeof (error.error) === 'string') {
        transformedError.msgKey = error.error;
      } else {
        transformedError = this.getMessageFromClass(error.error);

        if (transformedError.isEmtpy()) {
          // Message which comes translated from the server
          const err = body.error.message || JSON.stringify(body);
          transformedError.msg = `${error.status} - ${error.statusText || ''} ${err}`;
        }
      }
    } else {
      transformedError.msg = error.message ? error.message : error.toString();
      transformedError.msg = transformedError.msg.replace(/(\r\n|\n|\r)/gm, '');
    }

    if (transformedError.msgKey) {
      // The message needs a translation
      this.messageToastService.showMessageI18n(InfoLevelType.ERROR, transformedError.msgKey, transformedError.interpolateParams);
    } else {
      this.messageToastService.showMessage(InfoLevelType.ERROR, transformedError.msg);
    }

    return throwError(transformedError);
  }


  protected getMessageFromClass(errorWrapper: ErrorWrapper): TransformedError {
    let transformedError = new TransformedError('');

    if (BaseAuthService.classNameClassMap[errorWrapper.className] != null) {
      BaseAuthService.classNameClassMap[errorWrapper.className];
      const getTransformedError: GetTransformedError = Object.assign(new BaseAuthService.classNameClassMap[errorWrapper.className](),
        errorWrapper.error);
      transformedError = getTransformedError.getTransformedError();
    } else if (errorWrapper.className === 'ErrorWithLogout') {
      transformedError.bringUpDialog = true;
      this.toLogout();
    }
    return transformedError;
  }

  protected toLogout(): void {

  }
}
