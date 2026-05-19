import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export function passwordsMatch(a: string, b: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const av = group.get(a)?.value;
    const bv = group.get(b)?.value;
    return av && bv && av !== bv ? { mismatch: true } : null;
  };
}