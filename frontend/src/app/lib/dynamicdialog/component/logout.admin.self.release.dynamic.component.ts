import {Component} from '@angular/core';
import {Router} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {LoginService} from '../../login/service/log-in.service';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {BaseSettings} from '../../base.settings';
import {ButtonModule} from 'primeng/button';

/**
 * Confirmation dialog for admin self-release from lockout.
 *
 * When the admin exceeds security breach or request limit thresholds, they cannot
 * approve their own release through the normal ProposeUserTask flow. This dialog
 * lets the admin confirm a self-release which resets both counters and logs them in.
 */
@Component({
  template: `
    <p>{{ 'ADMIN_SELF_RELEASE_QUESTION' | translate }}</p>
    <div class="flex justify-content-end mt-3">
      <p-button [label]="'ADMIN_SELF_RELEASE_CONFIRM' | translate" icon="pi pi-check"
                (click)="confirm()" [loading]="loading" />
    </div>`,
  standalone: true,
  imports: [TranslateModule, ButtonModule]
})
export class LogoutAdminSelfReleaseDynamicComponent {
  loading = false;

  constructor(
    private translateService: TranslateService,
    private gps: GlobalparameterService,
    private messageToastService: MessageToastService,
    private loginService: LoginService,
    private router: Router,
    private dynamicDialogRef: DynamicDialogRef,
    private dynamicDialogConfig: DynamicDialogConfig) {
  }

  confirm(): void {
    this.loading = true;
    this.loginService.login(this.dynamicDialogConfig.data.email,
      this.dynamicDialogConfig.data.password, 'ADMIN_SELF_RELEASE')
      .subscribe({
        next: (response: Response) => {
          this.loginService.afterSuccessfulLogin(response.headers.get('x-auth-token'),
            (response as any).body);
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'ADMIN_SELF_RELEASE_SUCCESS');
          this.dynamicDialogRef.close();
          if (this.gps.getIdTenant()) {
            this.router.navigate([`/${BaseSettings.MAINVIEW_KEY}`]);
          } else {
            this.router.navigate([`/${BaseSettings.TENANT_KEY}`]);
          }
        },
        error: () => this.loading = false
      });
  }
}
