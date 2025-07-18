import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {AfterViewInit, Component, OnDestroy, OnInit} from '@angular/core';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {AppHelper} from '../../lib/helper/app.helper';
import {DataType} from '../../dynamic-form/models/data.type';
import {WatchlistService} from '../../watchlist/service/watchlist.service';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {Subscription} from 'rxjs';
import {AssetclassService} from '../../assetclass/service/assetclass.service';
import {Assetclass} from '../../entities/assetclass';
import {AlgoTopCreate, AssetclassPercentage} from '../../entities/backend/algo.top.create';
import {RuleStrategyType} from '../../shared/types/rule.strategy.type';
import {HelpIds} from '../../shared/help/help.ids';
import {AlgoTopService} from '../service/algo.top.service';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ProcessedAction} from '../../lib/types/processed.action';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {AppSettings} from '../../shared/app.settings';
import {BusinessSelectOptionsHelper} from '../../securitycurrency/component/business.select.options.helper';
import {SimpleDynamicEditBase} from '../../lib/edit/simple.dynamic.edit.base';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {AlgoTop} from '../model/algo.top';
import {CallParam} from '../../shared/maintree/types/dialog.visible';

/**
 * Dialog for define a strategy. Asset class can be added dynamically.
 * Project: Grafioschtrader
 */
