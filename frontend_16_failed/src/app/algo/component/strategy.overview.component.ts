import {Component} from '@angular/core';
import {CurrencyMaskInputMode} from 'ngx-currency';

@Component({
  template: `
    <h4>Strategy Overview</h4>
    <input currencyMask [options]="optionsInput01"/>
    <input currencyMask [options]="optionsInput02"/>
    <input type="number" id="tentacles21" name="tentacles" >
    <input type="number" id="tentacles32" name="tentacles" >
    <p-inputNumber [(ngModel)]="price1" mode="currency" [currency]="curreny1" locale="de-CH" onfocus="this.select()"
                   currencyDisplay="code" inputStyleClass="text-right"></p-inputNumber>

    <p-inputNumber [(ngModel)]="price2" mode="currency" [currency]="curreny2" locale="de-CH" onfocus="this.select()"
                   currencyDisplay="code"  inputStyleClass="text-right"></p-inputNumber>



    <p-button (click)="showBasicDialog()" icon="pi pi-external-link" label="Show"></p-button>
    <p-button (click)="changeCurrency()" icon="pi pi-external-link" label="Change currency"></p-button>
    <p-dialog header="Header" [(visible)]="displayBasic" [style]="{width: '50vw'}" [baseZIndex]="10000" focusTrap="false">

      <p-inputNumber [(ngModel)]="price1" mode="currency" [currency]="curreny1" locale="de-CH" onfocus="this.select()"
                     currencyDisplay="code" inputStyleClass="text-right"></p-inputNumber>

      <p-inputNumber [(ngModel)]="price2" mode="currency" [currency]="curreny2" locale="en-US" onfocus="this.select()"
                     currencyDisplay="code"  inputStyleClass="text-right"></p-inputNumber>

      <input currencyMask [options]="optionsInput01" onfocus="this.select()"/>
      <input currencyMask [options]="optionsInput02" onfocus="this.select()"/>
      <input type="number" id="tentacles1" name="tentacles">
      <input type="number" id="tentacles2" name="tentacles">

     <ng-template pTemplate="footer">
        <p-button icon="pi pi-check" (click)="displayBasic=false" label="Ok" styleClass="p-button-text"></p-button>
      </ng-template>
    </p-dialog>
  `

})
export class StrategyOverviewComponent {
  displayBasic: boolean;
  optionsInput01 = {
    prefix: 'CHF ',
    thousands: '\'',
    decimal: '.',
    inputMode: CurrencyMaskInputMode.NATURAL,
    precision: 2
  };
  optionsInput02 = {
    prefix: 'USD ',
    thousands: '\'',
    decimal: '.',
    inputMode: CurrencyMaskInputMode.NATURAL,
    precision: 2
  };
  price1 = 100.20;
  price2 = 43.10;
  curreny1 = 'CHF';
  curreny2 = 'EUR';

  showBasicDialog() {
    this.displayBasic = true;
  }

  changeCurrency() {
    this.optionsInput01.prefix = 'EUR ';
    this.optionsInput02.prefix = 'EUR ';
  }
}
