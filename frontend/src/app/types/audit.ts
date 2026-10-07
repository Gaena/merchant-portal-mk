/**
 * Словари журнала аудита — зеркало `AuditAction` и `AuditEntity` в `common` (P3-2). Новое значение там —
 * сюда и в подписи словаря `auditLogs`; незнакомый код экран показывает как есть.
 */
export const AUDIT_ACTIONS = [
  'CREATE', 'READ', 'UPDATE', 'DELETE', 'LIST', 'BLOCK', 'UNBLOCK', 'LOGIN', 'LOGOUT', 'LOCKOUT',
  'RATE_LIMIT', 'TOKEN_REUSE', 'PASSWORD_CHANGE', 'CAPTURE', 'REFUND', 'CANCEL', 'RESOLVE', 'STATUS_CHANGE',
] as const;

export type AuditActionCode = (typeof AUDIT_ACTIONS)[number];

export const AUDIT_ENTITIES = [
  'COMPANY', 'TERMINAL', 'USER', 'AUTH', 'PAYMENT_LINK', 'TRANSACTION', 'PROVIDER_ORDER', 'AUDIT_LOG',
] as const;

export type AuditEntityCode = (typeof AUDIT_ENTITIES)[number];
