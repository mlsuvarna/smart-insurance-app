import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import client from '../api/client';
import DashboardPage, { Status } from './DashboardPage';

vi.mock('../api/client', () => ({ default: { get: vi.fn() } }));
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { role: 'CLAIMS_OFFICER', fullName: 'Alex Officer' } }),
}));

beforeEach(() => {
  vi.clearAllMocks();
});

describe('claim status', () => {
  it('renders an accessible human-readable state', () => {
    render(<Status status="INFO_REQUESTED" />);
    expect(screen.getByText('INFO REQUESTED')).toBeInTheDocument();
  });

  it('uses paged queue totals for officer dashboard counts and shows latest claims', async () => {
    const recentClaim = {
      id: 8,
      claimNumber: 'CLM-RECENT',
      claimType: 'HOME',
      status: 'SUBMITTED',
      createdAt: '2026-11-03T10:00:00Z',
    };
    client.get.mockImplementation((_url, { params } = {}) => Promise.resolve({
      data: {
        content: params?.size === 5 ? [recentClaim] : [],
        totalElements: params?.assignedOnly ? 2 : params?.openOnly ? 5 : params?.needsAttention ? 1 : 7,
      },
    }));

    render(<MemoryRouter><DashboardPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('CLM-RECENT')).toBeInTheDocument();
      expect(within(screen.getByText('Assigned claims').closest('article')).getByText('2')).toBeInTheDocument();
      expect(within(screen.getByText('Open claims').closest('article')).getByText('5')).toBeInTheDocument();
      expect(within(screen.getByText('Needs attention').closest('article')).getByText('1')).toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: /view all/i })).toHaveAttribute('href', '/claims');
    expect(client.get).toHaveBeenCalledWith('/claims/queue', { params: { page: 0, size: 5 } });
  });
});
