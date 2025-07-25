import {GroupItem} from './value.key.html.select.options';
import {InputType} from './input.type';
import {BaseInputComponent} from '../components/base.input.component';
import {BaseFieldFieldgroupConfig} from './base.field.fieldgroup.config';
import {NgxCurrencyConfig} from 'ngx-currency';
import {BaseFieldDefinition, PropertyEditShare} from './base.field.definition';
import {MenuItem} from 'primeng/api';

/**
 * Definition of input elements and buttons.
 */
export interface FieldConfig extends BaseFieldFieldgroupConfig, BaseFieldDefinition, PropertyEditShare {

  /**
   * Disable works directly on the angular form control. Normally on the view the
   * field gets darker.
   */
  disabled?: boolean;

  /** Label on field are not shown   */
  invisible?: boolean;

  /** Attention: Change state of readonly is not working [readonly]  */
  readonly?: boolean;

  /** Normally buttons are placed at the end of form  */
  buttonInForm?: boolean;

  /** Contains the key for the i18n Translation No translation and output if the string begins with a "_".   */
  labelKey?: string;

  /** Follows after the label and before the input/output field */
  labelSuffix?: string;

  icon?: string;

  /** Output that follows after the input field */
  fieldSuffix?: string;

  /** Help text to be shown */
  labelHelpText?: string;

  /** This help text is shown. It is copied from labelHelpText when help button is clicked  */
  labelShowText?: string;

  /** To Uppercase */
  upperCase?: boolean;

  /** PrimeNG Dropdown may have children. For buttons can be used for loading state. */
  groupItemUseOrLoading?: boolean;

  /** Contains the items for a PrimeNG Dropdown */
  groupItem?: GroupItem[];

  /**
   * A short hint that describes the expected value of an input field.
   * The short hint is displayed in the input field before the user enters a value.
   */
  placeholder?: string;

  /** Input type is the indicator for the html element. */
  inputType: InputType;

  /**
   * Property name of the business object, it is used to transform from existing Object to the view. It is used for referenced
   * objects.
   */
  dataproperty?: string;

  /** Sometimes the full object is used not only the id of the object. When only the id is expected to not use this.  */
  referencedDataObject?: any;

  /** It is set by the application. */
  baseInputComponent?: BaseInputComponent;

  /** Defines the width of input field. */
  inputWidth?: number;

  /** Number of rows in a text area */
  textareaRows?: number;

  /** Used for limit the maximal numbers of input character */
  maxLength?: number;

  /**
   * Can be used to make a different row layout. Normally only a label and the input field occupies a single row.
   * This number must be 6 for two input fields with there labels.
   */
  usedLayoutColumns?: number;

  /** Default value */
  defaultValue?: any;

  /** Used for PrimeNG p-inputNumber */
  inputNumberSettings?: InputNumberSetting;

  calendarConfig?: CalendarConfig;

  /** Can only be used for input InputType.Input  */
  min?: number;
  max?: number;

  /** Value connected to this definition */
  userDefinedValue?: number | string;

  /** Function which is called when this function is defined */
  buttonFN?: (event?: any) => void;

  /**
   * Name of the fieldset, it is also used as translation key. In the view
   *  a box is drawn around a fieldset.
   */
  fieldsetName?: string;

  /** Only for the input suggestion */
  suggestions?: string[];
  suggestionsFN?: (any) => void;

  /** Accepted file upload type */
  acceptFileUploadType?: string;

  /** Sometimes it is required to get die file list directly after choosing the file/s
   *
   * @param fileList List of files
   */
  handleChangeFileInputFN?: (fileList: FileList) => void;

  /** Used for proposed data changes */
  labelTitle?: string;

  /** See https://github.com/nbfontana/ngx-currency/blob/master/src/currency-mask.config.ts  */
  currencyMaskConfig?: NgxCurrencyConfig;

  /** TODO: May be used for entering functions and variables as MenuItem in the Texterea.  */
  contextMenuItems?: MenuItem[];
}

export interface InputNumberSetting {
  maxFractionDigits?: number;
  currency?: string;
  prefix?: string;
}

export interface CalendarConfig {
  minDate?: Date;
  maxDate?: Date;
  disabledDays?: number[];
  disabledDates?: Date[];
}
