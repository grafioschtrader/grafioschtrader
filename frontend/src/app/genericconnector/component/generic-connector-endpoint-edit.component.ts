import {Component, Input, OnInit} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {GenericConnectorEndpoint} from '../../entities/generic.connector.endpoint';
import {GenericConnectorDef} from '../../entities/generic.connector.def';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppHelpIds} from '../../shared/help/help.ids';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {ResponseFormatType} from '../../shared/types/response.format.type';
import {DateFormatType} from '../../shared/types/date.format.type';
import {JsonDataStructure} from '../../shared/types/json.data.structure';
import {NumberFormatType} from '../../shared/types/number.format.type';
import {TickerBuildStrategy} from '../../shared/types/ticker.build.strategy';
import {HtmlExtractMode} from '../../shared/types/html.extract.mode';
import {EndpointOption, ENDPOINT_OPTION_BY_FEED} from '../../shared/types/endpoint.option';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

const ALL_FEED_INSTRUMENT_COMBOS = [
  'FS_HISTORY|SECURITY', 'FS_HISTORY|CURRENCY',
  'FS_INTRA|SECURITY', 'FS_INTRA|CURRENCY'
];

/**
 * Edit dialog for GenericConnectorEndpoint. Saves by updating the endpoint object in place;
 * the parent component handles persisting the full GenericConnectorDef via PUT.
 */
