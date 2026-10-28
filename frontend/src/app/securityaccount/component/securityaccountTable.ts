import { Directive } from '@angular/core';
import { SecurityPositionCurrenyGroupSummary } from '../../entities/view/security.position.curreny.group.summary';
import { SecurityaccountGroupBase } from './securityaccount.group.base';
import { SecurityaccountCurrencyGroup } from './securityaccount.currency.group';
import { SecurityaccountAssetclassCategortypeGroup } from './securityaccount.assetclass.categortype.group';
import { SecurityPositionDynamicGroupSummary } from '../../entities/view/security.position.dynamic.group.summary';
import { SecurityaccountAssetclassSpecInvestGroup } from './securityaccount.assetclass.spec.invest.group';
import { SecurityaccountAssetclassSubCategoryGroup } from './securityaccount.assetclass.sub.category.group';
import { SecurityaccountBaseTable } from './securityaccount.base.table';
import { SecurityaccountAssetclassGroup } from './securityaccount.assetclass.group';
import { SecurityAccountGroup } from '../model/security.account.group';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';

/**
 * @whatItDoes It is the base class for components which shows a summary for each security account. Single row
 * representing a security. It supports the user to change the grouping of this summery.
 * @howToUse
 * {@example
 *
 * @description
 *
 *
 * See
 *
 * @stable
 */
@Directive()
export abstract class SecurityaccountTable extends SecurityaccountBaseTable {
  SpecialInvestmentInstruments: typeof SpecialInvestmentInstruments = SpecialInvestmentInstruments;

  groupMapping: Map<string, any> = new Map();

  constructor() {
    super();
    this.groupMapping.set(
      SecurityAccountGroup[SecurityAccountGroup.GROUP_BY_CURRENCY],
      new SecurityaccountCurrencyGroup(this.translateService, this)
    );
    this.groupMapping.set(
      SecurityAccountGroup[SecurityAccountGroup.GROUP_BY_ASSETCLASS],
      new SecurityaccountAssetclassCategortypeGroup(this.translateService, this)
    );
    this.groupMapping.set(
      SecurityAccountGroup[SecurityAccountGroup.GROUP_BY_FINANCIAL_INSTRUMENT],
      new SecurityaccountAssetclassSpecInvestGroup(this.translateService, this)
    );
    this.groupMapping.set(
      SecurityAccountGroup[SecurityAccountGroup.GROUP_BY_SUB_CATEGORY],
      new SecurityaccountAssetclassSubCategoryGroup(this.gps, this.translateService, this)
    );
    this.groupMapping.set(
      SecurityAccountGroup[SecurityAccountGroup.GROUP_BY_ASSETCLASS_COMBINATION],
      new SecurityaccountAssetclassGroup(this.gps, this.translateService, this)
    );

    this.groupOptions = [];
    this.selectedGroup = SecurityAccountGroup[SecurityAccountGroup.GROUP_BY_CURRENCY];
    SelectOptionsHelper.createSelectItemForEnum(this.translateService, SecurityAccountGroup, this.groupOptions);

    this.securityaccountGroupBase = new SecurityaccountCurrencyGroup(this.translateService, this);
    this.createColumns();
  }

  override handleChangeGroup(event) {
    this.selectedGroup = event.value;
    this.changeGroupToView(this.groupMapping.get(this.selectedGroup));
  }

  protected override getTitleChart(): string {
    console.log('hugo');
    return this.groupOptions.find((item) => item.value === this.selectedGroup).label;
  }

  private changeGroupToView(
    securityPositionGroupSummary: SecurityaccountGroupBase<
      SecurityPositionDynamicGroupSummary<any> | SecurityPositionCurrenyGroupSummary,
      any
    >
  ): void {
    this.securityaccountGroupBase = securityPositionGroupSummary;
    this.showTable = false;
    this.securityPositionAll = null;
    this.securityPositionSummary = null;
    this.createColumns();
    this.readData();
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptionsParam(
        this.selectedSecurityPositionSummary ? this.selectedSecurityPositionSummary.security : null
      )
    });
  }
}
