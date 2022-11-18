import {Component, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {LoginService} from '../service/log-in.service';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../message/message.toast.service';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {AppSettings} from '../../app.settings';
import {User} from '../../../entities/user';
import {InfoLevelType} from '../../message/info.leve.type';
import {PasswordBaseComponent} from './password.base.component';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {ActuatorService, ApplicationInfo} from '../../service/actuator.service';
import {BusinessHelper} from '../../helper/business.helper';
import {HelpIds} from '../../help/help.ids';
import {combineLatest} from 'rxjs';
import {FieldDescriptorInputAndShow} from '../../dynamicfield/field.descriptor.input.and.show';
import {GlobalSessionNames} from '../../global.session.names';
import {DynamicFieldModelHelper} from '../../helper/dynamic.field.model.helper';

/**
 * Shows the register form.
 */
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
          <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                        #form="dynamicForm"
                        (submitBt)="submit($event)">
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
export class RegisterComponent extends PasswordBaseComponent implements OnInit, OnDestroy {
  progressValue: number;
  errorLastRegistration: string;
  queryParams: any;
  confirmEmail = false;
  applicationInfo: ApplicationInfo;

  constructor(private gps: GlobalparameterService,
              private messageToastService: MessageToastService,
              private actuatorService: ActuatorService,
              private activatedRoute: ActivatedRoute,
              private router: Router,
              private loginService: LoginService,
              translateService: TranslateService) {
    super(translateService);
  }

  ngOnInit(): void {
    combineLatest([this.actuatorService.applicationInfo(), this.gps.getUserFormDefinitions()]).subscribe({
        next: (data: [applicationInfo: ApplicationInfo, fdias: FieldDescriptorInputAndShow[]]) => {
          this.applicationInfo = data[0];
          sessionStorage.setItem(GlobalSessionNames.USER_FORM_DEFINITION, JSON.stringify(data[1]));
          this.loginFormDefinition(data[1]);
        }, error: err => this.applicationInfo = null
      }
    );
  }

  submit(value: { [name: string]: any }): void {
    const user = new User();
    this.form.cleanMaskAndTransferValuesToBusinessObject(user);
    const date = new Date();
    user.timezoneOffset = date.getTimezoneOffset();
    this.loginService.logout();
    this.form.setDisableAll(true);
    this.showProgressIndicator();
    this.loginService.update(user).subscribe( { next: newUser => {
      this.progressValue = 100;
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'USER_NEW_SAVED');
      this.progressValue = undefined;
      this.confirmEmail = true;
    }, error: () => {
      this.progressValue = undefined;
      this.form.setDisableAll(false);
      this.configObject.submit.disabled = false;
    }});
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

  ngOnDestroy(): void {
    this.queryParams && this.queryParams.unsubscribe();
  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.translateService.currentLang, HelpIds.HELP_INTRO_REGISTER);
  }

  private loginFormDefinition(fdias: FieldDescriptorInputAndShow[]): void {
    super.init(fdias, false);
    this.formConfig = {
      labelcolumns: 3, helpLinkFN: this.helpLink.bind(this), nonModal: true,
      language: this.translateService.currentLang
    };

    this.config = [
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF('nickname', fdias),
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF('email', fdias),
      {formGroupName: 'passwordGroup', fieldConfig: this.configPassword},
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF('localeStr', fdias),
      DynamicFieldHelper.createFunctionButton('SIGN_IN', (e) =>
        this.router.navigate([`/${AppSettings.LOGIN_KEY}`])),
      DynamicFieldHelper.createSubmitButton('REGISTRATION')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.prepareData();
  }

  private prepareData() {
    this.queryParams = this.activatedRoute.params.subscribe(params => {
      this.errorLastRegistration = params['failure'];
    });
    this.gps.getSupportedLocales().subscribe(data => {
        this.configObject.localeStr.valueKeyHtmlOptions = data;
        super.afterViewInit();
        this.configObject.nickname.elementRef.nativeElement.focus();
      }
    );
  }
}
