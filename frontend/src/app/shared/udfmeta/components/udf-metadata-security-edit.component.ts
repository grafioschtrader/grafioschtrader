import {SimpleEntityEditBase} from '../../edit/simple.entity.edit.base';
import {UDFMetadataSecurity, UDFMetadataSecurityParam} from '../model/udf.metadata';
import {HelpIds} from '../../help/help.ids';
import {AppSettings} from '../../app.settings';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {UDFMetadataSecurityService} from '../service/udf.metadata.security.service';
import {Component, Input, OnInit} from '@angular/core';
import {AppHelper} from '../../helper/app.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';

/**
 * Edit user defined fields metadata of security in a dialog
 */
@Component({
    selector: 'udf-metadata-security-edit',
    template: `
        <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
                  [style]="{width: '500px'}" (onShow)="onShow($event)" (onHide)="onHide($event)"
                  [modal]="true">
            <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                          #form="dynamicForm"
                          (submitBt)="submit($event)">
            </dynamic-form>
        </p-dialog>
    `
})
export class UDFMetadataSecurityEditComponent extends SimpleEntityEditBase<UDFMetadataSecurity> implements OnInit {
    @Input() callParam: UDFMetadataSecurityParam;

    constructor(translateService: TranslateService,
                gps: GlobalparameterService,
                messageToastService: MessageToastService,
                uDFMetadataSecurityService: UDFMetadataSecurityService) {
        super(HelpIds.HELP_BASEDATA_UDF_METADATA_SECURITY, AppHelper.toUpperCaseWithUnderscore(AppSettings.UDF_METADATA_SECURITY), translateService, gps,
            messageToastService, uDFMetadataSecurityService);
    }

    ngOnInit(): void {
        this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
            4, this.helpLink.bind(this));
        this.config = [
            DynamicFieldHelper.createFieldSelectNumberHeqF('uiOrder',  true),
            DynamicFieldHelper.createFieldSelectString(AppSettings.CATEGORY_TYPE, AppSettings.ASSETCLASS.toUpperCase(), true),
            DynamicFieldHelper.createFieldSelectString('specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', true),
            DynamicFieldHelper.createSubmitButton()
        ];
        this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    }

    protected override initialize(): void {

    }


    protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): UDFMetadataSecurity {
        const uDFMetadataSecurity: UDFMetadataSecurity = new UDFMetadataSecurity();

        // ...
        return uDFMetadataSecurity;
    }

}
