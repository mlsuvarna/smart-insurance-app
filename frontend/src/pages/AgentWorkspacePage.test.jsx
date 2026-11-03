import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import client from '../api/client';
import AgentWorkspacePage from './AgentWorkspacePage';

vi.mock('../api/client', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

describe('agent customer workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders customer, policy, and claim data from paged API responses', async () => {
    client.get.mockImplementation((url) => {
      if (url === '/agent/customers') {
        return Promise.resolve({ data: { content: [{ id: 8, fullName: 'Casey Customer', email: 'casey@example.test' }], last: true } });
      }
      if (url === '/customers/8') {
        return Promise.resolve({ data: { fullName: 'Casey Customer', email: 'casey@example.test', maskedPhone: '***1234' } });
      }
      if (url === '/agent/customers/8/policies') {
        return Promise.resolve({ data: { content: [{
          id: 40, policyNumber: 'POL-40', productId: 5, productName: 'Home Cover',
          startDate: '2026-01-01', endDate: '2026-12-31', status: 'ACTIVE', coverageAmount: 50000,
        }], last: true } });
      }
      if (url === '/agent/customers/8/claims') {
        return Promise.resolve({ data: { content: [{
          id: 70, claimNumber: 'CLM-70', claimType: 'Property', status: 'SUBMITTED',
        }], last: true } });
      }
      throw new Error(`Unexpected API request: ${url}`);
    });

    render(<MemoryRouter><AgentWorkspacePage /></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: 'Open workspace' }));

    expect(await screen.findByRole('heading', { name: 'Casey Customer' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Assigned policies' })).toBeInTheDocument();
    expect(screen.getByText('POL-40')).toBeInTheDocument();
    expect(screen.getByText('Home Cover')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Assigned claims' })).toBeInTheDocument();
    expect(screen.getByText('CLM-70')).toBeInTheDocument();
    expect(screen.getByText('Property')).toBeInTheDocument();
    await waitFor(() => expect(client.get).toHaveBeenCalledWith(
      '/agent/customers/8/claims', { params: { page: 0, size: 10 } },
    ));
  });

  it('shows an error when the assigned customers request fails', async () => {
    client.get.mockRejectedValue({
      response: { data: { message: 'Assigned customer request failed.' } },
    });

    render(<MemoryRouter><AgentWorkspacePage /></MemoryRouter>);

    expect(await screen.findByRole('alert')).toHaveTextContent('Assigned customer request failed.');
  });
});
