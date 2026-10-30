import { TransformedError } from './transformed.error';
import { GetTransformedError } from './get.transformed.error';

/** Field validation errors of the backend; the error body fills the fields. */
export class ValidationError implements GetTransformedError {
  public fieldErrors?: FieldError[];

  getTransformedError(): TransformedError {
    const transformedError = new TransformedError();
    transformedError.errorClass = this;
    this.fieldErrors?.forEach(
      (fieldError) => (transformedError.msg += `<b>${fieldError.field}</b>: ` + `${fieldError.message}</br>`)
    );
    return transformedError;
  }
}

/** One rejected field with the message of the server. */
export interface FieldError {
  field: string;
  message: string;
}
