import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';

@Component({
  template: `
      <div style="font-size:16px;" [innerHTML]="message">
      </div>
  `
})
export class MailMessageComponent implements OnInit {
  message: string;

  constructor(private activatedRoute: ActivatedRoute) {
  }

  ngOnInit(): void {
    this.activatedRoute.paramMap.subscribe(paramMap => {
      const inMessage: string = paramMap.get('message');
      this.message = inMessage.replace(/(?:\r\n|\r|\n)/g, '<br/>');
    });
  }
}
