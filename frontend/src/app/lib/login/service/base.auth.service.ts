import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, Observable, throwError } from 'rxjs';
import { catchError, map, switchMap, take } from 'rxjs/operators';
import { BaseSettings } from '../../base.settings';
import { InfoLevelType } from '../../message/info.leve.type';
import { MessageToastService } from '../../message/message.toast.service';
import { BaseService } from './base.service';
import { ErrorWrapper } from './error.wrapper';
import { GetTransformedError } from './get.transformed.error';
import { LimitEntityTransactionError } from './limit.entity.transaction.error';
import { LIMIT_REQUEST_OPENER } from './limit.request.opener';
import { SingleNativeMsgError } from './single.native.msg.error';
import { TransformedError } from './transformed.error';
import { ValidationError } from './validation.error';

export abstract class BaseAuthService<T> extends BaseService {
  /** Observable reads already report their transformed errors before an rxResource receives them. */
  private static readonly reportedErrors = new WeakSet<object>();
  /** True while the dialog for applying for a higher daily limit is open or about to open. */
  private static limitRequestOpen = false;
  private readonly limitRequestOpener = inject(LIMIT_REQUEST_OPENER, { optional: true });
  protected httpClient: HttpClient = inject(HttpClient);
  protected messageToastService: MessageToastService = inject(MessageToastService);

  private static readonly classNameClassMap: Record<string, new () => GetTransformedError> = {
    LimitEntityTransactionError,
    SingleNativeMsgError,
    ValidationError
  };

  /**
   * Creates a POST request for a new entity or a PUT request for an existing entity.
   *
   * @param entity Entity to be saved or created
   * @param id Id of an existing entity, or null/undefined for a new entity
   * @param pathApi Resource path.
   */
  updateEntity(entity: T, id: number | string | null | undefined, pathApi: string): Observable<T> {
    if (id != null) {
      return this.httpClient
        .put<T>(`${BaseSettings.API_ENDPOINT}${pathApi}`, entity, { headers: this.prepareHeaders() })
        .pipe(catchError(this.handleError.bind(this)));
    } else {
      return this.httpClient
        .post<T>(`${BaseSettings.API_ENDPOINT}${pathApi}`, entity, { headers: this.prepareHeaders() })
        .pipe(catchError(this.handleError.bind(this)));
    }
  }

