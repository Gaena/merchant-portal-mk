import React, { useState, useEffect, useMemo } from 'react';
import { apiClient } from '../api/client';
import { useAuth } from '../context/AuthContext';
import {
  Box,
  Paper,
  Typography,
  Button,
  TextField,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Switch,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Stack,
  Tooltip,
  InputAdornment,
} from '@mui/material';
import {
  Business as BusinessIcon,
  Add as AddIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
  CheckCircle as CheckCircleIcon,
  Block as BlockIcon,
  Search as SearchIcon,
} from '@mui/icons-material';

import { useLanguage } from '../context/LanguageContext';
import type { CompanyDto } from '../types/dto';

export const CompaniesPage: React.FC = () => {
  const { user } = useAuth();
  const { tObj } = useLanguage();
  const isAdmin = user?.role === 'SYSTEM_ADMIN' || user?.role === 'ADMIN';

  const [companies, setCompanies] = useState<CompanyDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState({ id: '', name: '' });
  const [error, setError] = useState('');
  const [snackbar, setSnackbar] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  const fetchCompanies = async () => {
    setLoading(true);
    try {
      const res = await apiClient.get('/api/v1/companies');
      const list = Array.isArray(res.data) ? res.data : (res.data?.content || []);
      setCompanies(list);
    } catch (err) {
      setCompanies([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCompanies();
  }, []);

  const handleToggleStatus = async (company: any) => {
    const newStatus = (company.status === 'INACTIVE' || company.status === 'DISABLED') ? 'ACTIVE' : 'INACTIVE';
    try {
      await apiClient.patch(`/api/v1/companies/${company.id}`, { name: company.name, status: newStatus });
      setCompanies(prev => prev.map(c => c.id === company.id ? { ...c, status: newStatus } : c));
      setSnackbar(`Company status updated to ${newStatus}`);
    } catch (err: any) {
      setSnackbar(err.response?.data?.message || 'Failed to update company status');
    }
  };

  const handleCreate = async () => {
    if (!form.id.trim() || !form.name.trim()) {
      setError('Company ID and Name are required');
      return;
    }
    setError('');
    try {
      const res = await apiClient.post('/api/v1/companies', {
        id: form.id.trim(),
        name: form.name.trim(),
      });
      setCompanies(prev => [...prev, res.data]);
      setCreateOpen(false);
      setForm({ id: '', name: '' });
      setSnackbar('Company created successfully');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to create company');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await apiClient.delete(`/api/v1/companies/${id}`);
      setCompanies(prev => prev.filter(c => c.id !== id));
      setSnackbar('Company deleted successfully');
    } catch (err: any) {
      setSnackbar(err.response?.data?.message || 'Failed to delete company');
    }
  };

  const filteredCompanies = useMemo(() => {
    return companies.filter(comp => {
      if (!searchQuery.trim()) return true;
      const q = searchQuery.toLowerCase();
      const matchName = (comp.name || '').toLowerCase().includes(q);
      const matchId = (comp.id || '').toLowerCase().includes(q);
      return matchName || matchId;
    });
  }, [companies, searchQuery]);

  if (!isAdmin) {
    return (
      <Box sx={{ p: 4 }}>
        <Alert severity="error">
          Access Denied: You do not have administrator permissions to view this page.
        </Alert>
      </Box>
    );
  }

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <BusinessIcon color="primary" fontSize="large" /> {tObj.companies.title}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {tObj.companies.subtitle}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5}>
          <Button variant="outlined" startIcon={<RefreshIcon />} onClick={fetchCompanies}>
            {tObj.common.refresh}
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
            {tObj.companies.addCompany}
          </Button>
        </Stack>
      </Box>

      {snackbar && (
        <Alert severity="info" sx={{ mb: 3 }} onClose={() => setSnackbar('')}>
          {snackbar}
        </Alert>
      )}

      {/* Search Bar */}
      <Paper elevation={0} sx={{ p: 2, mb: 3, border: '1px solid', borderColor: 'divider' }}>
        <TextField
          size="small"
          placeholder={tObj.companies.searchPlaceholder}
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
          sx={{ minWidth: 320, width: { xs: '100%', sm: 400 } }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
      </Paper>

      {/* Table */}
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.companies.companyId}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.companies.name}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.companies.status}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.common.actions}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.common.date}</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="center">{tObj.common.actions}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredCompanies.map((comp) => {
                const isActive = comp.status === 'ACTIVE' || !comp.status;
                return (
                  <TableRow key={comp.id} hover>
                    <TableCell sx={{ fontFamily: 'monospace', fontWeight: 700, color: 'primary.main' }}>
                      {comp.id}
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{comp.name}</TableCell>
                    <TableCell>
                      <Chip
                        icon={isActive ? <CheckCircleIcon fontSize="small" /> : <BlockIcon fontSize="small" />}
                        label={isActive ? tObj.common.active : tObj.common.inactive}
                        color={isActive ? 'success' : 'default'}
                        size="small"
                        sx={{ fontWeight: 600 }}
                      />
                    </TableCell>
                    <TableCell>
                      <Tooltip title={isActive ? tObj.common.inactive : tObj.common.active}>
                        <Switch
                          checked={isActive}
                          onChange={() => handleToggleStatus(comp)}
                          color="success"
                        />
                      </Tooltip>
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.85rem', color: 'text.secondary' }}>
                      {comp.createdAt ? new Date(comp.createdAt).toLocaleString() : 'N/A'}
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title={tObj.common.delete}>
                        <IconButton color="error" size="small" onClick={() => handleDelete(comp.id)}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                );
              })}
              {companies.length === 0 && !loading && (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 6 }}>
                    <BusinessIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
                    <Typography color="text.secondary">No companies found in directory.</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {/* Create Dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{tObj.companies.createDialogTitle}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{error}</Alert>}
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            <TextField
              label={tObj.companies.companyId}
              value={form.id}
              onChange={e => setForm(f => ({ ...f, id: e.target.value }))}
              placeholder="e.g. comp_001"
              fullWidth
            />
            <TextField
              label={tObj.companies.name}
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              placeholder="e.g. Acme Supermarket LLC"
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          <Button onClick={() => setCreateOpen(false)}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={handleCreate}>{tObj.common.create}</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
