import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import client from '../api/client';
import ClaimsPage from './ClaimsPage';

vi.mock('../api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 5, role: 'CLAIMS_OFFICER', fullName: 'Alex Officer' } }),
}));

describe('claims officer queue page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    client.get.mockImplementation((url) => Promise.resolve({
      data: url === '/claims/queues'
        ? [{ code: 'GENERAL', name: 'General' }]
        : {
          content: [{
            id: 12, claimNumber: 'CLM-12', claimType: 'HOME', status: 'SUBMITTED',
            assignedOfficerId: null, createdAt: '2026-11-03T10:00:00Z',
          }],
          last: true,
        },
    }));
    client.post.mockResolvedValue({ data: {} });
  });

  it('filters the queue and lets an officer take an unassigned claim for review', async () => {
    render(<MemoryRouter><ClaimsPage /></MemoryRouter>);

    expect(await screen.findByText('CLM-12')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Status filter'), { target: { value: 'SUBMITTED' } });
    fireEvent.change(screen.getByLabelText('SLA filter'), { target: { value: 'true' } });
    fireEvent.click(await screen.findByRole('button', { name: 'Take & start review' }));

    await waitFor(() => expect(client.post).toHaveBeenCalledWith('/claims/12/review'));
    expect(client.get).toHaveBeenCalledWith('/claims/queue', {
      params: { page: 0, size: 20, status: 'SUBMITTED', overdue: 'true' },
    });
  });
});