  /**
   * Posts a request that is answered with a PDF document. A failed request carries its error as a Blob; the body is
   * read back as JSON so that it is shown by the same translated error toast as any other failed request. An answer
   * that is not a PDF is a failure as well.
   *
   * @param pathApi Resource path.
   * @param body The request body, sent as JSON.
   * @param invalidDownloadKey Translation key of the error shown when the answer is not a PDF.
   * @returns The full response; its body holds the PDF and its headers the file name.
   */
  protected postForPdf(pathApi: string, body: unknown, invalidDownloadKey: string): Observable<HttpResponse<Blob>> {
    return this.httpClient
      .post(`${BaseSettings.API_ENDPOINT}${pathApi}`, body, {
        headers: this.prepareHeaders(),
        observe: 'response',
        responseType: 'blob'
      })
      .pipe(
        map((response) => {
          if (
            response.status !== 200 ||
            response.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase() !== 'application/pdf'
          ) {
            throw new HttpErrorResponse({ status: response.status, error: invalidDownloadKey });
          }
          return response;
        }),
        catchError((error) => {
          if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
            return from(error.error.text()).pipe(
              switchMap((text) => {
                let parsed: unknown;
                try {
                  parsed = JSON.parse(text);
                } catch {
                  parsed = { error: { message: text } };
                }
                return this.handleError(
                  new HttpErrorResponse({
                    error: parsed,
                    status: error.status,
                    statusText: error.statusText,
                    headers: error.headers
                  })
                );
              })
            );
          }
          return this.handleError(error);
        })
      ) as Observable<HttpResponse<Blob>>;
  }

  /** Re-throws through the error channel without adding a value to a request's result type. */
  protected handleError(error: HttpErrorResponse | any): Observable<never> {
    const transformedError = this.reportError(error);
    BaseAuthService.reportedErrors.add(transformedError);
    return throwError(() => transformedError);
  }

  /**
   * Reports the failure of an `httpResource` read exactly as a failed observable request: the same toast, and a logout
   * for `ErrorWithLogout` / `ErrorWithLogoutAdmin`. The caller passes each failed request once, normally through
   * `reportResourceErrors`, because every call shows a toast.
   *
   * @param error the value of the resource's `error()` signal, typically an `HttpErrorResponse`
   */
  handleResourceError(error: HttpErrorResponse | any): void {
    // Angular wraps non-Error stream failures and preserves the original TransformedError as its cause.
    const original = error instanceof Error && error.cause instanceof TransformedError ? error.cause : error;
    if (BaseAuthService.reportedErrors.has(original)) return;
    this.reportError(error);
  }

  /**
   * Maps a failed request to a TransformedError, shows it as an error toast and logs out when the server demands it.
   *
   * @param error the error of the request, typically an `HttpErrorResponse`
   * @returns the transformed error the toast was built from
   */
  private reportError(error: HttpErrorResponse | any): TransformedError {
    let transformedError: TransformedError = new TransformedError();
    if (error instanceof HttpErrorResponse) {
      const body = error.error;

      if (typeof error.error === 'string') {
        transformedError.msgKey = error.error;
      } else {
        transformedError = this.getMessageFromClass(error.error);

        if (transformedError.isEmtpy()) {
          // Message which comes translated from the server. A body-less failure - for example a 500 written by the
          // dev server proxy when it could not reach the backend - leaves nothing but status and status text.
          const err = body?.error?.message || (body == null ? '' : JSON.stringify(body));
          transformedError.msg = `${error.status} - ${error.statusText || ''} ${err}`.trimEnd();
        }
      }
    } else {
      transformedError.msg = error.message ? error.message : error.toString();
      transformedError.msg = transformedError.msg.replace(/(\r\n|\n|\r)/gm, '');
    }

    if (transformedError.msgKey) {
      // The message needs a translation
      this.messageToastService.showMessageI18n(
        InfoLevelType.ERROR,
        transformedError.msgKey,
        transformedError.interpolateParams
      );
    } else {
      this.messageToastService.showMessage(InfoLevelType.ERROR, transformedError.msg);
    }
    this.offerLimitIncrease(transformedError);
    return transformedError;
  }

  /**
   * Offers the user to apply for a higher daily limit when a request was refused because the daily limit of changes on
   * an entity was reached. It is done here, where every request error is reported, because the limit is checked at
   * many write sites - saving a dialog, a direct import, a rollback - and only the first of them reports through an edit
   * dialog. A second dialog is not opened while the first one is still open.
   *
   * @param transformedError the error the toast was built from
   */
  private offerLimitIncrease(transformedError: TransformedError): void {
    const errorClass = transformedError.errorClass;
    if (
      !this.limitRequestOpener ||
      !(errorClass instanceof LimitEntityTransactionError) ||
      !errorClass.entity ||
      BaseAuthService.limitRequestOpen
    ) {
      return;
    }
    BaseAuthService.limitRequestOpen = true;
    let opened = false;
    this.limitRequestOpener(errorClass.entity).subscribe({
      next: (ref) => {
        opened = true;
        ref.onClose.pipe(take(1)).subscribe(() => (BaseAuthService.limitRequestOpen = false));
      },
      complete: () => {
        if (!opened) BaseAuthService.limitRequestOpen = false;
      },
      error: () => (BaseAuthService.limitRequestOpen = false)
    });
  }

  /**
   * Maps the error body of a failed request to a TransformedError. The body may be missing entirely - an error
   * response without a payload, such as the body-less 500 the dev server proxy writes when the backend connection
   * breaks - in which case an empty TransformedError is returned and the caller falls back to status and status text.
   *
   * @param errorWrapper the parsed error body, may be null
   * @returns the transformed error, empty when the body carries no known error class
   */
  protected getMessageFromClass(errorWrapper: ErrorWrapper): TransformedError {
    let transformedError = new TransformedError('');
    if (errorWrapper == null) {
      return transformedError;
    }

    if (BaseAuthService.classNameClassMap[errorWrapper.className] != null) {
      BaseAuthService.classNameClassMap[errorWrapper.className];
      const getTransformedError: GetTransformedError = Object.assign(
        new BaseAuthService.classNameClassMap[errorWrapper.className](),
        errorWrapper.error
      );
      transformedError = getTransformedError.getTransformedError();
    } else if (errorWrapper.className === 'ErrorWithLogoutAdmin') {
      transformedError.bringUpAdminSelfReleaseDialog = true;
      this.toLogout();
    } else if (errorWrapper.className === 'ErrorWithLogout') {
      transformedError.bringUpDialog = true;
      this.toLogout();
    }
    return transformedError;
  }

  protected toLogout(): void {}
}
