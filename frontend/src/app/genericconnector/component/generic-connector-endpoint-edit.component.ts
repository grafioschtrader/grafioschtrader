import { Component, computed, input, inject, OnInit, signal } from '@angular/core';
import { disabled, FieldContext, metadata, PathKind, required, SchemaPathTree } from '@angular/forms/signals';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { SimpleFormEditBase } from '../../lib/edit/simple.form.edit.base';
import { GenericConnectorEndpoint } from '../../entities/generic.connector.endpoint';
import { GenericConnectorDef } from '../../entities/generic.connector.def';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppHelpIds } from '../../shared/help/help.ids';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { ResponseFormatType } from '../../shared/types/response.format.type';
import { DateFormatType } from '../../shared/types/date.format.type';
import { JsonDataStructure } from '../../shared/types/json.data.structure';
import { NumberFormatType } from '../../shared/types/number.format.type';
import { TickerBuildStrategy } from '../../shared/types/ticker.build.strategy';
import { HtmlExtractMode } from '../../shared/types/html.extract.mode';
import { EndpointOption, ENDPOINT_OPTION_BY_FEED } from '../../shared/types/endpoint.option';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { FIELD_DISPLAY, SignalValueLink } from '../../lib/dynamic-form/signal/signal-form.model';

const ALL_FEED_INSTRUMENT_COMBOS = [
  'FS_HISTORY|SECURITY',
  'FS_HISTORY|CURRENCY',
  'FS_INTRA|SECURITY',
  'FS_INTRA|CURRENCY'
];

/** The fields of the endpoint form its dependency rules refer to. */
interface EndpointFormModel {
  feedSupportInstrumentType: string | null;
  responseFormat: string | null;
  numberFormat: string | null;
  dateFormatType: string | null;
  dateFormatPattern: string | null;
  jsonDataStructure: string | null;
  jsonDataPath: string | null;
  jsonColumnNamesPath: string | null;
  jsonStatusPath: string | null;
  jsonStatusOkValue: string | null;
  csvDelimiter: string | null;
  csvSkipHeaderLines: number | null;
  htmlCssSelector: string | null;
  htmlExtractMode: string | null;
  htmlTextCleanup: string | null;
  htmlExtractRegex: string | null;
  htmlSplitDelimiter: string | null;
  tickerBuildStrategy: string | null;
  currencyPairSeparator: string | null;
  currencyPairSuffix: string | null;
  endpointOptions: string[] | null;
}

/** Fields whose options are translated constants, with an empty entry for the optional ones. */
type ChoiceField =
  | 'feedSupportInstrumentType'
  | 'responseFormat'
  | 'numberFormat'
  | 'dateFormatType'
  | 'jsonDataStructure'
  | 'htmlExtractMode'
  | 'tickerBuildStrategy';

const JSON_FIELDS = [
  'jsonDataStructure',
  'jsonDataPath',
  'jsonColumnNamesPath',
  'jsonStatusPath',
  'jsonStatusOkValue'
] as const;
const HTML_FIELDS = [
  'htmlCssSelector',
  'htmlExtractMode',
  'htmlTextCleanup',
  'htmlExtractRegex',
  'htmlSplitDelimiter'
] as const;

/**
 * Edit dialog for GenericConnectorEndpoint. Saves by updating the endpoint object in place;
 * the parent component handles persisting the full GenericConnectorDef via PUT.
 *
 * Prototype dialog of the signal dynamic form: the fields of the response format, the HTML extraction mode, the JSON
 * structure and the ticker strategy depend on each other through schema rules instead of valueChanges subscriptions.
 * A disabled field keeps its value while it is disabled and transfers null on save (transferDisabledAsNull); the
 * endpoint options follow the chosen feed through a value link.
 */
