import React, { useCallback, useEffect, useState } from 'react';
import { api } from '../api';
import { useLanguage } from '../i18n';
import type { Ballot, ElectionSummary } from '../types';
import { BallotView } from '../BallotView';
import { Card, Muted, Notice, SectionTitle, Spinner } from '../ui';

export function PreviewTab({ token, election }: { token: string; election: ElectionSummary }) {
  const { t } = useLanguage();
  const [ballot, setBallot] = useState<Ballot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await api<Ballot>(`/api/elections/${election.id}/ballot-preview`, { token });
      setBallot(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load the ballot preview.');
    } finally {
      setLoading(false);
    }
  }, [token, election.id]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <>
      <Card>
        <SectionTitle title={t('admin.preview')} subtitle="This is exactly what voters see after they authenticate." />
        {error ? <Notice tone="danger">{error}</Notice> : null}
        {loading ? <Spinner label={t('common.loading')} /> : null}
        {ballot ? (
          <>
            <Muted>{ballot.title}</Muted>
            <Muted>
              {ballot.posts.length} posts · {ballot.panels.length} panels · {ballot.eligibleVoters} voters on the roll
            </Muted>
          </>
        ) : null}
      </Card>
      {ballot ? (
        <BallotView
          posts={ballot.posts}
          panels={ballot.panels}
          selections={{}}
          readOnly
        />
      ) : null}
    </>
  );
}
