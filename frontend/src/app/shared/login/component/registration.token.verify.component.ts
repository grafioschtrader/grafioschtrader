import {Component, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {LoginService} from '../service/log-in.service';
import {Subscription} from 'rxjs';
import {AppSettings} from '../../app.settings';

@Component({
  template: `
    <div class="container">
      <h1>{{'VERIFYING_TOKEN' | translate}}</h1>
    </div>
  `
})
export class RegistrationTokenVerifyComponent implements OnInit, OnDestroy {
  token: string;
  private routeSubscribe: Subscription;

  constructor(private gps: GlobalparameterService,
              private activatedRoute: ActivatedRoute,
              private router: Router,
              private loginService: LoginService) {
  }

  ngOnInit(): void {
    this.routeSubscribe = this.activatedRoute.queryParams.subscribe(params => {
      this.loginService.getTokenVerified(params['token']).subscribe((redirect: string) => {
        switch (redirect) {
          case 'TOKEN_INVALID':
          case 'TOKEN_EXPIRED':
            this.router.navigate([AppSettings.REGISTER_KEY, {failure: redirect}]);
            break;
          case 'TOKEN_SUCCESS':
            this.router.navigate([AppSettings.LOGIN_KEY, {success: redirect}]);
            break;
        }
      });
    });
  }


  ngOnDestroy(): void {
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
  }

}
