import React, { useState, useEffect, useMemo } from 'react';
import {
  Box,
  Typography,
  Paper,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Stack,
  Alert,
  InputAdornment,
  CircularProgress,
  Tooltip
} from '@mui/material';
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Search as SearchIcon,
  Refresh as RefreshIcon,
  Group as GroupIcon
} from '@mui/icons-material';
import { apiClient } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';

import type { UserDto, CompanyDto } from '../types/dto';

export const UsersPage: React.FC = () => {
  const { user: currentUser } = useAuth();
  const { tObj } = useLanguage();
  const [usersList, setUsersList] = useState<UserDto[]>([]);
  const [companiesList, setCompaniesList] = useState<CompanyDto[]>([]);
  const [loading, setLoading] = useState(true);

  // Dialog & Form
  const [userDialogOpen, setUserDialogOpen] = useState(false);
  const [userForm, setUserForm] = useState({
    username: '',
    password: '',
    fullName: '',
    role: 'COMPANY_HEAD',
    companyId: ''
  });
  const [userError, setUserError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('all');

  const fetchUsers = () => {
    setLoading(true);
    apiClient.get('/api/v1/users')
      .then(res => {
        setUsersList(Array.isArray(res.data) ? res.data : []);
      })
      .catch(() => {
        setUsersList([]);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchUsers();
    apiClient.get('/api/v1/companies')
      .then(res => {
        if (Array.isArray(res.data)) {
          setCompaniesList(res.data);
          if (res.data.length > 0) {
            setUserForm(f => ({ ...f, companyId: res.data[0].id }));
          }
        }
      })
      .catch(() => {});
  }, []);

  const handleCreateUser = async () => {
    setUserError('');
    if (!userForm.username || !userForm.password || !userForm.fullName) {
      setUserError('Please fill in all required fields');
      return;
    }
    try {
      const payload = {
        username: userForm.username,
        password: userForm.password,
        fullName: userForm.fullName,
        role: userForm.role,
        companyId: userForm.companyId || undefined,
      };
      const res = await apiClient.post('/api/v1/users', payload);
      setUsersList(prev => [...prev, res.data]);
      setUserDialogOpen(false);
      setUserForm({
        username: '',
        password: '',
        fullName: '',
        role: 'COMPANY_HEAD',
        companyId: companiesList[0]?.id || ''
      });
    } catch (err: any) {
      setUserError(err.response?.data?.message || 'Failed to create user');
    }
  };

  const handleDeleteUser = async (id: string) => {
    try {
      await apiClient.delete(`/api/v1/users/${id}`);
      setUsersList(prev => prev.filter(u => u.id !== id));
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to delete user');
    }
  };

  const getCompanyName = (companyId?: string) => {
    if (!companyId) return '';
    const comp = companiesList.find(c => String(c.id) === String(companyId));
    return comp ? comp.name : companyId;
  };

  const filteredUsers = useMemo(() => {
    return usersList.filter(u => {
      if (roleFilter !== 'all' && u.role !== roleFilter) return false;
      if (searchQuery) {
        const q = searchQuery.toLowerCase();
        const matchUsername = (u.username || '').toLowerCase().includes(q);
        const matchName = (u.fullName || '').toLowerCase().includes(q);
        const matchCompanyId = (u.companyId || '').toLowerCase().includes(q);
        const compName = getCompanyName(u.companyId).toLowerCase();
        const matchCompanyName = compName.includes(q);
        if (!matchUsername && !matchName && !matchCompanyId && !matchCompanyName) return false;
      }
      return true;
    });
  }, [usersList, roleFilter, searchQuery, companiesList]);

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 600, mb: 0.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <GroupIcon color="primary" fontSize="large" />
            {tObj.users.title}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {tObj.users.subtitle}
          </Typography>
        </Box>
        <Stack direction="row" spacing={2}>
          <Button variant="outlined" startIcon={<RefreshIcon />} onClick={fetchUsers}>
            {tObj.common.refresh}
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setUserDialogOpen(true)}>
            {tObj.users.addUser}
          </Button>
        </Stack>
      </Box>

      {/* Filters Bar */}
      <Paper elevation={0} sx={{ p: 2, mb: 3, border: '1px solid', borderColor: 'divider', display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          placeholder={tObj.users.searchPlaceholder}
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
          sx={{ minWidth: 320 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
        <TextField
          select
          size="small"
          label={tObj.users.role}
          value={roleFilter}
          onChange={e => setRoleFilter(e.target.value)}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="all">{tObj.common.all}</MenuItem>
          <MenuItem value="COMPANY_HEAD">{tObj.users.roles.companyHead}</MenuItem>
          <MenuItem value="COMPANY_MANAGER">{tObj.users.roles.companyManager}</MenuItem>
          <MenuItem value="COMPANY_EMPLOYEE">{tObj.users.roles.companyEmployee}</MenuItem>
          <MenuItem value="AUDITOR">{tObj.users.roles.auditor}</MenuItem>
          <MenuItem value="SYSTEM_ADMIN">{tObj.users.roles.systemAdmin}</MenuItem>
        </TextField>
      </Paper>

      {/* Users Table */}
      <TableContainer component={Paper} variant="outlined">
        {loading ? (
          <Box sx={{ p: 6, textAlign: 'center' }}>
            <CircularProgress />
          </Box>
        ) : (
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: 'action.hover' }}>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.users.username}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.users.name}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.users.role}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.users.company}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.users.status}</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="center">{tObj.common.actions}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredUsers.map((u: any) => (
                <TableRow key={u.id} hover>
                  <TableCell sx={{ fontWeight: 600 }}>{u.username}</TableCell>
                  <TableCell>{u.fullName || '—'}</TableCell>
                  <TableCell>
                    <Chip label={u.role || 'USER'} color="primary" size="small" variant="outlined" />
                  </TableCell>
                  <TableCell>{getCompanyName(u.companyId) || '—'}</TableCell>
                  <TableCell>
                    <Chip label={tObj.common.active} color="success" size="small" />
                  </TableCell>
                  <TableCell align="center">
                    <Tooltip title={tObj.common.delete}>
                      <IconButton color="error" size="small" onClick={() => handleDeleteUser(u.id)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </TableContainer>

      {/* Create User Dialog */}
      <Dialog open={userDialogOpen} onClose={() => setUserDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{tObj.users.createDialogTitle}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {userError && <Alert severity="error">{userError}</Alert>}
            <TextField
              label={tObj.users.username}
              type="email"
              value={userForm.username}
              onChange={e => setUserForm(f => ({ ...f, username: e.target.value }))}
              placeholder="user@company.az"
              fullWidth
              required
            />
            <TextField
              label={tObj.users.password}
              type="password"
              value={userForm.password}
              onChange={e => setUserForm(f => ({ ...f, password: e.target.value }))}
              placeholder="••••••••"
              fullWidth
              required
            />
            <TextField
              label={tObj.users.name}
              value={userForm.fullName}
              onChange={e => setUserForm(f => ({ ...f, fullName: e.target.value }))}
              placeholder="John Doe"
              fullWidth
              required
            />
            <TextField
              select
              label={tObj.users.role}
              value={userForm.role}
              onChange={e => setUserForm(f => ({ ...f, role: e.target.value }))}
              fullWidth
            >
              <MenuItem value="COMPANY_HEAD">{tObj.users.roles.companyHead}</MenuItem>
              <MenuItem value="COMPANY_MANAGER">{tObj.users.roles.companyManager}</MenuItem>
              <MenuItem value="COMPANY_EMPLOYEE">{tObj.users.roles.companyEmployee}</MenuItem>
              <MenuItem value="AUDITOR">{tObj.users.roles.auditor}</MenuItem>
              <MenuItem value="SYSTEM_ADMIN">{tObj.users.roles.systemAdmin}</MenuItem>
            </TextField>
            {companiesList.length > 0 && (
              <TextField
                select
                label={tObj.users.company}
                value={userForm.companyId}
                onChange={e => setUserForm(f => ({ ...f, companyId: e.target.value }))}
                fullWidth
              >
                {companiesList.map((c: any) => (
                  <MenuItem key={c.id} value={c.id}>
                    {c.name} ({c.id})
                  </MenuItem>
                ))}
              </TextField>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setUserDialogOpen(false)}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={handleCreateUser}>{tObj.common.create}</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
