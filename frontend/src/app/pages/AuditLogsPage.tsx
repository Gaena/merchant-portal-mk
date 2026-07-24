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
  TextField,
  MenuItem,
  Stack,
  InputAdornment,
  CircularProgress
} from '@mui/material';
import {
  History as HistoryIcon,
  Search as SearchIcon,
  Refresh as RefreshIcon
} from '@mui/icons-material';
import { apiClient } from '../api/client';

import type { AuditLogDto } from '../types/dto';

export const AuditLogsPage: React.FC = () => {
  const [auditLogsList, setAuditLogsList] = useState<AuditLogDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [entityTypeFilter, setEntityTypeFilter] = useState('all');

  const fetchAuditLogs = () => {
    setLoading(true);
    apiClient.get('/api/v1/audit-logs')
      .then(res => {
        setAuditLogsList(Array.isArray(res.data) ? res.data : []);
      })
      .catch(() => {
        setAuditLogsList([]);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchAuditLogs();
  }, []);

  const filteredLogs = useMemo(() => {
    return auditLogsList.filter(log => {
      if (entityTypeFilter !== 'all' && log.entityType !== entityTypeFilter) return false;
      if (searchQuery) {
        const q = searchQuery.toLowerCase();
        const matchActor = (log.performedBy || '').toLowerCase().includes(q);
        const matchAction = (log.action || '').toLowerCase().includes(q);
        const matchEntityId = (log.entityId || '').toLowerCase().includes(q);
        const matchDetails = (log.details || '').toLowerCase().includes(q);
        if (!matchActor && !matchAction && !matchEntityId && !matchDetails) return false;
      }
      return true;
    });
  }, [auditLogsList, entityTypeFilter, searchQuery]);

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 600, mb: 0.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <HistoryIcon color="primary" fontSize="large" />
            Audit Logs
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Track security events, entity mutations, and administrative activities across the system
          </Typography>
        </Box>
        <Button variant="outlined" startIcon={<RefreshIcon />} onClick={fetchAuditLogs}>
          Refresh
        </Button>
      </Box>

      {/* Filters Bar */}
      <Paper elevation={0} sx={{ p: 2, mb: 3, border: '1px solid', borderColor: 'divider', display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          placeholder="Search actor, action, entity ID or details..."
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
          label="Entity Type"
          value={entityTypeFilter}
          onChange={e => setEntityTypeFilter(e.target.value)}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="all">All Entity Types</MenuItem>
          <MenuItem value="COMPANY">Company</MenuItem>
          <MenuItem value="TERMINAL">Terminal</MenuItem>
          <MenuItem value="USER">User</MenuItem>
          <MenuItem value="PAYMENT_LINK">Payment Link</MenuItem>
          <MenuItem value="TRANSACTION">Transaction</MenuItem>
        </TextField>
      </Paper>

      {/* Logs Table */}
      <TableContainer component={Paper} variant="outlined">
        {loading ? (
          <Box sx={{ p: 6, textAlign: 'center' }}>
            <CircularProgress />
          </Box>
        ) : (
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: 'action.hover' }}>
                <TableCell sx={{ fontWeight: 700 }}>Timestamp</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Action</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Performed By</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Entity Type</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Entity ID</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Details</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredLogs.map((log: any) => (
                <TableRow key={log.id || Math.random()} hover>
                  <TableCell sx={{ fontSize: '0.8rem', whiteSpace: 'nowrap' }}>
                    {log.createdAt ? new Date(log.createdAt).toLocaleString() : 'N/A'}
                  </TableCell>
                  <TableCell>
                    <Chip label={log.action} size="small" color="info" variant="outlined" />
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{log.performedBy || 'System'}</TableCell>
                  <TableCell>{log.entityType || '—'}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{log.entityId || '—'}</TableCell>
                  <TableCell sx={{ fontSize: '0.8rem', color: 'text.secondary' }}>{log.details || '—'}</TableCell>
                </TableRow>
              ))}
              {filteredLogs.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 6 }}>
                    <Typography color="text.secondary">No audit logs recorded yet.</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        )}
      </TableContainer>
    </Box>
  );
};
