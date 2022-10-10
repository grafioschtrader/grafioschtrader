import {Component, Input, OnInit} from "@angular/core";
import {TreeTableConfigBase} from "../../shared/datashowbase/tree.table.config.base";
import {DataType} from "../../dynamic-form/models/data.type";
import {TranslateValue} from "../../shared/datashowbase/column.config";
import {GTNetMessage} from "../model/gtnet.message";
import {TreeNode} from "primeng/api";
import {TranslateService} from "@ngx-translate/core";
import {GlobalparameterService} from "../../shared/service/globalparameter.service";

@Component({
  selector: 'gtnet-message-treetable',
  template: `
    <div class="datatable nestedtable" style="min-width: 200px; max-width: 400px;">
      <p-treeTable [value]="rootNode.children" [columns]="fields">
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th *ngFor="let field of fields" [style.width.px]="field.width">
              {{field.headerTranslated}}
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="fields">
          <tr>
            <ng-container *ngFor="let field of fields; let i = index">
              <td *ngIf="field.visible"
                  [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric)? 'text-right': ''"
                  [style.width.px]="field.width">
                <p-treeTableToggler [rowNode]="rowNode" *ngIf="i === 0"></p-treeTableToggler>
                <ng-container [ngSwitch]="field.templateName">
                  <ng-container *ngSwitchCase="'greenRed'">
                  <span [pTooltip]="getValueByPath(rowData, field)"
                        [style.color]='isValueByPathMinus(rowData, field)? "red": "inherit"'
                        tooltipPosition="top">
                    {{getValueByPath(rowData, field)}}
                  </span>
                  </ng-container>
                  <ng-container *ngSwitchDefault>
                    <span [pTooltip]="getValueByPath(rowData, field)">{{getValueByPath(rowData, field)}}</span>
                  </ng-container>
                </ng-container>
              </td>
            </ng-container>
          </tr>
        </ng-template>
      </p-treeTable>
    </div>
  `
})

export class GTNetMessageTreeTableComponent extends TreeTableConfigBase implements OnInit {
  @Input() gtNetMessages: GTNetMessage[];
  rootNode: TreeNode = {children: []};

  constructor(translateService: TranslateService,
              gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'property', 'PROPERTY_PERIOD', true, false,
      {translateValues: TranslateValue.UPPER_CASE});

    this.prepareData();
  }

  private prepareData(): void {
    const nodeMap = new Map<number, TreeNode>();
    this.gtNetMessages.forEach(gtMessage => {
      let addNode = this.rootNode;
      if(gtMessage.replyTo) {
        addNode = nodeMap.get(gtMessage.replyTo);
         if(addNode.leaf) {
           addNode.leaf = false;
           addNode.children = [];
         }
       }
      const node = {data: gtMessage, leaf: true};
      nodeMap.set(gtMessage.idGtNetMessage, node);
      addNode.children.push(node);
    })
  }

}
