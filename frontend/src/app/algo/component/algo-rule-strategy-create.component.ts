import {SimpleEditBase} from '../../shared/edit/simple.edit.base';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {AppHelper} from '../../shared/helper/app.helper';
import {DataType} from '../../dynamic-form/models/data.type';
import {WatchlistService} from '../../watchlist/service/watchlist.service';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {Subscription} from 'rxjs';
import {AssetclassService} from '../../assetclass/service/assetclass.service';
import {Assetclass} from '../../entities/assetclass';
import {AlgoTopCreate, AssetclassPercentage} from '../../entities/backend/algo.top.create';
import {RuleStrategy} from '../../shared/types/rule.strategy';
import {HelpIds} from '../../shared/help/help.ids';
import {AlgoTopService} from '../service/algo.top.service';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {ProcessedAction} from '../../shared/types/processed.action';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {AppSettings} from '../../shared/app.settings';

/**
 * Dialog for define a strategy. Asset class can be added dynamically.
 */
@Component({
  selector: 'algo-rule-strategy-create',
  template: `
    <p-dialog header="{{algoTitleKey | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '700px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,

})
export class AlgoRuleStrategyCreateComponent extends SimpleEditBase implements OnInit {

  private static readonly ASSETCLASS_FIELD = 'idAssetclass';
  private static readonly PERCENTAGE_FIELD = 'percentage';
  private static readonly maxAssetclassRows = 9;
  private static readonly NO_OF_BUTTONS = 3;

  @Input() visibleDialog: boolean;
  @Input() callParam: CallParam;

  // Output for parent view
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();
  assetclassCounter = 0;
  algoTitleKey: string;
  valueKeyHtmlOptionsAssetclasses: ValueKeyHtmlSelectOptions[];
  algoTopCreate: AlgoTopCreate;
  protected watchlistChangedSub: Subscription;
  protected assetclassChangeSubList: Subscription[] = new Array(AlgoRuleStrategyCreateComponent.maxAssetclassRows);
  private assetsclasses: Assetclass[] = [];

  constructor(public algoTopService: AlgoTopService,
              public assetclassService: AssetclassService,
              public watchlistService: WatchlistService,
              public translateService: TranslateService,
              public messageToastService: MessageToastService,
              gps: GlobalparameterService) {
    super(null, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      2, this.helpLink.bind(this));

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

  initialize() {
    this.algoTopCreate = <AlgoTopCreate>this.callParam.thisObject;
    if (this.algoTopCreate.ruleStrategy === RuleStrategy[RuleStrategy.RS_RULE]) {
      this.algoTitleKey = 'ALGO_RULE_BASED';
      this.helpId = HelpIds.HELP_ALGO_RULE;
    } else {
      this.algoTitleKey = 'ALGO_PORTFOLIO_STRATEGY';
      this.helpId = HelpIds.HELP_ALOG_STRATEGY;
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
    this.config = [...this.config.slice(0, this.config.length - AlgoRuleStrategyCreateComponent.NO_OF_BUTTONS - 2),
      ...this.config.slice(this.config.length - AlgoRuleStrategyCreateComponent.NO_OF_BUTTONS, this.config.length)];
  }


  addAssetclassPercentageRow(addFirstElement: boolean): void {
    this.assetclassCounter++;
    const fieldConfig: FieldConfig[] = [
      DynamicFieldHelper.createFieldSelectString(AlgoRuleStrategyCreateComponent.ASSETCLASS_FIELD + this.assetclassCounter,
        AppSettings.ASSETCLASS.toUpperCase(), true, {labelSuffix: '' + this.assetclassCounter, usedLayoutColumns: 8}),
      DynamicFieldHelper.createFieldMinMaxNumber(DataType.Numeric,
        AlgoRuleStrategyCreateComponent.PERCENTAGE_FIELD + this.assetclassCounter, 'ALGO_F_WEIGHTING_PERCENTAGE',
        true, 0.5, 100, {fieldSuffix: '%', usedLayoutColumns: 4}),

    ];
    this.config = [...this.config.slice(0, this.config.length - AlgoRuleStrategyCreateComponent.NO_OF_BUTTONS), ...fieldConfig,
      ...this.config.slice(this.config.length - AlgoRuleStrategyCreateComponent.NO_OF_BUTTONS, this.config.length)];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    !addFirstElement && this.fillAndfilterAssetclasses();
    setTimeout(() => this.valueChangedOnAssetclass());
  }

  valueChangedOnAssetclass(): void {
    const fieldName = AlgoRuleStrategyCreateComponent.ASSETCLASS_FIELD + this.assetclassCounter;
    this.assetclassChangeSubList[this.assetclassCounter] = this.configObject[fieldName].formControl.valueChanges.subscribe(idAssetclass => {
      this.disableEnableAssetclasses(this.getUsedAssetclasses());
    });
  }

  valueChangedOnWatchlist(): void {
    this.watchlistChangedSub = this.configObject.idWatchlist.formControl.valueChanges.subscribe(idWatchlist => {
      if (idWatchlist) {
        this.assetclassService.getInvestableAssetclassesByWatchlist(idWatchlist).subscribe((assetsclasses: Assetclass[]) => {
          this.assetsclasses = assetsclasses;
          this.valueKeyHtmlOptionsAssetclasses = SelectOptionsHelper.assetclassCreateValueKeyHtmlSelectOptions(
            this.gps, this.translateService, this.assetsclasses);
          this.fillAndfilterAssetclasses();
        });
      } else {
        this.assetsclasses = [];
        this.fillAndfilterAssetclasses();
      }
    });
  }

  onHide(event): void {
    this.watchlistChangedSub && this.watchlistChangedSub.unsubscribe();
    this.assetclassChangeSubList.forEach(assetclassChangeSub => assetclassChangeSub && assetclassChangeSub.unsubscribe());
    super.onHide(event);
  }

  submit(value: { [name: string]: any }): void {
    this.form.cleanMaskAndTransferValuesToBusinessObject(this.algoTopCreate);
    this.algoTopCreate.assetclassPercentageList = [];
    for (let i = 1; i <= this.assetclassCounter; i++) {
      this.algoTopCreate.assetclassPercentageList.push(new AssetclassPercentage(
        parseInt(value[AlgoRuleStrategyCreateComponent.ASSETCLASS_FIELD + i], 10),
        parseInt(value[AlgoRuleStrategyCreateComponent.PERCENTAGE_FIELD + i], 10)));
    }
    this.algoTopService.create(this.algoTopCreate).subscribe(returnEntity => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {i18nRecord: this.algoTitleKey});
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED, returnEntity));
    }, () => this.configObject.submit.disabled = false);
  }

  private getUsedAssetclasses(): string[] {
    const usedAssetclassIds: string[] = new Array(this.assetclassCounter);
    for (let i = 1; i <= this.assetclassCounter; i++) {
      const fieldName = AlgoRuleStrategyCreateComponent.ASSETCLASS_FIELD + i;
      if (usedAssetclassIds.length > 0 && this.configObject[fieldName].formControl) {
        usedAssetclassIds[i - 1] = this.configObject[fieldName].formControl.value;
      }
    }
    return usedAssetclassIds;
  }

  private fillAndfilterAssetclasses(): void {
    for (let i = 1; i <= this.assetclassCounter; i++) {
      const fieldName = AlgoRuleStrategyCreateComponent.ASSETCLASS_FIELD + i;
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


}
