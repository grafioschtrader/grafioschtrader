import { UDFDataType } from '../../lib/udfmeta/model/udf.metadata';
import { HelpIds } from '../../lib/help/help.ids';
import { AppSettings } from '../../shared/app.settings';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { UDFMetadataSecurityService } from '../service/udf.metadata.security.service';
import { Component, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AssetClassTypeSpecInstrument } from './asset.class.type.spec.instrument';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { Subscription } from 'rxjs';
import { UDFMetadataHelper } from '../../lib/udfmeta/components/udf.metadata.helper';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import {
  UDFFillResolverOption,
  UDFFillSource,
  UDFFillSourceOption,
  UDFFillState,
  UDFFillStatus,
  UDFFillTestRequest,
  UDFFillTestResult,
  UDFMetadataSecurity,
  UDFMetadataSecurityParam
} from '../model/udf.metadata.security';
import { FormHelper } from '../../lib/dynamic-form/components/FormHelper';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';

import { DialogModule } from '@openng/optimus-ui/dialog';
import { MessageModule } from '@openng/optimus-ui/message';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';
import { UDFFillTestResultComponent } from './udf-fill-test-result.component';
import { UDFFillRuleProposalDialogComponent } from './udf-fill-rule-proposal-dialog.component';
import { UDFFillSuggestTestResultComponent } from './udf-fill-suggest-test-result.component';

/**
 * Component for editing UDF metadata definitions specific to securities.
 * Allows users to create or modify custom field definitions applicable to securities and financial instruments,
 * with filtering by asset class type and special investment instrument. Configures field properties including
 * data type, size, display order, help text, and applicability criteria.
 *
 * A field may also get a fill source other than manual, which computes its values. The selectable fill sources come
 * from the server and depend on the data type; the rule can be tested against the user's securities before saving.
 */
