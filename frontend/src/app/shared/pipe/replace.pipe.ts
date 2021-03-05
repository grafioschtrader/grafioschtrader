import {Injectable, Pipe, PipeTransform} from '@angular/core';

@Pipe({
  name: 'replace'
})
@Injectable()
export class ReplacePipe implements PipeTransform {
  constructor() {
  }

  transform(item: any, replace, replacement): any {
    if (item == null) {
      return '';
    }
    item = item.replace(replace, replacement);
    return item;
  }
}
