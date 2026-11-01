import { requireFieldName, requireFormControl } from '../dynamic-form/models/form-control-access';
import { DataType } from '../dynamic-form/models/data.type';
import { InputType } from '../dynamic-form/models/input.type';
import { FieldConfig } from '../dynamic-form/models/field.config';
import moment from 'moment';
import { FormConfig } from '../dynamic-form/models/form.config';
import { BaseSettings } from '../base.settings';
import { ValueFormatHelper } from './value.format.helper';

export abstract class Helper {
  public static readonly CALENDAR_LANG: Record<
    string,
    {
      firstDayOfWeek: number;
      dayNames: string[];
      dayNamesShort: string[];
      dayNamesMin: string[];
      monthNames: string[];
      monthNamesShort: string[];
      today: string;
      clear: string;
    }
  > = {
    en: {
      firstDayOfWeek: 0,
      dayNames: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
      dayNamesShort: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
      dayNamesMin: ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'],
      monthNames: [
        'January',
        'February',
        'March',
        'April',
        'May',
        'June',
        'July',
        'August',
        'September',
        'October',
        'November',
        'December'
      ],
      monthNamesShort: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
      today: 'Today',
      clear: 'Clear'
    },
    de: {
      firstDayOfWeek: 1,
      dayNames: ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'],
      dayNamesShort: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
      dayNamesMin: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
      monthNames: [
        'Januar',
        'Februar',
        'März',
        'April',
        'Mai',
        'Juni',
        'Juli',
        'August',
        'September',
        'Oktober',
        'November',
        'Dezember'
      ],
      monthNamesShort: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'],
      today: 'Heute',
      clear: 'Löschen'
    },
    fr: {
      firstDayOfWeek: 1,
      dayNames: ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'],
      dayNamesShort: ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.'],
      dayNamesMin: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
      monthNames: [
        'janvier',
        'février',
        'mars',
        'avril',
        'mai',
        'juin',
        'juillet',
        'août',
        'septembre',
        'octobre',
        'novembre',
        'décembre'
      ],
      monthNamesShort: [
        'janv.',
        'févr.',
        'mars',
        'avr.',
        'mai',
        'juin',
        'juil.',
        'août',
        'sept.',
        'oct.',
        'nov.',
        'déc.'
      ],
      today: "aujourd'hui",
      clear: 'effacer'
    }
  };

  public static setValueByPath(data: any, path: string, value: any) {
    let schema = data;
    const pList = path.split('.');
    const len = pList.length;
    for (let i = 0; i < len - 1; i++) {
      const elem = pList[i];
      if (!schema[elem]) {
        schema[elem] = {};
      }
      schema = schema[elem];
    }

    schema[pList[len - 1]] = value;
  }

  /** @see ValueFormatHelper.getValueByPath */
  public static getValueByPath(dataObject: any, path: string): any {
    return ValueFormatHelper.getValueByPath(dataObject, path);
  }

  public static hasValue(value: string | number) {
    return (
      value !== null &&
      value !== undefined &&
      ((typeof value === 'string' && value.length > 0) || (typeof value === 'number' && value !== 0))
    );
  }

  public static copyFormSingleFormConfigToBusinessObject(
    formConfig: FormConfig,
    config: FieldConfig,
    targetObject: any,
    createProperty = false
  ): void {
    this.copyFieldValueToBusinessObject(config, requireFormControl(config).value, targetObject, createProperty);
  }

  /**
   * Copies the value of one form field to a business object, converting it according to the data and input type of
   * the field. Shared by the reactive and the signal dynamic form, so both transfer values identically.
   *
   * @param config - Configuration of the field
   * @param value - Current value of the field
   * @param targetObject - Object receiving the value
   * @param createProperty - When true a property missing on the target is created, otherwise it is skipped
   */
  public static copyFieldValueToBusinessObject(
    config: FieldConfig,
    value: any,
    targetObject: any,
    createProperty = false
  ): void {
    const field = requireFieldName(config);
    if (createProperty || field in targetObject) {
      if (config.referencedDataObject) {
        targetObject[field] = this.getReferencedDataObjectByValue(config, field, value);
      } else if (
        (config.dataType === DataType.Numeric || config.dataType === DataType.NumericInteger) &&
        config.inputType === InputType.Select &&
        (value == null || value === '')
      ) {
        targetObject[field] = null;
      } else if (config.dataType === DataType.Numeric || config.dataType === DataType.NumericInteger) {
        targetObject[field] = value != null ? +value : null;
      } else if (config.dataType === DataType.DateNumeric || config.dataType === DataType.DateString) {
        targetObject[field] = value ? this.formatDateValueAsString(value, BaseSettings.FORMAT_DATE_SHORT_NATIVE) : null;
      } else if (config.dataType === DataType.DateTimeNumeric) {
        targetObject[field] = moment(value).format('YYYY-MM-DDTHH:mm:ss');
      } else if (config.dataType === DataType.DateStringShortUS) {
        targetObject[field] = this.formatDateValueAsString(value, BaseSettings.FORMAT_DATE_SHORT_US);
      } else if (config.inputType === InputType.TriStateCheckbox && value == null) {
        targetObject[field] = value;
      } else if (config.inputType === InputType.Checkbox && value == null) {
        targetObject[field] = false;
      } else {
        targetObject[field] = value;
      }
    }
  }

