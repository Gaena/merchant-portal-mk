/**
 * Зеркало `PasswordConstraintValidator` (common): не короче 12 символов, строчная и заглавная латинские
 * буквы, цифра и спецсимвол из того же набора. Только чтобы сказать об ошибке до запроса — решает сервер.
 */
const SPECIAL = /[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/;

export const meetsPasswordPolicy = (password: string): boolean =>
  password.length >= 12
  && /[a-z]/.test(password)
  && /[A-Z]/.test(password)
  && /[0-9]/.test(password)
  && SPECIAL.test(password);
