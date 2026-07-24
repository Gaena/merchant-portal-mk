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
  CircularProgress
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

import type { UserDto, CompanyDto } from '../types/dto';

export const UsersPage: React.FC = () => {
  const { user: currentUser } = useAuth();
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
            User Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage team members, roles, and platform permissions across your organization
          </Typography>
        </Box>
        <Stack direction="row" spacing={2}>
          <Button variant="outlined" startIcon={<RefreshIcon />} onClick={fetchUsers}>
            Refresh
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setUserDialogOpen(true)}>
            Add User
          </Button>
        </Stack>
      </Box>

      {/* Filters Bar */}
      <Paper elevation={0} sx={{ p: 2, mb: 3, border: '1px solid', borderColor: 'divider', display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          placeholder="Search by username, name, or company..."
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
          label="Role"
          value={roleFilter}
          onChange={e => setRoleFilter(e.target.value)}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="all">All Roles</MenuItem>
          <MenuItem value="COMPANY_HEAD">Company Head</MenuItem>
          <MenuItem value="COMPANY_MANAGER">Company Manager</MenuItem>
          <MenuItem value="COMPANY_EMPLOYEE">Company Employee</MenuItem>
          <MenuItem value="AUDITOR">Auditor</MenuItem>
          <MenuItem value="SYSTEM_ADMIN">System Admin</MenuItem>
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
                <TableCell sx={{ fontWeight: 700 }}>Username / Email</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Full Name</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Role</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Company ID</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="center">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredUsers.map((u: any) => (
                <TableRow key={u.id} hover>
                  <TableCell sx={{ fontWeight: 600 }}>{u.username}</TableCell>
                  <TableCell>{u.fullName || '—'}</TableCell>
                  <TableCell>
                    <Chip
                      label={u.role || 'COMPANY_HEAD'}
                      size="small"
                      color={u.role === 'SYSTEM_ADMIN' ? 'primary' : 'default'}
                    />
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                    {u.companyId || '—'}
                  </TableCell>
                  <TableCell>
                    <Chip label={u.status || 'ACTIVE'} size="small" color="success" variant="outlined" />
                  </TableCell>
                  <TableCell align="center">
                    <IconButton color="error" size="small" onClick={() => handleDeleteUser(u.id)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
              {filteredUsers.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 6 }}>
                    <Typography color="text.secondary">No users found.</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        )}
      </TableContainer>

      {/* Create User Dialog */}
      <Dialog open={userDialogOpen} onClose={() => setUserDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>Add New User</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {userError && <Alert severity="error">{userError}</Alert>}
            <TextField
              label="Username (Email)"
              type="email"
              value={userForm.username}
              onChange={e => setUserForm(f => ({ ...f, username: e.target.value }))}
              placeholder="user@company.az"
              fullWidth
              required
            />
            <TextField
              label="Password"
              type="password"
              value={userForm.password}
              onChange={e => setUserForm(f => ({ ...f, password: e.target.value }))}
              placeholder="••••••••"
              fullWidth
              required
            />
            <TextField
              label="Full Name"
              value={userForm.fullName}
              onChange={e => setUserForm(f => ({ ...f, fullName: e.target.value }))}
              placeholder="John Doe"
              fullWidth
              required
            />
            <TextField
              select
              label="Role"
              value={userForm.role}
              onChange={e => setUserForm(f => ({ ...f, role: e.target.value }))}
              fullWidth
            >
              <MenuItem value="COMPANY_HEAD">Company Head (Руководитель компании)</MenuItem>
              <MenuItem value="COMPANY_MANAGER">Company Manager (Менеджер компании)</MenuItem>
              <MenuItem value="COMPANY_EMPLOYEE">Company Employee (Сотрудник компании)</MenuItem>
              <MenuItem value="AUDITOR">Auditor (Аудитор)</MenuItem>
              <MenuItem value="SYSTEM_ADMIN">System Admin (Системный администратор)</MenuItem>
            </TextField>
            {companiesList.length > 0 && (
              <TextField
                select
                label="Company"
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
          <Button onClick={() => setUserDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleCreateUser}>Create User</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
