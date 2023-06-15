import {DataType} from '../dynamic-form/models/data.type';
import {InputType} from '../dynamic-form/models/input.type';
import {FieldConfig} from '../dynamic-form/models/field.config';
import * as moment from 'moment';
import {FormConfig} from '../dynamic-form/models/form.config';
import {AppSettings} from '../shared/app.settings';


export abstract class Helper {

  public static readonly CALENDAR_LANG = {
    en: {
      firstDayOfWeek: 0,
      dayNames: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
      dayNamesShort: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
      dayNamesMin: ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'],
      monthNames: ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November',
        'December'],
      monthNamesShort: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
      today: 'Today',
      clear: 'Clear'
    },
    de: {
      firstDayOfWeek: 1,
      dayNames: ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'],
      dayNamesShort: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
      dayNamesMin: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
      monthNames: ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'],
      monthNamesShort: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'],
      today: 'Heute',
      clear: 'Löschen'
    },
    fr: {
      firstDayOfWeek: 1,
      dayNames: ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'],
      dayNamesShort: ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.'],
      dayNamesMin: ['D', 'L', 'M', 'M', 'J', 'V', 'S'],
      monthNames: ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'],
      monthNamesShort: ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'],
      today: 'aujourd\'hui',
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

  public static getValueByPath(dataobject: any, path: string): any {
    if (dataobject) {
      const iPath = path.split('.');
      const len = iPath.length;
      for (let i = 0; i < len; i++) {
        if (!dataobject) {
          return null;
        }
        dataobject = dataobject[iPath[i]];
      }
    }
    return dataobject;
  }

  public static hasValue(value: string | number) {
    return value !== null && value !== undefined && (typeof value === 'string' && value.length > 0
      || typeof value === 'number' && value !== 0);

  }

  public static copyFormSingleFormConfigToBusinessObject(formConfig: FormConfig, config: FieldConfig, targetObject: any,
                                                         createProperty = false): void {
    if (createProperty || config.field in targetObject) {
      if (config.referencedDataObject) {
        targetObject[config.field] = this.getReferencedDataObject(config, config.field);
      } else if (config.dataType === DataType.Numeric && config.inputType === InputType.Select
        && !Helper.hasValue(config.formControl.value)) {
        targetObject[config.field] = null;
      } else if (config.dataType === DataType.Numeric) {
        targetObject[config.field] = +config.formControl.value;
      } else if (config.dataType === DataType.DateNumeric || config.dataType === DataType.DateString) {
        if (config.formControl.value) {
          this.formatDateString(config, targetObject, AppSettings.FORMAT_DATE_SHORT_NATIVE);
        } else {
          targetObject[config.field] = null;
        }
      } else if (config.dataType === DataType.DateTimeNumeric) {
        targetObject[config.field] = config.formControl.value.getTime();
      } else if (config.dataType === DataType.DateStringShortUS) {
        this.formatDateString(config, targetObject, AppSettings.FORMAT_DATE_SHORT_US);
      } else {
        targetObject[config.field] = config.formControl.value;
      }
    }
  }

  public static getReferencedDataObject(fieldConfig: FieldConfig, keyProperty: string): any {
    if (fieldConfig.dataproperty) {
      keyProperty = fieldConfig.dataproperty.substring(fieldConfig.dataproperty.indexOf('.') + 1);
    }

    return fieldConfig.referencedDataObject.filter(
      refObject => refObject[keyProperty] === (+fieldConfig.formControl.value)
    )[0];
  }

  public static formatDateString(fieldConfig: FieldConfig, targetObject: any, dateFormat: string): void {
    if (fieldConfig.formControl.value && fieldConfig.formControl.value.toString().trim().length > 0) {
      targetObject[fieldConfig.field] = moment(fieldConfig.formControl.value).format(dateFormat);
    }
  }


  public static flattenObject(obj: { [name: string]: any }, res = {}): { [name: string]: any } {
    Object.keys(obj).forEach(key => {
      res[key]=obj[key];
      if (typeof obj[key] === 'object' && obj[key] !== null) {
        this.flattenObject(obj[key], res);
      }
    });
    return res;
  }

}
