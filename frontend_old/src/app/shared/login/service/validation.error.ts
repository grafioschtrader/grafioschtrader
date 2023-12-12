import {TransformedError} from './transformed.error';
import {GetTransformedError} from './get.transformed.error';

export class ValidationError implements GetTransformedError {
  public fieldErrors: FieldError[];

  getTransformedError(): TransformedError {
    const transformedError = new TransformedError();
    transformedError.errorClass = this;
    this.fieldErrors.forEach(fieldError => transformedError.msg += `<b>${fieldError.field}</b>: ` +
      `${fieldError.message}</br>`);
    return transformedError;
  }
}

export class FieldError {
  public field: string;
  public message: string;
}
