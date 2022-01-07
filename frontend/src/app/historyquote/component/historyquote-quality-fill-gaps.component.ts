import {Component, Input, OnInit} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {SimpleEditBase} from '../../shared/edit/simple.edit.base';
import {HelpIds} from '../../shared/help/help.ids';
import {AppHelper} from '../../shared/helper/app.helper';
import {IHistoryquoteQuality} from '../../entities/view/ihistoryquote.quality';
import {Securitycurrency} from '../../entities/securitycurrency';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {HisotryqouteLinearFilledSummary} from '../../securitycurrency/model/historyquote.quality.group';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';

/**
 * Dialog to fill gaps of history quotes.
 */
@Component({
  selector: 'historyquote-quality-fill-gaps',
  template: `
    <p-dialog header="{{'HISTORYQUOTE_FILL_GAPS' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class HistoryquoteQualityFillGapsComponent extends SimpleEditBase implements OnInit {
  @Input() historyquoteQuality: IHistoryquoteQuality;
  @Input() securitycurrency: Securitycurrency;

  readonly moveWeekendToFriday = 'moveWeekendToFriday';

  constructor(public translateService: TranslateService,
              private securityService: SecurityService,
              private messageToastService: MessageToastService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_HISTORYQUOTE_QUALITY, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      7, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldCheckboxHeqF(this.moveWeekendToFriday),
      // Angular -> Submit button can not be used because they may be a single no enabled input field
      DynamicFieldHelper.createFunctionButtonFieldName('execute', 'EXECUTE',
        (e) => this.submit(null))
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(value: { [name: string]: any }): void {
    this.configObject.execute.disabled = true;
    this.securityService.fillHistoryquoteGapsLinear(this.securitycurrency.idSecuritycurrency,
      this.configObject[this.moveWeekendToFriday].formControl.value)
      .subscribe((hlfs: HisotryqouteLinearFilledSummary) => {
        this.messageToastService.showMessageI18nEnableHtml(hlfs.warning ? InfoLevelType.WARNING : InfoLevelType.SUCCESS,
          'HISTORYQUOTE_FILL_RESULT', hlfs);
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
      }, () => this.configObject.execute.disabled = false);
  }

  protected initialize(): void {
    this.form.setDefaultValuesAndEnableSubmit();
    const hasWeekendDays: boolean = this.historyquoteQuality.quoteSaturday !== null && this.historyquoteQuality.quoteSaturday > 0
      || this.historyquoteQuality.quoteSunday !== null && this.historyquoteQuality.quoteSunday > 0;
    this.configObject[this.moveWeekendToFriday].formControl.setValue(hasWeekendDays);
    !hasWeekendDays && this.configObject[this.moveWeekendToFriday].formControl.disable();
  }
}
