import {Component, Input, OnInit} from '@angular/core';
import {DialogModule} from 'primeng/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {HelpIds} from '../../lib/help/help.ids';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {HistoryquoteLegacyService} from '../service/historyquote.legacy.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';

/**
 * Dialog that captures a forgotten split (splitDate, fromFactor, toFactor) and applies it to
 * every legacy row for the given security whose date is strictly before {@code splitDate}.
 * Backed by {@code POST /historyquotes/legacy/{idSec}/split}. No corresponding {@code Securitysplit}
 * row is created — the split only affects the shadow archive.
 */
@Component({
  selector: 'historyquote-legacy-apply-split-dialog',
  template: `
    <p-dialog header="{{'HISTORYQUOTE_LEGACY_APPLY_SPLIT' | translate}}" [visible]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
  standalone: true,
  imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class HistoryquoteLegacyApplySplitDialogComponent extends SimpleEditBase implements OnInit {
  @Input() idSecuritycurrency: number;

  constructor(public translateService: TranslateService,
              gps: GlobalparameterService,
              private historyquoteLegacyService: HistoryquoteLegacyService,
              private messageToastService: MessageToastService) {
    super(HelpIds.HELP_WATCHLIST_HISTORYQUOTES, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'splitDate', true),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'fromFactor', true, 1, 99_999_999),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'toFactor', true, 1, 99_999_999),
      DynamicFieldHelper.createSubmitButton('APPLY')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(value: { [name: string]: any }): void {
    this.configObject.submit.disabled = true;
    this.historyquoteLegacyService.applySplitToLegacy(this.idSecuritycurrency, {
      splitDate: value.splitDate,
      fromFactor: value.fromFactor,
      toFactor: value.toFactor
    }).subscribe({
      next: () => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'HISTORYQUOTE_LEGACY_APPLY_SPLIT');
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
      },
      error: () => this.configObject.submit.disabled = false
    });
  }

  protected override initialize(): void {
    this.form.setDefaultValuesAndEnableSubmit();
  }
}
