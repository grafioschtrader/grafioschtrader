import {Component, Input, OnInit} from '@angular/core';
import {SimpleEditBase} from '../../shared/edit/simple.edit.base';
import {AppHelper} from '../../shared/helper/app.helper';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {HelpIds} from '../../shared/help/help.ids';
import {IHistoryquoteQuality} from '../../entities/view/ihistoryquote.quality';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {HistoryquoteCreateType} from '../../entities/historyquote';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {HistoryquoteService} from '../service/historyquote.service';
import {DeleteHistoryquotesSuccess} from '../../securitycurrency/model/historyquote.quality.group';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';

@Component({
  selector: 'historyquote-delete-dialog',
  template: `
      <p-dialog header="{{'DELETE_CREATE_TYPES_QUOTES' | translate}}" [(visible)]="visibleDialog"
                [responsive]="true" [style]="{width: '500px'}"
                (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

          <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                        (submitBt)="submit($event)">
          </dynamic-form>
      </p-dialog>
  `
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
        (dhs: DeleteHistoryquotesSuccess) => {
          this.messageToastService.showMessageI18nEnableHtml(InfoLevelType.SUCCESS,
            'HISTORYQUOTE_DELETE_CREATE_TYPES', dhs);
          this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
        }, () => this.configObject.execute.disabled = false);
    } else {
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
    }
  }

  protected initialize(): void {
  }

}

class FieldCreateType {
  constructor(public fieldName: string, public hct: HistoryquoteCreateType) {
  }
}


