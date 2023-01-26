import {AbstractControl, FormGroup, UntypedFormGroup, ValidationErrors, ValidatorFn, Validators} from '@angular/forms';

const isPresent = (obj: any): boolean => obj !== undefined && obj !== null;

/**
 * At least one input value of a FormGroup must be filled in.
 */
export const atLeastOneFieldValidator = (group: UntypedFormGroup): { [key: string]: any } => {
  let isAtLeastOne = false;
  if (group && group.controls) {
    for (const control in group.controls) {
      if (group.controls.hasOwnProperty(control) && group.controls[control].valid && group.controls[control].value) {
        isAtLeastOne = true;
        break;
      }
    }
  }
  return isAtLeastOne ? null : {required: true};
};

export const gtWithMask = (gt: number): ValidatorFn => (control: AbstractControl): ValidationErrors | null => {
  if (!isPresent(gt)) {
    return null;
  }
  if (isPresent(Validators.required(control))) {
    return null;
  }

  const v: number = (typeof control.value === 'string') ? +control.value.replace(/[^\d.-]/g, '') : control.value;
  return v > +gt ? null : {gt: {requiredGt: gt, actualValue: v}};
};

export const gteWithMask = (gte: number): ValidatorFn => (control: AbstractControl): ValidationErrors | null => {
  if (!isPresent(gte)) {
    return null;
  }
  if (isPresent(Validators.required(control))) {
    return null;
  }

  const v: number = (typeof control.value === 'string') ? +control.value.replace(/[^\d.-]/g, '') : control.value;
  return v >= +gte ? null : {gte: {requiredGt: gte, actualValue: v}};
};


export const gteDate = (minDate: Date): ValidatorFn => (control: AbstractControl): ValidationErrors | null => {
  if (!isPresent(minDate)) {
    return null;
  }

  if (isPresent(Validators.required(control))) {
    return null;
  }

  return control.value.getTime() >= minDate.getTime() ? null :
    {gteDate: {requiredGt: gteDate, actualValue: control.value}};
};


export const dateRange = (dateField1: string, dateField2: string, validatorField: string):
  ValidatorFn => (group: FormGroup): ValidationErrors | null => {
  const date1 = group.get(dateField1).value;
  const date2 = group.get(dateField2).value;
  if ((date1 !== null && date2 !== null) && date1 > date2) {
    return {dateRange: {requiredGt: dateRange, actualValue: group.get(validatorField).value}};
  }
  return null;
};


export const gteWithMaskIncludeNegative = (gte: number): ValidatorFn => (control: AbstractControl): ValidationErrors | null => {
  if (!isPresent(gte)) {
    return null;
  }
  if (isPresent(Validators.required(control))) {
    return null;
  }
  const v: number = (typeof control.value === 'string') ? +control.value.replace(/[^\d.-]/g, '') : control.value;
  return v >= +gte || v <= -gte ? null : {gteWithMaskIncludeNegative: {requiredGt: gte, actualValue: v}};
};

export const webUrl: ValidatorFn = (control: AbstractControl): { [key: string]: boolean } => {
  if (isPresent(Validators.required(control))) {
    return null;
  }
  const v: string = control.value;

  const webUrlRegex = new RegExp(
    '^' +
    // protocol identifier (optional)
    // short syntax // still required
    '(?:(?:(?:https?|ftp):)?\\/\\/)' +
    // user:pass BasicAuth (optional)
    '(?:\\S+(?::\\S*)?@)?' +
    '(?:' +
    // IP address exclusion
    // private & local networks
    '(?!(?:10|127)(?:\\.\\d{1,3}){3})' +
    '(?!(?:169\\.254|192\\.168)(?:\\.\\d{1,3}){2})' +
    '(?!172\\.(?:1[6-9]|2\\d|3[0-1])(?:\\.\\d{1,3}){2})' +
    // IP address dotted notation octets
    // excludes loopback network 0.0.0.0
    // excludes reserved space >= 224.0.0.0
    // excludes network & broacast addresses
    // (first & last IP address of each class)
    '(?:[1-9]\\d?|1\\d\\d|2[01]\\d|22[0-3])' +
    '(?:\\.(?:1?\\d{1,2}|2[0-4]\\d|25[0-5])){2}' +
    '(?:\\.(?:[1-9]\\d?|1\\d\\d|2[0-4]\\d|25[0-4]))' +
    '|' +
    // host & domain names, may end with dot
    // can be replaced by a shortest alternative
    // (?![-_])(?:[-\\w\\u00a1-\\uffff]{0,63}[^-_]\\.)+
    '(?:' +
    '(?:' +
    '[a-z0-9\\u00a1-\\uffff]' +
    '[a-z0-9\\u00a1-\\uffff_-]{0,62}' +
    ')?' +
    '[a-z0-9\\u00a1-\\uffff]\\.' +
    ')+' +
    // TLD identifier name, may end with dot
    '(?:[a-z\\u00a1-\\uffff]{2,}\\.?)' +
    ')' +
    // port number (optional)
    '(?::\\d{2,5})?' +
    // resource path (optional)
    '(?:[/?#]\\S*)?' +
    '$', 'i'
  );
  return webUrlRegex.test(v) ? null : {webUrl: true};
};


