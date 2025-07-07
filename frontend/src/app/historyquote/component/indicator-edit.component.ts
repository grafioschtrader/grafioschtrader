import {Component, Input, OnInit} from '@angular/core';
import {HelpIds} from '../../shared/help/help.ids';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../lib/helper/app.helper';
import {TaEditParam, TaEditReturn} from './indicator.definitions';
import {DynamicFieldSimpleEditBase} from '../../shared/edit/dynamic.field.simple.edit.base.directive';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {TranslateHelper} from '../../helper/translate.helper';

@Component({
    selector: 'indicator-edit',
    template: `
    <p-dialog header="{{'DEFINITION' | translate}}: {{taEditParam.taIndicators | translate}}"
              [(visible)]="visibleDialog"
              [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: false
})
export class IndicatorEditComponent extends DynamicFieldSimpleEditBase implements OnInit {
  @Input() taEditParam: TaEditParam;

  constructor(public translateService: TranslateService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_WATCHLIST_HISTORYQUOTES_CHART, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      6, this.helpLink.bind(this));
  }

  submit(values: { [name: string]: any }): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED,
      new TaEditReturn(this.taEditParam.taIndicators, values)));
  }

  protected override initialize(): void {
    this.config = [...this.taEditParam.fieldConfig];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    setTimeout(() => this.form.transferBusinessObjectToForm(this.taEditParam.taDynamicDataModel));

  }
}
