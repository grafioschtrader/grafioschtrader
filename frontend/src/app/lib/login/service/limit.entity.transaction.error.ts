import { GetTransformedError } from './get.transformed.error';
import { TransformedError } from './transformed.error';

/** The daily limit of changes on an entity is reached; the error body fills the fields. */
export class LimitEntityTransactionError implements GetTransformedError {
  public entity?: string;
  public limit?: number;
  public transactionsCount?: number;

  getTransformedError(): TransformedError {
    const transformedError = new TransformedError();
    transformedError.errorClass = this;
    transformedError.msgKey = 'transactionDayLimit';
    transformedError.interpolateParams['limit'] = this.limit;
    transformedError.interpolateParams['i18nEntity'] = this.entity;

    return transformedError;
  }
}