@Component({
  selector: 'generic-connector-endpoint-edit',
  template: `
    <p-dialog
      header="{{ 'ENDPOINT_SETTINGS' | translate }}"
      [visible]="visibleDialog()"
      [style]="{ width: '900px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <signal-dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [rules]="rules"
        [source]="endpoint()"
        [values]="startValues()"
        [valueLinks]="valueLinks"
        [transferDisabledAsNull]="true"
        #form="signalDynamicForm"
        (submitBt)="submit()" />
    </p-dialog>
  `,
  imports: [DialogModule, SignalDynamicFormComponent, TranslateModule]
})
export class GenericConnectorEndpointEditComponent
  extends SimpleFormEditBase<SignalDynamicFormComponent<EndpointFormModel>>
  implements OnInit
{
  public translateService: TranslateService = inject(TranslateService);

  readonly endpoint = input.required<GenericConnectorEndpoint>();
  readonly connectorDef = input.required<GenericConnectorDef>();

  /** Labels of the option constants; null until loaded in initialize(), so the selects never list raw keys. */
  private readonly optionTranslations = signal<Record<string, string> | null>(null);
  private readonly isEdit = computed(() => !!this.endpoint()?.feedSupport);

  /** The feed and instrument combination is no property of the endpoint, so it is a start value of its own. */
  protected readonly startValues = computed<Partial<EndpointFormModel>>(() => {
    const endpoint = this.endpoint();
    return endpoint?.feedSupport && endpoint.instrumentType
      ? { feedSupportInstrumentType: `${endpoint.feedSupport}|${endpoint.instrumentType}` }
      : {};
  });

  /** Selected endpoint options that the chosen feed does not offer are removed when the feed changes. */
  readonly valueLinks: SignalValueLink<EndpointFormModel>[] = [
    {
      sources: ['feedSupportInstrumentType'],
      target: 'endpointOptions',
      derive: (values, current) => {
        const valid = this.endpointOptionsOf(values.feedSupportInstrumentType ?? null);
        return Array.isArray(current) ? current.filter((option) => valid.includes(option)) : current;
      }
    }
  ];

  /** Feed and instrument combinations: the one of an existing endpoint, otherwise those not used by another one. */
  private readonly availableCombos = computed(() => {
    const endpoint = this.endpoint();
    if (this.isEdit()) {
      return [`${endpoint.feedSupport}|${endpoint.instrumentType}`];
    }
    const used = this.connectorDef()
      .endpoints.filter((e) => e !== endpoint)
      .map((e) => `${e.feedSupport}|${e.instrumentType}`);
    return ALL_FEED_INSTRUMENT_COMBOS.filter((c) => !used.includes(c));
  });

  private readonly choiceOptions = computed<Record<ChoiceField, ValueKeyHtmlSelectOptions[]>>(() => ({
    feedSupportInstrumentType: this.translatedOptions(this.availableCombos()),
    responseFormat: this.translatedOptions(Object.values(ResponseFormatType)),
    numberFormat: this.translatedOptions(Object.values(NumberFormatType)),
    dateFormatType: this.translatedOptions(Object.values(DateFormatType)),
    jsonDataStructure: this.translatedOptions(Object.values(JsonDataStructure), true),
    htmlExtractMode: this.translatedOptions(Object.values(HtmlExtractMode), true),
    tickerBuildStrategy: this.translatedOptions(Object.values(TickerBuildStrategy))
  }));

  /** Dependency rules of the form; disabled fields are not validated, so `required` needs no condition of its own. */
  readonly rules = (p: SchemaPathTree<EndpointFormModel>): void => {
    (Object.keys(this.choiceOptions()) as ChoiceField[]).forEach((field) =>
      metadata(p[field], FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.choiceOptions()[field] }))
    );
    metadata(p.endpointOptions, FIELD_DISPLAY, ({ valueOf }) => ({
      valueKeyHtmlOptions: this.translatedOptions(this.endpointOptionsOf(valueOf(p.feedSupportInstrumentType)))
    }));
    disabled(p.feedSupportInstrumentType, { when: () => this.isEdit() });

    disabled(p.dateFormatPattern, { when: ({ valueOf }) => valueOf(p.dateFormatType) !== DateFormatType.PATTERN });
    required(p.dateFormatPattern);

    const notFormat =
      (format: ResponseFormatType) =>
      ({ valueOf }: FieldContext<unknown, PathKind.Child>) =>
        valueOf(p.responseFormat) !== format;
    JSON_FIELDS.forEach((f) => disabled(p[f], { when: notFormat(ResponseFormatType.JSON) }));
    disabled(p.csvDelimiter, { when: notFormat(ResponseFormatType.CSV) });
    disabled(p.csvSkipHeaderLines, { when: notFormat(ResponseFormatType.CSV) });
    HTML_FIELDS.forEach((f) => disabled(p[f], { when: notFormat(ResponseFormatType.HTML) }));
    required(p.jsonDataStructure);
    required(p.jsonColumnNamesPath, {
      when: ({ valueOf }) => valueOf(p.jsonDataStructure) === JsonDataStructure.COLUMN_ROW_ARRAYS
    });

    required(p.htmlExtractMode);
    required(p.htmlCssSelector, {
      when: ({ valueOf }) => valueOf(p.htmlExtractMode) !== HtmlExtractMode.MULTI_SELECTOR
    });
    required(p.htmlExtractRegex, {
      when: ({ valueOf }) => valueOf(p.htmlExtractMode) === HtmlExtractMode.REGEX_GROUPS
    });
    disabled(p.htmlExtractRegex, {
      when: ({ valueOf }) =>
        [HtmlExtractMode.SPLIT_POSITIONS, HtmlExtractMode.MULTI_SELECTOR].includes(
          valueOf(p.htmlExtractMode) as HtmlExtractMode
        )
    });
    disabled(p.htmlSplitDelimiter, {
      when: ({ valueOf }) =>
        [HtmlExtractMode.REGEX_GROUPS, HtmlExtractMode.MULTI_SELECTOR].includes(
          valueOf(p.htmlExtractMode) as HtmlExtractMode
        )
    });

    disabled(p.currencyPairSeparator, {
      when: ({ valueOf }) => valueOf(p.tickerBuildStrategy) === TickerBuildStrategy.URL_EXTEND
    });
    disabled(p.currencyPairSuffix, {
      when: ({ valueOf }) => valueOf(p.tickerBuildStrategy) === TickerBuildStrategy.URL_EXTEND
    });
  };

  constructor() {
    super(AppHelpIds.HELP_BASEDATA_GENERIC_CONNECTOR_ENDPOINTS);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectString('feedSupportInstrumentType', 'FEED_INSTRUMENT_TYPE', true),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('urlTemplate', 1000, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('httpMethod', true, {
        valueKeyHtmlOptions: ['GET', 'POST'].map((v) => new ValueKeyHtmlSelectOptions(v, v))
      }),
      DynamicFieldHelper.createFieldSelectStringHeqF('responseFormat', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('numberFormat', true),
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
      DynamicFieldHelper.createFieldSelectStringHeqF('tickerBuildStrategy', true),
      DynamicFieldHelper.createFieldInputStringHeqF('currencyPairSeparator', 4, false),
      DynamicFieldHelper.createFieldInputStringHeqF('currencyPairSuffix', 20, false),
      DynamicFieldHelper.createFieldCheckboxHeqF('tickerUppercase'),
      DynamicFieldHelper.createFieldInputNumberHeqF('maxDataPoints', false, 7, 0, false),
      DynamicFieldHelper.createFieldCheckboxHeqF('paginationEnabled'),
      DynamicFieldHelper.createFieldMultiSelectStringHeqF('endpointOptions', false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
  }

  protected override initialize(): void {
    // The form shows the endpoint from its first rendering ([source], [values]); only the option labels load here.
    const keys = [
      ...ALL_FEED_INSTRUMENT_COMBOS,
      ...Object.values(ResponseFormatType),
      ...Object.values(NumberFormatType),
      ...Object.values(DateFormatType),
      ...Object.values(JsonDataStructure),
      ...Object.values(HtmlExtractMode),
      ...Object.values(TickerBuildStrategy),
      ...Object.values(EndpointOption)
    ];
    this.translateService
      .get([...new Set(keys)])
      .pipe(this.untilDialogClosed())
      .subscribe((translations) => this.optionTranslations.set(translations));
  }

  submit(): void {
    this.form().cleanMaskAndTransferValuesToBusinessObject(this.endpoint(), false);
    const combo = this.form().value('feedSupportInstrumentType');
    if (typeof combo === 'string' && combo) {
      const [feedSupport, instrumentType] = combo.split('|');
      const endpoint = this.endpoint();
      endpoint.feedSupport = feedSupport;
      endpoint.instrumentType = instrumentType;
    }
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
  }

  /** The endpoint options a feed supports; every option while no feed is chosen. */
  private endpointOptionsOf(combo: string | null): string[] {
    const feedSupport = combo ? combo.split('|')[0] : null;
    return feedSupport ? ENDPOINT_OPTION_BY_FEED[feedSupport] || [] : Object.values(EndpointOption);
  }

  private translatedOptions(values: string[], addEmpty = false): ValueKeyHtmlSelectOptions[] {
    const translations = this.optionTranslations();
    if (!translations) {
      return [];
    }
    const options = values
      .map((value) => new ValueKeyHtmlSelectOptions(value, translations[value] || value))
      .sort((a, b) => a.value.localeCompare(b.value));
    return addEmpty ? [new ValueKeyHtmlSelectOptions('', ''), ...options] : options;
  }
}
