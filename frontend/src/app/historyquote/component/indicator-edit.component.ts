import {Component, Input, OnInit} from '@angular/core';
import {HelpIds} from '../../shared/help/help.ids';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../shared/helper/app.helper';
import {TaEditParam, TaEditReturn} from './indicator.definitions';
import {DynamicSimpleEditBase} from '../../shared/edit/dynamic.simple.edit.base';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {TranslateHelper} from '../../shared/helper/translate.helper';

@Component({
  selector: 'indicator-edit',
  template: `
    <p-dialog header="{{'DEFINITION' | translate}}: {{taEditParam.taIndicators | translate}}"
              [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class IndicatorEditComponent extends DynamicSimpleEditBase implements OnInit {
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

  protected initialize(): void {
    this.config = [...this.taEditParam.fieldConfig];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    setTimeout(() => this.form.transferBusinessObjectToForm(this.taEditParam.taDynamicDataModel));

  }
}
