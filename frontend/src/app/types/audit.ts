/**
 * Словари журнала аудита — зеркало `AuditAction` и `AuditEntity` в `common` (P3-2). Новое значение там —
 * сюда и в подписи словаря `auditLogs`; незнакомый код экран показывает как есть.
 */
export const AUDIT_ACTIONS = [
  'CREATE', 'READ', 'UPDATE', 'DELETE', 'LIST', 'BLOCK', 'UNBLOCK', 'LOGIN', 'LOGOUT', 'LOCKOUT',
  'RATE_LIMIT', 'TOKEN_REUSE', 'PASSWORD_CHANGE', 'CAPTURE', 'REFUND', 'CANCEL', 'RESOLVE', 'STATUS_CHANGE',
  'START', 'STOP', 'EXPORT', 'VERIFY',
] as const;

export type AuditActionCode = (typeof AUDIT_ACTIONS)[number];

export const AUDIT_ENTITIES = [
  'COMPANY', 'TERMINAL', 'USER', 'AUTH', 'PAYMENT_LINK', 'TRANSACTION', 'PROVIDER_ORDER', 'AUDIT_LOG', 'SERVICE',
] as const;

export type AuditEntityCode = (typeof AUDIT_ENTITIES)[number];

/** Виды находок проверки цепочки журнала (Р-138) — зеркало `AuditIntegrityReport.Problem.kind`. */
export const INTEGRITY_PROBLEM_KINDS = [
  'RECORD_CHANGED', 'RECORD_DELETED', 'LINKS_MISSING', 'TIME_CHANGED', 'HEAD_MISMATCH', 'RECORDS_OUTSIDE_CHAIN',
] as const;

export type IntegrityProblemKind = (typeof INTEGRITY_PROBLEM_KINDS)[number];

/** `POST /api/v1/audit-logs/integrity-checks` (Р-138). */
export interface AuditIntegrityReport {
  intact: boolean;
  checkedRecords: number;
  headSeq: number;
  chainStartedAt: string | null;
  notCovered: number;
  unsealedRecords: number;
  problems: { kind: string; seq: number | null; auditId: string | null; detail: string }[];
  problemsTruncated: boolean;
  verifiedAt: string;
}
