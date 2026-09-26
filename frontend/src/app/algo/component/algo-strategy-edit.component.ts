import { ViewChild, Component, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { AlgoStrategy } from '../model/algo.strategy';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { HelpIds } from '../../lib/help/help.ids';
import { AlgoStrategyService } from '../service/algo.strategy.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { AlgoCallParam } from '../model/algo.dialog.visible';
import { AlgoStrategyImplementationType } from '../../shared/types/algo.strategy.implementation.type';
import { Subscription } from 'rxjs';
import { FieldDescriptorInputAndShow } from '../../lib/dynamicfield/field.descriptor.input.and.show';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { BaseParam } from '../../lib/entities/base.param';
import { AppSettings } from '../../shared/app.settings';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AlgoStrategyHelper } from './algo.strategy.helper';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { YamlEditorComponent } from './yaml-editor.component';
import { ButtonModule } from '@openng/optimus-ui/button';
import * as yaml from 'js-yaml';

/** Default YAML template for the Mean Reversion Dip strategy */
const STRATEGY_TEMPLATE_YAML = `strategy_name: daily_dip_with_stop
version: "1.0"
universe:
  mode: watchlist
  direction: long_only
data:
  price_field: close
  timeframe: 1d
execution:
  order_type: market
  slippage_model: none
  fees_model: none
cooldowns:
  after_buy_days: 2
  after_sell_days: 2
  max_trades_per_asset_per_30d: 10
entry:
  type: dip_buy
  lookback_T: 10
  dip_reference:
    type: price_T_ago
  dip_threshold_pct: -0.12
  initial_buy_sizing:
    mode: pct_portfolio
    pct: 0.03
profit_management:
  # Partial profit taking. Switch scale_out_enabled on to give the position back in the tranches below;
  # each of them executes at most once per position, and the last one closes whatever is left.
  scale_out_enabled: false
  sell_fraction_basis: current_position
  scale_out_plan:
    - id: t1
      trigger:
        type: pct_gain
        value: 0.04
        reference: avg_cost
      sell_fraction: 0.33
    - id: t2
      trigger:
        type: pct_gain
        value: 0.07
        reference: avg_cost
      sell_remainder: true
  take_profit:
    mode: pct_gain
    pct: 0.10
    reference: avg_cost
    action: sell_all_remaining
downside_management:
  trigger:
    down_reference: avg_cost
    down_threshold_pct: -0.10
    decision_basis: simple_threshold
  loss_action: A_sell_loss
  variant_A_sell_loss:
    enabled: true
    stop_type: hard_stop
    stop_reference: avg_cost
    stop_threshold_pct: -0.10
    order_type: market
    action: sell_all_remaining
  variant_B_average_down:
    enabled: false
    add_sizing:
      mode: pct_portfolio
      pct: 0.01
    max_adds: 3
    add_step_rule:
      type: each_n_pct_drop
      reference: initial_entry_price
      drop_pct_step: 0.05
    recalculate_avg_cost: true
risk_controls:
  max_position_exposure_pct: 0.10
  max_position_drawdown_pct: 0.25
  force_exit_on_risk_breach: true
  block_entry_if_exposure_exceeded: true
  block_add_if_exposure_exceeded: true
`;

/**
 * Allows editing a new or existing strategy. The input fields will be different according to the selected strategy.
 * For simple strategies, input fields are created dynamically from FieldDescriptorInputAndShow definitions.
 * For complex strategies (e.g. AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP), a Monaco YAML editor is shown to edit the nested JSON config
 * with syntax highlighting, autocompletion, validation, and hover documentation.
 */
