import React, { useState, useEffect, useMemo } from 'react';
import { apiClient } from '../api/client';
import {
  Box,
  Paper,
  Typography,
  Button,
  TextField,
  MenuItem,
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
  Alert,
  Stack,
  Tooltip,
  InputAdornment,
} from '@mui/material';
import {
  PointOfSale as POSIcon,
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
  Business as BusinessIcon,
  Search as SearchIcon,
} from '@mui/icons-material';
import type { TerminalDto, CompanyDto } from '../types/dto';

export const TerminalsPage: React.FC = () => {
  const [terminals, setTerminals] = useState<TerminalDto[]>([]);
  const [companies, setCompanies] = useState<CompanyDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editingTerminalId, setEditingTerminalId] = useState<number | null>(null);
  const [form, setForm] = useState({ id: '', name: '', login: '', password: '', companyId: '' });
  const [error, setError] = useState('');
  const [snackbar, setSnackbar] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  const fetchTerminals = async () => {
    setLoading(true);
    try {
      const res = await apiClient.get('/api/v1/terminals');
      const list = Array.isArray(res.data) ? res.data : (res.data?.content || []);
      setTerminals(list);
    } catch {
      setTerminals([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchCompanies = async () => {
    try {
      const res = await apiClient.get('/api/v1/companies');
      const list = Array.isArray(res.data) ? res.data : (res.data?.content || []);
      setCompanies(list);
      if (list.length > 0 && !form.companyId) {
        setForm(f => ({ ...f, companyId: list[0].id }));
      }
    } catch {
      setCompanies([]);
    }
  };

  useEffect(() => {
    fetchTerminals();
    fetchCompanies();
  }, []);

  const handleOpenCreate = () => {
    fetchCompanies();
    setError('');
    setEditingTerminalId(null);
    setForm({ id: '', name: '', login: '', password: '', companyId: companies[0]?.id || '' });
    setCreateOpen(true);
  };

  const handleOpenEdit = (term: any) => {
    fetchCompanies();
    setError('');
    setEditingTerminalId(term.id);
    setForm({
      id: String(term.id),
      name: term.name || '',
      login: term.login || '',
      password: '', // blank password unless changing
      companyId: term.companyId || (companies[0]?.id ?? '')
    });
    setEditOpen(true);
  };

  const handleCreate = async () => {
    if (!form.id || !form.name.trim() || !form.login.trim() || !form.password.trim() || !form.companyId) {
      setError('All fields including Company selection are required');
      return;
    }
    setError('');
    try {
      const payload = {
        id: Number(form.id),
        name: form.name.trim(),
        login: form.login.trim(),
        password: form.password.trim(),
        companyId: form.companyId,
      };
      const res = await apiClient.post('/api/v1/terminals', payload);
      setTerminals(prev => [...prev, res.data]);
      setCreateOpen(false);
      setForm({ id: '', name: '', login: '', password: '', companyId: companies[0]?.id || '' });
      setSnackbar('Acquiring terminal registered successfully');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to create terminal');
    }
  };

  const handleUpdate = async () => {
    if (!editingTerminalId) return;
    if (!form.name.trim() || !form.login.trim() || !form.companyId) {
      setError('Name, Login and Company selection are required');
      return;
    }
    setError('');
    try {
      const payload: any = {
        name: form.name.trim(),
        login: form.login.trim(),
        companyId: form.companyId,
      };
      if (form.password.trim()) {
        payload.password = form.password.trim();
      }
      const res = await apiClient.patch(`/api/v1/terminals/${editingTerminalId}`, payload);
      setTerminals(prev => prev.map(t => (t.id === editingTerminalId ? res.data : t)));
      setEditOpen(false);
      setEditingTerminalId(null);
      setForm({ id: '', name: '', login: '', password: '', companyId: companies[0]?.id || '' });
      setSnackbar('Acquiring terminal updated successfully');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to update terminal');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await apiClient.delete(`/api/v1/terminals/${id}`);
      setTerminals(prev => prev.filter(t => t.id !== id));
      setSnackbar('Terminal deleted successfully');
    } catch (err: any) {
      setSnackbar(err.response?.data?.message || 'Failed to delete terminal');
    }
  };

  const getCompanyName = (companyId: string) => {
    const comp = companies.find(c => c.id === companyId);
    return comp ? comp.name : companyId;
  };

  const filteredTerminals = useMemo(() => {
    return terminals.filter(term => {
      if (!searchQuery.trim()) return true;
      const q = searchQuery.toLowerCase();
      const matchId = String(term.id).toLowerCase().includes(q);
      const matchName = (term.name || '').toLowerCase().includes(q);
      const matchLogin = (term.login || '').toLowerCase().includes(q);
      const compName = getCompanyName(term.companyId).toLowerCase();
      const matchCompany = compName.includes(q) || (term.companyId || '').toLowerCase().includes(q);
      return matchId || matchName || matchLogin || matchCompany;
    });
  }, [terminals, searchQuery, companies]);

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <POSIcon color="primary" fontSize="large" /> Acquiring Terminals
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage payment gateway terminals and assign them to merchant companies
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5}>
          <Button variant="outlined" startIcon={<RefreshIcon />} onClick={fetchTerminals}>
            Refresh
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={handleOpenCreate}>
            Add Terminal
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
          placeholder="Search terminals by ID, name, login, or company..."
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
          sx={{ minWidth: 320, width: { xs: '100%', sm: 420 } }}
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
                <TableCell sx={{ fontWeight: 700 }}>Terminal ID</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Terminal Name</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Login</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Assigned Company</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Created At</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="center">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredTerminals.map((term) => (
                <TableRow key={term.id} hover>
                  <TableCell sx={{ fontFamily: 'monospace', fontWeight: 700, color: 'primary.main' }}>
                    #{term.id}
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{term.name}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace' }}>{term.login}</TableCell>
                  <TableCell>
                    <Chip
                      icon={<BusinessIcon fontSize="small" />}
                      label={getCompanyName(term.companyId)}
                      variant="outlined"
                      size="small"
                      sx={{ fontWeight: 600 }}
                    />
                  </TableCell>
                  <TableCell sx={{ fontSize: '0.85rem', color: 'text.secondary' }}>
                    {term.createdAt ? new Date(term.createdAt).toLocaleString() : 'N/A'}
                  </TableCell>
                  <TableCell align="center">
                    <Stack direction="row" spacing={0.5} justifyContent="center">
                      <Tooltip title="Edit Terminal">
                        <IconButton color="primary" size="small" onClick={() => handleOpenEdit(term)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Delete Terminal">
                        <IconButton color="error" size="small" onClick={() => handleDelete(term.id)}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
              {terminals.length === 0 && !loading && (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 6 }}>
                    <POSIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
                    <Typography color="text.secondary">No acquiring terminals registered yet.</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {/* Create Terminal Dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>Register New Acquiring Terminal</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{error}</Alert>}
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            <TextField
              select
              fullWidth
              label="Assigned Company *"
              value={form.companyId || (companies[0]?.id ?? '')}
              onChange={e => setForm(f => ({ ...f, companyId: e.target.value }))}
              helperText="Выберите компанию, к которой относится создаваемый терминал"
            >
              {companies.map((comp) => (
                <MenuItem key={comp.id} value={comp.id}>
                  {comp.name} (ID: {comp.id})
                </MenuItem>
              ))}
            </TextField>

            <TextField
              label="Terminal ID (Numeric) *"
              type="number"
              value={form.id}
              onChange={e => setForm(f => ({ ...f, id: e.target.value }))}
              placeholder="e.g. 1"
              fullWidth
              helperText="Числовой ID терминала в эквайринговой системе"
            />
            <TextField
              label="Terminal Name *"
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              placeholder="e.g. Main Online E-commerce Terminal"
              fullWidth
            />
            <TextField
              label="Terminal Login *"
              value={form.login}
              onChange={e => setForm(f => ({ ...f, login: e.target.value }))}
              placeholder="e.g. term_login_001"
              fullWidth
            />
            <TextField
              label="Terminal Password *"
              type="password"
              value={form.password}
              onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
              placeholder="••••••••"
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleCreate}>Register Terminal</Button>
        </DialogActions>
      </Dialog>

      {/* Edit Terminal Dialog */}
      <Dialog open={editOpen} onClose={() => setEditOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>Edit Acquiring Terminal #{editingTerminalId}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{error}</Alert>}
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            <TextField
              select
              fullWidth
              label="Assigned Company *"
              value={form.companyId}
              onChange={e => setForm(f => ({ ...f, companyId: e.target.value }))}
              helperText="Выберите компанию, к которой относится терминал"
            >
              {companies.map((comp) => (
                <MenuItem key={comp.id} value={comp.id}>
                  {comp.name} (ID: {comp.id})
                </MenuItem>
              ))}
            </TextField>

            <TextField
              label="Terminal Name *"
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              placeholder="e.g. Main Online E-commerce Terminal"
              fullWidth
            />
            <TextField
              label="Terminal Login *"
              value={form.login}
              onChange={e => setForm(f => ({ ...f, login: e.target.value }))}
              placeholder="e.g. term_login_001"
              fullWidth
            />
            <TextField
              label="New Terminal Password (Optional)"
              type="password"
              value={form.password}
              onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
              placeholder="Leave blank to keep existing password"
              fullWidth
              helperText="Оставьте пустым, если не хотите менять пароль терминала"
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          <Button onClick={() => setEditOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleUpdate}>Save Changes</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

