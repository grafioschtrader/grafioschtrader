import * as moment from 'moment';
import {combineLatest} from 'rxjs';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../service/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {Helper} from '../../helper/helper';
import {ColumnConfig} from '../datashowbase/column.config';
import {ParamMap} from '@angular/router';
import {FormConfig} from '../../dynamic-form/models/form.config';
import {HttpHeaders, HttpParams} from '@angular/common/http';
import {GlobalSessionNames} from '../global.session.names';
import {ConfirmationService} from 'primeng/api';
import {FileSystemFileEntry, NgxFileDropEntry} from 'ngx-file-drop';
import {InfoLevelType} from '../message/info.leve.type';
import {MessageToastService} from '../message/message.toast.service';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {AppSettings} from '../app.settings';

export const enum Comparison { GT, LT, EQ }

type CompareFunc<T, S> = (a: T, b: S) => Comparison;

export class AppHelper {

  static readonly fieldToLabelRegex = new RegExp('^(rand|year|group)|(DiffMC|MC)$', 'g');

  static getUntilDateBySessionStorage(): Date {
    return AppHelper.getDateFromSessionStorage(GlobalSessionNames.REPORT_UNTIL_DATE, new Date());
  }

  static getDateFromSessionStorage(property: string, defaultDate = new Date()): Date {
    const date = sessionStorage.getItem(property);
    return date ? moment(date).toDate() : defaultDate;
  }

  static saveUntilDateInSessionStorage(untilDate: Date): void {
    AppHelper.saveDateToSessionStore(GlobalSessionNames.REPORT_UNTIL_DATE, untilDate);
  }

  static addSpaceToCurrency(currency: string): string {
    return currency + ' ';
  }

  static saveDateToSessionStore(property: string, date: Date) {
    sessionStorage.setItem(property, moment(date).format(AppSettings.FORMAT_DATE_SHORT_NATIVE));
  }

  static getOptionsWithIncludeClosedPositionAndUntilDate(includeClosedPosition: boolean, untilDate: Date, httpHeaders: HttpHeaders) {
    const headerParam = AppHelper.getOptionsWithUntilDate(untilDate, httpHeaders);
    headerParam.params = headerParam.params.append('includeClosedPosition', includeClosedPosition.toString());
    return headerParam;
  }

  static getOptionsWithUntilDate(untilDate: Date, httpHeaders: HttpHeaders) {
    const httpParams = new HttpParams()
      .set('untilDate', moment(untilDate).format(AppSettings.FORMAT_DATE_SHORT_NATIVE));
    return {headers: httpHeaders, params: httpParams};
  }

  static transformDataToRealClassDataList<T>(type: new() => T, data: T): T {
    const instance = new type();
    return (Object.assign(instance, data));
  }

  public static createParamObjectFromParamMap(paramMap: ParamMap): any {
    const paramObject = {};
    paramMap.keys.forEach(key => paramObject[key] = JSON.parse(paramMap.get(key)));
    return paramObject;
  }

  /**
   * Returns 'ABC_DEF_GH' from 'abcDefGh', and TRANSACTION_GAIN_LOSS_MC from 'transactionGainLossMC'
   */
  public static convertPropertyNameToUppercase(upperLower: string): string {
    let startPoint = upperLower.indexOf('.');
    startPoint = startPoint < 0 ? 0 : startPoint + 1;
    return upperLower.substring(startPoint).replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase();
  }

  public static convertPropertyForLabelOrHeaderKey(upperLower: string): string {
    return this.convertPropertyNameToUppercase(upperLower.replace(this.fieldToLabelRegex, ''));
  }

  public static invisibleAndHide(fieldConfig: FieldConfig, hide: boolean): void {
    if (hide) {
      AppHelper.disableAndHideInput(fieldConfig);
    } else {
      AppHelper.enableAndVisibleInput(fieldConfig);
    }
  }

  public static enableAndVisibleInput(fieldConfig: FieldConfig): void {
    fieldConfig.invisible = false;
    fieldConfig.formControl.enable();
  }

  public static disableAndHideInput(fieldConfig: FieldConfig): void {
    fieldConfig.formControl.disable();
    fieldConfig.invisible = true;
  }

  public static truncateString(str: string, length: number, useWordBoundary: boolean): string {
    if (str.length <= length) {
      return str;
    } else {
      const subString = str.substr(0, length - 1);
      return (useWordBoundary
        ? subString.substr(0, subString.lastIndexOf(' '))
        : subString) + '...';
    }
  }

  public static getValueByPathWithField(gps: GlobalparameterService, translateService: TranslateService,
                                        dataobject: any, field: ColumnConfig, valueField: string) {
    dataobject = Helper.getValueByPath(dataobject, valueField);
    if (dataobject || field.dataType === DataType.NumericShowZero && dataobject === 0) {

      switch (field.dataType) {
        case DataType.NumericInteger:
          return AppHelper.numberIntegerFormat(gps, dataobject);
        case DataType.Numeric:
        case DataType.NumericShowZero:
          return AppHelper.numberFormat(gps, dataobject, field.maxFractionDigits, field.minFractionDigits);
        case DataType.NumericRaw:
          return gps.getNumberFormatRaw().format(dataobject);
        case DataType.DateNumeric:
        case DataType.DateString:
          return this.getDateByFormat(gps, dataobject);
        case DataType.DateTimeNumeric:
          return moment(+dataobject).format(gps.getTimeDateFormatForTable());
        case DataType.DateTimeString:
          return moment(dataobject).format(gps.getTimeDateFormatForTable());
        case DataType.DateTimeSecondString:
          return moment.utc(dataobject).local().format(gps.getTimeSecondDateFormatForTable());
        default:
          return dataobject;
      }
    }
  }