@Component({
  selector: 'algo-strategy-edit',
  template: ` <p-dialog
    styleClass="big-dialog"
    header="{{ 'ALGO_STRATEGY' | translate }}"
    [visible]="visibleDialog"
    [style]="{ width: dialogWidth }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)">
    </dynamic-form>

    @if (isComplexStrategy) {
      <yaml-editor
        #yamlEditor
        format="STRATEGY"
        [activatable]="configObject.activatable?.formControl?.value !== false"
        [(value)]="yamlContent"
        [height]="'500px'"
        [schema]="configObject.activatable?.formControl?.value === false ? draftYamlSchema : yamlSchema" />
      <div class="mt-2 text-end">
        <p-button [label]="'LOAD_TEMPLATE' | translate" severity="secondary" (click)="loadTemplate()" styleClass="me-2">
          <i class="pi pi-file" pButtonIcon></i>
        </p-button>
        <p-button
          [label]="'APPLY' | translate"
          [disabled]="yamlEditor.validating || !yamlEditor.syntaxValid"
          (click)="submitComplexStrategy()">
          <i class="pi pi-check" pButtonIcon></i>
        </p-button>
      </div>
    }
  </p-dialog>`,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslateModule, DialogModule, DynamicFormComponent, YamlEditorComponent, ButtonModule]
})
export class AlgoStrategyEditComponent extends SimpleEntityEditBase<AlgoStrategy> implements OnInit {
  @ViewChild(YamlEditorComponent) yamlEditor: YamlEditorComponent;
  @Input() algoCallParam: AlgoCallParam;

  dynamicModel: any = {};
  algoStrategyImplementationsChangedSub: Subscription;
  ignoreValueChanged = false;
  isComplexStrategy = false;

  fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[];

  dialogWidth = '700px';

  /** YAML content for complex strategies, bound to the Monaco editor */
  yamlContent = '';

  /** JSON Schema for the Mean Reversion Dip strategy, loaded from assets */
  yamlSchema: any;
  draftYamlSchema: any;

  private static readonly FIELD_STRATEGY_CONFIG = 'strategyConfig';

  constructor(
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    private algoStrategyService: AlgoStrategyService
  ) {
    super(HelpIds.HELP_ALGO_STRATEGY, 'ALGO_STRATEGY', translateService, gps, messageToastService, algoStrategyService);
    this.loadYamlSchema();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 6, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectString(AlgoStrategyHelper.FIELD_STRATEGY_IMPL, 'ALGO_STRATEGY_NAME', true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.isComplexStrategy = false;
    this.dialogWidth = '700px';
    this.yamlContent = '';
    this.config = [this.config[0], this.config[this.config.length - 1]];
    this.config[this.config.length - 1].invisible = false;
    this.configObject[AlgoStrategyHelper.FIELD_STRATEGY_IMPL].formControl.enable();
    this.valueChangedOnAlgoStrategyImplementation();
    if (this.algoCallParam.thisObject) {
      this.ignoreValueChanged = false;
      this.preparePossibleStrategies();
    } else {
      const possibleValues: AlgoStrategyImplementationType[] =
        this.algoCallParam.algoStrategyDefinitionForm.unusedAlgoStrategyMap.get(
          this.algoCallParam.parentObject.idAlgoAssetclassSecurity
        );
      this.configObject[AlgoStrategyHelper.FIELD_STRATEGY_IMPL].valueKeyHtmlOptions =
        SelectOptionsHelper.createHtmlOptionsFromEnum(
          this.translateService,
          AlgoStrategyImplementationType,
          possibleValues.map(
            (algoStrategyImplementations) => AlgoStrategyImplementationType[algoStrategyImplementations]
          ),
          false
        );
    }
  }

  /**
   * Loads the default YAML template into the Monaco editor.
   * Provides a complete example configuration as a starting point.
   */
  loadTemplate(): void {
    this.yamlContent = STRATEGY_TEMPLATE_YAML;
  }

  /**
   * Submits the complex strategy form by combining the selector value from the dynamic form
   * with the YAML content from the Monaco editor.
   *
   * The YAML is parsed here rather than only during the save, so that unparseable content reports itself and leaves
   * the dialog open. Letting the error escape out of the save instead left the dialog in a state the user could not
   * submit from again.
   */
  async submitComplexStrategy(): Promise<void> {
    if (!(await this.yamlEditor.validateForSubmit())) return;
    if (this.yamlContent && this.parseYaml(this.yamlContent) === undefined) {
      return;
    }
    const value = { ...this.form.value };
    value[AlgoStrategyEditComponent.FIELD_STRATEGY_CONFIG] = this.yamlContent;
    this.submit(value);
  }

  /**
   * Parses the editor content, reporting a syntax error to the user instead of raising it.
   *
   * @param yamlStr the editor content
   * @returns the parsed object, or undefined when the content is not valid YAML
   */
  private parseYaml(yamlStr: string): any {
    try {
      return yaml.load(yamlStr);
    } catch (e) {
      this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'YAML_PARSE_ERROR');
      return undefined;
    }
  }

