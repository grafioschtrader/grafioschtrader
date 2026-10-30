import { Component, output, input, effect, untracked } from '@angular/core';

import { TranslateModule } from '@ngx-translate/core';
import { SingleRecordConfigBase } from '../../lib/datashowbase/single.record.config.base';
import { GenericConnectorEndpoint } from '../../entities/generic.connector.endpoint';
import { GenericConnectorFieldMapping } from '../../entities/generic.connector.field.mapping';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { GenericConnectorFieldMappingTableComponent } from './generic-connector-field-mapping-table.component';

/**
 * Displays endpoint detail fields in fieldset groups and contains the field mappings table.
 * Used as an accordion panel content within the main connector component.
 */
@Component({
  selector: 'generic-connector-endpoint-panel',
  template: `
    <div class="fcontainer">
      @for (fieldSetName of Object.keys(fieldSetGroups); track fieldSetName) {
        <fieldset class="out-border fbox">
          <legend class="out-border-legend">
            {{ fieldSetName | translate }}
          </legend>
          @for (field of getFieldsForFieldSet(fieldSetName); track field.field) {
            <div class="row">
              <div class="col-md-5 showlabel text-end">{{ field.headerTranslated }}:</div>
              <div class="col-md-7 nopadding wrap">
                @switch (field.templateName) {
                  @case ('check') {
                    <span
                      ><i
                        [class.fa]="getValueByPath(endpoint(), field)"
                        [class.fa-check]="getValueByPath(endpoint(), field)"></i
                    ></span>
                  }
                  @default {
                    {{ getValueByPath(endpoint(), field) }}
                  }
                }
              </div>
            </div>
          }
        </fieldset>
      }
    </div>
    <br />
    <generic-connector-field-mapping-table
      [fieldMappings]="$safeNavigationMigration(endpoint().fieldMappings)"
      [feedSupport]="endpoint().feedSupport ?? undefined"
      [responseFormat]="endpoint().responseFormat ?? undefined"
      [editable]="editable()"
      (fieldMappingsChange)="onFieldMappingsChange($event)" />
  `,
  imports: [TranslateModule, GenericConnectorFieldMappingTableComponent]
})
export class GenericConnectorEndpointPanelComponent extends SingleRecordConfigBase {
  readonly endpoint = input.required<GenericConnectorEndpoint>();
  readonly editable = input<boolean>(true);
  readonly editEndpoint = output<void>();
  readonly fieldMappingsChange = output<GenericConnectorFieldMapping[]>();

  constructor() {
    super();

    this.addFieldPropertyFeqH(DataType.String, 'feedSupport', {
      translateValues: TranslateValue.NORMAL,
      fieldsetName: 'ENDPOINT_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'instrumentType', {
      translateValues: TranslateValue.NORMAL,
      fieldsetName: 'ENDPOINT_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'urlTemplate', {
      fieldsetName: 'ENDPOINT_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'httpMethod', {
      fieldsetName: 'ENDPOINT_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'responseFormat', {
      translateValues: TranslateValue.NORMAL,
      fieldsetName: 'ENDPOINT_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'numberFormat', {
      translateValues: TranslateValue.NORMAL,
      fieldsetName: 'ENDPOINT_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'dateFormatType', {
      translateValues: TranslateValue.NORMAL,
      fieldsetName: 'ENDPOINT_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'dateFormatPattern', {
      fieldsetName: 'ENDPOINT_SETTINGS'
    });

    this.addFieldPropertyFeqH(DataType.String, 'jsonDataStructure', {
      translateValues: TranslateValue.NORMAL,
      fieldsetName: 'JSON_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'jsonDataPath', {
      fieldsetName: 'JSON_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'jsonColumnNamesPath', {
      fieldsetName: 'JSON_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'jsonStatusPath', {
      fieldsetName: 'JSON_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'jsonStatusOkValue', {
      fieldsetName: 'JSON_SETTINGS'
    });

    this.addFieldPropertyFeqH(DataType.String, 'csvDelimiter', {
      fieldsetName: 'CSV_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'csvSkipHeaderLines', {
      fieldsetName: 'CSV_SETTINGS'
    });

    this.addFieldPropertyFeqH(DataType.String, 'htmlCssSelector', {
      fieldsetName: 'HTML_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'htmlExtractMode', {
      translateValues: TranslateValue.NORMAL,
      fieldsetName: 'HTML_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'htmlTextCleanup', {
      fieldsetName: 'HTML_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'htmlExtractRegex', {
      fieldsetName: 'HTML_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'htmlSplitDelimiter', {
      fieldsetName: 'HTML_SETTINGS'
    });

    this.addFieldPropertyFeqH(DataType.String, 'tickerBuildStrategy', {
      translateValues: TranslateValue.NORMAL,
      fieldsetName: 'TICKER_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'currencyPairSeparator', {
      fieldsetName: 'TICKER_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, 'currencyPairSuffix', {
      fieldsetName: 'TICKER_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.Boolean, 'tickerUppercase', {
      templateName: 'check',
      fieldsetName: 'TICKER_SETTINGS'
    });

    this.addFieldPropertyFeqH(DataType.NumericInteger, 'maxDataPoints', {
      fieldsetName: 'PAGINATION_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.Boolean, 'paginationEnabled', {
      templateName: 'check',
      fieldsetName: 'PAGINATION_SETTINGS'
    });

    this.translateHeadersAndColumns();

    effect(() => {
      this.endpoint();
      untracked(() => {
        const endpoint = this.endpoint();
        if (endpoint) {
          this._fieldSetGroups = null;
          this.createTranslatedValueStore([endpoint]);
        }
      });
    });
  }

  onFieldMappingsChange(mappings: GenericConnectorFieldMapping[]): void {
    this.fieldMappingsChange.emit(mappings);
  }
}
