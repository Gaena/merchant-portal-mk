import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Divider,
  MenuItem,
  Paper,
  TextField,
  Typography,
} from '@mui/material';
import { CheckCircle as CheckCircleIcon } from '@mui/icons-material';

import { apiClient } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';
import type { Language } from '../i18n/translations';
import type { CompanyDto } from '../types/dto';
import { useCompanyName } from '../hooks/useCompanyName';

// Живы только название компании и язык; выдуманные вкладки и настройки не возвращать (P3-6).
export const SettingsPage: React.FC = () => {
  const { user } = useAuth();
  const { language, setLanguage, tObj } = useLanguage();
  const companyId = user?.companyId;
  // Название меняет только SYSTEM_ADMIN (CompanyService.updateCompany), компания — из claim токена.
  const canEditCompany = user?.role === 'SYSTEM_ADMIN' && Boolean(companyId);

  const [companyName, setCompanyName] = useState('');
  const [loadedName, setLoadedName] = useState('');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  // null — ошибки нет; пустая строка — отказ без текста от бэкенда, показываем свой.
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  // Одна компания по id из токена: список GET /companies остальным ролям — 403 и отказ в журнале.
  const company = useCompanyName(companyId);
  useEffect(() => {
    if (company.name !== null) {
      setCompanyName(company.name);
      setLoadedName(company.name);
    }
    setLoadError(company.error);
  }, [company.name, company.error]);

  const trimmedName = companyName.trim();
  // Пустое имя бэкенд молча пропускает и отвечает 200 — «сохранено» было бы неправдой.
  const canSave = canEditCompany && !saving && trimmedName.length > 0 && trimmedName !== loadedName;

  const handleSaveCompany = async () => {
    if (!companyId || !canSave) return;
    setSaving(true);
    setSaveError(null);
    setSaved(false);
    try {
      const res = await apiClient.patch<CompanyDto>(
        `/api/v1/companies/${encodeURIComponent(companyId)}`,
        { name: trimmedName },
      );
      const name = res.data?.name ?? trimmedName;
      setCompanyName(name);
      setLoadedName(name);
      setSaved(true);
    } catch (err: any) {
      setSaveError(err.response?.data?.message || '');
    } finally {
      setSaving(false);
    }
  };

  const handleNameChange = (value: string) => {
    setCompanyName(value);
    setSaved(false);
    setSaveError(null);
  };

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" sx={{ fontWeight: 600, mb: 1 }}>
          {tObj.settings.title}
        </Typography>
        <Typography variant="body1" color="text.secondary">
          {tObj.settings.subtitle}
        </Typography>
      </Box>

      {saved && (
        <Alert severity="success" sx={{ mb: 3 }} icon={<CheckCircleIcon />} onClose={() => setSaved(false)}>
          {tObj.settings.saveSuccess}
        </Alert>
      )}
      {saveError !== null && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setSaveError(null)}>
          {saveError || tObj.settings.account.saveFailed}
        </Alert>
      )}

      <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider' }}>
        <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
          {tObj.settings.account.title}
        </Typography>

        {loadError !== null && (
          <Alert severity="warning" sx={{ mb: 3 }}>
            {loadError || tObj.settings.account.loadFailed}
          </Alert>
        )}
        {!companyId && (
          <Alert severity="info" sx={{ mb: 3 }}>
            {tObj.settings.account.noCompany}
          </Alert>
        )}

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 3 }}>
          {companyId && (
            <TextField
              fullWidth
              label={tObj.settings.account.merchantName}
              value={companyName}
              onChange={e => handleNameChange(e.target.value)}
              disabled={!canEditCompany}
              helperText={canEditCompany ? undefined : tObj.settings.account.nameReadOnly}
            />
          )}
          <TextField
            fullWidth
            label={tObj.settings.account.merchantEmail}
            value={user?.email ?? ''}
            InputProps={{ readOnly: true }}
            helperText={tObj.settings.account.emailReadOnly}
          />
        </Box>

        {canEditCompany && (
          <Button variant="contained" size="large" sx={{ mt: 3 }} disabled={!canSave} onClick={handleSaveCompany}>
            {tObj.settings.saveChanges}
          </Button>
        )}

        <Divider sx={{ my: 4 }} />

        <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
          {tObj.settings.display.title}
        </Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 3 }}>
          <TextField
            select
            fullWidth
            label={tObj.settings.display.language}
            value={language}
            onChange={e => setLanguage(e.target.value as Language)}
          >
            <MenuItem value="en">English</MenuItem>
            <MenuItem value="az">Azərbaycan</MenuItem>
            <MenuItem value="ru">Русский</MenuItem>
          </TextField>
        </Box>
      </Paper>
    </Box>
  );
};