export const rangeLength = (rangeLengthParam: Array<number>): ValidatorFn => (control: AbstractControl): ValidationErrors | null => {
  if (!isPresent(rangeLength)) {
    return null;
  }
  if (isPresent(Validators.required(control))) {
    return null;
  }
  const v: string = control.value;
  return v.length >= rangeLengthParam[0] && v.length <= rangeLengthParam[1] ? null :
    {rangeLength: {param1: rangeLengthParam[0], param2: rangeLengthParam[1]}};
};


export const email: ValidatorFn = (control: AbstractControl): ValidationErrors => {
  if (isPresent(Validators.required(control))) {
    return null;
  }

  const v: string = control.value;
  /* eslint-disable */
  return /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/.test(v) ? null : {'email': true};
  /* eslint-enable */
};

export const maxValue = (value: number): ValidatorFn => (control: AbstractControl): ValidationErrors => {
  if (!isPresent(value)) {
    return null;
  }
  if (isPresent(Validators.required(control))) {
    return null;
  }

  const v: number = +control.value;
  return v <= +value ? null : {max: {value}};
};

export const equalTo = (equalControl: AbstractControl): ValidatorFn => {
  let subscribe = false;

  return (control: AbstractControl): ValidationErrors => {
    if (!subscribe) {
      subscribe = true;
      equalControl.valueChanges.subscribe(() => {
        control.updateValueAndValidity();
      });
    }

    const v: string = control.value;

    return equalControl.value === v ? null : {equalTo: {control: equalControl, value: equalControl.value}};
  };
};


export const range = (value: Array<number>): ValidatorFn => (control: AbstractControl): ValidationErrors => {
  if (!isPresent(value)) {
    return null;
  }
  if (isPresent(Validators.required(control))) {
    return null;
  }

  const v: number = +control.value;
  return v >= value[0] && v <= value[1] ? null : {range: {value}};
};


export const validISIN: ValidatorFn = (control: AbstractControl): { [key: string]: boolean } => {

  if (isPresent(Validators.required(control))) {
    return null;
  }

  const isin: string = control.value;
  const regex = new RegExp('^(AD|AE|AF|AG|AI|AL|AM|AO|AQ|AR|AS|AT|AU|AW|AX|AZ|BA|BB|BD|BE|BF|BG|BH|BI|BJ|BL|BM|BN|' +
    'BO|BQ|BR|BS|BT|BV|BW|BY|BZ|CA|CC|CD|CF|CG|CH|CI|CK|CL|CM|CN|CO|CR|CU|CV|CW|CX|CY|CZ|DE|DJ|DK|DM|DO|' +
    'DZ|EC|EE|EG|EH|ER|ES|ET|EU|FI|FJ|FK|FM|FO|FR|GA|GB|GD|GE|GF|GG|GH|GI|GL|GM|GN|GP|GQ|GR|GS|GT|GU|GW|GY|' +
    'HK|HM|HN|HR|HT|HU|ID|IE|IL|IM|IN|IO|IQ|IR|IS|IT|JE|JM|JO|JP|KE|KG|KH|KI|KM|KN|KP|KR|KW|KY|KZ|LA|LB|' +
    'LC|LI|LK|LR|LS|LT|LU|LV|LY|MA|MC|MD|ME|MF|MG|MH|MK|ML|MM|MN|MO|MP|MQ|MR|MS|MT|MU|MV|MW|MX|MY|MZ|NA|' +
    'NC|NE|NF|NG|NI|NL|NO|NP|NR|NU|NZ|OM|PA|PE|PF|PG|PH|PK|PL|PM|PN|PR|PS|PT|PW|PY|QA|RE|RO|RS|RU|RW|SA|' +
    'SB|SC|SD|SE|SG|SH|SI|SJ|SK|SL|SM|SN|SO|SR|SS|ST|SV|SX|SY|SZ|TC|TD|TF|TG|TH|TJ|TK|TL|TM|TN|TO|TR|TT|' +
    'TV|TW|TZ|UA|UG|UM|US|UY|UZ|VA|VC|VE|VG|VI|VN|VU|WF|WS|YE|XC|XS|YT|ZA|ZM|ZW)([0-9A-Z]{9})([0-9])$');
  const match = regex.exec(isin);
  // validate the check digit
  return match && match.length === 4 && (+match[3] === calcISINCheck(match[1] + match[2])) ? null
    : {validISIN: true};
};

/**
 * /**
 * Calculates a check digit for an isin
 *
 * @param code code an ISIN code with country code, but without check digit
 * @return number {Integer} The check digit for this code
 */

const calcISINCheck = (code): number => {

  let conv = '';
  let digits = '';
  let sd = 0;
  // convert letters
  for (let i = 0; i < code.length; i++) {
    const c = code.charCodeAt(i);
    conv += (c > 57) ? (c - 55).toString() : code[i];
  }
  // group by odd and even, multiply digits from group containing rightmost character by 2
  for (let i = 0; i < conv.length; i++) {
    digits += (parseInt(conv[i], 10) * ((i % 2) === (conv.length % 2 !== 0 ? 0 : 1) ? 2 : 1)).toString();
  }
  // sum all digits
  for (const digit of digits) {
    sd += parseInt(digit, 10);
  }
  // subtract mod 10 of the sum from 10, return mod 10 of result
  return (10 - (sd % 10)) % 10;
};



