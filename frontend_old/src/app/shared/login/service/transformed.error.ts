import {GetTransformedError} from './get.transformed.error';

export class TransformedError {
  public msgKey: string;
  public errorClass: GetTransformedError;
  public bringUpDialog: boolean;

  constructor(public msg: string = '', public interpolateParams: any = {}) {
  }

  isEmtpy(): boolean {
    return this.msg.trim().length === 0;
  }

  getMsgOrKey(): string {
    return this.isEmtpy() ? this.msgKey : this.msg;
  }

}
