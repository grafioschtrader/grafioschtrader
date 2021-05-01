import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {LoginService} from '../service/log-in.service';
import {DataType} from '../../../dynamic-form/models/data.type';
import {TranslateService} from '@ngx-translate/core';
import {AppSettings} from '../../app.settings';
import {DynamicFormComponent} from '../../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {DynamicFieldHelper, VALIDATION_SPECIAL} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {FormBase} from '../../edit/form.base';
import {DynamicDialogHelper} from '../../dynamicdialog/component/dynamic.dialog.helper';
import {DialogService} from 'primeng/dynamicdialog';
import {ActuatorService, ApplicationInfo} from '../../service/actuator.service';
import {BusinessHelper} from '../../helper/business.helper';
import {HelpIds} from '../../help/help.ids';


@Component({
  template: `
    <div class="container">
      <div class="login jumbotron center-block">
        <div class="alert alert-success" role="alert" *ngIf="successLastRegistration">
          {{successLastRegistration | translate}}
        </div>
        <application-info [applicationInfo]="applicationInfo"></application-info>
        <ng-container *ngIf="formConfig">
          <h2>{{'SIGN_IN' | translate}}</h2>
          <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                        #form="dynamicForm"
                        (submit)="submit($event)">
          </dynamic-form>
        </ng-container>
        <p-card header="{{'RELEASE_NOTE' | translate}}">
          <h4>0.14.0</h4>
          {{'V_0_14_0' | translate}}
          <h4>0.13.0</h4>
          {{'V_0_13_0' | translate}}
          <h4>0.12.0</h4>
          {{'V_0_12_0' | translate}}
        </p-card>
      </div>
    </div>
  `,
  providers: [DialogService]
})
export class LoginComponent extends FormBase implements OnInit, OnDestroy {
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;
  applicationInfo: ApplicationInfo = new ApplicationInfo();
  queryParams: any;
  successLastRegistration: string;

  constructor(private router: Router,
              private activatedRoute: ActivatedRoute,
              private loginService: LoginService,
              private actuatorService: ActuatorService,
              public translateService: TranslateService,
              private dialogService: DialogService,
              private globalparameterService: GlobalparameterService) {
    super();
  }

  ngOnInit(): void {
    this.actuatorService.applicationInfo().subscribe((applicationInfo: ApplicationInfo) => {
      this.applicationInfo = applicationInfo;
      this.loginFormDefinition();
    }, err => this.applicationInfo = null);
  }

  private loginFormDefinition(): void {
    this.formConfig = {labelcolumns: 2, helpLinkFN: this.helpLink.bind(this), nonModal: true,
      language: this.translateService.currentLang};
    console.log('currentlang:', this.translateService.currentLang);

    this.applicationInfo.users;

    this.config = [];
    this.config.push(DynamicFieldHelper.createFieldDAInputStringVS(DataType.Email, 'email', 'EMAIL', 30, true,
      [VALIDATION_SPECIAL.EMail]));
    this.config.push(DynamicFieldHelper.createFieldDAInputString(DataType.Password, 'password', 'PASSWORD', 30,
      true));
    if (this.applicationInfo.users.active < this.applicationInfo.users.allowed) {
      this.config.push(DynamicFieldHelper.createFunctionButton('REGISTRATION',
        (e) => this.router.navigate([`/${AppSettings.REGISTER_KEY}`])));
    }
    this.config.push(DynamicFieldHelper.createSubmitButton('SIGN_IN'));
    this.queryParams = this.activatedRoute.params.subscribe(params => this.successLastRegistration = params['success']);
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(value: { [name: string]: any }): void {
    this.loginService.login(value.email, value.password)
      .subscribe((response: Response) => {
        this.loginService.aftersuccessfully(response.headers.get('x-auth-token'), (response as any).body);
        if (this.globalparameterService.getIdTenant()) {
          // Navigate to the main view
          this.router.navigate([`/${AppSettings.MAINVIEW_KEY}`]);
        } else {
          // It is a new tenant -> setup is required
          this.router.navigate([`/${AppSettings.TENANT_KEY}`]);
        }
      }, (errorBackend) => {
        this.configObject.submit.disabled = false;

        if (errorBackend.bringUpDialog) {
          const dynamicDialogHelper = DynamicDialogHelper.getOpenedLogoutReleaseRequestDynamicComponent(
            this.translateService, this.dialogService, value.email, value.password);
        }
      });
  }

  ngOnDestroy(): void {
    this.queryParams.unsubscribe();
  }

  helpLink() {
    BusinessHelper.toExternalHelpWebpage(this.translateService.currentLang, HelpIds.HELP_INTRO);
  }

}

export interface ConfigurationWithLogin {
  useWebsocket: boolean;
  useAlgo: boolean;
  entityNameWithKeyNameList: EntityNameWithKeyName[];
  crypotcurrencies: string[];
}

export interface EntityNameWithKeyName {
  entityName: string;
  keyName: string;
}
