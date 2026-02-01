import moment from 'moment';
import {combineLatest} from 'rxjs';
import {TranslateService} from '@ngx-translate/core';
import {DataType} from '../dynamic-form/models/data.type';
import {Helper} from './helper';
import {ColumnConfig} from '../datashowbase/column.config';
import {ParamMap} from '@angular/router';
import {FormConfig} from '../dynamic-form/models/form.config';
import {HttpHeaders, HttpParams} from '@angular/common/http';
import {ConfirmationService} from 'primeng/api';
import {FileSystemFileEntry, NgxFileDropEntry} from 'ngx-file-drop';
import {InfoLevelType} from '../message/info.leve.type';
import {MessageToastService} from '../message/message.toast.service';
import {FieldConfig} from '../dynamic-form/models/field.config';
import {BaseSettings} from '../base.settings';
import {GlobalparameterService} from '../services/globalparameter.service';

export const enum Comparison { GT, LT, EQ }

type CompareFunc<T, S> = (a: T, b: S) => Comparison;

/**
 * Comprehensive utility class providing static helper methods for common application operations.
 * Handles date/time operations, internationalization, data formatting, HTTP operations,
 * file processing, and various UI utilities. Serves as a central hub for reusable
 * functionality across the application.
 *
 * Key features:
 * - Number and currency formatting with locale support
 * - HTTP parameter construction and URL handling
 * - String manipulation and transformation utilities
 * - Binary search algorithm implementation
 * - Confirmation dialog management with dual translation key support
 * - File upload processing with validation
 * - Form configuration and field state management
 */
export class AppHelper {

  static readonly fieldToLabelRegex = new RegExp('^(rand|year|group)|(DiffMC|MC)$', 'g');

  /**
   * Normalizes language codes to supported application languages.
   * Ensures only supported languages ('de', 'en') are returned, defaults to 'en'.
   *
   * @param currentLang Current language code to normalize
   * @returns Normalized language code ('de' or 'en')
   */
  static getNonUserDefinedLanguage(currentLang: string): string {
    return ['de', 'en'].indexOf(currentLang) >= 0 ? currentLang : 'en';
  }

  /**
   * Adds a space suffix to currency code for display formatting.
   * Ensures proper spacing between currency symbol and amount.
   *
   * @param currency Currency code to format
   * @returns Currency code with trailing space
   */
  static addSpaceToCurrency(currency: string): string {
    return currency + ' ';
  }

  /**
   * Creates HTTP options with closed position flag and until date parameters.
   * Combines multiple query parameters for position-related API calls.
   *
   * @param includeClosedPosition Whether to include closed positions in results
   * @param untilDate Date limit for position data
   * @param httpHeaders HTTP headers to include in request
   * @returns HTTP options object with headers and query parameters
   */
  static getOptionsWithIncludeClosedPositionAndUntilDate(includeClosedPosition: boolean, untilDate: Date, httpHeaders: HttpHeaders) {
    const headerParam = AppHelper.getOptionsWithUntilDate(untilDate, httpHeaders);
    headerParam.params = headerParam.params.append('includeClosedPosition', includeClosedPosition.toString());
    return headerParam;
  }

