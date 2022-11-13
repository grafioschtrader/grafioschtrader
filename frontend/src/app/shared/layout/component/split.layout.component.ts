import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {ChangedViewSizeType, ViewSizeChangedService} from '../service/view.size.changed.service';


declare function Split(ids, options);

@Component({
  selector: 'split-layout',
  template: `
    <menubar></menubar>
    <div class="row fullheight" #split (window:resize)="onResize($event)">
      <div id="tree" class="split split-horizontal">
        <main-tree></main-tree>
      </div>
      <div id="mainpanel" class="split split-horizontal">
        <div id="maintop" class="split split-vetrical">
          <router-outlet></router-outlet>
        </div>
        <div id="mainbottom" class="split split-vetrical">
          <router-outlet name="mainbottom"></router-outlet>
        </div>
      </div>
    </div>

    <main-dialog></main-dialog>
  `
})

export class SplitLayoutComponent implements OnInit {
  @ViewChild('split', {static: true}) splitElementRef: ElementRef;

  constructor(private viewSizeChangedService: ViewSizeChangedService) {
  }

  ngOnInit() {
    const splitTreeMain = Split(['#tree', '#mainpanel'], {
      sizes: [20, 80],
      minSize: 0,
      gutterSize: 8,
      direction: 'horizontal',
      onDragEnd: () => this.viewSizeChangedService.viewPanelChanged(ChangedViewSizeType.ALL_VIEWS)

    });

    const splitTopBottom = Split(['#maintop', '#mainbottom'], {
      sizes: [80, 20],
      minSize: 100,
      gutterSize: 8,
      direction: 'vertical',
      onDragEnd: () => this.viewSizeChangedService.viewPanelChanged(ChangedViewSizeType.DATA_VIEWS)
    });
    this.viewSizeChangedService.setMainTreeSplit(splitTreeMain);
  }

  onResize(event): void {
    this.viewSizeChangedService.viewPanelChanged(ChangedViewSizeType.WINDOW);
  }

}
