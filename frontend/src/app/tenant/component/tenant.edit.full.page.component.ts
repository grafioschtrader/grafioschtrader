import { AfterViewInit, Component, OnInit, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { Tenant } from '../../entities/tenant';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { LoginService } from '../../lib/login/service/log-in.service';
import { TenantEditComponent } from './tenant.edit.component';

/**
 * Edit tenant fields on a full page layout used for a new tenant.
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
          (submitBt)="submit($event)" />
      </div>
    </div>
  `,
  imports: [TranslateModule, DynamicFormComponent]
})
export class TenantEditFullPageComponent extends TenantEditComponent implements OnInit, AfterViewInit {
  private loginService = inject(LoginService);

  constructor() {
    super(true, 4);
  }

  ngOnInit(): void {
    super.init(false, true);
  }

  ngAfterViewInit(): void {
    this.loadData();
  }

  protected override closeInputDialog(tenant: Tenant): void {
    this.loginService.logoutWithLoginView();
  }
}
