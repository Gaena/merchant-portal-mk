import React, { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Button,
  Chip,
  Stack,
  Collapse,
  IconButton,
  Grid,
  InputAdornment,
  type SelectChangeEvent,
  Autocomplete
} from '@mui/material';
import {
  Search as SearchIcon,
  FilterList as FilterIcon,
  Clear as ClearIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon
} from '@mui/icons-material';
import type { TransactionFilters, TransactionStatus, PaymentMethod, POSPaymentType } from '../types/transaction';
import { DatePicker } from '@mui/lab';

interface POSFilterPanelProps {
  filters: TransactionFilters;
  onFilterChange: (filters: TransactionFilters) => void;
  totalTransactions: number;
  filteredTransactions: number;
  cashiers: string[];
  locations: string[];
  terminalRids: string[];
}

export const POSFilterPanel: React.FC<POSFilterPanelProps> = ({
  filters,
  onFilterChange,
  totalTransactions,
  filteredTransactions,
  cashiers,
  locations,
  terminalRids
}) => {
  const [isExpanded, setIsExpanded] = useState(true);

  const handleFilterChange = (field: keyof TransactionFilters, value: any) => {
    onFilterChange({ ...filters, [field]: value });
  };

  const handleClearFilters = () => {
    onFilterChange({
      dateFrom: null,
      dateTo: null,
      status: 'all',
      paymentMethod: 'all',
      minAmount: '',
      maxAmount: '',
      searchQuery: '',
      terminalRid: [],
      posPaymentType: 'all',
      cashierId: 'all',
      locationName: 'all',
      batchId: 'all'
    });
  };

  const activeFilterCount = [
    filters.dateFrom,
    filters.dateTo,
    filters.status !== 'all',
    filters.paymentMethod !== 'all',
    filters.minAmount,
    filters.maxAmount,
    filters.searchQuery,
    filters.terminalRid.length > 0,
    filters.posPaymentType !== 'all',
    filters.cashierId !== 'all',
    filters.locationName !== 'all',
    filters.batchId !== 'all'
  ].filter(Boolean).length;

  return (
    <Paper sx={{ mb: 3, overflow: 'hidden' }}>
      {/* Header */}
      <Box
        sx={{
          p: 2,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          bgcolor: 'background.default',
          cursor: 'pointer'
        }}
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <FilterIcon color="primary" />
          <Typography variant="h6">
            Filters
          </Typography>
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
          <Typography variant="body2" color="text.secondary">
            Showing {filteredTransactions.toLocaleString()} of {totalTransactions.toLocaleString()} transactions
          </Typography>
          <IconButton size="small">
            {isExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
          </IconButton>
        </Box>
      </Box>

      {/* Filter Content */}
      <Collapse in={isExpanded}>
        <Box sx={{ p: 3, pt: 2 }}>
          <Grid container spacing={2.5}>
            {/* Search - Full width */}
            <Grid size={{ xs: 12 }}>
              <TextField
                fullWidth
                placeholder="Search by Transaction ID, Receipt Number, Customer Name, Email, Cashier, Location..."
                value={filters.searchQuery}
                onChange={(e) => handleFilterChange('searchQuery', e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon />
                    </InputAdornment>
                  ),
                  endAdornment: filters.searchQuery && (
                    <InputAdornment position="end">
                      <IconButton
                        size="small"
                        onClick={() => handleFilterChange('searchQuery', '')}
                      >
                        <ClearIcon />
                      </IconButton>
                    </InputAdornment>
                  )
                }}
              />
            </Grid>

            {/* Row 1: Status, Payment Method, Card Entry Mode, Location */}
            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <FormControl fullWidth>
                <InputLabel>Status</InputLabel>
                <Select
                  value={filters.status}
                  label="Status"
                  onChange={(e: SelectChangeEvent) => handleFilterChange('status', e.target.value)}
                >
                  <MenuItem value="all">All Statuses</MenuItem>
                  <MenuItem value="success">Success</MenuItem>
                  <MenuItem value="pending">Pending</MenuItem>
                  <MenuItem value="canceled">Canceled</MenuItem>
                  <MenuItem value="3d-failed">3D-Failed</MenuItem>
                </Select>
              </FormControl>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <FormControl fullWidth>
                <InputLabel>Payment Method</InputLabel>
                <Select
                  value={filters.paymentMethod}
                  label="Payment Method"
                  onChange={(e: SelectChangeEvent) => handleFilterChange('paymentMethod', e.target.value)}
                >
                  <MenuItem value="all">All Methods</MenuItem>
                  <MenuItem value="sms">SMS</MenuItem>
                  <MenuItem value="dms">DMS</MenuItem>
                  <MenuItem value="mit">MIT</MenuItem>
                  <MenuItem value="cit">CIT</MenuItem>
                </Select>
              </FormControl>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <FormControl fullWidth>
                <InputLabel>Card Entry Mode</InputLabel>
                <Select
                  value={filters.posPaymentType || 'all'}
                  label="Card Entry Mode"
                  onChange={(e: SelectChangeEvent) => handleFilterChange('posPaymentType', e.target.value)}
                >
                  <MenuItem value="all">All Entry Modes</MenuItem>
                  <MenuItem value="chip">Chip & PIN</MenuItem>
                  <MenuItem value="contactless">Contactless/NFC</MenuItem>
                  <MenuItem value="swipe">Magnetic Swipe</MenuItem>
                  <MenuItem value="manual">Manual Entry</MenuItem>
                </Select>
              </FormControl>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <FormControl fullWidth>
                <InputLabel>Location</InputLabel>
                <Select
                  value={filters.locationName || 'all'}
                  label="Location"
                  onChange={(e: SelectChangeEvent) => handleFilterChange('locationName', e.target.value)}
                >
                  <MenuItem value="all">All Locations</MenuItem>
                  {locations.map((location) => (
                    <MenuItem key={location} value={location}>
                      {location}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>

            {/* Row 2: Cashier, Terminal RID, Batch ID, Amount Range */}
            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <FormControl fullWidth>
                <InputLabel>Cashier</InputLabel>
                <Select
                  value={filters.cashierId || 'all'}
                  label="Cashier"
                  onChange={(e: SelectChangeEvent) => handleFilterChange('cashierId', e.target.value)}
                >
                  <MenuItem value="all">All Cashiers</MenuItem>
                  {cashiers.map((cashier) => (
                    <MenuItem key={cashier} value={cashier}>
                      {cashier}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <Autocomplete
                multiple
                options={terminalRids}
                value={filters.terminalRid}
                onChange={(event, newValue) => handleFilterChange('terminalRid', newValue)}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Terminal RID"
                    placeholder="Select terminals..."
                  />
                )}
                renderTags={(value, getTagProps) =>
                  value.map((option, index) => (
                    <Chip
                      label={option}
                      size="small"
                      {...getTagProps({ index })}
                      key={option}
                    />
                  ))
                }
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <TextField
                fullWidth
                label="Batch ID"
                placeholder="Enter batch ID..."
                value={filters.batchId || ''}
                onChange={(e) => handleFilterChange('batchId', e.target.value)}
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <Stack direction="row" spacing={1}>
                <TextField
                  fullWidth
                  type="number"
                  label="Min Amount"
                  value={filters.minAmount}
                  onChange={(e) => handleFilterChange('minAmount', e.target.value)}
                  InputProps={{
                    startAdornment: <InputAdornment position="start">₼</InputAdornment>
                  }}
                />
                <TextField
                  fullWidth
                  type="number"
                  label="Max Amount"
                  value={filters.maxAmount}
                  onChange={(e) => handleFilterChange('maxAmount', e.target.value)}
                  InputProps={{
                    startAdornment: <InputAdornment position="start">₼</InputAdornment>
                  }}
                />
              </Stack>
            </Grid>

            {/* Actions */}
            <Grid size={{ xs: 12 }}>
              <Stack direction="row" spacing={2} justifyContent="flex-end">
                <Button
                  variant="outlined"
                  startIcon={<ClearIcon />}
                  onClick={handleClearFilters}
                  disabled={activeFilterCount === 0}
                >
                  Clear All Filters
                </Button>
              </Stack>
            </Grid>
          </Grid>
        </Box>
      </Collapse>
    </Paper>
  );
};