  private preparePossibleStrategies(): void {
    this.configObject[AlgoStrategyHelper.FIELD_STRATEGY_IMPL].valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(
        this.translateService,
        AlgoStrategyImplementationType,
        [AlgoStrategyImplementationType[(<AlgoStrategy>this.algoCallParam.thisObject).algoStrategyImplementations]],
        false
      );
    this.createViewFromSelectedEnum((<AlgoStrategy>this.algoCallParam.thisObject).algoStrategyImplementations);
    this.configObject[AlgoStrategyHelper.FIELD_STRATEGY_IMPL].formControl.setValue(
      (<AlgoStrategy>this.algoCallParam.thisObject).algoStrategyImplementations
    );
  }

  private valueChangedOnAlgoStrategyImplementation(): void {
    this.algoStrategyImplementationsChangedSub = this.configObject[
      AlgoStrategyHelper.FIELD_STRATEGY_IMPL
    ].formControl.valueChanges.subscribe((asi: AlgoStrategyImplementationType) => {
      if (!this.ignoreValueChanged) {
        this.createViewFromSelectedEnum(asi);
      }
    });
  }

  private createViewFromSelectedEnum(asi: string | AlgoStrategyImplementationType): void {
    const asiNo: number = AlgoStrategyImplementationType[asi];
    const inputAndShowDefinition = this.algoCallParam.algoStrategyDefinitionForm.inputAndShowDefinitionMap.get(asiNo);
    if (!inputAndShowDefinition) {
      this.algoStrategyService.getFormDefinitionsByAlgoStrategy(asiNo).subscribe((iasd) => {
        this.algoCallParam.algoStrategyDefinitionForm.inputAndShowDefinitionMap.set(asiNo, iasd);
        if (iasd.isComplexStrategy) {
          this.isComplexStrategy = true;
          this.createComplexStrategyInput();
        } else {
          this.isComplexStrategy = false;
          this.fieldDescriptorInputAndShows = AlgoStrategyHelper.getFieldDescriptorInputAndShowByLevel(
            this.algoCallParam.parentObject,
            iasd
          );
          this.strategyDefaults = iasd.defaultValues ?? {};
          this.createDynamicInputFields();
        }
      });
    } else {
      if (inputAndShowDefinition.isComplexStrategy) {
        this.isComplexStrategy = true;
        this.createComplexStrategyInput();
      } else {
        this.isComplexStrategy = false;
        this.fieldDescriptorInputAndShows = AlgoStrategyHelper.getFieldDescriptorInputAndShowByLevel(
          this.algoCallParam.parentObject,
          inputAndShowDefinition
        );
        this.strategyDefaults = inputAndShowDefinition.defaultValues ?? {};
        this.createDynamicInputFields();
      }
    }
  }

