import { useEffect, useState } from 'react';
import axios from 'axios';
import { apiClient } from '../api/client';
import type { CompanyDto } from '../types/dto';

export interface CompanyNameState {
  /** `null` — не загружено или не удалось; выдуманного названия вместо него нет (Р-48). */
  name: string | null;
  /** `null` — ошибки нет; пустая строка — отказ без текста от бэкенда. */
  error: string | null;
}

/**
 * Название своей компании — одиночный `GET /companies/{id}` по `companyId` из токена: список
 * `GET /companies` ролям компании отвечает 403 и пишет отказ в журнал. `enabled: false` — не спрашивать.
 */
export function useCompanyName(companyId: string | null | undefined, enabled = true): CompanyNameState {
  const [state, setState] = useState<CompanyNameState>({ name: null, error: null });

  useEffect(() => {
    if (!companyId || !enabled) {
      setState({ name: null, error: null });
      return;
    }
    const controller = new AbortController();
    apiClient
      .get<CompanyDto>(`/api/v1/companies/${encodeURIComponent(companyId)}`, { signal: controller.signal })
      .then(res => setState({ name: res.data?.name ?? '', error: null }))
      .catch((err: any) => {
        if (axios.isCancel(err)) return;
        setState({ name: null, error: err.response?.data?.message || '' });
      });
    return () => controller.abort();
  }, [companyId, enabled]);

  return state;
}