  /**
   * Creates HTTP options with until date parameter.
   * Standardizes date parameter formatting for API requests.
   *
   * @param untilDate Date to include as query parameter
   * @param httpHeaders HTTP headers to include in request
   * @returns HTTP options object with headers and date parameter
   */
  static getOptionsWithUntilDate(untilDate: Date, httpHeaders: HttpHeaders) {
    const httpParams = new HttpParams()
      .set('untilDate', moment(untilDate).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE));
    return {headers: httpHeaders, params: httpParams};
  }

  /**
   * Transforms plain data object into typed class instance.
   * Creates new instance of specified type and assigns properties from data.
   *
   * @param type Constructor function for target class
   * @param data Source data object to transform
   * @returns New instance of specified type with data properties
   */
  static transformDataToRealClassDataList<T>(type: new() => T, data: T): T {
    const instance = new type();
    return (Object.assign(instance, data));
  }

  /**
   * Converts Angular ParamMap to plain object with JSON-parsed values.
   * Handles URL parameters that contain JSON-encoded data.
   *
   * @param paramMap Angular ParamMap from route parameters
   * @returns Plain object with parsed parameter values
   */
  public static createParamObjectFromParamMap(paramMap: ParamMap): any {
    const paramObject = {};
    paramMap.keys.forEach(key => paramObject[key] = JSON.parse(paramMap.get(key)));
    return paramObject;
  }

  /**
   * Converts camelCase string to UPPER_CASE_WITH_UNDERSCORES format.
   * Handles dot notation paths and various camelCase patterns. Used for
   * generating translation keys from property names.
   *
   * Examples:
   * - abcDefGh -> ABC_DEF_GH
   * - transactionGainLossMC -> TRANSACTION_GAIN_LOSS_MC
   * - UDFMetadataSecurity -> UDF_METADATA_SECURITY
   *
   * @param upperLower CamelCase string to convert
   * @returns Converted string in UPPER_CASE_WITH_UNDERSCORES format
   */
  public static toUpperCaseWithUnderscore(upperLower: string): string {
    let startPoint = upperLower.indexOf('.');
    startPoint = startPoint < 0 ? 0 : startPoint + 1;
    let str = upperLower.substring(startPoint).replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2')
    return str.replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase();
  }

  /**
   * Removes irrelevant prefixes and suffixes from field names and converts to uppercase with underscores.
   * Applies fieldToLabelRegex to remove field name parts that are not relevant for translation keys:
   * - Prefixes: 'rand', 'year', 'group' at start of field name
   * - Suffixes: 'DiffMC', 'MC' at end of field name
   * Used for generating clean, meaningful translation keys from technical field names.
   *
   * @param upperLower String to clean and convert
   * @returns Cleaned string in UPPER_CASE_WITH_UNDERSCORES format with irrelevant parts removed
   */
  public static removeSomeStringAndToUpperCaseWithUnderscore(upperLower: string): string {
    return this.toUpperCaseWithUnderscore(upperLower.replace(this.fieldToLabelRegex, ''));
  }

  /**
   * Opens an external webpage in a new window or tab. Handles URL preprocessing
   * including domain prefixing for internal links and protocol addition for external links.
   *
   * @param url The URL to open (can be relative, absolute, or prefixed with '--') A “--” is used in the user interface
   * with the link output, and an icon should also appear after the link content. Clicking on the icon navigates to
   * the corresponding page.
   * @param targetPage The target window name (default: 'blank' for new tab)
   */
  public static toExternalWebpage(url: string, targetPage: string = 'blank'): void {
    if (url.startsWith('--')) {
      url = location.host + url.substring(2);
    }
    if (!url.match(/^https?:\/\//i)) {
      url = 'http://' + url;
    }
    window.open(url, targetPage);
  }

  /**
   * Sets field visibility and state based on hide flag.
   * Convenience method for toggling field visibility and enabled state.
   *
   * @param fieldConfig Field configuration to modify
   * @param hide Whether to hide and disable the field
   */
  public static invisibleAndHide(fieldConfig: FieldConfig, hide: boolean): void {
    if (hide) {
      AppHelper.disableAndHideInput(fieldConfig);
    } else {
      AppHelper.enableAndVisibleInput(fieldConfig);
    }
  }

  /**
   * Makes field visible and enabled.
   * Sets field to visible state and enables form control.
   * For button fields without formControl, sets the disabled property directly.
   *
   * @param fieldConfig Field configuration to enable and show
   */
  public static enableAndVisibleInput(fieldConfig: FieldConfig): void {
    fieldConfig.invisible = false;
    if (fieldConfig.formControl) {
      fieldConfig.formControl.enable();
    } else {
      fieldConfig.disabled = false;
    }
  }

  /**
   * Makes field invisible and disabled.
   * Sets field to hidden state and disables form control.
   * For button fields without formControl, sets the disabled property directly.
   *
   * @param fieldConfig Field configuration to disable and hide
   */
  public static disableAndHideInput(fieldConfig: FieldConfig): void {
    if (fieldConfig.formControl) {
      fieldConfig.formControl.disable();
    } else {
      fieldConfig.disabled = true;
    }
    fieldConfig.invisible = true;
  }

  /**
   * Truncates string to specified length with optional word boundary respect.
   * Adds ellipsis (...) to indicate truncation. Optionally breaks at word boundaries.
   *
   * @param str String to truncate
   * @param length Maximum length including ellipsis
   * @param useWordBoundary Whether to respect word boundaries when truncating
   * @returns Truncated string with ellipsis if needed
   */
  public static truncateString(str: string, length: number, useWordBoundary: boolean): string {
    if (str.length <= length) {
      return str;
    } else {
      const subString = str.slice(0, length - 1);
      return (useWordBoundary
        ? subString.slice(0, subString.lastIndexOf(' '))
        : subString) + '...';
    }
  }

  /**
   * Formats data value based on field configuration and data type.
   * Handles various data types including numbers, dates, and strings with
   * appropriate locale-specific formatting.
   *
   * @param gps Global parameter service for locale and formatting settings
   * @param translateService Translation service for internationalization
   * @param dataobject Source data object
   * @param field Column configuration specifying data type and formatting
   * @param valueField Path to the value within the data object
   * @returns Formatted value string or undefined if no data
   */
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
          return moment.utc(dataobject).local().format(gps.getTimeDateFormatForTable());
        case DataType.DateTimeSecondString:
          return moment.utc(dataobject).local().format(gps.getTimeSecondDateFormatForTable());
        default:
          return dataobject;
      }
    }
  }

  /**
   * Formats date string according to global parameter service format.
   * Uses moment.js for reliable date parsing and formatting.
   *
   * @param gps Global parameter service for date format settings
   * @param dataobject Date string to format
   * @returns Formatted date string
   */
  public static getDateByFormat(gps: GlobalparameterService, dataobject: string): string {
    return moment(dataobject).format(gps.getDateFormat());
  }

  /**
   * Formats numeric value with locale-specific number formatting.
   * Handles precision, fraction digits, and decimal symbol based on locale.
   * Applies special formatting for very small numbers.
   *
   * @param gps Global parameter service for locale and formatting settings
   * @param value Numeric value to format
   * @param maxFractionDigits Maximum number of decimal places
   * @param minFractionDigits Minimum number of decimal places
   * @returns Formatted number string
   */
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

  /**
   * Formats integer value with locale-specific formatting.
   * Uses browser's built-in locale formatting for integers.
   *
   * @param gps Global parameter service for locale settings
   * @param value Integer value to format
   * @returns Formatted integer string
   */
  public static numberIntegerFormat(gps: GlobalparameterService, value: number) {
    return value.toLocaleString(gps.getLocale());
  }

  /**
   * Performs binary search on sorted array using custom comparison function.
   * Returns negative index if item not found, indicating insertion point.
   *
   * @param array Sorted array to search
   * @param item Item to search for
   * @param compare Comparison function returning GT, LT, or EQ
   * @returns Index of found item, or negative insertion point if not found
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
   * Shows confirmation dialog with translated message and header.
   * Supports parameterized messages with pipe separator (|) for dynamic content.
   *
   * @param translateService Translation service for message and header
   * @param confirmationService PrimeNG confirmation service
   * @param msgKey Translation key for message (supports "key|param" format)
   * @param acceptFN Function to execute when user confirms
   * @param headerKey Translation key for dialog header
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

  /**
   * Creates default form configuration with locale and formatting settings.
   * Provides standardized form configuration based on global parameters.
   *
   * @param gps Global parameter service for locale and formatting settings
   * @param labelColums Number of columns for form labels
   * @param helpLinkFN Optional function for help link handling
   * @param nonModal Whether form is non-modal
   * @returns FormConfig object with locale-specific settings
   */
  public static getDefaultFormConfig(gps: GlobalparameterService, labelColums: number,
    helpLinkFN: () => void = null, nonModal = false): FormConfig {
    return {
      locale: gps.getLocale(),
      labelColumns: labelColums, language: gps.getUserLang(),
      thousandsSeparatorSymbol: gps.getThousandsSeparatorSymbol(),
      dateFormat: gps.getDateFormatForCalendar().toLowerCase(),
      decimalSymbol: gps.getDecimalSymbol(), helpLinkFN, nonModal
    };
  }

  /**
   * Converts object properties to HTTP query parameters.
   * Excludes null, undefined, and empty string values by default.
   *
   * @param dataobject Object containing properties to convert
   * @returns HttpParams object with query parameters
   */
  public static getHttpParamsOfObject(dataobject: any): HttpParams {
    return this.getHttpParamsOfObjectAllowBooleanNullFields(dataobject, []);
  }

  /**
   * Converts object properties to HTTP query parameters with boolean null field support.
   * Allows specific fields to be included even when null (useful for boolean parameters).
   *
   * @param dataobject Object containing properties to convert
   * @param allowBooleanNullFields Array of field names that can be null
   * @returns HttpParams object with query parameters
   */
  public static getHttpParamsOfObjectAllowBooleanNullFields(dataobject: any, allowBooleanNullFields: string[]): HttpParams {
    let params = new HttpParams();
    for (const key in dataobject) {
      if (dataobject.hasOwnProperty(key) && (dataobject[key] != null
        && dataobject[key] !== '' || allowBooleanNullFields.length > 0 && allowBooleanNullFields.indexOf(key) >= 0)) {
        const val = dataobject[key];
        params = (dataobject[key] === null) ? params.append(key, '') : params.append(key, '' + val);
      }
    }
    return params;
  }

  /**
   * Processes dropped files with validation and upload functionality.
   * Validates file extensions, shows error messages, and calls upload function
   * with FormData containing all valid files.
   *
   * @param files Array of dropped file entries from ngx-file-drop
   * @param messageToastService Service for showing validation error messages
   * @param allowedFileExtension Single file extension to allow (without dot)
   * @param uploadFunc Function to call with FormData containing valid files
   */
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

  /**
   * Shows parameterized confirmation dialog with translated messages.
   * Internal method for handling confirmation dialogs with optional parameters.
   *
   * @param translateService Translation service for message translation
   * @param confirmationService PrimeNG confirmation service
   * @param msgKey Translation key for dialog message
   * @param param Optional parameter for message interpolation
   * @param acceptFN Function to execute when user confirms
   * @param headerKey Translation key for dialog header
   */
  private static confirmationDialogParam(translateService: TranslateService, confirmationService: ConfirmationService,
    msgKey: string, param: string, acceptFN: () => void, headerKey: string) {
    const observableMsg = (param) ? translateService.get(msgKey, {i18nRecord: param})
      : translateService.get(msgKey);
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

/**
 * Helper class for managing translation parameters with conditional translation.
 * Stores parameter name, value, and whether the value should be translated.
 * Used for building complex translation interpolation objects.
 */
export class TranslateParam {
  /** The translated value (set after translation occurs) */
  translatedValue: string;

  /**
   * Creates a new translation parameter.
   *
   * @param paramName Name of the parameter for interpolation
   * @param paramValue Value of the parameter
   * @param translate Whether this parameter value should be translated
   */
  constructor(public paramName, public paramValue: string, public translate: boolean) {
  }

  /**
   * Creates interpolation object for use with Angular translate service.
   * Uses translated value if translation was requested, otherwise uses original value.
   *
   * @returns Object with parameter name as key and appropriate value for interpolation
   */
  getInterpolateParam(): { [key: string]: any } {
    const interpolateParam = {};
    interpolateParam[this.paramName] = this.translate ? this.translatedValue : this.paramValue;
    return interpolateParam;
  }
}
