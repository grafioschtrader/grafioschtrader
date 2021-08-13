import {BaseLocale} from './base.locale';

export interface FormConfig extends BaseLocale {
  labelcolumns: number;
  nonModal?: boolean;
  helpLinkFN?: Function;
  fieldHeaders?: { [fieldName: string]: String };
}
