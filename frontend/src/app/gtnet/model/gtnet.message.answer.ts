import {BaseID} from '../../entities/base.id';
import {GTNetMessageCodeType} from './gtnet.message';

export class GTNetMessageAnswer implements BaseID {
  requestMsgCode: GTNetMessageCodeType | number = null;

  getId(): number {
    return this.requestMsgCode;
  }
}
