import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { FormBase } from '../../lib/edit/form.base';
import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HelpIds } from '../../lib/help/help.ids';
import { CopyTradingDaysFromSourceToTarget, TradingDaysMinusService } from '../service/trading.days.minus.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';

/**
 * Dialog for input optional year and Stock exchange to copy a single year or all years of a trading calendar.
 */
@Component({
  template: ` <dynamic-form
    [config]="config"
    [formConfig]="formConfig"
    [translateService]="translateService"
    #form="dynamicForm"
    (submitBt)="submit($event)" />`,
  imports: [DynamicFormComponent]
})
export class TradingCalendarOtherExchangeDynamicDialogComponent extends FormBase implements OnInit {
  public translateService: TranslateService = inject(TranslateService);
  public gps: GlobalparameterService = inject(GlobalparameterService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private tradingDaysMinusService: TradingDaysMinusService = inject(TradingDaysMinusService);
  private ref: DynamicDialogRef = inject(DynamicDialogRef);
  private dynamicDialogConfig: DynamicDialogConfig = inject(DynamicDialogConfig);

  private readonly destroyRef = inject(DestroyRef);
  readonly copyTradingDaysFromSourceToTarget: CopyTradingDaysFromSourceToTarget =
    this.dynamicDialogConfig.data.copyTradingDaysFromSourceToTarget;

  readonly YEAR_OR_FULL_PROP = 'copyYearOrFull';
  readonly NAME = 'name';

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF(this.YEAR_OR_FULL_PROP, 20, true, { readonly: true }),
      DynamicFieldHelper.createFieldSelectNumberHeqF(this.NAME, true),
      DynamicFieldHelper.createSubmitButton()
    ];

    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.configObject[this.NAME].valueKeyHtmlOptions = this.dynamicDialogConfig.data.sourceCopyStockexchanges;
    this.configObject[this.YEAR_OR_FULL_PROP].defaultValue = this.copyTradingDaysFromSourceToTarget.fullCopy
      ? this.dynamicDialogConfig.data.calendarRange
      : this.copyTradingDaysFromSourceToTarget.returnOrCopyYear;
  }

  submit(value: { [name: string]: any }): void {
    this.copyTradingDaysFromSourceToTarget.sourceIdStockexchange = +value.name;
    this.tradingDaysMinusService
      .copyAllTradingDaysMinusToOtherStockexchange(this.copyTradingDaysFromSourceToTarget)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (tradingDaysWithDateBoundaries) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'TRADING_CALENDAR_COPY_SUCCESS');
          this.ref.close(tradingDaysWithDateBoundaries);
        },
        error: (error1) => {
          this.configObject.submit.disabled = false;
        }
      });
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_BASEDATA_STOCKEXCHANGE);
  }
}