@Component({
  selector: 'generic-connector-endpoint-edit',
  template: `
    <p-dialog header="{{'ENDPOINT_SETTINGS' | translate}}" [visible]="visibleDialog"
              [style]="{width: '900px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
  standalone: true,
  imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class GenericConnectorEndpointEditComponent extends SimpleEditBase implements OnInit {
  @Input() endpoint: GenericConnectorEndpoint;
  @Input() connectorDef: GenericConnectorDef;

  constructor(public translateService: TranslateService,
              gps: GlobalparameterService) {
    super(AppHelpIds.HELP_BASEDATA_GENERIC_CONNECTOR_ENDPOINTS, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectString('feedSupportInstrumentType', 'FEED_INSTRUMENT_TYPE', true),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('urlTemplate', 1000, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('httpMethod', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('responseFormat', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('numberFormat', false),
      DynamicFieldHelper.createFieldSelectStringHeqF('dateFormatType', true),
      DynamicFieldHelper.createFieldInputStringHeqF('dateFormatPattern', 64, false),
      DynamicFieldHelper.createFieldSelectStringHeqF('jsonDataStructure', false),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('jsonDataPath', 255, false),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('jsonColumnNamesPath', 255, false),
      DynamicFieldHelper.createFieldInputStringHeqF('jsonStatusPath', 128, false),
      DynamicFieldHelper.createFieldInputStringHeqF('jsonStatusOkValue', 64, false),
      DynamicFieldHelper.createFieldInputStringHeqF('csvDelimiter', 4, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('csvSkipHeaderLines', false, 3, 0, false),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('htmlCssSelector', 255, false),
      DynamicFieldHelper.createFieldSelectStringHeqF('htmlExtractMode', false),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('htmlTextCleanup', 255, false),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('htmlExtractRegex', 512, false),
      DynamicFieldHelper.createFieldInputStringHeqF('htmlSplitDelimiter', 16, false),
      DynamicFieldHelper.createFieldSelectStringHeqF('tickerBuildStrategy', false),
      DynamicFieldHelper.createFieldInputStringHeqF('currencyPairSeparator', 4, false),
      DynamicFieldHelper.createFieldInputStringHeqF('currencyPairSuffix', 20, false),
      DynamicFieldHelper.createFieldCheckboxHeqF('tickerUppercase'),
      DynamicFieldHelper.createFieldInputNumberHeqF('maxDataPoints', false, 7, 0, false),
      DynamicFieldHelper.createFieldCheckboxHeqF('paginationEnabled'),
      DynamicFieldHelper.createFieldMultiSelectStringHeqF('endpointOptions', false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    const t = this.translateService;
    const isEdit = !!this.endpoint?.feedSupport;
    const usedCombos = this.connectorDef.endpoints
      .filter(e => e !== this.endpoint)
      .map(e => `${e.feedSupport}|${e.instrumentType}`);
    const availableCombos = isEdit
      ? [`${this.endpoint.feedSupport}|${this.endpoint.instrumentType}`]
      : ALL_FEED_INSTRUMENT_COMBOS.filter(c => !usedCombos.includes(c));
    this.configObject.feedSupportInstrumentType.valueKeyHtmlOptions =
      SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(t,
        availableCombos.map(v => new ValueKeyHtmlSelectOptions(v, v)), false);
    if (isEdit) {
      this.configObject.feedSupportInstrumentType.formControl.disable();
    }
    this.configObject.httpMethod.valueKeyHtmlOptions =
      ['GET', 'POST'].map(v => new ValueKeyHtmlSelectOptions(v, v));
    this.configObject.responseFormat.valueKeyHtmlOptions =
      SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(t,
        Object.values(ResponseFormatType).map(v => new ValueKeyHtmlSelectOptions(v, v)), false);
    this.configObject.numberFormat.valueKeyHtmlOptions =
      SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(t,
        Object.values(NumberFormatType).map(v => new ValueKeyHtmlSelectOptions(v, v)), true);
    this.configObject.dateFormatType.valueKeyHtmlOptions =
      SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(t,
        Object.values(DateFormatType).map(v => new ValueKeyHtmlSelectOptions(v, v)), false);
    this.configObject.jsonDataStructure.valueKeyHtmlOptions =
      SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(t,
        Object.values(JsonDataStructure).map(v => new ValueKeyHtmlSelectOptions(v, v)), true);
    this.configObject.htmlExtractMode.valueKeyHtmlOptions =
      SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(t,
        Object.values(HtmlExtractMode).map(v => new ValueKeyHtmlSelectOptions(v, v)), true);
    this.configObject.tickerBuildStrategy.valueKeyHtmlOptions =
      SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(t,
        Object.values(TickerBuildStrategy).map(v => new ValueKeyHtmlSelectOptions(v, v)), true);

    this.updateEndpointOptionsForFeed(this.endpoint?.feedSupport);
    this.configObject.feedSupportInstrumentType.formControl.valueChanges.subscribe(combo => {
      const feedSupport = combo ? combo.split('|')[0] : null;
      this.updateEndpointOptionsForFeed(feedSupport);
      const current: string[] = this.configObject.endpointOptions.formControl.value || [];
      const validKeys = (ENDPOINT_OPTION_BY_FEED[feedSupport] || []).map(o => o as string);
      this.configObject.endpointOptions.formControl.setValue(current.filter(v => validKeys.includes(v)));
    });

    this.form.setDefaultValuesAndEnableSubmit();
    if (this.endpoint) {
      this.form.transferBusinessObjectToForm(this.endpoint);
      if (this.endpoint.feedSupport && this.endpoint.instrumentType) {
        this.configObject.feedSupportInstrumentType.formControl.setValue(
          `${this.endpoint.feedSupport}|${this.endpoint.instrumentType}`);
      }
      this.updateEndpointOptionsForFeed(this.endpoint.feedSupport);
    }
  }

  private updateEndpointOptionsForFeed(feedSupport: string): void {
    const options = feedSupport ? (ENDPOINT_OPTION_BY_FEED[feedSupport] || []) : Object.values(EndpointOption);
    this.configObject.endpointOptions.valueKeyHtmlOptions =
      SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(this.translateService,
        options.map(v => new ValueKeyHtmlSelectOptions(v, v)), false);
  }

  submit(value: { [name: string]: any }): void {
    this.form.cleanMaskAndTransferValuesToBusinessObject(this.endpoint, false);
    const combo = this.configObject.feedSupportInstrumentType.formControl.value;
    if (combo) {
      const [feedSupport, instrumentType] = combo.split('|');
      this.endpoint.feedSupport = feedSupport;
      this.endpoint.instrumentType = instrumentType;
    }
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
  }
}