@Component({
  selector: 'udf-metadata-security-edit',
  template: `
    <p-dialog
      header="{{ i18nRecord | translate }}"
      [visible]="visibleDialog"
      [style]="{ width: isAutomaticFill() ? '850px' : '500px' }"
      [contentStyle]="{ display: 'flex', 'flex-direction': 'column', 'min-height': 0 }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      @if (fillStatusText) {
        <p class="bg-info">{{ fillStatusText }}</p>
      }
      @if (isLLMWithoutKeys()) {
        <p-message severity="warn" [text]="'UDF_FILL_LLM_NO_KEYS' | translate"></p-message>
      }
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)">
      </dynamic-form>
      <!-- The content is a flex column so the test result takes only the height the form leaves within the dialog
           and scrolls there; the global overflow:visible of the dialog content keeps form overlays unclipped. -->
      @if (isAutomaticFill() && testResult) {
        <udf-fill-test-result [testResult]="testResult"></udf-fill-test-result>
      }
      @if (isAutomaticFill() && suggestTestRequest) {
        <udf-fill-suggest-test-result [request]="suggestTestRequest"></udf-fill-suggest-test-result>
      }
    </p-dialog>
    @if (visibleProposalDialog) {
      <udf-fill-rule-proposal-dialog
        [idUDFMetadata]="callParam.uDFMetadataSecurity?.idUDFMetadata"
        [visibleDialog]="visibleProposalDialog"
        (ruleAccepted)="acceptProposedRule($event)"
        (closeDialog)="visibleProposalDialog = false">
      </udf-fill-rule-proposal-dialog>
    }
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    DialogModule,
    MessageModule,
    DynamicFormModule,
    TranslateModule,
    UDFFillTestResultComponent,
    UDFFillRuleProposalDialogComponent,
    UDFFillSuggestTestResultComponent
  ]
})
export class UDFMetadataSecurityEditComponent
  extends AssetClassTypeSpecInstrument<UDFMetadataSecurity>
  implements OnInit
{
  private static readonly FILL_SOURCE = 'fillSource';
  private static readonly FILL_RULE = 'fillRule';
  private static readonly TEST_BUTTON = 'testFillRule';
  private static readonly RESOLVER_ID = 'resolverId';
  private static readonly RESOLVER_ARGUMENT = 'resolverArgument';
  private static readonly PROPOSE_BUTTON = 'proposeFillRule';
  private static readonly FILL_CONTEXT = 'fillContext';
  private static readonly TEST_CONTEXT_BUTTON = 'testFillContext';
  private static readonly FILL_PERIOD = 'fillPeriod';

  /** Shows the dialog in which a language model proposes a template rule */
  visibleProposalDialog = false;

  /** True when the instance has a language model that can propose a rule */
  private ruleProposal = false;

  /** True when the user has a language model of the own tenant, which can propose and therefore test values */
  private valueSuggestion = false;

  /** True once the offer of the server arrived; before, a missing key cannot be told from a pending answer */
  private offerLoaded = false;

  /** Draft whose description for the language model is being tested; null hides the test */
  suggestTestRequest: UDFFillTestRequest;

  /** Parameters containing security UDF metadata to edit and validation exclusion lists */
  @Input() callParam: UDFMetadataSecurityParam;

  /** Result of the last test of the fill rule, shown below the form */
  testResult: UDFFillTestResult;

  /** One line summarising the fill state of an existing automatically filled field */
  fillStatusText: string;

  /** Field configurations for data type and field size inputs */
  private configDataTypeFields: FieldConfig[];

  /** Subscription to data type changes for conditional field size enabling/disabling */
  private dataTypeSubscribe: Subscription;

  /** Subscriptions of the fill source fields */
  private fillSubscriptions: Subscription[] = [];

  /** Fill sources offered by the server */
  private fillSourceOptions: UDFFillSourceOption[] = [];

  /** Resolvers of the fill source RESOLVER offered by the server */
  private resolverOptions: UDFFillResolverOption[] = [];

  /** Fill periods offered by the server for RESOLVER and LLM, without the empty choice */
  private fillPeriods: string[] = [];

  /**
   * Creates the UDF metadata security edit component.
   *
   * @param translateService - Translation service for i18n support
   * @param gpsGT - GT-specific global parameter service for asset class mappings
   * @param gps - Global parameter service providing user settings and system configuration
   * @param messageToastService - Service for displaying user notifications
   * @param uDFMetadataSecurityService - Service for security UDF metadata CRUD operations and the fill endpoints
   */
  constructor(
    translateService: TranslateService,
    gpsGT: GlobalparameterGTService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    private uDFMetadataSecurityService: UDFMetadataSecurityService
  ) {
    super(
      'categoryTypeEnums',
      gpsGT,
      'specialInvestmentInstrumentEnums',
      HelpIds.HELP_BASEDATA_UDF_METADATA_SECURITY,
      AppHelper.toUpperCaseWithUnderscore(AppSettings.UDF_METADATA_SECURITY),
      translateService,
      gps,
      messageToastService,
      uDFMetadataSecurityService
    );
  }

  /**
   * Initializes component configuration and form fields.
   * Sets up metadata fields, asset class selector, special instrument selector and the fill source fields.
   */
  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.configDataTypeFields = UDFMetadataHelper.createDataTypeFields();
    this.config = [
      ...UDFMetadataHelper.createMetadataBaseFields(this.configDataTypeFields, this.callParam.excludeFieldNames),
      DynamicFieldHelper.createFieldMultiSelectString('categoryTypeEnums', AppSettings.ASSETCLASS.toUpperCase(), true),
      DynamicFieldHelper.createFieldMultiSelectString('specialInvestmentInstrumentEnums', 'FINANCIAL_INSTRUMENT', true),
      DynamicFieldHelper.createFieldSelectStringHeqF(UDFMetadataSecurityEditComponent.FILL_SOURCE, true),
      DynamicFieldHelper.createFieldSelectStringHeqF(UDFMetadataSecurityEditComponent.FILL_PERIOD, false, {
        labelHelpText: 'FILL_PERIOD_HELP'
      }),
      DynamicFieldHelper.createFieldSelectStringHeqF(UDFMetadataSecurityEditComponent.RESOLVER_ID, false),
      DynamicFieldHelper.createFieldInputStringHeqF(UDFMetadataSecurityEditComponent.RESOLVER_ARGUMENT, 64, false, {
        labelHelpText: 'RESOLVER_ARGUMENT_HELP'
      }),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF(
        UDFMetadataSecurityEditComponent.FILL_CONTEXT,
        1000,
        false,
        {
          textareaRows: 4,
          labelHelpText: 'FILL_CONTEXT_HELP'
        }
      ),
      DynamicFieldHelper.createFunctionButtonFieldName(
        UDFMetadataSecurityEditComponent.TEST_CONTEXT_BUTTON,
        'UDF_FILL_TEST_LLM',
        () => (this.suggestTestRequest = this.createTestRequest(UDFMetadataSecurityEditComponent.FILL_CONTEXT))
      ),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF(UDFMetadataSecurityEditComponent.FILL_RULE, 1000, false, {
        textareaRows: 6,
        labelHelpText: 'FILL_RULE_HELP'
      }),
      DynamicFieldHelper.createFunctionButtonFieldName(
        UDFMetadataSecurityEditComponent.TEST_BUTTON,
        'UDF_FILL_TEST',
        () => this.testFillRule()
      ),
      DynamicFieldHelper.createFunctionButtonFieldName(
        UDFMetadataSecurityEditComponent.PROPOSE_BUTTON,
        'UDF_FILL_PROPOSE',
        () => (this.visibleProposalDialog = true)
      ),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  /**
   * Tells whether the language model is selected although the tenant has no key: such a field is saved and keeps its
   * values, but nothing fills it.
   */
  isLLMWithoutKeys(): boolean {
    return (
      this.offerLoaded &&
      !this.valueSuggestion &&
      this.configObject?.[UDFMetadataSecurityEditComponent.FILL_SOURCE]?.formControl?.value === UDFFillSource.LLM
    );
  }

  /**
   * Tells whether a fill source other than manual is selected, which shows the rule, the test and a wider dialog.
   */
  isAutomaticFill(): boolean {
    const fillSource = this.configObject?.[UDFMetadataSecurityEditComponent.FILL_SOURCE]?.formControl?.value;
    return !!fillSource && fillSource !== UDFFillSource.MANUAL;
  }

  /**
   * Completes component initialization after asset class mappings are loaded.
   * Sets up data type fields, populates data type options and loads the fill sources offered by the server.
   *
   * @protected
   */
  protected override initializeOthers(): void {
    this.configObject.udfDataType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService,
      UDFDataType
    );
    this.configObject[UDFMetadataSecurityEditComponent.FILL_SOURCE].valueKeyHtmlOptions = [
      new ValueKeyHtmlSelectOptions(UDFFillSource.MANUAL, this.translateService.instant(UDFFillSource.MANUAL))
    ];
    this.dataTypeSubscribe = UDFMetadataHelper.prepareDataTypeFields(
      this.translateService,
      this.form,
      this.configObject,
      this.callParam.excludeUiOrders,
      this.callParam.uDFMetadataSecurity,
      this.configDataTypeFields
    );
    const existing = this.callParam.uDFMetadataSecurity;
    this.configObject[UDFMetadataSecurityEditComponent.FILL_SOURCE].formControl.setValue(
      existing?.fillSource ?? UDFFillSource.MANUAL
    );
    this.transferResolverRule(existing);
    if (existing?.fillSource === UDFFillSource.LLM) {
      this.configObject[UDFMetadataSecurityEditComponent.FILL_CONTEXT].formControl.setValue(existing.fillRule);
    }
    this.fillSubscriptions.push(
      this.configObject[UDFMetadataSecurityEditComponent.FILL_SOURCE].formControl.valueChanges.subscribe(() =>
        this.showHideFillRule()
      ),
      this.groupControl(UDFMetadataHelper.UDF_DATA_TYPE).valueChanges.subscribe((udt) =>
        this.setFillSourceOptions(udt)
      ),
      this.configObject[UDFMetadataSecurityEditComponent.RESOLVER_ID].formControl.valueChanges.subscribe((id) =>
        this.setDefaultArgument(id)
      )
    );
    this.showHideFillRule();
    this.uDFMetadataSecurityService.getFillSources().subscribe((offer) => {
      this.fillSourceOptions = offer.fillSources;
      this.resolverOptions = offer.resolvers;
      this.ruleProposal = offer.ruleProposal;
      this.valueSuggestion = offer.valueSuggestion;
      this.offerLoaded = true;
      this.fillPeriods = offer.fillPeriods ?? [];
      this.setFillSourceOptions(this.groupControl(UDFMetadataHelper.UDF_DATA_TYPE).value);
      this.showHideFillRule();
      this.configObject[UDFMetadataSecurityEditComponent.FILL_PERIOD].formControl.setValue(existing?.fillPeriod ?? '');
    });
    this.loadFillStatus(existing);
  }

  /**
   * Offers only the fill sources that support the selected data type; a selection no longer offered falls back to
   * manual.
   *
   * @param udfDataType - The selected data type
   */
  private setFillSourceOptions(udfDataType: string): void {
    if (this.fillSourceOptions.length === 0) {
      return;
    }
    const fillSourceConfig = this.configObject[UDFMetadataSecurityEditComponent.FILL_SOURCE];
    const fitting = this.fillSourceOptions.filter((o) => udfDataType && o.udfDataTypes.includes(udfDataType));
    fillSourceConfig.valueKeyHtmlOptions = (
      fitting.length > 0 ? fitting.map((o) => o.fillSource) : [UDFFillSource.MANUAL]
    ).map((fs) => new ValueKeyHtmlSelectOptions(fs, this.translateService.instant(fs)));
    if (!fillSourceConfig.valueKeyHtmlOptions.some((o) => o.key === fillSourceConfig.formControl.value)) {
      fillSourceConfig.formControl.setValue(UDFFillSource.MANUAL);
    }
    const resolverConfig = this.configObject[UDFMetadataSecurityEditComponent.RESOLVER_ID];
    resolverConfig.valueKeyHtmlOptions = this.resolverOptions
      .filter((r) => udfDataType && r.udfDataTypes.includes(udfDataType))
      .map((r) => new ValueKeyHtmlSelectOptions(r.resolverId, this.translateService.instant(r.resolverId)));
    if (!resolverConfig.valueKeyHtmlOptions.some((o) => o.key === resolverConfig.formControl.value)) {
      resolverConfig.formControl.setValue(resolverConfig.valueKeyHtmlOptions[0]?.key ?? null);
    }
  }

  /**
   * Splits the stored rule of a resolver field into resolver and argument.
   */
  private transferResolverRule(existing: UDFMetadataSecurity): void {
    if (existing?.fillSource === UDFFillSource.RESOLVER && existing.fillRule) {
      const rule = existing.fillRule.trim();
      const space = rule.indexOf(' ');
      this.configObject[UDFMetadataSecurityEditComponent.RESOLVER_ID].formControl.setValue(
        space < 0 ? rule : rule.substring(0, space)
      );
      this.configObject[UDFMetadataSecurityEditComponent.RESOLVER_ARGUMENT].formControl.setValue(
        space < 0 ? null : rule.substring(space + 1).trim()
      );
    }
  }

  /**
   * Fills the argument with the default of a newly selected resolver when it is still empty.
   */
  private setDefaultArgument(resolverId: string): void {
    const argument = this.configObject[UDFMetadataSecurityEditComponent.RESOLVER_ARGUMENT].formControl;
    const resolver = this.resolverOptions.find((r) => r.resolverId === resolverId);
    if (resolver && !argument.value) {
      argument.setValue(resolver.defaultArgument);
    }
  }

  /**
   * Shows the rule and the test button for a template, the resolver and its argument for a lookup, and nothing of
   * them for manual.
   */
  private showHideFillRule(): void {
    const fillSource = this.configObject[UDFMetadataSecurityEditComponent.FILL_SOURCE].formControl.value;
    const template = fillSource === UDFFillSource.TEMPLATE;
    const resolver = fillSource === UDFFillSource.RESOLVER;
    FormHelper.hideVisibleFieldConfigs(!template, [
      this.configObject[UDFMetadataSecurityEditComponent.FILL_RULE],
      this.configObject[UDFMetadataSecurityEditComponent.TEST_BUTTON]
    ]);
    FormHelper.hideVisibleFieldConfigs(fillSource !== UDFFillSource.LLM, [
      this.configObject[UDFMetadataSecurityEditComponent.FILL_CONTEXT]
    ]);
    FormHelper.hideVisibleFieldConfigs(fillSource !== UDFFillSource.LLM || !this.valueSuggestion, [
      this.configObject[UDFMetadataSecurityEditComponent.TEST_CONTEXT_BUTTON]
    ]);
    FormHelper.hideVisibleFieldConfigs(!resolver, [
      this.configObject[UDFMetadataSecurityEditComponent.RESOLVER_ID],
      this.configObject[UDFMetadataSecurityEditComponent.RESOLVER_ARGUMENT]
    ]);
    this.setFillPeriodOptions(fillSource);
    // A rule is proposed from the stored values of a saved field that is filled manually or by a template
    const existing = this.callParam.uDFMetadataSecurity;
    const proposable =
      template &&
      this.ruleProposal &&
      !!existing?.idUDFMetadata &&
      (existing.fillSource === UDFFillSource.MANUAL || existing.fillSource === UDFFillSource.TEMPLATE);
    FormHelper.hideVisibleFieldConfigs(!proposable, [
      this.configObject[UDFMetadataSecurityEditComponent.PROPOSE_BUTTON]
    ]);
    if (!template) {
      this.testResult = null;
    }
    if (fillSource !== UDFFillSource.LLM) {
      this.suggestTestRequest = null;
    }
  }

  /**
   * Shows the fill period for a lookup and a language model only. The empty choice means something different for
   * each: a lookup follows the global refresh setting, a language model is asked on request only.
   *
   * @param fillSource - The selected fill source
   */
  private setFillPeriodOptions(fillSource: string): void {
    const periodConfig = this.configObject[UDFMetadataSecurityEditComponent.FILL_PERIOD];
    const withPeriod = fillSource === UDFFillSource.RESOLVER || fillSource === UDFFillSource.LLM;
    FormHelper.hideVisibleFieldConfigs(!withPeriod || this.fillPeriods.length === 0, [periodConfig]);
    const emptyLabel = fillSource === UDFFillSource.RESOLVER ? 'FILL_PERIOD_GLOBAL' : 'FILL_PERIOD_ON_REQUEST';
    periodConfig.valueKeyHtmlOptions = [
      new ValueKeyHtmlSelectOptions('', this.translateService.instant(emptyLabel)),
      ...this.fillPeriods.map((fp) => new ValueKeyHtmlSelectOptions(fp, this.translateService.instant(fp)))
    ];
    if (!withPeriod) {
      periodConfig.formControl.setValue('');
    }
  }

  /**
   * Takes the rule proposed by the language model over into the rule of the form; it is saved with the field.
   *
   * @param rule - The proposed rule
   */
  acceptProposedRule(rule: string): void {
    this.configObject[UDFMetadataSecurityEditComponent.FILL_RULE].formControl.setValue(rule);
    this.testResult = null;
  }

  /**
   * Loads the one-line fill status of an existing field with a fill source other than manual.
   */
  private loadFillStatus(existing: UDFMetadataSecurity): void {
    this.fillStatusText = null;
    if (existing?.idUDFMetadata && existing.fillSource && existing.fillSource !== UDFFillSource.MANUAL) {
      this.uDFMetadataSecurityService
        .getFillStatus(existing.idUDFMetadata)
        .subscribe((status) => (this.fillStatusText = this.formatFillStatus(status)));
    }
  }

  private formatFillStatus(status: UDFFillStatus): string {
    const count = (state: string) => status.countByState?.[state] ?? 0;
    let text: string = this.translateService.instant('UDF_FILL_STATUS', {
      inScope: status.inScope,
      applied: count(UDFFillState.APPLIED),
      locked: count(UDFFillState.LOCKED),
      manual: status.manual,
      notFound: count(UDFFillState.NOT_FOUND)
    });
    if (existingIsLLM(this.callParam.uDFMetadataSecurity)) {
      text += this.translateService.instant('UDF_FILL_STATUS_PROPOSALS', {
        proposed: count(UDFFillState.PROPOSED),
        rejected: count(UDFFillState.REJECTED),
        discarded: count(UDFFillState.DISCARDED)
      });
    }
    if (status.running) {
      text += ', ' + this.translateService.instant('UDF_FILL_STATUS_RUNNING');
    } else if (status.waiting) {
      text += ', ' + this.translateService.instant('UDF_FILL_STATUS_WAITING');
    }
    return text;
  }

  /**
   * Evaluates the rule of the form against the securities of the user and shows the counters and examples. Nothing
   * is saved.
   */
  private testFillRule(): void {
    this.uDFMetadataSecurityService
      .testFillRule(this.createTestRequest(UDFMetadataSecurityEditComponent.FILL_RULE))
      .subscribe((testResult) => (this.testResult = testResult));
  }

  /**
   * Builds the draft of a test from the form, so an unsaved change is tested and not the stored field.
   *
   * @param ruleField - The form field holding the rule: the template rule or the description for the model
   * @returns A new draft; a new object also restarts the test of the description
   */
  private createTestRequest(ruleField: string): UDFFillTestRequest {
    const value = (field: string) => this.configObject[field].formControl.value;
    return {
      idUDFMetadata: this.callParam.uDFMetadataSecurity?.idUDFMetadata ?? null,
      udfDataType: this.groupControl(UDFMetadataHelper.UDF_DATA_TYPE).value,
      fieldSize: this.groupControl(UDFMetadataHelper.FIELD_SIZE).value,
      categoryTypeEnums: value('categoryTypeEnums'),
      specialInvestmentInstrumentEnums: value('specialInvestmentInstrumentEnums'),
      fillSource: value(UDFMetadataSecurityEditComponent.FILL_SOURCE),
      fillRule: value(ruleField)
    };
  }

  /**
   * Returns a control of the form group that holds the data type and the field size.
   */
  private groupControl(field: string): AbstractControl {
    return this.configObject[UDFMetadataHelper.DATA_TYPE_FIELD_GROUP].formControl.get([field]);
  }

  /**
   * Prepares security UDF metadata entity for saving by copying form values and setting user ownership.
   *
   * @param value - Form values to save
   * @returns Security UDF metadata entity ready for persistence
   * @protected
   */
  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): UDFMetadataSecurity {
    const uDFMetadataSecurity: UDFMetadataSecurity = this.copyFormToPrivateBusinessObject(
      new UDFMetadataSecurity(),
      this.callParam.uDFMetadataSecurity
    );
    uDFMetadataSecurity.idUser = this.gps.getIdUser();
    // Resolver, argument and description are form fields only; the entity has no such properties, so the form copy
    // above leaves them out and they are read from their controls.
    const formValue = (field: string) => this.configObject[field].formControl.value;
    if (uDFMetadataSecurity.fillSource === UDFFillSource.RESOLVER) {
      // The server stores the rule as resolver ID and normalised argument
      uDFMetadataSecurity.fillRule = [
        formValue(UDFMetadataSecurityEditComponent.RESOLVER_ID),
        formValue(UDFMetadataSecurityEditComponent.RESOLVER_ARGUMENT)
      ]
        .filter((part) => !!part && String(part).trim().length > 0)
        .join(' ');
    }
    if (uDFMetadataSecurity.fillSource === UDFFillSource.LLM) {
      // The rule of a language model field is the description for the model
      uDFMetadataSecurity.fillRule = formValue(UDFMetadataSecurityEditComponent.FILL_CONTEXT);
    }
    // The empty choice of the select is no period
    uDFMetadataSecurity.fillPeriod = uDFMetadataSecurity.fillPeriod || null;
    return uDFMetadataSecurity;
  }

  /**
   * Cleanup handler when dialog is closed.
   * Unsubscribes from data type, category type and fill source change listeners to prevent memory leaks.
   *
   * @param event - Dialog hide event
   */
  override onHide(event): void {
    this.dataTypeSubscribe && this.dataTypeSubscribe.unsubscribe();
    this.fillSubscriptions.forEach((s) => s.unsubscribe());
    this.fillSubscriptions = [];
    this.testResult = null;
    this.suggestTestRequest = null;
    super.onHide(event);
  }
}

/**
 * Tells whether a stored field gets its values proposed by a language model.
 */
function existingIsLLM(field: UDFMetadataSecurity): boolean {
  return field?.fillSource === UDFFillSource.LLM;
}
