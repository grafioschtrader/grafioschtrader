import { BaseLocale } from './base.locale';

export interface FormConfig extends BaseLocale {
  labelColumns: number;
  nonModal?: boolean;
  helpLinkFN?: (() => void) | null;
  fieldHeaders?: { [fieldName: string]: string | null };
}
