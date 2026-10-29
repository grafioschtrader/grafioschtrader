import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'replace'
})
export class ReplacePipe implements PipeTransform {
  constructor() {}

  transform(item: any, replace: string | RegExp, replacement: string): any {
    if (item == null) {
      return '';
    }
    item = item.replaceAll(replace, replacement);
    return item;
  }
}
