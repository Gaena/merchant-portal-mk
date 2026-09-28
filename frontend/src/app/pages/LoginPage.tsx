import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { AuthError, clearIdleNotice, hasIdleNotice } from '../auth/session';
import { returnPathFrom } from '../auth/guards';
import { meetsPasswordPolicy } from '../utils/password';
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  IconButton,
  InputAdornment,
  Alert,
  Stack,
  CircularProgress
} from '@mui/material';
import {
  Visibility as VisibilityIcon,
  VisibilityOff as VisibilityOffIcon,
  Lock as LockIcon,
  Email as EmailIcon,
} from '@mui/icons-material';

import { useLanguage } from '../context/LanguageContext';

/**
 * Форма входа. Здесь нет ни «Forgot password», ни двухфакторной проверки: бэкенд ни того, ни
 * другого не умеет, а надпись «Secured with 2-Factor Authentication» над недостижимым OTP-окном
 * была утверждением о защите, которой нет (Р-48 — выдуманного на экранах не бывает).
 */
export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, changePassword } = useAuth();
  const { tObj } = useLanguage();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  // Пароль задал не владелец (PCI DSS 8.3.5, Р-100): после верного пароля — форма смены, сессия после неё.
  const [mode, setMode] = useState<'signIn' | 'changePassword'>('signIn');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  // Выход по простою (PCI DSS 8.2.8, Р-99) — сказать один раз, почему снова форма входа.
  const [idleNotice, setIdleNotice] = useState(hasIdleNotice);
  useEffect(() => {
    clearIdleNotice();
  }, []);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isLoading) return;
    setError('');

    if (!email.trim() || !password) {
      setError(tObj.auth.fillBoth);
      return;
    }
    if (!email.includes('@')) {
      setError(tObj.auth.invalidEmail);
      return;
    }

    setIsLoading(true);
    try {
      await login(email.trim(), password);
      // Туда, откуда увели на вход (ProtectedRoute кладёт адрес в state), иначе на главную.
      // replace: «назад» не должен возвращать на форму входа.
      navigate(returnPathFrom(location.state), { replace: true });
    } catch (err: unknown) {
      setIsLoading(false);
      if (err instanceof AuthError && err.code === 'PASSWORD_CHANGE_REQUIRED') {
        setMode('changePassword');
        return;
      }
      if (err instanceof AuthError) {
        // Сервер ответил 200, но сессию из ответа собрать нельзя — вход отклонён на клиенте
        // (fail-closed). Оба случая — свои тексты, а не отладочная строка из session.ts.
        setError(err.code === 'UNKNOWN_ROLE' ? tObj.auth.unknownRole : tObj.auth.malformedResponse);
        return;
      }
      if (axios.isAxiosError(err) && !err.response) {
        // Запрос до сервера не дошёл: предлагать «проверьте пароль» было бы неправдой.
        setError(tObj.auth.networkError);
        return;
      }
      setError(serverMessageOf(err) ?? tObj.auth.authFailed);
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isLoading) return;
    setError('');

    if (!meetsPasswordPolicy(newPassword)) {
      setError(tObj.auth.passwordRules);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError(tObj.auth.passwordsDoNotMatch);
      return;
    }
    if (newPassword === password) {
      setError(tObj.auth.samePassword);
      return;
    }

    setIsLoading(true);
    try {
      await changePassword(email.trim(), password, newPassword);
      navigate(returnPathFrom(location.state), { replace: true });
    } catch (err: unknown) {
      setIsLoading(false);
      if (axios.isAxiosError(err) && !err.response) {
        setError(tObj.auth.networkError);
        return;
      }
      setError(serverMessageOf(err) ?? tObj.auth.authFailed);
    }
  };

  const backToSignIn = () => {
    setMode('signIn');
    setError('');
    setPassword('');
    setNewPassword('');
    setConfirmPassword('');
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'grey.50',
        p: 3,
        backgroundImage: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      }}
    >
      <Paper
        elevation={8}
        sx={{
          width: '100%',
          maxWidth: 480,
          p: 5,
          position: 'relative',
          zIndex: 1,
          borderRadius: 3
        }}
      >
        {/* Header */}
        <Box sx={{ textAlign: 'center', mb: 4 }}>
          <Box
            sx={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 72,
              height: 72,
              borderRadius: '50%',
              bgcolor: 'primary.main',
              mb: 2,
              boxShadow: '0 8px 24px rgba(25, 118, 210, 0.3)'
            }}
          >
            <LockIcon sx={{ fontSize: 40, color: 'white' }} />
          </Box>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 1, color: 'text.primary' }}>
            {tObj.header.title}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {tObj.auth.subtitle}
          </Typography>
        </Box>

        {idleNotice && !error && (
          <Alert severity="info" sx={{ mb: 3 }} onClose={() => setIdleNotice(false)}>
            {tObj.auth.idleSignedOut}
          </Alert>
        )}

        {error && (
          <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError('')}>
            {error}
          </Alert>
        )}

        {mode === 'changePassword' && (
          <form onSubmit={handleChangePassword} noValidate>
            <Stack spacing={3}>
              <Alert severity="info">{tObj.auth.passwordChangeRequired}</Alert>
              <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>{email.trim()}</Typography>
              <TextField
                fullWidth
                label={tObj.auth.newPasswordLabel}
                name="new-password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                autoFocus
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                disabled={isLoading}
                helperText={tObj.auth.passwordRules}
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton onClick={() => setShowPassword(!showPassword)} edge="end" disabled={isLoading} tabIndex={-1}>
                        {showPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                      </IconButton>
                    </InputAdornment>
                  )
                }}
              />
              <TextField
                fullWidth
                label={tObj.auth.confirmPasswordLabel}
                name="confirm-password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                disabled={isLoading}
              />
              <Button
                type="submit"
                variant="contained"
                size="large"
                fullWidth
                disabled={isLoading}
                sx={{ py: 1.75, fontWeight: 600, fontSize: '1rem' }}
              >
                {isLoading ? <CircularProgress size={24} sx={{ color: 'white' }} /> : tObj.auth.changePasswordAndSignIn}
              </Button>
              <Button onClick={backToSignIn} disabled={isLoading}>{tObj.common.back}</Button>
            </Stack>
          </form>
        )}

        {/* noValidate: подсказки браузера для type="email" шли на языке браузера и раньше наших. */}
        {mode === 'signIn' && (
        <form onSubmit={handleLogin} noValidate>
          <Stack spacing={3}>
            <TextField
              fullWidth
              label={tObj.auth.emailLabel}
              name="username"
              type="email"
              autoComplete="username"
              autoFocus
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={isLoading}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <EmailIcon color="action" />
                  </InputAdornment>
                )
              }}
            />

            <TextField
              fullWidth
              label={tObj.auth.passwordLabel}
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={isLoading}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LockIcon color="action" />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      onClick={() => setShowPassword(!showPassword)}
                      edge="end"
                      disabled={isLoading}
                      tabIndex={-1}
                    >
                      {showPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                    </IconButton>
                  </InputAdornment>
                )
              }}
            />

            <Button
              type="submit"
              variant="contained"
              size="large"
              fullWidth
              disabled={isLoading}
              sx={{ py: 1.75, fontWeight: 600, fontSize: '1rem' }}
            >
              {isLoading ? <CircularProgress size={24} sx={{ color: 'white' }} /> : tObj.auth.signIn}
            </Button>
          </Stack>
        </form>
        )}
      </Paper>
    </Box>
  );
};

/** Текст отказа сервера, если он его прислал: `message` из `ErrorResponse`, иначе `error`. */
const serverMessageOf = (err: unknown): string | null => {
  const message = axios.isAxiosError(err) ? (err.response?.data?.message || err.response?.data?.error) : undefined;
  return typeof message === 'string' && message ? message : null;
};
