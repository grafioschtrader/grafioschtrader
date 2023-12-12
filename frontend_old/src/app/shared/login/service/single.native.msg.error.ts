import {GetTransformedError} from './get.transformed.error';
import {TransformedError} from './transformed.error';

export class SingleNativeMsgError implements GetTransformedError {
  public message: string;

  getTransformedError(): TransformedError {
    const transformedError = new TransformedError();
    transformedError.errorClass = this;
    transformedError.msg = this.message;
    return transformedError;
  }
}
