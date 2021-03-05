export class RuleEvent {
  public static readonly TOUCHED = 'touched';
  public static readonly DIRTY = 'dirty';
  public static readonly FOCUSOUT = 'focusout';
}

export interface ErrorMessageRules {

  /** Name of validator  */
  name: string;
  /** Must match with the key in the translation file */
  keyi18n: string;
  param1?: string | number;
  param2?: string | number;
  rules: string[];
  /** Will be set from the translation service */
  text?: string;
}
