import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { Component, OnInit, input, inject } from '@angular/core';
import { EMPTY, finalize, Subscription, switchMap, take, tap } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { AppHelper } from '../../lib/helper/app.helper';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppSettings } from '../../shared/app.settings';
import { HelpIds } from '../../lib/help/help.ids';
import { BankruptSecurity } from '../../entities/bankrupt.security';
import { CurrencypairWatchlist } from '../../entities/view/currencypair.watchlist';
import { Security } from '../../entities/security';
import { SecuritycurrencySearchAndSetDialogComponent } from '../../securitycurrency/component/securitycurrency-search-and-set-dialog.component';
import { SupplementCriteria } from '../../securitycurrency/model/supplement.criteria';
import { BankruptSecurityService } from '../service/bankrupt.security.service';
import { BankruptSecurityCallParam } from './bankrupt.security.call.param';

/**
 * Marks an instrument as no longer receiving price data, or edits an existing marker.
 *
 * <p>
 * The instrument is chosen through the ordinary search dialog and only when the marker is created. On an edit it is
 * shown but not changeable: the instrument is what the row is about, so moving it would silently mark a different one
 * and unmark the instrument the reader was looking at.
 * </p>
 */
@Component({
  selector: 'bankrupt-security-edit',
  template: `
    <p-dialog
      header="{{ 'BANKRUPT_SECURITY' | translate }}"
      [visible]="visibleDialog()"
      [style]="{ width: '560px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <p class="big-size">{{ 'BANKRUPT_SECURITY_HELP' | translate }}</p>
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)" />
    </p-dialog>
  `,
  providers: [DialogService],
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class BankruptSecurityEditComponent extends SimpleEntityEditBase<BankruptSecurity> implements OnInit {
  private dialogService: DialogService = inject(DialogService);

  readonly callParam = input.required<BankruptSecurityCallParam>();

  private readonly supplementCriteria = new SupplementCriteria(true, true);
  private selectedSecurity: Security | null = null;
  private searchSubscription?: Subscription;
  readonly searchSecurity = (fieldConfig: FieldConfig): void => this.handleSecuritySearchClick(fieldConfig);

  constructor() {
    super(
      HelpIds.HELP_BASEDATA_BANKRUPT_SECURITY,
      AppHelper.toUpperCaseWithUnderscore(AppSettings.BANKRUPT_SECURITY),
      inject(BankruptSecurityService)
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    // Built synchronously from the descriptor the opener pre-fetched: the dialog is mounted lazily, so a config
    // arriving later would leave the form empty and the edit values would not transfer.
    const fieldDescriptors = this.callParam().formDefinition.fieldDescriptorInputAndShows;
    this.config = [
      DynamicFieldHelper.createFieldInputButton(DataType.String, 'securityName', 'SECURITY', this.searchSecurity, true),
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'noDataSince', fieldDescriptors),
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'noTradingSince', fieldDescriptors),
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'note', fieldDescriptors),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.selectedSecurity = null;
    // On an edit the instrument is fixed, so the search button is disabled rather than left to be pressed in vain.
    // Undefined and not false in the create case: the button binds through [attr.disabled], which renders a present
    // disabled="false" attribute for a false value and would take the button away exactly when it is needed.
    this.configObject.securityName.disabled = this.callParam().bankruptSecurity ? true : undefined;
    this.form().setDefaultValuesAndEnableSubmit();
    const callParam = this.callParam();
    if (callParam.bankruptSecurity) {
      this.form().transferBusinessObjectToForm(callParam.bankruptSecurity);
    }
    requireFormControl(this.configObject.securityName).setValue(callParam.securityName ?? '');
  }

  handleSecuritySearchClick(fieldConfig: FieldConfig): void {
    this.searchSubscription?.unsubscribe();
    this.searchSubscription = this.translateService
      .get('SET_SECURITY')
      .pipe(
        switchMap((title) => {
          const ref = this.dialogService.open(SecuritycurrencySearchAndSetDialogComponent, {
            header: title,
            width: '720px',
            resizable: false,
            modal: true,
            closable: true,
            closeOnEscape: true,
            data: { supplementCriteria: this.supplementCriteria }
          });
          if (!ref) return EMPTY;
          // The nested dialog belongs to this edit session, including when navigation destroys its owner.
          let closed = false;
          return ref.onClose.pipe(
            take(1),
            tap(() => (closed = true)),
            finalize(() => {
              if (!closed) ref.close();
            })
          );
        }),
        this.untilDialogClosed()
      )
      .subscribe((security: Security | CurrencypairWatchlist) => {
        if (security) {
          this.selectedSecurity = security as Security;
          requireFormControl(this.configObject.securityName).setValue(this.selectedSecurity.name);
        }
      });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): BankruptSecurity {
    const bankruptSecurity = new BankruptSecurity();
    const callParam = this.callParam();
    this.copyFormToPrivateBusinessObject(bankruptSecurity, callParam.bankruptSecurity);
    bankruptSecurity.idSecuritycurrency = callParam.bankruptSecurity
      ? callParam.bankruptSecurity.idSecuritycurrency
      : this.selectedSecurity?.idSecuritycurrency;
    return bankruptSecurity;
  }
}
