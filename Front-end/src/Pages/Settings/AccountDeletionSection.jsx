import { useState } from 'react';
import useApi from '../../hooks/useApi.js';
import './AccountDeletionSection.css';

export default function AccountDeletionSection({ onLogout }) {
  const [modalOpen, setModalOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState(null);
  const { del } = useApi();

  function openModal() {
    setError(null);
    setModalOpen(true);
  }

  function closeModal() {
    if (!deleting) {
      setModalOpen(false);
      setError(null);
    }
  }

  async function handleConfirmDelete() {
    setDeleting(true);
    setError(null);

    const { status, error: apiError } = await del('/users/me');

    if (status === 204) {
      localStorage.clear();
      onLogout();
    } else {
      setError(apiError || 'Failed to delete account. Please try again.');
      setDeleting(false);
    }
  }

  return (
    <section className="account-deletion-section">
      <h2 className="account-deletion-section__title">Account</h2>

      <div className="account-deletion-section__content">
        <span className="account-deletion-section__label">
          Permanently delete your account and all associated data
        </span>
        <button
          type="button"
          className="account-deletion-section__delete-btn"
          onClick={openModal}
        >
          Delete Account
        </button>
      </div>

      {modalOpen && (
        <div className="account-deletion-section__overlay" onClick={closeModal}>
          <div
            className="account-deletion-section__modal"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-modal-title"
          >
            <h3 id="delete-modal-title" className="account-deletion-section__modal-title">
              Delete Account
            </h3>

            <p className="account-deletion-section__modal-warning">
              Are you sure you want to delete your account? This action is permanent and cannot be
              undone. All your holdings and data will be removed.
            </p>

            {error && (
              <p className="account-deletion-section__modal-error">{error}</p>
            )}

            <div className="account-deletion-section__modal-actions">
              <button
                type="button"
                className="account-deletion-section__cancel-btn"
                onClick={closeModal}
                disabled={deleting}
              >
                Cancel
              </button>
              <button
                type="button"
                className="account-deletion-section__confirm-btn"
                onClick={handleConfirmDelete}
                disabled={deleting}
              >
                {deleting ? 'Deleting…' : 'Confirm Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
