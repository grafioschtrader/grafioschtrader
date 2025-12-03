import {Component, Input, OnInit} from '@angular/core';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {AppHelper} from '../../lib/helper/app.helper';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {HelpIds} from '../../lib/help/help.ids';
import {IHistoryquoteQuality} from '../../entities/view/ihistoryquote.quality';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {HistoryquoteCreateType} from '../../entities/historyquote';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {HistoryquoteService} from '../service/historyquote.service';
import {DeleteHistoryquotesSuccess} from '../../securitycurrency/model/historyquote.quality.group';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

@Component({
    selector: 'historyquote-delete-dialog',
    template: `
      <p-dialog header="{{'DELETE_CREATE_TYPES_QUOTES' | translate}}" [(visible)]="visibleDialog"
                [style]="{width: '500px'}"
                (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

          <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                        (submitBt)="submit($event)">
          </dynamic-form>
      </p-dialog>
  `,
    standalone: true,
    imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class HistoryquoteDeleteDialogComponent extends SimpleEditBase implements OnInit {
  @Input() idSecuritycurrency: number;
  @Input() historyquoteQuality: IHistoryquoteQuality;

  readonly fieldCreatTypes: FieldCreateType[] = [new FieldCreateType('filledLinear',
    HistoryquoteCreateType.FILLED_CLOSED_LINEAR_TRADING_DAY),
    new FieldCreateType('manualImported', HistoryquoteCreateType.MANUAL_IMPORTED)];

  constructor(public translateService: TranslateService,
              private historyquoteService: HistoryquoteService,
              private messageToastService: MessageToastService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_HISTORYQUOTE_QUALITY, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      7, this.helpLink.bind(this));

    this.fieldCreatTypes.forEach(fct => {
      if (this.historyquoteQuality[fct.fieldName]) {
        this.config.push(DynamicFieldHelper.createFieldCheckboxHeqF(fct.fieldName, {defaultValue: true}));
      }
    });
    this.config.push(DynamicFieldHelper.createFunctionButtonFieldName('execute', 'EXECUTE',
      (e) => this.submit(null)));
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(value: { [name: string]: any }): void {
    this.configObject.execute.disabled = true;
    const hct: HistoryquoteCreateType[] = [];
    this.fieldCreatTypes.forEach(fct => {
      if (this.historyquoteQuality[fct.fieldName]) {
        if (this.configObject[fct.fieldName].formControl.value) {
          hct.push(fct.hct);
        }
      }
    });

    if (hct.length > 0) {
      this.historyquoteService.deleteHistoryquotesByCreateTypes(this.idSecuritycurrency, hct).subscribe(
        {next: (dhs: DeleteHistoryquotesSuccess) => {
          this.messageToastService.showMessageI18nEnableHtml(InfoLevelType.SUCCESS,
            'HISTORYQUOTE_DELETE_CREATE_TYPES', dhs);
          this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
        }, error: () => this.configObject.execute.disabled = false});
    } else {
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
    }
  }

  protected override initialize(): void {
  }

}

class FieldCreateType {
  constructor(public fieldName: string, public hct: HistoryquoteCreateType) {
  }
}


