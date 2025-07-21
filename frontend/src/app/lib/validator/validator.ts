import {AbstractControl, FormGroup, UntypedFormGroup, ValidationErrors, ValidatorFn, Validators} from '@angular/forms';
import {UDFDataType} from '../../shared/udfmeta/model/udf.metadata';
import {GlobalSessionNames} from '../../shared/global.session.names';
import {UDFConfig} from '../../shared/login/component/login.component';

export const isPresent = (obj: any): boolean => obj !== undefined && obj !== null;

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
  if (!isPresent(gt) || isPresent(Validators.required(control))) {
    return null;
  }
  const v: number = (typeof control.value === 'string') ? +control.value.replace(/[^\d.-]/g, '') : control.value;
  return v > +gt ? null : {gt: {requiredGt: gt, actualValue: v}};
};

export const gteWithMask = (gte: number): ValidatorFn => (control: AbstractControl): ValidationErrors | null => {
  if (!isPresent(gte) || isPresent(Validators.required(control))) {
    return null;
  }
  const v: number = (typeof control.value === 'string') ? +control.value.replace(/[^\d.-]/g, '') : control.value;
  return v >= +gte ? null : {gte: {requiredGt: gte, actualValue: v}};
};

export const gteDate = (minDate: Date): ValidatorFn => (control: AbstractControl): ValidationErrors | null => {
  if (!isPresent(minDate) || isPresent(Validators.required(control))) {
    return null;
  }
  return control.value.getTime() >= minDate.getTime() ? null :
    {gteDate: {requiredGt: gteDate, actualValue: control.value}};
};

export const dateRange = (dateField1: string, dateField2: string, validatorField: string):
  ValidatorFn => (group: FormGroup): ValidationErrors | null => {
  const date1 = group.get(dateField1).value;
  const date2 = group.get(dateField2).value;
  return (date1 !== null && date2 !== null) && date1 > date2 ?
    {dateRange: {requiredGt: dateRange, actualValue: group.get(validatorField).value}} : null;
};

export const dataTypeFieldSizeGroup = (dataField1: string, dataField2: string, validatorField: string):
  ValidatorFn => (group: FormGroup): ValidationErrors | null => {
  const dataType: string = group.get(dataField1).value;
  const fieldSize: string = group.get(dataField2).value;
  let hasError = false;
  if (dataType && fieldSize) {
    const udfType = UDFDataType[dataType];
    if (udfType == UDFDataType.UDF_Numeric || udfType == UDFDataType.UDF_NumericInteger
      || udfType == UDFDataType.UDF_String) {
      const prefixSuffixStr: string[] = fieldSize.replaceAll(' ', '').split(',');
      if (prefixSuffixStr.length == 2) {
        const prefixSuffix: number[] = prefixSuffixStr.map(Number).filter(Number);
        if (prefixSuffix.length == 2) {
          const udfConfig: UDFConfig = JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_CONFIG));
          const ups = udfConfig.uDFPrefixSuffixMap[dataType];
          switch (udfType) {
            case UDFDataType.UDF_Numeric:
              hasError = prefixSuffix[0] > ups.prefix || prefixSuffix[1] > ups.suffix || prefixSuffix[0] <= prefixSuffix[1]
                || prefixSuffix[1] < ups.together;
              break;
            default:
              hasError = prefixSuffix[0] < ups.prefix || prefixSuffix[1] > ups.suffix || prefixSuffix[0] > prefixSuffix[1];
              break;
          }
        } else {
          hasError = true;
        }
      } else {
        hasError = true;
      }
    }
  }

  return hasError ? {dataTypeFieldSizeGroup: true} : null;
}

export const gteWithMaskIncludeNegative = (gte: number): ValidatorFn => (control: AbstractControl): ValidationErrors | null => {
  if (!isPresent(gte) || isPresent(Validators.required(control))) {
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

/**
 * The length of the input string must be between a minimum and maximum length.
 * @param rangeLengthParam The minimum and maximum length stored in the array
 */
export const rangeLength = (rangeLengthParam: Array<number>): ValidatorFn => (control: AbstractControl): ValidationErrors | null => {
  if (!isPresent(rangeLength) || isPresent(Validators.required(control))) {
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
  if (!isPresent(value) || isPresent(Validators.required(control))) {
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


export const notContainStringInList = (strArray: Array<string>): ValidatorFn => (control: AbstractControl): ValidationErrors => {
  if (!isPresent(strArray) || isPresent(Validators.required(control))) {
    return null;
  }
  return strArray.indexOf(control.value) > -1 ? {notContainStringInList: true} : null;
};

export const range = (value: Array<number>): ValidatorFn => (control: AbstractControl): ValidationErrors => {
  if (!isPresent(value) || isPresent(Validators.required(control))) {
    return null;
  }
  const v: number = +control.value;
  return v >= value[0] && v <= value[1] ? null : {range: {value}};
};