  public static getDateByFormat(gps: GlobalparameterService, dataobject: string): string {
    return moment(dataobject).format(gps.getDateFormat());
  }

  public static numberFormat(gps: GlobalparameterService, value: number, maxFractionDigits: number,
                             minFractionDigits: number) {
    if (maxFractionDigits != null) {
      if (maxFractionDigits > 0) {
        const n = Math.log(Math.abs(value)) / Math.LN10;
        if (n < 1) {
          // negative number means fractions or less than 0
          return value.toFixed(Math.min(maxFractionDigits, Math.max(minFractionDigits || 2,
            Math.max(2, Math.ceil(Math.abs(n)) + ((n < 0) ? 4 : 2)))))
            .split('.').join(gps.getDecimalSymbol());
        }
      } else {
        return value.toFixed(0);
      }
    }
    return gps.getNumberFormat().format(value);
  }


  public static numberIntegerFormat(gps: GlobalparameterService, value: number) {
    return value.toLocaleString(gps.getLocale());
  }

  /**
   * When return value is minus -> it is the index of the next value which is greater than serch value.
   */
  public static binarySearch<T, S>(array: T[], item: S, compare: CompareFunc<T, S>): number {
    let [left, right] = [0, array.length - 1];
    let middle = 1;
    while (left <= right) {
      middle = Math.floor((left + right) / 2);
      switch (compare(array[middle], item)) {
        case Comparison.LT:
          left = middle + 1;
          break;
        case Comparison.GT:
          right = middle - 1;
          break;
        default:
          return middle;
      }
    }
    if (array.length > middle && compare(array[middle], item) === Comparison.LT) {
      middle += 1;
    }
    return middle * -1;
  }



  /**
   * Shows a confirm dialog which is expecting an user input to confirm the action.
   */
  public static confirmationDialog(translateService: TranslateService, confirmationService: ConfirmationService, msgKey: string,
                                   acceptFN: () => void, headerKey: string = 'MSG_GENERAL_HEADER') {
    if (msgKey.indexOf('|') >= 0) {
      const msgParam: string[] = msgKey.split('|');
      translateService.get(msgParam[1]).subscribe(paramTrans => AppHelper.confirmationDialogParam(translateService,
        confirmationService, msgParam[0], paramTrans, acceptFN, headerKey));
    } else {
      AppHelper.confirmationDialogParam(translateService, confirmationService, msgKey, null, acceptFN, headerKey);
    }
  }

  public static getDefaultFormConfig(gps: GlobalparameterService, labelcolums: number,
                                     helpLinkFN: () => void = null, nonModal = false): FormConfig {
    return {
      locale: gps.getLocale(),
      labelcolumns: labelcolums, language: gps.getUserLang(),
      thousandsSeparatorSymbol: gps.getThousandsSeparatorSymbol(),
      dateFormat: gps.getDateFormatForCalendar().toLowerCase(),
      decimalSymbol: gps.getDecimalSymbol(), helpLinkFN, nonModal
    };
  }

  public static getHttpParamsOfObject(dataobject: any): HttpParams {
    let params = new HttpParams();
    for (const key in dataobject) {
      if (dataobject.hasOwnProperty(key) && dataobject[key] != null
        && dataobject[key] !== '') {
        const val = dataobject[key];
        params = params.append(key, '' + val);
      }
    }
    return params;
  }

  public static processDroppedFiles(files: NgxFileDropEntry[], messageToastService: MessageToastService,
                                    allowedFileExtension: string, uploadFunc: (formData: FormData) => void): void {
    let totalFilesSize = 0;
    const formData = new FormData();
    for (let i = 0; i < files.length; i++) {
      if (files[i].fileEntry.isFile && files[i].fileEntry.name.toLocaleLowerCase().endsWith('.' + allowedFileExtension)) {
        // Is it a PDF-file
        const fileEntry = files[i].fileEntry as FileSystemFileEntry;
        fileEntry.file((file: File) => {
          totalFilesSize += file.size;
          formData.append('file', file, files[i].relativePath);
          if (i === files.length - 1) {
            uploadFunc(formData);
          }
        });
      } else {
        messageToastService.showMessageI18n(InfoLevelType.ERROR, allowedFileExtension.toUpperCase() + '_ONLY_ALLOWED');
        break;
      }
    }
  }

  private static confirmationDialogParam(translateService: TranslateService, confirmationService: ConfirmationService,
                                         msgKey: string, param: string, acceptFN: () => void, headerKey: string) {
    const observableMsg = (param) ? translateService.get(msgKey, {i18nRecord: param}) : translateService.get(msgKey);
    const observableHeaderKey = translateService.get(headerKey);

    combineLatest([observableMsg, observableHeaderKey]).subscribe((translated: string[]) => {
      confirmationService.confirm({
        message: translated[0],
        header: translated[1],
        accept: acceptFN
      });
    });
  }
}

export class TranslateParam {
  translatedValue: string;

  constructor(public paramName, public paramValue: string, public translate: boolean) {
  }

  getInterpolateParam(): { [key: string]: any } {
    const interpolateParam = {};
    interpolateParam[this.paramName] = this.translate ? this.translatedValue : this.paramValue;
    return interpolateParam;
  }
}
