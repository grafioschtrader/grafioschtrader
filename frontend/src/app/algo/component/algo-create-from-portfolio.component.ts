import {AfterViewInit, Component, OnInit} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {SimpleDynamicEditBase} from '../../lib/edit/simple.dynamic.edit.base';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {AlgoTopService} from '../service/algo.top.service';
import {WatchlistService} from '../../watchlist/service/watchlist.service';
import {AlgoTop} from '../model/algo.top';
import {AlgoTopCreateFromPortfolio} from '../../entities/backend/algo.top.create';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {HelpIds} from '../../lib/help/help.ids';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

/**
 * Dialog for auto-generating an AlgoTop hierarchy from current portfolio holdings at a reference date.
 * The backend calculates security values and groups them by asset class with percentage weightings.
 */
@Component({
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                  #form="dynamicForm" (submitBt)="submit($event)">
    </dynamic-form>
  `,
  standalone: true,
  imports: [
    DynamicFormModule,
    TranslateModule
  ]
})
export class AlgoCreateFromPortfolioDynamicComponent extends SimpleDynamicEditBase<AlgoTop> implements OnInit, AfterViewInit {
  static readonly DIALOG_WIDTH = 500;
  callParam: CallParam;
  private dto: AlgoTopCreateFromPortfolio;

  constructor(
    private algoTopService: AlgoTopService,
    private watchlistService: WatchlistService,
    dynamicDialogConfig: DynamicDialogConfig,
    dynamicDialogRef: DynamicDialogRef,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService
  ) {
    super(dynamicDialogConfig, dynamicDialogRef, HelpIds.HELP_ALGO_STRATEGY, translateService, gps, messageToastService, algoTopService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('name', 40, true),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'referenceDate', true),
      DynamicFieldHelper.createFieldSelectString('idWatchlist', AppSettings.WATCHLIST.toUpperCase(), true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngAfterViewInit(): void {
    this.callParam = this.dynamicDialogConfig.data.callParam;
    this.dto = this.callParam.thisObject as AlgoTopCreateFromPortfolio;
    this.watchlistService.getWatchlistsByIdTenant().subscribe(watchlists => {
      this.configObject.idWatchlist.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
        'idWatchlist', 'name', watchlists, true);
    });
  }

  override submit(value: {[name: string]: any}): void {
    this.form.cleanMaskAndTransferValuesToBusinessObject(this.dto);
    this.algoTopService.createFromPortfolio(this.dto).subscribe({
      next: returnEntity => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
          {i18nRecord: this.dynamicDialogConfig.header});
        this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.CREATED, returnEntity));
      },
      error: () => this.configObject.submit.disabled = false
    });
  }

  protected getNewOrExistingInstanceBeforeSave(value: {[p: string]: any}): AlgoTop {
    return undefined;
  }
}