  /**
   * Sets up the complex strategy editing view with the Monaco YAML editor.
   * The dynamic form only contains the strategy selector with a hidden submit button.
   * The actual submit is handled by a separate button below the editor.
   */
  private createComplexStrategyInput(): void {
    this.dialogWidth = '1100px';
    const submitButton = this.config[this.config.length - 1];
    submitButton.invisible = true;
    this.config = [
      this.config[0],
      DynamicFieldHelper.createFieldCheckboxHeqF('activatable', { defaultValue: true }),
      submitButton
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

    if (this.algoCallParam.thisObject) {
      setTimeout(() => this.setExistingComplexModel());
    } else {
      this.loadTemplate();
    }
  }

  /**
   * Populates the Monaco editor with existing strategy configuration converted from JSON to YAML.
   */
  private setExistingComplexModel(): void {
    this.ignoreValueChanged = true;
    const algoStrategy = <AlgoStrategy>this.algoCallParam.thisObject;
    const model: any = {};
    model.activatable = algoStrategy.activatable;
    model[AlgoStrategyHelper.FIELD_STRATEGY_IMPL] = algoStrategy.algoStrategyImplementations;
    if (algoStrategy.strategyConfig) {
      try {
        const jsonObj = JSON.parse(algoStrategy.strategyConfig);
        this.yamlContent = yaml.dump(jsonObj, { lineWidth: 120, noRefs: true });
      } catch (e) {
        this.yamlContent = algoStrategy.strategyConfig;
      }
    }
    this.form.transferBusinessObjectToForm(model);
    this.configObject[AlgoStrategyHelper.FIELD_STRATEGY_IMPL].formControl.disable();
    this.ignoreValueChanged = false;
  }

  private strategyDefaults: Record<string, number> = {};

  private createDynamicInputFields(): void {
    this.dialogWidth = '700px';
    const submitButton = this.config[this.config.length - 1];
    submitButton.invisible = false;
    const fieldConfig: FieldConfig[] = DynamicFieldModelHelper.createConfigFieldsFromDescriptor(
      this.translateService,
      this.fieldDescriptorInputAndShows,
      AppSettings.PREFIX_ALGO_FIELD,
      false
    );

    fieldConfig.forEach((field) => {
      if (this.strategyDefaults[field.field] != null) field.defaultValue = this.strategyDefaults[field.field];
    });
    this.config = [this.config[0], ...fieldConfig, submitButton];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

    if (this.algoCallParam.thisObject) {
      setTimeout(() => this.setExistingModel());
    }
  }

  private setExistingModel(): void {
    this.ignoreValueChanged = true;
    const dynamicModel = DynamicFieldModelHelper.createAndSetValuesInDynamicModel(
      (<AlgoStrategy>this.algoCallParam.thisObject)[AlgoStrategyHelper.FIELD_STRATEGY_IMPL],
      AlgoStrategyHelper.FIELD_STRATEGY_IMPL,
      (<AlgoStrategy>this.algoCallParam.thisObject).algoRuleStrategyParamMap,
      this.fieldDescriptorInputAndShows,
      true
    );
    Object.entries(this.strategyDefaults).forEach(([field, value]) => {
      if (dynamicModel[field] == null) dynamicModel[field] = value;
    });
    this.form.transferBusinessObjectToForm(dynamicModel);
    this.configObject[AlgoStrategyHelper.FIELD_STRATEGY_IMPL].formControl.disable();
    this.ignoreValueChanged = false;
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): AlgoStrategy {
    const algoStrategy: AlgoStrategy = new AlgoStrategy();
    if (this.algoCallParam.thisObject) {
      Object.assign(algoStrategy, this.algoCallParam.thisObject);
    }
    this.form.cleanMaskAndTransferValuesToBusinessObject(algoStrategy);
    algoStrategy.idAlgoAssetclassSecurity = this.algoCallParam.parentObject.idAlgoAssetclassSecurity;

    if (this.isComplexStrategy) {
      // submitComplexStrategy has already rejected unparseable content, so this parse cannot fail on the normal path.
      const parsed = this.yamlContent ? this.parseYaml(this.yamlContent) : undefined;
      algoStrategy.strategyConfig = parsed === undefined ? null : JSON.stringify(parsed);
      algoStrategy.algoRuleStrategyParamMap = {};
    } else {
      algoStrategy.algoRuleStrategyParamMap = {};
      this.fieldDescriptorInputAndShows.forEach(
        (fDIAS) => (algoStrategy.algoRuleStrategyParamMap[fDIAS.fieldName] = new BaseParam(value[fDIAS.fieldName]))
      );
    }
    return algoStrategy;
  }

  override onHide(event): void {
    this.algoStrategyImplementationsChangedSub && this.algoStrategyImplementationsChangedSub.unsubscribe();
    super.onHide(event);
  }

  /** Loads the JSON Schema for YAML autocompletion from the assets directory */
  private loadYamlSchema(): void {
    fetch(new URL('assets/schemas/mean-reversion-dip-draft-schema.json', document.baseURI).toString())
      .then((res) => res.json())
      .then((schema) => (this.draftYamlSchema = schema));
    const schemaUrl = new URL('assets/schemas/mean-reversion-dip-schema.json', document.baseURI).toString();
    fetch(schemaUrl)
      .then((res) => res.json())
      .then((schema) => (this.yamlSchema = schema))
      .catch((err) => console.warn('Could not load YAML schema for autocompletion:', err));
  }
}
