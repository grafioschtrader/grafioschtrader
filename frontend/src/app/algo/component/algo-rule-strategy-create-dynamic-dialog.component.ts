import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { of, switchMap, distinctUntilChanged, Subscription } from 'rxjs';
import { afterNextRender, inject, Injector, Component, OnDestroy, OnInit } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { AppHelper } from '../../lib/helper/app.helper';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { WatchlistService } from '../../watchlist/service/watchlist.service';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { AssetclassService } from '../../assetclass/service/assetclass.service';
import { Assetclass } from '../../entities/assetclass';
import { AlgoTopCreate, AssetclassPercentage } from '../../entities/backend/algo.top.create';
import { HelpIds } from '../../lib/help/help.ids';
import { AlgoTopService } from '../service/algo.top.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { ProcessedAction } from '../../lib/types/processed.action';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { AppSettings } from '../../shared/app.settings';
import { BusinessSelectOptionsHelper } from '../../shared/securitycurrency/business.select.options.helper';
import { SimpleDynamicEditBase } from '../../lib/edit/simple.dynamic.edit.base';
import { AlgoTop } from '../model/algo.top';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';

/**
 * Dialog for define a strategy. Asset class can be added dynamically.
 */
@Component({
  template: `
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  `,
  imports: [...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class AlgoRuleStrategyCreateDynamicDialogComponent
  extends SimpleDynamicEditBase<AlgoTop>
  implements OnInit, OnDestroy
{
  private assetclassService: AssetclassService = inject(AssetclassService);
  private watchlistService: WatchlistService = inject(WatchlistService);
  private algoTopService: AlgoTopService = inject(AlgoTopService);

  static readonly DIALOG_WIDTH = 700;
  private readonly injector = inject(Injector);
  callParam: CallParam;
  private static readonly ASSETCLASS_FIELD = 'idAssetclass';
  private static readonly PERCENTAGE_FIELD = 'percentage';
  private static readonly maxAssetclassRows = 9;
  private static readonly NO_OF_BUTTONS = 3;

  assetclassCounter = 0;
  valueKeyHtmlOptionsAssetclasses: ValueKeyHtmlSelectOptions[] = [];
  algoTopCreate: AlgoTopCreate;
  protected watchlistChangedSub: Subscription;
  protected assetclassChangeSubList: Subscription[] = new Array(
    AlgoRuleStrategyCreateDynamicDialogComponent.maxAssetclassRows
  );
  private assetsclasses: Assetclass[] = [];

  constructor() {
    super(HelpIds.HELP_WATCHLIST, inject(AlgoTopService));
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 2, this.helpLink.bind(this));
    this.callParam = this.dynamicDialogConfig.data.callParam;
    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'NAME', 40, true),
      DynamicFieldHelper.createFieldMinMaxNumber(
        DataType.Numeric,
        'percentage',
        'ALGO_MAXIMUM_INVESTMENT',
        true,
        2,
        100,
        { fieldSuffix: '%' }
      ),
      DynamicFieldHelper.createFieldSelectString('idWatchlist', AppSettings.WATCHLIST.toUpperCase(), true),
      DynamicFieldHelper.createFunctionButton('ALGO_ADD_ASSETCLASS', () => this.addAssetclassPercentageRow(false)),
      DynamicFieldHelper.createFunctionButton('ALGO_REMOVE_ASSETCLASS', () => this.removeAssetclassPercentageRow()),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.addAssetclassPercentageRow(true);
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    afterNextRender(() => this.initializeForm(), { injector: this.injector });
  }

  private initializeForm(): void {
    this.algoTopCreate = <AlgoTopCreate>this.callParam.thisObject;
    this.helpId = HelpIds.HELP_ALGO_TREE;
    this.assetsclasses = [];
    this.valueChangedOnWatchlist();
    this.watchlistService
      .getWatchlistsByIdTenant()
      .pipe(this.untilDialogClosed())
      .subscribe((watchlists) => {
        // this.configObject.idWatchlist.referencedDataObject = watchlists;
        this.configObject.idWatchlist.valueKeyHtmlOptions =
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idWatchlist', 'name', watchlists, true);
      });
  }

  removeAssetclassPercentageRow(): void {
    this.assetclassChangeSubList[this.assetclassCounter] &&
      this.assetclassChangeSubList[this.assetclassCounter].unsubscribe();
    this.assetclassChangeSubList[this.assetclassCounter] = null;
    this.assetclassCounter--;
    this.config = [
      ...this.config.slice(0, this.config.length - AlgoRuleStrategyCreateDynamicDialogComponent.NO_OF_BUTTONS - 2),
      ...this.config.slice(
        this.config.length - AlgoRuleStrategyCreateDynamicDialogComponent.NO_OF_BUTTONS,
        this.config.length
      )
    ];
  }

  addAssetclassPercentageRow(addFirstElement: boolean): void {
    this.assetclassCounter++;
    const fieldConfig: FieldConfig[] = [
      DynamicFieldHelper.createFieldSelectString(
        AlgoRuleStrategyCreateDynamicDialogComponent.ASSETCLASS_FIELD + this.assetclassCounter,
        AppSettings.ASSETCLASS.toUpperCase(),
        true,
        { labelSuffix: '' + this.assetclassCounter, usedLayoutColumns: 8 }
      ),
      DynamicFieldHelper.createFieldMinMaxNumber(
        DataType.Numeric,
        AlgoRuleStrategyCreateDynamicDialogComponent.PERCENTAGE_FIELD + this.assetclassCounter,
        'ALGO_PERCENTAGE',
        true,
        0.5,
        100,
        { fieldSuffix: '%', usedLayoutColumns: 4 }
      )
    ];
    this.config = [
      ...this.config.slice(0, this.config.length - AlgoRuleStrategyCreateDynamicDialogComponent.NO_OF_BUTTONS),
      ...fieldConfig,
      ...this.config.slice(
        this.config.length - AlgoRuleStrategyCreateDynamicDialogComponent.NO_OF_BUTTONS,
        this.config.length
      )
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    !addFirstElement && this.fillAndfilterAssetclasses();
    const rowIndex = this.assetclassCounter;
    afterNextRender(() => this.valueChangedOnAssetclass(rowIndex), { injector: this.injector });
  }

  valueChangedOnAssetclass(rowIndex: number): void {
    const fieldName = AlgoRuleStrategyCreateDynamicDialogComponent.ASSETCLASS_FIELD + rowIndex;
    if (!this.configObject[fieldName]?.formControl) return;
    this.assetclassChangeSubList[rowIndex]?.unsubscribe();
    this.assetclassChangeSubList[rowIndex] = this.configObject[fieldName].formControl.valueChanges
      .pipe(this.untilDialogClosed())
      .subscribe((idAssetclass) => {
        this.disableEnableAssetclasses(this.getUsedAssetclasses());
      });
  }

  valueChangedOnWatchlist(): void {
    this.watchlistChangedSub?.unsubscribe();
    this.watchlistChangedSub = requireFormControl(this.configObject.idWatchlist)
      .valueChanges.pipe(
        distinctUntilChanged(),
        switchMap((id) => (id ? this.assetclassService.getInvestableAssetclassesByWatchlist(id) : of([]))),
        this.untilDialogClosed()
      )
      .subscribe((assetclasses: Assetclass[]) => {
        this.assetsclasses = assetclasses;
        this.valueKeyHtmlOptionsAssetclasses = BusinessSelectOptionsHelper.assetclassCreateValueKeyHtmlSelectOptions(
          this.gps,
          this.translateService,
          assetclasses
        );
        this.fillAndfilterAssetclasses();
      });
  }

  ngOnDestroy(): void {
    this.watchlistChangedSub?.unsubscribe();
    this.assetclassChangeSubList.filter((sub) => !!sub).forEach((sub) => sub.unsubscribe());
  }

  override submit(value: { [name: string]: any }): void {
    this.form().cleanMaskAndTransferValuesToBusinessObject(this.algoTopCreate);
    this.algoTopCreate.assetclassPercentageList = [];
    for (let i = 1; i <= this.assetclassCounter; i++) {
      this.algoTopCreate.assetclassPercentageList.push(
        new AssetclassPercentage(
          parseInt(value[AlgoRuleStrategyCreateDynamicDialogComponent.ASSETCLASS_FIELD + i], 10),
          Number(value[AlgoRuleStrategyCreateDynamicDialogComponent.PERCENTAGE_FIELD + i])
        )
      );
    }
    this.algoTopService
      .create(this.algoTopCreate)
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: (returnEntity) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
            i18nRecord: this.dynamicDialogConfig.header
          });
          this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.CREATED, returnEntity));
        },
        error: () => (this.configObject.submit.disabled = false)
      });
  }

  private getUsedAssetclasses(): string[] {
    const usedAssetclassIds: string[] = new Array(this.assetclassCounter);
    for (let i = 1; i <= this.assetclassCounter; i++) {
      const fieldName = AlgoRuleStrategyCreateDynamicDialogComponent.ASSETCLASS_FIELD + i;
      if (usedAssetclassIds.length > 0 && this.configObject[fieldName].formControl) {
        usedAssetclassIds[i - 1] = this.configObject[fieldName].formControl.value;
      }
    }
    return usedAssetclassIds;
  }

  private fillAndfilterAssetclasses(): void {
    for (let i = 1; i <= this.assetclassCounter; i++) {
      const fieldName = AlgoRuleStrategyCreateDynamicDialogComponent.ASSETCLASS_FIELD + i;
      this.configObject[fieldName].referencedDataObject = this.assetsclasses;
      this.configObject[fieldName].valueKeyHtmlOptions = this.valueKeyHtmlOptionsAssetclasses;
    }
    this.disableEnableAssetclasses(this.getUsedAssetclasses());
  }

  private disableEnableAssetclasses(usedAssetclassIds: string[]): void {
    this.valueKeyHtmlOptionsAssetclasses = this.valueKeyHtmlOptionsAssetclasses.map((option) => ({
      ...option,
      disabled: usedAssetclassIds.some((id) => id === '' + option.key)
    }));
    for (let i = 1; i <= this.assetclassCounter; i++) {
      this.configObject[AlgoRuleStrategyCreateDynamicDialogComponent.ASSETCLASS_FIELD + i].valueKeyHtmlOptions = [
        ...this.valueKeyHtmlOptionsAssetclasses
      ];
    }
  }

  protected getNewOrExistingInstanceBeforeSave(value: { [p: string]: any }): AlgoTop {
    return undefined;
  }
}
