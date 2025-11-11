import {UDFData} from '../../lib/udfmeta/model/udf.metadata';
import {Security} from '../../entities/security';

export class UDFSecurityCallParam {
  constructor(public security: Security, public udfData: UDFData) {
  }
}
