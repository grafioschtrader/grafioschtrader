import {Component, Input, OnChanges, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {SingleRecordConfigBase} from '../../lib/datashowbase/single.record.config.base';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {GenericConnectorDef} from '../../entities/generic.connector.def';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateValue} from '../../lib/datashowbase/column.config';

/**
 * Displays connector definition detail fields in fieldset groups.
 * Shows connector settings, rate limit settings, and advanced settings.
 */
@Component({
  selector: 'generic-connector-def-detail',
  template: `
    <div class="fcontainer">
      @for (fieldSetName of Object.keys(fieldSetGroups); track fieldSetName) {
        <fieldset class="out-border fbox">
          <legend class="out-border-legend">{{fieldSetName | translate}}</legend>
          @for (field of getFieldsForFieldSet(fieldSetName); track field.field) {
            <div class="row">
              <div class="col-md-5 showlabel text-end">{{field.headerTranslated}}:</div>
              <div class="col-md-7 nopadding wrap">
                @switch (field.templateName) {
                  @case ('check') {
                    <span><i [ngClass]="{'fa fa-check': getValueByPath(connectorDef, field)}"></i></span>
                  }
                  @case ('yaml') {
                    @if (getValueByPath(connectorDef, field)) {
                      <pre style="white-space: pre-wrap; font-family: monospace; font-size: 0.85em; max-height: 200px; overflow-y: auto; margin: 0;">{{getValueByPath(connectorDef, field)}}</pre>
                    } @else {
                      <span class="text-secondary">&mdash;</span>
                    }
                  }
                  @default {
                    {{getValueByPath(connectorDef, field)}}
                  }
                }
              </div>
            </div>
          }
        </fieldset>
      }
    </div>
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule]
})
export class GenericConnectorDefDetailComponent extends SingleRecordConfigBase implements OnChanges {
  @Input() connectorDef: GenericConnectorDef;

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);

    this.addFieldPropertyFeqH(DataType.String, 'shortId', {fieldsetName: 'CONNECTOR_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.String, 'readableName', {fieldsetName: 'CONNECTOR_SETTINGS'});
    this.addFieldProperty(DataType.String, 'descriptionNLS.map.en', 'DESCRIPTION',
      {headerSuffix: 'EN', fieldsetName: 'CONNECTOR_SETTINGS'});
    this.addFieldProperty(DataType.String, 'descriptionNLS.map.de', 'DESCRIPTION',
      {headerSuffix: 'DE', fieldsetName: 'CONNECTOR_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.String, 'domainUrl', {fieldsetName: 'CONNECTOR_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.Boolean, 'activated',
      {templateName: 'check', fieldsetName: 'CONNECTOR_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.Boolean, 'needsApiKey',
      {templateName: 'check', fieldsetName: 'CONNECTOR_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.Boolean, 'supportsSecurity',
      {templateName: 'check', fieldsetName: 'CONNECTOR_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.Boolean, 'supportsCurrency',
      {templateName: 'check', fieldsetName: 'CONNECTOR_SETTINGS'});

    this.addFieldPropertyFeqH(DataType.String, 'rateLimitType',
      {translateValues: TranslateValue.NORMAL, fieldsetName: 'RATE_LIMIT_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'rateLimitRequests',
      {fieldsetName: 'RATE_LIMIT_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'rateLimitPeriodSec',
      {fieldsetName: 'RATE_LIMIT_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'rateLimitConcurrent',
      {fieldsetName: 'RATE_LIMIT_SETTINGS'});

    this.addFieldPropertyFeqH(DataType.NumericInteger, 'intradayDelaySeconds',
      {fieldsetName: 'ADVANCED_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.String, 'regexUrlPattern',
      {fieldsetName: 'ADVANCED_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.Boolean, 'needHistoryGapFiller',
      {templateName: 'check', fieldsetName: 'ADVANCED_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.Boolean, 'gbxDividerEnabled',
      {templateName: 'check', fieldsetName: 'ADVANCED_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.String, 'tokenConfigYaml',
      {templateName: 'yaml', fieldsetName: 'ADVANCED_SETTINGS'});

    this.translateHeadersAndColumns();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['connectorDef'] && this.connectorDef) {
      this._fieldSetGroups = null;
      this.createTranslatedValueStore([this.connectorDef]);
    }
  }
}