  public static getReferencedDataObject(fieldConfig: FieldConfig, keyProperty: string | null): any {
    return this.getReferencedDataObjectByValue(fieldConfig, keyProperty, requireFormControl(fieldConfig).value);
  }

  /**
   * Finds the referenced object whose key equals the value of a field.
   *
   * @param fieldConfig - Field holding the referenced objects and optionally the `dataproperty` path of the key
   * @param keyProperty - Key property of the referenced objects when the field has no `dataproperty`
   * @param value - Current value of the field
   * @returns The referenced object, undefined when no object has that key
   */
  public static getReferencedDataObjectByValue(fieldConfig: FieldConfig, keyProperty: string | null, value: any): any {
    if (fieldConfig.dataproperty) {
      keyProperty = fieldConfig.dataproperty.substring(fieldConfig.dataproperty.indexOf('.') + 1);
    }

    if (keyProperty == null) throw new Error(`Referenced data for "${fieldConfig.field}" requires a key property.`);
    const key = keyProperty;
    return fieldConfig.referencedDataObject.filter(
      (refObject: { [x: string]: number }) => refObject[key] === +value
    )[0];
  }

  public static formatDateString(fieldConfig: FieldConfig, targetObject: any, dateFormat: string): void {
    targetObject[requireFieldName(fieldConfig)] = Helper.formatDateStringAsString(fieldConfig, dateFormat);
  }

  public static formatDateStringAsString(fieldConfig: FieldConfig, dateFormat: string): string | null {
    return this.formatDateValueAsString(requireFormControl(fieldConfig).value, dateFormat);
  }

  /**
   * Formats the value of a date field, null for an empty value.
   *
   * @param value - Date, date string or empty value of a calendar field
   * @param dateFormat - moment format of the result
   * @returns The formatted date, or null
   */
  public static formatDateValueAsString(value: any, dateFormat: string): string | null {
    return value && value.toString().trim().length > 0 ? moment(value).format(dateFormat) : null;
  }

  /**
   * Parses calendar days as local midnight so users west of UTC retain the stored day when editing.
   * Instants, epoch numbers and wall-clock date-times keep the native Date constructor's behaviour.
   *
   * @param value - Backend date or date-time value to load into a calendar control
   * @returns A Date with local calendar components for a date-only string
   */
  public static parseDateOnlyAsLocal(value: string | number | Date): Date {
    return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
      ? moment(value, BaseSettings.FORMAT_DATE_SHORT_NATIVE).toDate()
      : new Date(value);
  }

  public static flattenObject(
    obj: { [name: string]: any },
    res: Record<string, unknown> = {}
  ): { [name: string]: any } {
    Object.keys(obj).forEach((key) => {
      res[key] = obj[key];
      if (typeof obj[key] === 'object' && obj[key] !== null) {
        this.flattenObject(obj[key], res);
      }
    });
    return res;
  }

  public static findPropertyNamesInObjectTree(obj: object, propertyName: string, path: string = ''): string[] {
    let result: string[] = [];
    for (const key in obj) {
      const value = (obj as Record<string, unknown>)[key];
      const currentPath = path.length === 0 ? path.concat(key) : path.concat('.' + key);
      if (value !== null && typeof value === 'object') {
        if (key === propertyName) {
          result.push(currentPath);
        }
        result = result.concat(Helper.findPropertyNamesInObjectTree(value, propertyName, currentPath));
      } else if (key === propertyName) {
        result.push(currentPath);
      }
    }
    return result;
  }

  /**
   * Reduces the selection emitted by a table to the one row of a single-selection table. The Optimus table types its
   * selection for every selection mode, so a table in single mode still reports `T | T[]`.
   *
   * @param selection - Selection as emitted by the table
   * @returns The selected row, or null when nothing is selected
   */
  public static singleSelection<T>(selection: T | T[] | null | undefined): T | null {
    return Array.isArray(selection) ? (selection[0] ?? null) : (selection ?? null);
  }

  /**
   * Normalises the selection emitted by a multiple-selection table to an array.
   *
   * @param selection - Selection as emitted by the table
   * @returns The selected rows, empty when nothing is selected
   */
  public static multipleSelection<T>(selection: T | T[] | null | undefined): T[] {
    return selection == null ? [] : Array.isArray(selection) ? selection : [selection];
  }
}
