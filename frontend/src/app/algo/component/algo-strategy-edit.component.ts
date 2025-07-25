import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {AlgoStrategy} from '../model/algo.strategy';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {AlgoStrategyService} from '../service/algo.strategy.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {AlgoCallParam} from '../model/algo.dialog.visible';
import {AlgoStrategyImplementationType} from '../../shared/types/algo.strategy.implementation.type';
import {Subscription} from 'rxjs';
import {FieldDescriptorInputAndShow} from '../../shared/dynamicfield/field.descriptor.input.and.show';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {BaseParam} from '../../lib/entities/base.param';
import {AppSettings} from '../../shared/app.settings';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AlgoStrategyHelper} from './algo.strategy.helper';
import {DynamicFieldModelHelper} from '../../lib/helper/dynamic.field.model.helper';

/**
 * Allows editing a new or existing strategy. The input fields will be different according to the selected strategy.
 * For this reason, except for the selection box, all input fields are created dynamically.
 * A strategy can consist of different parameters, therefore the output is created dynamically from a map.
 *
 * Project: Grafioschtrader
 */
@Component({
    selector: 'algo-strategy-edit',
    template: `
    <p-dialog header="{{'ALGO_STRATEGY' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: false
})
export class AlgoStrategyEditComponent extends SimpleEntityEditBase<AlgoStrategy> implements OnInit {
  @Input() algoCallParam: AlgoCallParam;

  dynamicModel: any = {};
  algoStrategyImplementationsChangedSub: Subscription;
  ignoreValueChanged = false;

  fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[];

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              private algoStrategyService: AlgoStrategyService) {
    super(HelpIds.HELP_ALGO, 'ALGO_SECURITY', translateService, gps,
      messageToastService, algoStrategyService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      6, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectString(AlgoStrategyHelper.FIELD_STRATEGY_IMPL, 'ALGO_STRATEGY_NAME', true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }


  protected override initialize(): void {
    this.config = [this.config[0], this.config[this.config.length - 1]];
    this.configObject[AlgoStrategyHelper.FIELD_STRATEGY_IMPL].formControl.enable();
    this.valueChangedOnAlgoStrategyImplementation();
    if (this.algoCallParam.thisObject) {
      // Existing strategy can not be changed
      this.ignoreValueChanged = false;
      this.preparePossibleStrategies();
    } else {
        const possibleValues: AlgoStrategyImplementationType[] =
          this.algoCallParam.algoStrategyDefinitionForm.unusedAlgoStrategyMap.get(this.algoCallParam.parentObject.idAlgoAssetclassSecurity);
        this.configObject[AlgoStrategyHelper.FIELD_STRATEGY_IMPL].valueKeyHtmlOptions =
          SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, AlgoStrategyImplementationType,
            possibleValues.map(algoStrategyImplementations => AlgoStrategyImplementationType[algoStrategyImplementations]), false);
    }
  }

  private preparePossibleStrategies(): void {
    this.configObject[AlgoStrategyHelper.FIELD_STRATEGY_IMPL].valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, AlgoStrategyImplementationType,
        [AlgoStrategyImplementationType[(<AlgoStrategy>this.algoCallParam.thisObject).algoStrategyImplementations]], false);
    this.createViewFromSelectedEnum((<AlgoStrategy>this.algoCallParam.thisObject).algoStrategyImplementations);
    this.configObject[AlgoStrategyHelper.FIELD_STRATEGY_IMPL].formControl.setValue(
      (<AlgoStrategy>this.algoCallParam.thisObject).algoStrategyImplementations);
  }

  private valueChangedOnAlgoStrategyImplementation(): void {
    this.algoStrategyImplementationsChangedSub = this.configObject[AlgoStrategyHelper.FIELD_STRATEGY_IMPL].formControl.valueChanges
      .subscribe((asi: AlgoStrategyImplementationType) => {
        if (!this.ignoreValueChanged) {
          this.createViewFromSelectedEnum(asi);
        }
      });
  }

  private createViewFromSelectedEnum(asi: string | AlgoStrategyImplementationType): void {
    const asiNo: number = AlgoStrategyImplementationType[asi];
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
    const fieldConfig: FieldConfig[] = DynamicFieldModelHelper.createConfigFieldsFromDescriptor(this.fieldDescriptorInputAndShows,
      AppSettings.PREFIX_ALGO_FIELD, false);

    this.config = [this.config[0], ...fieldConfig, this.config[this.config.length - 1]];
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
      this.fieldDescriptorInputAndShows, true);
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
    algoStrategy.algoRuleStrategyParamMap = {};
    this.fieldDescriptorInputAndShows.forEach(fDIAS =>
      algoStrategy.algoRuleStrategyParamMap[fDIAS.fieldName] = new BaseParam(value[fDIAS.fieldName])
    );
   algoStrategy.idAlgoAssetclassSecurity = this.algoCallParam.parentObject.idAlgoAssetclassSecurity;
   return algoStrategy;
}


  override onHide(event): void {
    this.algoStrategyImplementationsChangedSub && this.algoStrategyImplementationsChangedSub.unsubscribe();
    super.onHide(event);
  }
}
