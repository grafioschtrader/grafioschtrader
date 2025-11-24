import {ShowRecordConfigBase} from './show.record.config.base';
import {TranslateService} from '@ngx-translate/core';
import {ColumnConfig, ColumnGroupConfig} from './column.config';
import {AppHelper} from '../helper/app.helper';
import {Auditable} from '../entities/auditable';
import {GlobalparameterService} from '../services/globalparameter.service';
import {BaseSettings} from '../base.settings';

/**
 * Abstract base class extending ShowRecordConfigBase with functionality for handling
 * grouped data display in tables and tree tables. Provides utilities for displaying
 * subtotals, grand totals, and other aggregate values in table group sections.
 *
 * This class is designed for tables that need to show calculated values, totals,
 * or grouped data at various levels, commonly used in financial reports,
 * data summaries, and hierarchical data displays.
 */
export abstract class TableTreetableTotalBase extends ShowRecordConfigBase {

  /**
   * Creates a new table/tree table total base configuration.
   *
   * @param translateService - Angular translation service for internationalization
   * @param gps - Global parameter base service for locale and formatting settings
   */
  protected constructor(translateService: TranslateService,
    gps: GlobalparameterService) {
    super(translateService, gps);
  }

  /**
   * Configures columns starting from a specified index to use their field names for group display.
   * Creates default ColumnGroupConfig for each field to enable total/subtotal functionality.
   *
   * @param startIndex - Index from which to start applying group configurations
   */
  setSameFieldNameForGroupField(startIndex: number): void {
    for (let i = startIndex; i < this.fields.length; i++) {
      this.fields[i].columnGroupConfigs = [new ColumnGroupConfig(this.fields[i].field)];
    }
  }

  /**
   * Checks if a column total value is negative for styling purposes.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to check
   * @param data - Data object or collection containing the values
   * @param mapKey - Key for accessing data from collections/maps
   * @returns True if the total value starts with '-', false otherwise
   */
  isValueColumnTotalMinus(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any): boolean {
    return this.getValueColumnTotal(columnConfig, arrIndex, data, mapKey).startsWith('-');
  }

  /**
   * Retrieves and formats the total value for a column group section.
   * Combines label text with field values, supporting both custom functions and standard field access.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to process
   * @param data - Data object or collection containing the values
   * @param mapKey - Key for accessing data from collections/maps
   * @returns Formatted string combining label and value for display
   */
  getValueColumnTotal(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any) {
    let value = '';
    if (columnConfig.columnGroupConfigs && arrIndex < columnConfig.columnGroupConfigs.length) {
      if (columnConfig.columnGroupConfigs[arrIndex].fieldTextFN) {
        value = columnConfig.columnGroupConfigs[arrIndex].fieldTextFN(columnConfig, arrIndex, data, mapKey);
      } else {
        value = this.getTextValueColumnTotal(columnConfig, arrIndex);
        if (columnConfig.columnGroupConfigs[arrIndex].fieldValue) {
          const dataValue = this.getFieldValueColumnTotal(columnConfig, arrIndex, data, mapKey);
          if (dataValue) {
            value += (value.length > 0) ? ' ' + dataValue : dataValue;
          }
        }
      }
    }
    return value;
  }

  /**
   * Checks if a column total value accessed by row index is negative.
   * Alternative access method for row-based data structures.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to check
   * @param groupChangeIndexMap - Mapping of group changes to data indices
   * @param rowIndex - Specific row index to check
   * @returns True if the total value starts with '-', false otherwise
   */
  isColumnTotalByRowIndexMinus(columnConfig: ColumnConfig, arrIndex: number, groupChangeIndexMap: any, rowIndex: number): boolean {
    return this.getValueColumnTotalByRowIndex(columnConfig, arrIndex, groupChangeIndexMap, rowIndex).startsWith('-');
  }

  /**
   * Retrieves and formats column total value using row-based access.
   * Similar to getValueColumnTotal but uses row index for data access instead of direct object access.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to process
   * @param groupChangeIndexMap - Mapping of group changes to data indices
   * @param rowIndex - Specific row index to access
   * @returns Formatted string combining label and value for display
   */
  getValueColumnTotalByRowIndex(columnConfig: ColumnConfig, arrIndex: number, groupChangeIndexMap: any, rowIndex: number): string {
    let value = '';
    if (columnConfig.columnGroupConfigs && arrIndex < columnConfig.columnGroupConfigs.length) {
      if (columnConfig.columnGroupConfigs[arrIndex].fieldTextFN) {
        value = columnConfig.columnGroupConfigs[arrIndex].fieldTextFN(columnConfig, arrIndex, groupChangeIndexMap, rowIndex);
      } else {
        value = this.getTextValueColumnTotal(columnConfig, arrIndex);
        if (columnConfig.columnGroupConfigs[arrIndex].fieldValue) {
          const dataValue = this.getFieldValueColumnTotal(columnConfig, arrIndex, groupChangeIndexMap, rowIndex);
          if (dataValue) {
            value += (value.length > 0) ? ' ' + dataValue : dataValue;
          }
        }
      }
    }
    return value;
  }

  /**
   * Determines if an entity should be displayed with owner highlighting.
   * Used for visually distinguishing user-owned entities in shared data scenarios.
   *
   * @param entity - Auditable entity to check for ownership
   * @param columnConfig - Column configuration with template settings
   * @returns True if entity should be highlighted as user-owned
   */
  public isNotSingleModeAndOwner(entity: Auditable, columnConfig: ColumnConfig): boolean {
    return this.gps.isUiShowMyProperty() && columnConfig.templateName === BaseSettings.OWNER_TEMPLATE && this.gps.isEntityCreatedByUser(entity);
  }

  /**
   * Retrieves the translated label text for a column group section.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to process
   * @returns Translated label text or empty string if no label is configured
   */
  getTextValueColumnTotal(columnConfig: ColumnConfig, arrIndex: number): string {
    if (columnConfig.columnGroupConfigs[arrIndex].textValueKey) {
      return columnConfig.columnGroupConfigs[arrIndex].textValueTranslated;
    }
    return '';
  }

  /**
   * Retrieves and formats a field value for column totals with custom field path.
   * Supports both direct data access and map-based data retrieval.
   *
   * @param columnConfig - Column configuration for formatting
   * @param arrIndex - Index of the group configuration
   * @param data - Data object or collection containing values
   * @param mapKey - Key for accessing data from collections/maps
   * @param field - Specific field path to access
   * @returns Formatted field value ready for display
   */
  getFieldValueForFieldColumnTotal(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any, field: string) {
    if (mapKey !== null) {
      data = data.get(mapKey);
    }
    return AppHelper.getValueByPathWithField(this.gps, this.translateService, data, columnConfig,
      field);
  }

  /**
   * Retrieves and formats the configured field value for a column group.
   * Uses the fieldValue property from the group configuration to access data.
   *
   * @param columnConfig - Column configuration containing group settings
   * @param arrIndex - Index of the group configuration to process
   * @param data - Data object or collection containing values
   * @param mapKey - Key for accessing data from collections/maps
   * @returns Formatted field value ready for display
   */
  getFieldValueColumnTotal(columnConfig: ColumnConfig, arrIndex: number, data: any, mapKey: any) {
    return this.getFieldValueForFieldColumnTotal(columnConfig, arrIndex, data, mapKey,
      columnConfig.columnGroupConfigs[arrIndex].fieldValue);
  }
}
