import {Component, OnInit} from '@angular/core';
import {FormBase} from '../../shared/edit/form.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {AppHelper} from '../../shared/helper/app.helper';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {HelpIds} from '../../shared/help/help.ids';
import {CopyTradingDaysFromSourceToTarget, TradingDaysMinusService} from '../service/trading.days.minus.service';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';


/**
 * Dialog for input optional year and Stock exchange to copy a single year or all years of a trading calendar.
 */
@Component({
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                  (submitBt)="submit($event)">
    </dynamic-form>`
})
export class TradingCalendarOtherExchangeDynamicComponent extends FormBase implements OnInit {
  copyTradingDaysFromSourceToTarget: CopyTradingDaysFromSourceToTarget;

  readonly YEAR_OR_FULL_PROP = 'copyYearOrFull';
  readonly NAME = 'name';

  constructor(public translateService: TranslateService,
              public gps: GlobalparameterService,
              private messageToastService: MessageToastService,
              private tradingDaysMinusService: TradingDaysMinusService,
              private ref: DynamicDialogRef,
              private dynamicDialogConfig: DynamicDialogConfig) {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF(this.YEAR_OR_FULL_PROP, 20, true, {readonly: true}),
      DynamicFieldHelper.createFieldSelectNumberHeqF(this.NAME, true),
      DynamicFieldHelper.createSubmitButton()
    ];

    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.configObject[this.NAME].valueKeyHtmlOptions = this.dynamicDialogConfig.data.sourceCopyStockexchanges;
    this.copyTradingDaysFromSourceToTarget = this.dynamicDialogConfig.data.copyTradingDaysFromSourceToTarget;
    this.configObject[this.YEAR_OR_FULL_PROP].defaultValue = this.copyTradingDaysFromSourceToTarget.fullCopy
      ? this.dynamicDialogConfig.data.calendarRange : this.copyTradingDaysFromSourceToTarget.returnOrCopyYear;
  }

  submit(value: { [name: string]: any }): void {
    this.copyTradingDaysFromSourceToTarget.sourceIdStockexchange = value.name;
    this.tradingDaysMinusService.copyAllTradingDaysMinusToOtherStockexchange(this.copyTradingDaysFromSourceToTarget)
      .subscribe(tradingDaysWithDateBoundaries => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'TRADING_CALENDAR_COPY_SUCCESS');
        this.ref.close(tradingDaysWithDateBoundaries);
      }, error1 => {
        this.configObject.submit.disabled = false;
      });

  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_BASEDATA_STOCKEXCHANGE);
  }
}
