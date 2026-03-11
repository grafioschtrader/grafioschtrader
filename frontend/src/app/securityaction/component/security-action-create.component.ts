import {Component, OnInit} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SecurityActionService} from '../service/security-action.service';
import {SecurityAction} from '../model/security-action.model';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {AppHelper} from '../../lib/helper/app.helper';
import {HelpIds} from '../../lib/help/help.ids';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {Security} from '../../entities/security';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {SupplementCriteria} from '../../securitycurrency/model/supplement.criteria';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {DialogModule} from 'primeng/dialog';
import {SecuritycurrencySearchAndSetComponent} from '../../securitycurrency/component/securitycurrency-search-and-set.component';
import {DialogService} from 'primeng/dynamicdialog';

/**
 * Dialog component for admin users to create a new ISIN change event. Uses DialogService to open
 * the SecuritySearch dialog as a top-level DynamicDialog, avoiding nested dialog z-index issues.
 */
@Component({
  selector: 'security-action-create',
  template: `
    <p-dialog header="{{'CREATE_ISIN_CHANGE' | translate}}" [visible]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
  standalone: true,
  imports: [DialogModule, DynamicFormModule, TranslateModule],
  providers: [DialogService]
})
export class SecurityActionCreateComponent extends SimpleEditBase implements OnInit {

  private supplementCriteria = new SupplementCriteria(true, false);
  private selectedSecurity: Security | null = null;

  constructor(public translateService: TranslateService,
              gps: GlobalparameterService,
              private messageToastService: MessageToastService,
              private securityActionService: SecurityActionService,
              private dialogService: DialogService) {
    super(HelpIds.HELP_BASEDATA_SECURITY_ACTION_ISIN_RENAME, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputButtonHeqF(DataType.String, 'isinOld',
        this.handleSecuritySearchClick.bind(this), true),
      DynamicFieldHelper.createFieldInputStringVSHeqF('isinNew', 12, true, ['ISIN']),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'actionDate', true),
      DynamicFieldHelper.createFieldInputNumberHeqF('fromFactor', false, 10, 0, false),
      DynamicFieldHelper.createFieldInputNumberHeqF('toFactor', false, 10, 0, false),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', 1024, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.selectedSecurity = null;
    this.form.setDefaultValuesAndEnableSubmit();
  }

  handleSecuritySearchClick(fieldConfig: FieldConfig): void {
    this.translateService.get('SET_SECURITY').subscribe(title => {
      const ref = this.dialogService.open(SecuritycurrencySearchAndSetComponent, {
        header: title, width: '720px', resizable: false, closable: true, closeOnEscape: true,
        data: {supplementCriteria: this.supplementCriteria}
      });
      ref.onClose.subscribe((security: Security | CurrencypairWatchlist) => {
        if (security) {
          this.selectedSecurity = security as Security;
          this.configObject.isinOld.formControl.setValue(this.selectedSecurity.isin);
        }
      });
    });
  }

  submit(value: any): void {
    const action: SecurityAction = {
      idSecurityAction: null,
      securityOld: this.selectedSecurity,
      securityNew: null,
      isinOld: value.isinOld,
      isinNew: value.isinNew,
      actionDate: value.actionDate,
      note: value.note,
      fromFactor: value.fromFactor || null,
      toFactor: value.toFactor || null,
      affectedCount: 0,
      appliedCount: 0,
      createdBy: null,
      creationTime: null
    };
    this.securityActionService.createSecurityAction(action).subscribe({
      next: () => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
          {i18nRecord: 'CREATE_ISIN_CHANGE'});
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED));
      },
      error: () => this.configObject.submit.disabled = false
    });
  }
}
