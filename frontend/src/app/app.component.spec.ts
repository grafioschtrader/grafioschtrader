/* tslint:disable:no-unused-variable */

import { TestBed, waitForAsync } from '@angular/core/testing';
import {HistoryquoteTableComponent} from './historyquote/component/historyquote-table.component';

describe('App: PrimengQuickstartCli', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [
        HistoryquoteTableComponent
      ],
    });
  });

  it('should create the app', waitForAsync(() => {
    let fixture = TestBed.createComponent(HistoryquoteTableComponent);
    let app = fixture.debugElement.componentInstance;
    expect(app).toBeTruthy();
  }));

  it(`should have as title 'app works!'`, waitForAsync(() => {
    let fixture = TestBed.createComponent(HistoryquoteTableComponent);
    let app = fixture.debugElement.componentInstance;
    expect(app.title).toEqual('app works!');
  }));

  it('should render title in a h1 tag', waitForAsync(() => {
    let fixture = TestBed.createComponent(HistoryquoteTableComponent);
    fixture.detectChanges();
    let compiled = fixture.debugElement.nativeElement;
    expect(compiled.querySelector('h1').textContent).toContain('app works!');
  }));
});
