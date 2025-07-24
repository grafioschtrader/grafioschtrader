import {Pipe, PipeTransform} from '@angular/core';

@Pipe({
    name: 'filterOut',
    standalone: false
})
export class FilterOutPipe implements PipeTransform {
  constructor() {
  }

  transform(inputStr: string, filterOutValue: string): any {
    return inputStr === filterOutValue ? null : inputStr;
  }
}
