import { AfterViewInit, Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { SimpleDynamicEditBase } from '../../lib/edit/simple.dynamic.edit.base';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { AlgoTopService } from '../service/algo.top.service';
import { WatchlistService } from '../../watchlist/service/watchlist.service';
import { AlgoTop } from '../model/algo.top';
import { AlgoTopCreateFromWatchlist } from '../../entities/backend/algo.top.create';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { AppSettings } from '../../shared/app.settings';
import { HelpIds } from '../../lib/help/help.ids';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';

/**
 * Dialog for auto-generating an AlgoTop hierarchy from the instruments of a watchlist. The backend groups them by
 * asset class and weights both generated levels equally, because a watchlist carries no amounts to weight them by.
 * It therefore needs no reference date, unlike the generation from portfolio holdings.
 */
@Component({
  template: `
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)">
    </dynamic-form>
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DynamicFormModule, TranslateModule]
})
export class AlgoCreateFromWatchlistDynamicComponent
  extends SimpleDynamicEditBase<AlgoTop>
  implements OnInit, AfterViewInit
{
  static readonly DIALOG_WIDTH = 500;
  callParam: CallParam;
  private dto: AlgoTopCreateFromWatchlist;

  constructor(
    private algoTopService: AlgoTopService,
    private watchlistService: WatchlistService,
    dynamicDialogConfig: DynamicDialogConfig,
    dynamicDialogRef: DynamicDialogRef,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService
  ) {
    super(
      dynamicDialogConfig,
      dynamicDialogRef,
      HelpIds.HELP_ALGO_TREE,
      translateService,
      gps,
      messageToastService,
      algoTopService
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('name', 40, true),
      DynamicFieldHelper.createFieldSelectString('idWatchlist', AppSettings.WATCHLIST.toUpperCase(), true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngAfterViewInit(): void {
    this.callParam = this.dynamicDialogConfig.data.callParam;
    this.dto = this.callParam.thisObject as AlgoTopCreateFromWatchlist;
    this.watchlistService.getWatchlistsByIdTenant().subscribe((watchlists) => {
      this.configObject.idWatchlist.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
        'idWatchlist',
        'name',
        watchlists,
        true
      );
    });
  }

  override submit(value: { [name: string]: any }): void {
    this.form.cleanMaskAndTransferValuesToBusinessObject(this.dto);
    this.algoTopService.createFromWatchlist(this.dto).subscribe({
      next: (returnEntity) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
          i18nRecord: this.dynamicDialogConfig.header
        });
        this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.CREATED, returnEntity));
      },
      error: () => (this.configObject.submit.disabled = false)
    });
  }

  protected getNewOrExistingInstanceBeforeSave(value: { [p: string]: any }): AlgoTop {
    return undefined;
  }
}
