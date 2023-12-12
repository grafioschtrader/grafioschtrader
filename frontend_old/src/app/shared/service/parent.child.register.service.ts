import {Injectable} from '@angular/core';

@Injectable()
export class ParentChildRegisterService {

  childPreservePages: ChildPreservePage[];
  pageServerMap: Map<number, PageFirstRowSelectedRow>;

  public initRegistry() {
    this.childPreservePages = [];
    this.pageServerMap = new Map();
  }

  public registerChildComponent(childPreservePage: ChildPreservePage): void {
    this.childPreservePages.push(childPreservePage);
  }

  public unregisterChildComponent(childPreservePage: ChildPreservePage): void {
    const index = this.childPreservePages.indexOf(childPreservePage);
    if (index > -1) {
      this.childPreservePages.splice(index, 1);
    }
  }

  public callChildrenToPreserve(data: any) {
    this.childPreservePages.forEach(childPreservePage => childPreservePage.preservePage(data));
  }

  /**
   *
   * @param id ID of the parent
   * @param pageFirstRowSelectedRow Position of the child record
   */
  public saveRowPosition(id: number, pageFirstRowSelectedRow: PageFirstRowSelectedRow) {
    this.pageServerMap.set(id, pageFirstRowSelectedRow);
  }

  public getRowPostion(id: number): PageFirstRowSelectedRow {
    const pageFirstRowSelectedRow = (id) ? this.pageServerMap.get(id) : null;
    return pageFirstRowSelectedRow ? pageFirstRowSelectedRow : new PageFirstRowSelectedRow(0, null);
  }
}

export class PageFirstRowSelectedRow {
  constructor(public topPageRow: number, public selectedRow: any) {

  }
}

export interface ChildPreservePage {
  preservePage(data: any);
}
