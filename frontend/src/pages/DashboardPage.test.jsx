import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Status } from './DashboardPage';

describe('claim status', () => {
  it('renders an accessible human-readable state', () => {
    render(<Status status="INFO_REQUESTED" />);
    expect(screen.getByText('INFO REQUESTED')).toBeInTheDocument();
  });
});
