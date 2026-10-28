import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { LoginService } from '../service/log-in.service';
import { Subscription } from 'rxjs';
import { BaseSettings } from '../../base.settings';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  template: `
    <div class="container">
      <h1>{{ 'VERIFYING_TOKEN' | translate }}</h1>
    </div>
  `,
  imports: [TranslateModule]
})
export class RegistrationTokenVerifyComponent implements OnInit, OnDestroy {
  private gps = inject(GlobalparameterService);
  private activatedRoute = inject(ActivatedRoute);
  private router = inject(Router);
  private loginService = inject(LoginService);

  token: string;
  private routeSubscribe: Subscription;

  ngOnInit(): void {
    this.routeSubscribe = this.activatedRoute.queryParams.subscribe((params) => {
      this.loginService.getTokenVerified(params['token']).subscribe((redirect: string) => {
        switch (redirect) {
          case 'TOKEN_INVALID':
          case 'TOKEN_EXPIRED':
            this.router.navigate([BaseSettings.REGISTER_KEY, { failure: redirect }]);
            break;
          case 'TOKEN_SUCCESS':
            this.router.navigate([BaseSettings.LOGIN_KEY, { success: redirect }]);
            break;
        }
      });
    });
  }

  ngOnDestroy(): void {
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
  }
}
