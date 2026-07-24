import React, { useState, useMemo, useEffect } from 'react';
import {
  Box,
  Paper,
  TextField,
  MenuItem,
  Button,
  Typography,
  Divider,
  IconButton,
  InputAdornment,
  Chip,
  Stack,
  Collapse,
  Checkbox,
  ListItemText
} from '@mui/material';
import {
  Search as SearchIcon,
  Close as CloseIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon
} from '@mui/icons-material';
import FilterListOutlinedIcon from '@mui/icons-material/FilterListOutlined';
import type { TransactionFilters, TransactionStatus, PaymentMethod } from '../types/transaction';
import { terminalRids } from '../utils/mockData';

interface FilterPanelProps {
  filters: TransactionFilters;
  onFilterChange: (filters: TransactionFilters) => void;
  totalTransactions: number;
  filteredTransactions: number;
}

export const FilterPanel: React.FC<FilterPanelProps> = ({
  filters,
  onFilterChange,
  totalTransactions,
  filteredTransactions
}) => {
  const [localFilters, setLocalFilters] = useState<TransactionFilters>({
    ...filters,
    terminalRid: filters.terminalRid || []
  });
  const [expanded, setExpanded] = useState(false);

  // Sync with parent filters
  useEffect(() => {
    setLocalFilters({
      ...filters,
      terminalRid: filters.terminalRid || []
    });
  }, [filters]);

  const handleChange = (field: keyof TransactionFilters, value: any) => {
    const newFilters = { ...localFilters, [field]: value };
    setLocalFilters(newFilters);
    onFilterChange(newFilters);
  };

  const handleReset = () => {
    const resetFilters: TransactionFilters = {
      dateFrom: new Date(Date.now() - 10 * 60 * 1000),
      dateTo: new Date(),
      status: 'all',
      paymentMethod: 'all',
      minAmount: '',
      maxAmount: '',
      searchQuery: '',
      terminalRid: []
    };
    setLocalFilters(resetFilters);
    onFilterChange(resetFilters);
  };

  const activeFilterCount = useMemo(() => {
    let count = 0;
    if (localFilters.status !== 'all') count++;
    if (localFilters.paymentMethod !== 'all') count++;
    if (localFilters.terminalRid.length > 0) count++;
    if (localFilters.minAmount) count++;
    if (localFilters.maxAmount) count++;
    if (localFilters.searchQuery) count++;
    return count;
  }, [localFilters]);

  const formatDateTimeLocal = (date: Date | null) => {
    if (!date) return '';
    const d = new Date(date);
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
    return d.toISOString().slice(0, 16);
  };

  const handleDateChange = (field: 'dateFrom' | 'dateTo', value: string) => {
    const date = value ? new Date(value) : null;
    handleChange(field, date);
  };

  return (
    <Paper elevation={0} sx={{ p: 3, mb: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
      <Box 
        sx={{ 
          display: 'flex', 
          justifyContent: 'space-between', 
          alignItems: 'center', 
          mb: expanded ? 3 : 0,
          cursor: 'pointer'
        }}
        onClick={() => setExpanded(!expanded)}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              p: 1,
              borderRadius: 1,
              bgcolor: 'primary.light',
              color: 'primary.main',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <FilterListOutlinedIcon />
          </Box>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 600 }}>
              Filter Transactions
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Refine your search with filters below
            </Typography>
          </Box>
          {activeFilterCount > 0 && (
            <Chip 
              label={`${activeFilterCount} active`} 
              size="small" 
              color="primary"
              sx={{ ml: 1 }}
            />
          )}
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Button 
            variant="outlined" 
            size="medium"
            onClick={(e) => {
              e.stopPropagation();
              handleReset();
            }}
            disabled={activeFilterCount === 0}
            startIcon={<CloseIcon />}
          >
            Clear Filters
          </Button>
          <IconButton>
            {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
          </IconButton>
        </Box>
      </Box>

      <Collapse in={expanded}>
        <Divider sx={{ mb: 3, mt: 3 }} />

        {/* Row 1: Search - Most used, full width */}
        <Box sx={{ mb: 3 }}>
          <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 600, color: 'text.primary' }}>
            Quick Search
          </Typography>
          <TextField
            fullWidth
            placeholder="Search by transaction ID, customer name, email, or reference..."
            value={localFilters.searchQuery}
            onChange={(e) => handleChange('searchQuery', e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon color="action" />
                </InputAdornment>
              ),
              endAdornment: localFilters.searchQuery && (
                <InputAdornment position="end">
                  <IconButton
                    size="small"
                    onClick={() => handleChange('searchQuery', '')}
                  >
                    <CloseIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              )
            }}
            sx={{
              '& .MuiOutlinedInput-root': {
                bgcolor: 'background.paper'
              }
            }}
          />
        </Box>

        {/* Row 2: Date Range - High importance for time-based filtering */}
        <Box sx={{ mb: 3 }}>
          <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 600, color: 'text.primary' }}>
            Date & Time Range
          </Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <TextField
              fullWidth
              label="From Date & Time"
              type="datetime-local"
              value={formatDateTimeLocal(localFilters.dateFrom)}
              onChange={(e) => handleDateChange('dateFrom', e.target.value)}
              InputLabelProps={{ shrink: true }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  bgcolor: 'background.paper'
                }
              }}
            />
            <TextField
              fullWidth
              label="To Date & Time"
              type="datetime-local"
              value={formatDateTimeLocal(localFilters.dateTo)}
              onChange={(e) => handleDateChange('dateTo', e.target.value)}
              InputLabelProps={{ shrink: true }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  bgcolor: 'background.paper'
                }
              }}
            />
          </Box>
        </Box>

        {/* Row 3: Status & Payment Method - Popular categorical filters */}
        <Box sx={{ mb: 3 }}>
          <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 600, color: 'text.primary' }}>
            Transaction Details
          </Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: '1fr 1fr 1fr' }, gap: 2 }}>
            <TextField
              select
              fullWidth
              label="Status"
              value={localFilters.status}
              onChange={(e) => handleChange('status', e.target.value)}
              sx={{
                '& .MuiOutlinedInput-root': {
                  bgcolor: 'background.paper',
                  '&:hover': {
                    bgcolor: 'action.hover'
                  },
                  '&.Mui-focused': {
                    bgcolor: 'background.paper'
                  }
                }
              }}
            >
              <MenuItem key="all" value="all">All Statuses</MenuItem>
              <MenuItem key="success" value="success">
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#4caf50' }} />
                  Success
                </Box>
              </MenuItem>
              <MenuItem key="pending" value="pending">
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#ff9800' }} />
                  Pending
                </Box>
              </MenuItem>
              <MenuItem key="canceled" value="canceled">
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#2196f3' }} />
                  Canceled
                </Box>
              </MenuItem>
              <MenuItem key="3d-failed" value="3d-failed">
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#f44336' }} />
                  3D-Failed
                </Box>
              </MenuItem>
            </TextField>
            <TextField
              select
              fullWidth
              label="Payment Method"
              value={localFilters.paymentMethod}
              onChange={(e) => handleChange('paymentMethod', e.target.value)}
              sx={{
                '& .MuiOutlinedInput-root': {
                  bgcolor: 'background.paper',
                  '&:hover': {
                    bgcolor: 'action.hover'
                  },
                  '&.Mui-focused': {
                    bgcolor: 'background.paper'
                  }
                }
              }}
            >
              <MenuItem key="all" value="all">All Methods</MenuItem>
              <MenuItem key="sms" value="sms">SMS</MenuItem>
              <MenuItem key="dms" value="dms">DMS</MenuItem>
              <MenuItem key="mit" value="mit">MIT</MenuItem>
              <MenuItem key="cit" value="cit">CIT</MenuItem>
            </TextField>
            <Box>
              <TextField
                select
                fullWidth
                label="Terminal RID"
                value={Array.isArray(localFilters.terminalRid) ? localFilters.terminalRid : []}
                onChange={(e) => handleChange('terminalRid', e.target.value)}
                SelectProps={{
                  multiple: true,
                  renderValue: (selected) => {
                    const selectedArray = selected as string[];
                    if (selectedArray.length === 0) {
                      return <Typography color="text.secondary">All</Typography>;
                    }
                    return `${selectedArray.length} terminal${selectedArray.length > 1 ? 's' : ''} selected`;
                  }
                }}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    bgcolor: 'background.paper',
                    '&:hover': {
                      bgcolor: 'action.hover'
                    },
                    '&.Mui-focused': {
                      bgcolor: 'background.paper'
                    }
                  }
                }}
              >
                {[...new Set(terminalRids)].map((terminal) => (
                  <MenuItem key={terminal} value={terminal}>
                    <Checkbox
                      checked={localFilters.terminalRid.indexOf(terminal) > -1}
                      sx={{ mr: 1 }}
                    />
                    <ListItemText
                      primary={terminal}
                      primaryTypographyProps={{
                        fontFamily: 'monospace',
                        fontWeight: 500
                      }}
                    />
                  </MenuItem>
                ))}
              </TextField>
              {localFilters.terminalRid.length > 0 && (
                <Box sx={{ mt: 1.5, display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                  {localFilters.terminalRid.map((terminal) => (
                    <Chip
                      key={terminal}
                      label={terminal}
                      size="small"
                      onDelete={() => {
                        const newTerminals = localFilters.terminalRid.filter(t => t !== terminal);
                        handleChange('terminalRid', newTerminals);
                      }}
                      sx={{
                        fontFamily: 'monospace',
                        fontWeight: 600,
                        bgcolor: 'primary.main',
                        color: 'white',
                        '& .MuiChip-deleteIcon': {
                          color: 'rgba(255, 255, 255, 0.7)',
                          '&:hover': {
                            color: 'white'
                          }
                        }
                      }}
                    />
                  ))}
                </Box>
              )}
            </Box>
          </Box>
        </Box>

        {/* Row 4: Amount Range - Less common, advanced filtering */}
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 600, color: 'text.primary' }}>
            Amount Range
          </Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <TextField
              fullWidth
              label="Minimum Amount"
              type="number"
              value={localFilters.minAmount}
              onChange={(e) => handleChange('minAmount', e.target.value)}
              InputProps={{
                startAdornment: <InputAdornment position="start">AZN</InputAdornment>
              }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  bgcolor: 'background.paper'
                }
              }}
            />
            <TextField
              fullWidth
              label="Maximum Amount"
              type="number"
              value={localFilters.maxAmount}
              onChange={(e) => handleChange('maxAmount', e.target.value)}
              InputProps={{
                startAdornment: <InputAdornment position="start">AZN</InputAdornment>
              }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  bgcolor: 'background.paper'
                }
              }}
            />
          </Box>
        </Box>
      </Collapse>

      {/* Results Summary - Always visible */}
      <Box sx={{ mt: 3, pt: 2.5, borderTop: 1, borderColor: 'divider' }}>
        <Stack direction="row" spacing={2} alignItems="center" justifyContent="space-between">
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Showing
            </Typography>
            <Typography variant="body2" color="primary.main" sx={{ fontWeight: 700 }}>
              {filteredTransactions}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              of {totalTransactions} total transactions
            </Typography>
          </Box>
          {filteredTransactions !== totalTransactions && (
            <Typography variant="caption" color="primary" sx={{ fontWeight: 600 }}>
              {((filteredTransactions / totalTransactions) * 100).toFixed(0)}% of results shown
            </Typography>
          )}
        </Stack>
      </Box>
    </Paper>
  );
};