@Component({

  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                  #form="dynamicForm" (submitBt)="submit($event)">
    </dynamic-form>
  `,
  standalone: false
})
export class AlgoRuleStrategyCreateDynamicComponent extends SimpleDynamicEditBase<AlgoTop> implements OnInit, OnDestroy, AfterViewInit {
  static readonly DIALOG_WIDTH = 500;
  callParam: CallParam;
  private static readonly ASSETCLASS_FIELD = 'idAssetclass';
  private static readonly PERCENTAGE_FIELD = 'percentage';
  private static readonly maxAssetclassRows = 9;
  private static readonly NO_OF_BUTTONS = 3;

  assetclassCounter = 0;
  valueKeyHtmlOptionsAssetclasses: ValueKeyHtmlSelectOptions[];
  algoTopCreate: AlgoTopCreate;
  protected watchlistChangedSub: Subscription;
  protected assetclassChangeSubList: Subscription[] = new Array(AlgoRuleStrategyCreateDynamicComponent.maxAssetclassRows);
  private assetsclasses: Assetclass[] = [];

  constructor(private assetclassService: AssetclassService,
    private watchlistService: WatchlistService,
    private algoTopService: AlgoTopService,
    dynamicDialogConfig: DynamicDialogConfig,
    dynamicDialogRef: DynamicDialogRef,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,) {
    super(dynamicDialogConfig, dynamicDialogRef, HelpIds.HELP_WATCHLIST, translateService, gps, messageToastService, algoTopService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      2, this.helpLink.bind(this));
    this.callParam = this.dynamicDialogConfig.data.callParam;
    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'NAME', 32, true),
      DynamicFieldHelper.createFieldMinMaxNumber(DataType.Numeric, 'percentage', 'ALGO_MAXIMUM_INVESTMENT', true,
        2, 100, {fieldSuffix: '%'}),
      DynamicFieldHelper.createFieldSelectString('idWatchlist', AppSettings.WATCHLIST.toUpperCase(), true),
      DynamicFieldHelper.createFunctionButton('ALGO_ADD_ASSETCLASS', () => this.addAssetclassPercentageRow(false)),
      DynamicFieldHelper.createFunctionButton('ALGO_REMOVE_ASSETCLASS', () => this.removeAssetclassPercentageRow()),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.addAssetclassPercentageRow(true);
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngAfterViewInit(): void {
    this.algoTopCreate = <AlgoTopCreate>this.callParam.thisObject;
    if (this.algoTopCreate.ruleStrategy === RuleStrategyType[RuleStrategyType.RS_RULE]) {
      this.helpId = HelpIds.HELP_ALGO_RULE;
    } else {
      this.helpId = HelpIds.HELP_ALGO_STRATEGY;
    }
    this.assetsclasses = [];
    this.valueChangedOnWatchlist();
    this.watchlistService.getWatchlistsByIdTenant().subscribe(watchlists => {
      // this.configObject.idWatchlist.referencedDataObject = watchlists;
      this.configObject.idWatchlist.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idWatchlist', 'name',
        watchlists, true);
    });
  }

  removeAssetclassPercentageRow(): void {
    this.assetclassChangeSubList[this.assetclassCounter] && this.assetclassChangeSubList[this.assetclassCounter].unsubscribe();
    this.assetclassChangeSubList[this.assetclassCounter] = null;
    this.assetclassCounter--;
    this.config = [...this.config.slice(0, this.config.length - AlgoRuleStrategyCreateDynamicComponent.NO_OF_BUTTONS - 2),
      ...this.config.slice(this.config.length - AlgoRuleStrategyCreateDynamicComponent.NO_OF_BUTTONS, this.config.length)];
  }

  addAssetclassPercentageRow(addFirstElement: boolean): void {
    this.assetclassCounter++;
    const fieldConfig: FieldConfig[] = [
      DynamicFieldHelper.createFieldSelectString(AlgoRuleStrategyCreateDynamicComponent.ASSETCLASS_FIELD + this.assetclassCounter,
        AppSettings.ASSETCLASS.toUpperCase(), true, {labelSuffix: '' + this.assetclassCounter, usedLayoutColumns: 8}),
      DynamicFieldHelper.createFieldMinMaxNumber(DataType.Numeric,
        AlgoRuleStrategyCreateDynamicComponent.PERCENTAGE_FIELD + this.assetclassCounter, 'ALGO_F_WEIGHTING_PERCENTAGE',
        true, 0.5, 100, {fieldSuffix: '%', usedLayoutColumns: 4}),

    ];
    this.config = [...this.config.slice(0, this.config.length - AlgoRuleStrategyCreateDynamicComponent.NO_OF_BUTTONS), ...fieldConfig,
      ...this.config.slice(this.config.length - AlgoRuleStrategyCreateDynamicComponent.NO_OF_BUTTONS, this.config.length)];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    !addFirstElement && this.fillAndfilterAssetclasses();
    setTimeout(() => this.valueChangedOnAssetclass());
  }

  valueChangedOnAssetclass(): void {
    const fieldName = AlgoRuleStrategyCreateDynamicComponent.ASSETCLASS_FIELD + this.assetclassCounter;
    this.assetclassChangeSubList[this.assetclassCounter] = this.configObject[fieldName].formControl.valueChanges.subscribe(idAssetclass => {
      this.disableEnableAssetclasses(this.getUsedAssetclasses());
    });
  }

  valueChangedOnWatchlist(): void {
    this.watchlistChangedSub = this.configObject.idWatchlist.formControl.valueChanges.subscribe(idWatchlist => {
      if (idWatchlist) {
        this.assetclassService.getInvestableAssetclassesByWatchlist(idWatchlist).subscribe((assetsclasses: Assetclass[]) => {
          this.assetsclasses = assetsclasses;
          this.valueKeyHtmlOptionsAssetclasses = BusinessSelectOptionsHelper.assetclassCreateValueKeyHtmlSelectOptions(
            this.gps, this.translateService, this.assetsclasses);
          this.fillAndfilterAssetclasses();
        });
      } else {
        this.assetsclasses = [];
        this.fillAndfilterAssetclasses();
      }
    });
  }

  ngOnDestroy(): void  {
    this.watchlistChangedSub?.unsubscribe();
    this.assetclassChangeSubList.filter(sub => !!sub).forEach(sub => sub.unsubscribe());
  }

  override submit(value: { [name: string]: any }): void {
    this.form.cleanMaskAndTransferValuesToBusinessObject(this.algoTopCreate);
    this.algoTopCreate.assetclassPercentageList = [];
    for (let i = 1; i <= this.assetclassCounter; i++) {
      this.algoTopCreate.assetclassPercentageList.push(new AssetclassPercentage(
        parseInt(value[AlgoRuleStrategyCreateDynamicComponent.ASSETCLASS_FIELD + i], 10),
        parseInt(value[AlgoRuleStrategyCreateDynamicComponent.PERCENTAGE_FIELD + i], 10)));
    }
    this.algoTopService.create(this.algoTopCreate).subscribe({
      next: returnEntity => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
          {i18nRecord: this.dynamicDialogConfig.header});
        this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.CREATED, returnEntity));
      }, error: () => this.configObject.submit.disabled = false
    });
  }

  private getUsedAssetclasses(): string[] {
    const usedAssetclassIds: string[] = new Array(this.assetclassCounter);
    for (let i = 1; i <= this.assetclassCounter; i++) {
      const fieldName = AlgoRuleStrategyCreateDynamicComponent.ASSETCLASS_FIELD + i;
      if (usedAssetclassIds.length > 0 && this.configObject[fieldName].formControl) {
        usedAssetclassIds[i - 1] = this.configObject[fieldName].formControl.value;
      }
    }
    return usedAssetclassIds;
  }

  private fillAndfilterAssetclasses(): void {
    for (let i = 1; i <= this.assetclassCounter; i++) {
      const fieldName = AlgoRuleStrategyCreateDynamicComponent.ASSETCLASS_FIELD + i;
      this.configObject[fieldName].referencedDataObject = this.assetsclasses;
      this.configObject[fieldName].valueKeyHtmlOptions = this.valueKeyHtmlOptionsAssetclasses;
    }
    this.disableEnableAssetclasses(this.getUsedAssetclasses());
  }

  private disableEnableAssetclasses(usedAssetclassIds: string[]) {
    this.valueKeyHtmlOptionsAssetclasses.forEach(vkh => {
      vkh.disabled = !!usedAssetclassIds.find(id => id === ('' + vkh.key));
    });
  }

  protected getNewOrExistingInstanceBeforeSave(value: { [p: string]: any }): AlgoTop {
    return undefined;
  }


}
