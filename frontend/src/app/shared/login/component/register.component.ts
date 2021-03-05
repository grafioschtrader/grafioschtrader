import {AfterViewInit, Component, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {LoginService} from '../service/log-in.service';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../message/message.toast.service';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {AppSettings} from '../../app.settings';
import {User} from '../../../entities/user';
import {InfoLevelType} from '../../message/info.leve.type';
import {PasswordBaseComponent} from './password.base.component';
import {DynamicFieldHelper, VALIDATION_SPECIAL} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {ActuatorService, ApplicationInfo} from '../../service/actuator.service';

@Component({
  template: `
    <div class="container">
      <div class="login jumbotron center-block">

        <div class="alert alert-danger" role="alert" *ngIf="errorLastRegistration">
          {{errorLastRegistration | translate}}
        </div>
        <application-info [applicationInfo]="applicationInfo"></application-info>
        <ng-container *ngIf="applicationInfo">
          <h2>{{'REGISTRATION' | translate}}</h2>
          <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                        (submit)="submit($event)">
          </dynamic-form>

          <div *ngIf="progressValue">
            <h4>{{'REGISTER_WAIT' | translate}}</h4>
            <p-progressBar [value]="progressValue"></p-progressBar>
          </div>
          <div class="alert alert-info" role="alert" *ngIf="confirmEmail">
            {{'REGISTRATION_EMAIL' | translate}}
          </div>
        </ng-container>
      </div>
    </div>
  `
})
export class RegisterComponent extends PasswordBaseComponent implements OnInit, AfterViewInit, OnDestroy {
  progressValue: number;
  errorLastRegistration: string;
  queryParams: any;
  confirmEmail = false;
  applicationInfo: ApplicationInfo;


  constructor(private globalparameterService: GlobalparameterService,
              private messageToastService: MessageToastService,
              private actuatorService: ActuatorService,
              private activatedRoute: ActivatedRoute,
              private router: Router,
              private loginService: LoginService,
              translateService: TranslateService) {
    super(translateService, false);
  }

  ngOnInit(): void {
    this.actuatorService.applicationInfo().subscribe((applicationInfo: ApplicationInfo) => {
      this.applicationInfo = applicationInfo;
      this.loginFormDefinition();
    });

  }

  private loginFormDefinition(): void {
    this.formConfig = {labelcolumns: 3, nonModal: true};
    this.config = [
      DynamicFieldHelper.createFieldInputString('nickname', 'NICKNAME', 30, true,
        {minLength: 2}),
      DynamicFieldHelper.createFieldInputStringVS('email', 'EMAIL', 30, true, [VALIDATION_SPECIAL.EMail]),
      {formGroupName: 'passwordGroup', fieldConfig: this.configPassword},
      DynamicFieldHelper.createFieldSelectString('localeStr', 'LOCALE', true,
        {inputWidth: 10}),
      DynamicFieldHelper.createFunctionButton('SIGN_IN', (e) => this.router.navigate([`/${AppSettings.LOGIN_KEY}`])),
      DynamicFieldHelper.createSubmitButton('REGISTRATION')
    ];

    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.prepareData();
  }

  submit(value: { [name: string]: any }): void {
    const user = new User();
    this.form.cleanMaskAndTransferValuesToBusinessObject(user);
    const date = new Date();
    user.timezoneOffset = date.getTimezoneOffset();
    this.loginService.logout();
    this.form.setDisableAll(true);
    this.showProgressIndicator();
    this.loginService.update(user).subscribe(newUser => {
      this.progressValue = 100;
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'USER_NEW_SAVED');
      this.progressValue = undefined;
      this.confirmEmail = true;
    }, () => {
      this.progressValue = undefined;
      this.form.setDisableAll(false);
      this.configObject.submit.disabled = false;
    });
  }

  showProgressIndicator() {
    this.progressValue = 0;
    const interval = setInterval(() => {
      this.progressValue = this.progressValue + Math.floor(Math.random() * 10) + 1;
      if (this.progressValue >= 100) {
        this.progressValue = 100;

        clearInterval(interval);
      }
    }, 1000);
  }

  ngAfterViewInit(): void {

  }

  ngOnDestroy(): void {
    this.queryParams && this.queryParams.unsubscribe();
  }

  private prepareData() {
    this.queryParams = this.activatedRoute.params.subscribe(params => {
      this.errorLastRegistration = params['failure'];
    });
    this.globalparameterService.getSupportedLocales().subscribe(data => {
        this.configObject.localeStr.valueKeyHtmlOptions = data;
      super.afterViewInit();
        this.configObject.nickname.elementRef.nativeElement.focus();
      }
    );
  }
}
