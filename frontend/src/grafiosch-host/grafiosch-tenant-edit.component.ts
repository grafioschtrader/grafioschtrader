import { Component, OnInit, ViewChild, ChangeDetectionStrategy } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { DynamicFormComponent } from '../app/lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { FieldConfig } from '../app/lib/dynamic-form/models/field.config';
import { FormConfig } from '../app/lib/dynamic-form/models/form.config';
import { DynamicFieldHelper } from '../app/lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../app/lib/helper/translate.helper';
import { LoginService } from '../app/lib/login/service/log-in.service';
import { MessageToastService } from '../app/lib/message/message.toast.service';
import { InfoLevelType } from '../app/lib/message/info.leve.type';
import { GrafioschTenant, GrafioschTenantService } from './grafiosch-tenant.service';

/**
 * Full page tenant registration, shown once directly after the first login: {@code LoginComponent} routes here whenever
 * the signed-in user has no tenant yet.
 *
 * <p>The page belongs to the host and not to the library, because {@code TenantBase} is extended per application — this
 * one has nothing but a name, while Grafioschtrader adds currency, kind and more. What is reused is everything around
 * it: the dynamic form, the toast service and the login service.</p>
 *
 * <p>As in Grafioschtrader the user is signed out afterwards, so the next login is issued with a JWT that carries the
 * new tenant.</p>
 */
@Component({
  template: `
    <div class="container">
      <div class="jumbotron-replacement mx-auto">
        <h2>{{ 'TENANT' | translate }}</h2>
        <h4>{{ 'TENANT_REGISTER' | translate }}</h4>
        <dynamic-form
          [config]="config"
          [formConfig]="formConfig"
          [translateService]="translateService"
          #form="dynamicForm"
          (submitBt)="submit($event)">
        </dynamic-form>
      </div>
    </div>
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslateModule, DynamicFormComponent]
})
export class GrafioschTenantEditComponent implements OnInit {
  @ViewChild('form') form: DynamicFormComponent;

  config: FieldConfig[] = [];
  configObject: { [name: string]: FieldConfig };
  formConfig: FormConfig;

  constructor(
    public translateService: TranslateService,
    private loginService: LoginService,
    private messageToastService: MessageToastService,
    private tenantService: GrafioschTenantService
  ) {}

  ngOnInit(): void {
    this.formConfig = { labelColumns: 4 };
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('tenantName', 25, true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    setTimeout(() => this.form.setDefaultValuesAndEnableSubmit());
  }

  submit(value: { [name: string]: any }): void {
    const tenant = new GrafioschTenant();
    tenant.tenantName = value.tenantName;
    this.tenantService.update(tenant).subscribe({
      next: () => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', { i18nRecord: 'TENANT' });
        // The JWT still says "no tenant"; a fresh login is the shortest way to a consistent session.
        this.loginService.logoutWithLoginView();
      },
      error: () => (this.configObject.submit.disabled = false)
    });
  }
}
