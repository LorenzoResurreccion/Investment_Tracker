import { describe, it, expect } from 'vitest';
import ConnectionIndicator from '../Status/ConnectionIndicator.jsx';

describe('ConnectionIndicator', () => {
  it('returns null when status is connected', () => {
    const result = ConnectionIndicator({ status: 'connected' });
    expect(result).toBeNull();
  });

  it('renders a reconnecting indicator when status is reconnecting', () => {
    const result = ConnectionIndicator({ status: 'reconnecting' });
    expect(result).not.toBeNull();
    expect(result.props.role).toBe('alert');
    expect(result.props.className).toContain('connection-indicator--reconnecting');
    // Check message content
    const messageSpan = result.props.children.find(
      (child) => child?.props?.className === 'connection-indicator__message'
    );
    expect(messageSpan.props.children).toContain('Connection lost');
  });

  it('renders a failed indicator when status is failed', () => {
    const result = ConnectionIndicator({ status: 'failed' });
    expect(result).not.toBeNull();
    expect(result.props.role).toBe('alert');
    expect(result.props.className).toContain('connection-indicator--failed');
    // Check message content
    const messageSpan = result.props.children.find(
      (child) => child?.props?.className === 'connection-indicator__message'
    );
    expect(messageSpan.props.children).toContain('Real-time updates are unavailable');
  });

  it('includes aria-live="polite" for accessibility', () => {
    const result = ConnectionIndicator({ status: 'reconnecting' });
    expect(result.props['aria-live']).toBe('polite');
  });

  it('hides the icon from screen readers with aria-hidden', () => {
    const result = ConnectionIndicator({ status: 'failed' });
    const iconSpan = result.props.children.find(
      (child) => child?.props?.className === 'connection-indicator__icon'
    );
    expect(iconSpan.props['aria-hidden']).toBe('true');
  });
});
