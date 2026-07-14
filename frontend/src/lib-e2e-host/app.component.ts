import {Component} from '@angular/core';
import {RouterLink, RouterOutlet} from '@angular/router';
import {DialogService, DynamicDialogModule} from 'primeng/dynamicdialog';
import {MailSendDynamicComponent, MailSendParam} from '../app/lib/dynamicdialog/component/mail.send.dynamic.component';
import {ActivePanelService} from '../app/lib/mainmenubar/service/active.panel.service';

/** Minimal consumer application proving that the frontend library can run without Grafioschtrader modules. */
@Component({
  selector: 'lib-e2e-root',
  standalone: true,
  imports: [DynamicDialogModule, RouterLink, RouterOutlet],
  template: `
    <nav class="lib-e2e-nav">
      <strong>Grafiosch library</strong>
      <a routerLink="/mail/mailsendrecv">Mail send recv</a>
      <a routerLink="/mail/mailsettingforward">Mail setting forward</a>
      <button type="button" class="btn btn-sm btn-primary" (click)="sendToUser()">Send to user</button>
    </nav>
    <main class="lib-e2e-content"><router-outlet></router-outlet></main>
  `,
})
export class LibE2EAppComponent {
  constructor(private dialogService: DialogService, activePanelService: ActivePanelService) {
    // The reusable panels publish menu state even though this deliberately small host has no top menubar.
    activePanelService.topMenuItems = [{items: []}, {items: []}];
  }

  sendToUser(): void {
    this.dialogService.open(MailSendDynamicComponent, {
      data: {mailSendParam: new MailSendParam(-1)},
      header: 'Send to user',
      width: '50vw',
    });
  }
}
