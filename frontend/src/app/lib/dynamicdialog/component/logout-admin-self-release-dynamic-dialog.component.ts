import { Component, signal, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { LoginService } from '../../login/service/log-in.service';
import { InfoLevelType } from '../../message/info.leve.type';
import { MessageToastService } from '../../message/message.toast.service';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { BaseSettings } from '../../base.settings';
import { ButtonModule } from '@openng/optimus-ui/button';

/**
 * Confirmation dialog for admin self-release from lockout.
 *
 * When the admin exceeds security breach or request limit thresholds, they cannot
 * approve their own release through the normal ProposeUserTask flow. This dialog
 * lets the admin confirm a self-release which resets both counters and logs them in.
 */
@Component({
  template: ` <p>{{ 'ADMIN_SELF_RELEASE_QUESTION' | translate }}</p>
    <div class="flex justify-content-end mt-3">
      <p-button [label]="'ADMIN_SELF_RELEASE_CONFIRM' | translate" (click)="confirm()" [loading]="loading()">
        <i class="pi pi-check" pButtonIcon></i>
      </p-button>
    </div>`,
  imports: [TranslateModule, ButtonModule]
})
export class LogoutAdminSelfReleaseDynamicDialogComponent {
  private translateService = inject(TranslateService);
  private gps = inject(GlobalparameterService);
  private messageToastService = inject(MessageToastService);
  private loginService = inject(LoginService);
  private router = inject(Router);
  private dynamicDialogRef = inject(DynamicDialogRef);
  private dynamicDialogConfig = inject(DynamicDialogConfig);

  readonly loading = signal(false);

  confirm(): void {
    this.loading.set(true);
    this.loginService
      .login(this.dynamicDialogConfig.data.email, this.dynamicDialogConfig.data.password, 'ADMIN_SELF_RELEASE')
      .subscribe({
        next: (response) => {
          this.loginService.afterSuccessfulLogin(response.headers.get('x-auth-token'), response.body);
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'ADMIN_SELF_RELEASE_SUCCESS');
          this.dynamicDialogRef.close();
          if (this.gps.getIdTenant()) {
            this.router.navigate([`/${BaseSettings.MAINVIEW_KEY}`]);
          } else {
            this.router.navigate([`/${BaseSettings.TENANT_KEY}`]);
          }
        },
        error: () => this.loading.set(false)
      });
  }
}
