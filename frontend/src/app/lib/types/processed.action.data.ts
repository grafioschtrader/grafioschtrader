import {ProcessedAction} from './processed.action';
import {TransformedError} from '../../shared/login/service/transformed.error';

export class ProcessedActionData {
  constructor(public action: ProcessedAction, public data?: any, public transformedError?: TransformedError) {
  }

}
