import {Component, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {DataType} from '../../dynamic-form/models/data.type';
import {Portfolio} from '../../entities/portfolio';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {TenantDividendAccountSelectionComponent} from './tenant-dividend-account-selection.component';
import {ColumnConfig} from '../../shared/datashowbase/column.config';
import {IdsAccounts} from '../model/ids.accounts';

/**
 * Thie dialog allows to select certain cash or security accounts. It includes two tree table one for cash the other
 * for security accounts.
 */
@Component({
  selector: 'tenant-dividend-security-account-selection-dialog',
  template: `
    <p-dialog header="{{'DIV_INCLUDE_SECURITYACCOUNT' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '600px'}"
              [contentStyle]="{'max-height':'800px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <div class="nopadding container-fluid">
        <div class="row">
          <div class="col-sm-5">
            <tenant-dividend-account-selection #securityaccountTree
                                               [columnConfig]="saColumnConfig"
                                               [portfolios]="portfolios"
                                               [idsAccount]="idsAccounts.idsSecurityaccount"
                                               [selectionRequired]="true"
                                               listAttributeName="securityaccountList" title="SECURITYACCOUNTS">
            </tenant-dividend-account-selection>
          </div>
          <div class="col-sm-7">
            <tenant-dividend-account-selection #cashaccountTree
                                               [columnConfig]="caColumnConfig"
                                               [portfolios]="portfolios"
                                               [idsAccount]="idsAccounts.idsCashaccount"
                                               [selectionRequired]="false"
                                               listAttributeName="cashaccountList" title="CASHACCOUNTS">
            </tenant-dividend-account-selection>
          </div>
        </div>
        <div style="float: right;" class="ui-dialog-buttonpane  ui-helper-clearfix">
          <button pButton class="btn" type="submit"
                  [disabled]="disableButton"
                  label="{{'APPLY' | translate}}" (click)="submit($event)">
          </button>
        </div>
      </div>
    </p-dialog>
  `
})
export class TenantDividendSecurityAccountSelectionDialogComponent {
  @Input() portfolios: Portfolio[];
  @Input() idsAccounts: IdsAccounts;
  @Input() visibleDialog;
  // Output for parent view
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  @ViewChild('securityaccountTree', {static: true}) securityaccountTree: TenantDividendAccountSelectionComponent;
  @ViewChild('cashaccountTree', {static: true}) cashaccountTree: TenantDividendAccountSelectionComponent;

  saColumnConfig: ColumnConfig[] = [];
  caColumnConfig: ColumnConfig[] = [];

  constructor() {
    const columnConfig = new ColumnConfig(DataType.String, 'name', 'Name', true, false);
    this.saColumnConfig.push(columnConfig);
    this.caColumnConfig.push(columnConfig);
    this.caColumnConfig.push(new ColumnConfig(DataType.String, 'currency', 'CURRENCY', true, false));
  }

  get disableButton(): boolean {
    return this.securityaccountTree.getSelectedAccountIds().length === 0;
  }

  public onShow(event): void {
    setTimeout(() => this.prepareData());
  }

  submit(event) {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED,
      new IdsAccounts(this.securityaccountTree.getSelectedAccountIds(), this.cashaccountTree.getSelectedAccountIds())
    ));
  }

  onHide(event) {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  private prepareData(): void {
    this.securityaccountTree.prepareData();
    this.cashaccountTree.prepareData();
  }

}



