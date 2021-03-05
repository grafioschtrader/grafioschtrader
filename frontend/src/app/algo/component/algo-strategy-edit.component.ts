import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {AlgoStrategy} from '../../entities/algo.strategy';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {AlgoStrategyService} from '../service/algo.strategy.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {InputType} from '../../dynamic-form/models/input.type';
import {Validators} from '@angular/forms';
import {AppHelper} from '../../shared/helper/app.helper';
import {ErrorMessageRules, RuleEvent} from '../../dynamic-form/error/error.message.rules';
import {AlgoCallParam} from '../model/algo.dialog.visible';
import {AlgoStrategyImplementations} from '../../shared/types/algo.strategy.implementations';
import {Subscription} from 'rxjs';
import {FieldDescriptorInputAndShow} from '../model/field.descriptor.input.and.show';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {AlgoRuleStrategyParam} from '../../entities/algo.rule.strategy.param';
import {AppSettings} from '../../shared/app.settings';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {AlgoStrategyHelper} from './algo.strategy.helper';


@Component({
  selector: 'algo-strategy-edit',
  template: `
    <p-dialog header="{{'ALGO_STRATEGY' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submit)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class AlgoStrategyEditComponent extends SimpleEntityEditBase<AlgoStrategy> implements OnInit {
  @Input() algoCallParam: AlgoCallParam;

  dynamicModel: any = {};
  algoStrategyImplementationsChangedSub: Subscription;
  ignoreValueChanged = false;

  fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[];

  constructor(translateService: TranslateService,
              globalparameterService: GlobalparameterService,
              messageToastService: MessageToastService,
              private algoStrategyService: AlgoStrategyService) {
    super(HelpIds.HELP_ALGO, 'ALGO_SECURITY', translateService, globalparameterService,
      messageToastService, algoStrategyService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.globalparameterService,
      6, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectString(AlgoStrategyHelper.FIELD_STRATEGY_IMPL, 'ALGO_STRATEGY_NAME', true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }


  protected initialize(): void {
    this.config = [this.config[0], this.config[this.config.length - 1]];
    this.configObject.algoStrategyImplementations.formControl.enable();
    this.valueChangedOnAlgoStrategyImplemtation();
    if (this.algoCallParam.thisObject) {
      // Existing strategy can not be changed
      this.ignoreValueChanged = false;

      this.configObject.algoStrategyImplementations.valueKeyHtmlOptions =
        SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, AlgoStrategyImplementations,
          [AlgoStrategyImplementations[(<AlgoStrategy> this.algoCallParam.thisObject).algoStrategyImplementations]], false);
      this.getFieldDescriptorAndSetItForForm((<AlgoStrategy> this.algoCallParam.thisObject).algoStrategyImplementations);
      this.configObject.algoStrategyImplementations.formControl.setValue(
        (<AlgoStrategy>this.algoCallParam.thisObject).algoStrategyImplementations);
    } else {
      const possibleValues: AlgoStrategyImplementations[] =
        this.algoCallParam.algoStrategyDefinitionForm.unusedAlgoStrategyMap.get(this.algoCallParam.parentObject.idAlgoAssetclassSecurity);
      this.configObject.algoStrategyImplementations.valueKeyHtmlOptions =
        SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, AlgoStrategyImplementations,
          possibleValues.map(algoStrategyImplementations => AlgoStrategyImplementations[algoStrategyImplementations]), false);
    }
  }

  valueChangedOnAlgoStrategyImplemtation(): void {
    this.algoStrategyImplementationsChangedSub = this.configObject.algoStrategyImplementations.formControl.valueChanges
      .subscribe((asi: AlgoStrategyImplementations) => {
        if (!this.ignoreValueChanged) {
          this.getFieldDescriptorAndSetItForForm(asi);
        }
      });
  }


  private getFieldDescriptorAndSetItForForm(asi: string | AlgoStrategyImplementations): void {
    const asiNo: number = AlgoStrategyImplementations[asi];
    const inputAndShowDefinition = this.algoCallParam.algoStrategyDefinitionForm.inputAndShowDefinitionMap.get(asiNo);
    if (!inputAndShowDefinition) {
      this.algoStrategyService.getFormDefinitionsByAlgoStrategy(asiNo).subscribe(iasd => {
        this.algoCallParam.algoStrategyDefinitionForm.inputAndShowDefinitionMap.set(asiNo, iasd);
        this.fieldDescriptorInputAndShows = AlgoStrategyHelper.getFieldDescriptorInputAndShowByLevel(
          this.algoCallParam.parentObject, iasd);
        this.createDynamicInputFields();
      });
    } else {
      this.fieldDescriptorInputAndShows = AlgoStrategyHelper.getFieldDescriptorInputAndShowByLevel(this.algoCallParam.parentObject,
        inputAndShowDefinition);
      this.createDynamicInputFields();
    }
  }

  private createDynamicInputFields(): void {
    const fieldConfig: FieldConfig[] = DynamicFieldHelper.createConfigFieldsFromDescriptor(this.fieldDescriptorInputAndShows,
      AppSettings.PREFIX_ALGO_FIELD, false);

    this.config = [this.config[0], ...fieldConfig, this.config[this.config.length - 1]];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

    if (this.algoCallParam.thisObject) {
      setTimeout(() => this.setExistingModel());
    }
  }

  private setExistingModel(): void {
    this.ignoreValueChanged = true;
    const dynamicModel = AlgoStrategyHelper.createAndSetValuesInDynamicModel(<AlgoStrategy> this.algoCallParam.thisObject,
      this.fieldDescriptorInputAndShows, true);
    this.form.transferBusinessObjectToForm(dynamicModel);
    this.configObject.algoStrategyImplementations.formControl.disable();
    this.ignoreValueChanged = false;
  }

  protected getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): AlgoStrategy {
    const algoStrategy: AlgoStrategy = new AlgoStrategy();
    if (this.algoCallParam.thisObject) {
      Object.assign(algoStrategy, this.algoCallParam.thisObject);
    }
    this.form.cleanMaskAndTransferValuesToBusinessObject(algoStrategy);
    algoStrategy.idAlgoAssetclassSecurity = this.algoCallParam.parentObject.idAlgoAssetclassSecurity;
    algoStrategy.algoRuleStrategyParamMap = {};
    this.fieldDescriptorInputAndShows.forEach(fDIAS => {
      algoStrategy.algoRuleStrategyParamMap[fDIAS.fieldName] = new AlgoRuleStrategyParam(value[fDIAS.fieldName]);
    });

    return algoStrategy;
  }

  onHide(event): void {
    this.algoStrategyImplementationsChangedSub && this.algoStrategyImplementationsChangedSub.unsubscribe();
    super.onHide(event);
  }
}
