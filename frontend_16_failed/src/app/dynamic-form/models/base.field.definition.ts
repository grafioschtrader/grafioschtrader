import {DataType} from './data.type';
import {ValueKeyHtmlSelectOptions} from './value.key.html.select.options';

/**
 * Common properties from the definition of a table column and an input field should be included here.
 */
export interface BaseFieldDefinition {
  /**
   * Property name of the field
   */
  field?: string;

  /**
   * For simple values and referenced datatyes should be name of the bunsiness object property. It is always used to transfer
   * the view to the business object.
   */
  dataType: DataType;
}

/**
 * GT basically uses the tables to display data, while editing is done by a single entity in a form.
 * It is possible that more data will be edited in a table in GT in the future.
 * The common properties for editing in a table and in a form should be included here.
 */
export interface PropertyEditShare {
  /**
   * Contains the options for a html select
   */
  valueKeyHtmlOptions?: ValueKeyHtmlSelectOptions[];
}
