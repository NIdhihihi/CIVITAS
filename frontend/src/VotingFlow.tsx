import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import * as LocalAuthentication from 'expo-local-authentication';
import { api, downloadFile } from './api';
import { useLanguage } from './i18n';
import type {
  BallotSession,
  Confirmation,
  Member,
  OtpChallenge,
  PublicElection,
  PublicElectionDetail,
  Receipt,
  VoteSelection,
  VotingSession,
} from './types';
import { BallotView } from './BallotView';
import {
  Badge,
  Button,
  Card,
  Chip,
  Divider,
  EmptyState,
  Field,
  KeyValue,
  Muted,
  Notice,
  Photo,
  Screen,
  SectionTitle,
  Spinner,
  Stats,
  formatDateTime,
  shortHash,
  styles as uiStyles,
  theme,
} from './ui';

type Stage = 'join' | 'identify' | 'otp' | 'ballot' | 'review' | 'confirm' | 'receipt';
type Method = 'EMAIL_OTP' | 'BIOMETRIC';

export function VotingFlow({
  token,
  member,
  onRequireAccount,
  initialElectionId,
}: {
  token: string | null;
  member: Member | null;
  onRequireAccount: () => void;
  initialElectionId?: string | null;
}) {
  const { t } = useLanguage();
  const [stage, setStage] = useState<Stage>('join');
  const [publicElections, setPublicElections] = useState<PublicElection[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const [codeInput, setCodeInput] = useState('');
  const [election, setElection] = useState<PublicElectionDetail | null>(null);
  const [method, setMethod] = useState<Method>('EMAIL_OTP');
  const [identifier, setIdentifier] = useState('');
  const [challenge, setChallenge] = useState<OtpChallenge | null>(null);
  const [otp, setOtp] = useState('');
  const [votingToken, setVotingToken] = useState('');
  const [ballotSession, setBallotSession] = useState<BallotSession | null>(null);
  const [selections, setSelections] = useState<Record<string, VoteSelection>>({});
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [confirmCode, setConfirmCode] = useState('');
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [receiptCheckCode, setReceiptCheckCode] = useState('');
  const [checkedReceipt, setCheckedReceipt] = useState<Receipt | null>(null);

  const loadPublicElections = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const list = await api<PublicElection[]>('/api/public/elections');
      setPublicElections(list);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load elections.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadPublicElections();
  }, [loadPublicElections]);

  const lastDeepLink = useRef<string | null>(null);
  useEffect(() => {
    if (initialElectionId && initialElectionId !== lastDeepLink.current) {
      lastDeepLink.current = initialElectionId;
      openElection(initialElectionId);
    }
    // openElection is intentionally read when a new deep link arrives.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialElectionId]);

  const selectablePosts = useMemo(() => {
    if (!ballotSession) return [];
    return ballotSession.ballot.posts.filter((post) => {
      const hasCandidates = post.candidates && post.candidates.length > 0;
      const hasPanels = ballotSession.ballot.panels && ballotSession.ballot.panels.some((p) =>
        p.members && p.members.some((m) => m.postId === post.id),
      );
      if (post.votingMode === 'PANEL') return hasPanels;
      if (post.votingMode === 'CANDIDATE') return hasCandidates;
      return hasCandidates || hasPanels;
    });
  }, [ballotSession]);

  const totalSelectable = selectablePosts.length;
  const selectedCount = Object.keys(selections).length;
  const allSelected = totalSelectable > 0 && selectablePosts.every((p) => selections[p.id]);

  const selectedSummary = useMemo(() => {
    if (!ballotSession) return [];
    return ballotSession.ballot.posts
      .map((post) => {
        const selection = selections[post.id];
        if (!selection) return null;
        if (selection.candidateId) {
          const candidate = post.candidates.find((c) => c.id === selection.candidateId);
          return { post: post.title, choice: candidate?.name || 'Candidate', panel: candidate?.panelName || null };
        }
        const panel = ballotSession.ballot.panels.find((p) => p.id === selection.panelId);
        const member = panel?.members.find((m) => m.postId === post.id);
        return {
          post: post.title,
          choice: panel?.name || 'Panel',
          panel: member ? member.candidateName : null,
        };
      })
      .filter(Boolean) as Array<{ post: string; choice: string; panel: string | null }>;
  }, [ballotSession, selections]);

  function resetToJoin() {
    setStage('join');
    setElection(null);
    setChallenge(null);
    setOtp('');
    setVotingToken('');
    setBallotSession(null);
    setSelections({});
    setConfirmation(null);
    setConfirmCode('');
    setReceipt(null);
    setError('');
    setNotice('');
    loadPublicElections();
  }

  async function openElection(rawId: string) {
    setBusy(true);
    setError('');
    try {
      const id = extractElectionId(rawId);
      if (!id) {
        throw new Error('Enter a valid election link or election ID.');
      }
      const detail = await api<PublicElectionDetail>(`/api/public/elections/${id}`);
      setElection(detail);
      setStage('identify');
      setIdentifier(member?.email || '');
      setMethod('EMAIL_OTP');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not open this election.');
    } finally {
      setBusy(false);
    }
  }

  async function requestOtp() {
    if (!election) return;
    setBusy(true);
    setError('');
    try {
      const result = await api<OtpChallenge>('/api/voting/auth/request-otp', {
        method: 'POST',
        body: { electionId: election.id, method, identifier: identifier.trim() },
      });
      setChallenge(result);
      setOtp(result.developmentCode || '');
      setStage('otp');
      setNotice(result.developmentCode ? t('vote.otpDev', { code: result.developmentCode }) : '');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not send the verification code.');
    } finally {
      setBusy(false);
    }
  }

  async function verifyOtp() {
    if (!election || !challenge) return;
    setBusy(true);
    setError('');
    try {
      const session = await api<VotingSession>('/api/voting/auth/verify-otp', {
        method: 'POST',
        body: { electionId: election.id, challengeId: challenge.challengeId, code: otp.trim() },
      });
      await openBallot(session);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'That verification code could not be accepted.');
    } finally {
      setBusy(false);
    }
  }

  async function biometricLogin() {
    if (!election) return;
    if (!token || !member) {
      setNotice(t('vote.signInForBiometric'));
      onRequireAccount();
      return;
    }
    setBusy(true);
    setError('');
    try {
      if (Platform.OS === 'web') {
        throw new Error(t('vote.biometricUnavailable'));
      }
      const hardware = await LocalAuthentication.hasHardwareAsync();
      const enrolled = await LocalAuthentication.isEnrolledAsync();
      if (!hardware || !enrolled) {
        throw new Error(t('vote.biometricUnavailable'));
      }
      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: t('vote.biometricPrompt'),
        fallbackLabel: t('common.password'),
        cancelLabel: t('common.cancel'),
      });
      if (!result.success) {
        throw new Error(t('vote.biometricUnavailable'));
      }
      const session = await api<VotingSession>('/api/voting/auth/biometric', {
        method: 'POST',
        token,
        body: { electionId: election.id },
      });
      await openBallot(session);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('vote.biometricUnavailable'));
    } finally {
      setBusy(false);
    }
  }

  async function openBallot(session: VotingSession) {
    const loaded = await api<BallotSession>('/api/voting/ballot', {
      headers: { 'X-Voting-Session': session.token },
    });
    setVotingToken(session.token);
    setBallotSession(loaded);
    setSelections({});
    setStage('ballot');
    setNotice('');
    setError('');
  }

  function select(selection: VoteSelection) {
    setSelections((current) => ({ ...current, [selection.postId]: selection }));
    setError('');
  }

  function applyPanel(panel: PublicElectionDetail['panels'][number]) {
    if (!ballotSession) return;
    setSelections((current) => {
      const next = { ...current };
      for (const member of panel.members) {
        const post = ballotSession.ballot.posts.find((p) => p.id === member.postId);
        if (post && post.votingMode !== 'CANDIDATE') {
          next[post.id] = { postId: post.id, panelId: panel.id };
        }
      }
      return next;
    });
  }

  function review() {
    if (!allSelected) {
      setError(t('vote.mustSelectAll'));
      return;
    }
    setConfirmation(null);
    setConfirmCode('');
    setError('');
    setStage('review');
  }

  async function requestConfirmation() {
    if (!votingToken) return;
    if (confirmation) {
      setError('A confirmation code is already active. Use it or wait for it to expire before requesting another.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const result = await api<Confirmation>('/api/voting/confirm', {
        method: 'POST',
        headers: { 'X-Voting-Session': votingToken },
        body: { selections: Object.values(selections) },
      });
      setConfirmation(result);
      setConfirmCode(result.developmentCode || '');
      setStage('confirm');
      setNotice(result.developmentCode ? t('vote.otpDev', { code: result.developmentCode }) : '');
    } catch (err) {
      setError(err instanceof Error ? err.message : t('vote.selectionError'));
    } finally {
      setBusy(false);
    }
  }

  async function castVote() {
    if (!votingToken) return;
    setBusy(true);
    setError('');
    try {
      const result = await api<Receipt>('/api/voting/cast', {
        method: 'POST',
        headers: { 'X-Voting-Session': votingToken },
        body: { selections: Object.values(selections), confirmationCode: confirmCode.trim() },
      });
      setReceipt(result);
      setStage('receipt');
      setNotice('');
      setSelections({});
      setBallotSession(null);
      setVotingToken('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Your vote could not be cast. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  async function editBallot() {
    if (!votingToken) return;
    setBusy(true);
    setError('');
    try {
      await api('/api/voting/confirmation/cancel', {
        method: 'POST',
        headers: { 'X-Voting-Session': votingToken },
      });
      setConfirmation(null);
      setConfirmCode('');
      setStage('ballot');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The active confirmation could not be cancelled.');
    } finally {
      setBusy(false);
    }
  }

  async function verifyReceipt() {
    if (!receiptCheckCode.trim()) return;
    setBusy(true);
    setError('');
    setCheckedReceipt(null);
    try {
      const result = await api<Receipt>(`/api/voting/receipt/${encodeURIComponent(receiptCheckCode.trim())}`);
      setCheckedReceipt(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('vote.receiptInvalid'));
    } finally {
      setBusy(false);
    }
  }

  async function downloadReceipt(code: string) {
    setBusy(true);
    setError('');
    try {
      await downloadFile(
        `/api/voting/receipt/${encodeURIComponent(code)}/download`,
        'civitas-vote-receipt.txt',
      );
      setNotice('Receipt saved. Use the share sheet to keep or send a copy.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not download the receipt.');
    } finally {
      setBusy(false);
    }
  }

  if (loading && stage === 'join') {
    return (
      <Screen>
        <Spinner label={t('common.loading')} />
      </Screen>
    );
  }

  return (
    <Screen onRefresh={stage === 'join' ? loadPublicElections : undefined} refreshing={loading}>
      {stage === 'join' ? (
        <>
          <Card>
            <SectionTitle title={t('vote.title')} subtitle={t('vote.subtitle')} />
            <View style={styles.heroArt}>
              <View style={styles.heroOrb}>
                <Text style={styles.heroOrbText}>🗳</Text>
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.heroKicker}>SECRET BALLOT · VERIFIABLE RECEIPT</Text>
                <Text style={uiStyles.muted}>
                  Multi-post elections, panel voting, encrypted ballots, and a tamper-evident audit trail.
                </Text>
              </View>
            </View>
            {error ? <Notice tone="danger">{error}</Notice> : null}
            {notice ? <Notice tone="success">{notice}</Notice> : null}
            <Field
              label={t('vote.joinByCode')}
              value={codeInput}
              onChangeText={setCodeInput}
              placeholder={t('vote.codeHint')}
              autoCapitalize="none"
              autoCorrect={false}
            />
            <Button
              title={t('vote.openBallot')}
              onPress={() => openElection(codeInput)}
              loading={busy}
              disabled={!codeInput.trim()}
            />
          </Card>

          <Card>
            <SectionTitle title={t('vote.activeElections')} />
            {publicElections.length === 0 ? (
              <EmptyState title={t('vote.noActive')} />
            ) : (
              publicElections.map((item) => (
                <Pressable key={item.id} onPress={() => openElection(item.id)} style={styles.electionRow}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.electionTitle}>{item.title}</Text>
                    <Text style={uiStyles.muted}>{item.society}</Text>
                    <Text style={styles.electionMeta}>{t('vote.pollsClose', { date: formatDateTime(item.endsAt) })}</Text>
                  </View>
                  <Badge label="OPEN" tone="success" />
                </Pressable>
              ))
            )}
          </Card>

          <Card>
            <SectionTitle title={t('vote.verifyReceipt')} />
            <Field
              label={t('vote.receiptCode')}
              value={receiptCheckCode}
              onChangeText={setReceiptCheckCode}
              placeholder="cvs_receipt_... / UUID / Hash"
              autoCapitalize="none"
            />
            <Button
              title={t('vote.checkReceipt')}
              kind="secondary"
              onPress={verifyReceipt}
              loading={busy}
              disabled={!receiptCheckCode.trim()}
            />
            {checkedReceipt ? <ReceiptView receipt={checkedReceipt} /> : null}
          </Card>
        </>
      ) : null}

      {stage !== 'join' && election ? (
        <>
          <Pressable onPress={resetToJoin} style={styles.backRow}>
            <Text style={styles.backText}>‹ {t('common.back')}</Text>
          </Pressable>
          <Card>
            <View style={styles.electionHeader}>
              <View style={{ flex: 1 }}>
                <Text style={styles.electionKicker}>{election.society}</Text>
                <Text style={styles.electionHeading}>{election.title}</Text>
              </View>
              <Badge label={election.status} tone="success" />
            </View>
            <Text style={uiStyles.muted}>{election.description}</Text>
            <Stats
              items={[
                { label: t('admin.stats.posts'), value: election.posts.length },
                { label: t('admin.stats.voters'), value: election.eligibleVoters },
              ]}
            />
            <Text style={styles.electionMeta}>{t('vote.pollsClose', { date: formatDateTime(election.endsAt) })}</Text>
          </Card>
        </>
      ) : null}

      {error && stage !== 'join' ? <Notice tone="danger">{error}</Notice> : null}
      {notice && stage !== 'join' ? <Notice tone="info">{notice}</Notice> : null}

      {stage === 'identify' ? (
        <Card>
          <SectionTitle title={t('vote.identifyTitle')} subtitle={t('vote.identifyText')} />
          <View style={styles.methodRow}>
            <Chip label={t('vote.method.email')} active={method === 'EMAIL_OTP'} onPress={() => setMethod('EMAIL_OTP')} />
            <Chip label={t('vote.method.biometric')} active={method === 'BIOMETRIC'} onPress={() => setMethod('BIOMETRIC')} />
          </View>

          {method === 'EMAIL_OTP' ? (
            <>
              <Field
                label={t('vote.identifierEmail')}
                value={identifier}
                onChangeText={setIdentifier}
                keyboardType="email-address"
                autoCapitalize="none"
                autoCorrect={false}
                placeholder="you@example.com"
              />
              <Button
                title={t('vote.sendOtp')}
                onPress={requestOtp}
                loading={busy}
                disabled={!identifier.includes('@')}
              />
            </>
          ) : null}

          {method === 'BIOMETRIC' ? (
            <>
              <Muted>
                {token
                  ? t('vote.method.biometric')
                  : t('vote.signInForBiometric')}
              </Muted>
              <Button title={t('vote.method.biometric')} onPress={biometricLogin} loading={busy} />
            </>
          ) : null}
        </Card>
      ) : null}

      {stage === 'otp' && challenge ? (
        <Card>
          <SectionTitle
            title={t('vote.otpTitle')}
            subtitle={t('vote.otpSent', { destination: challenge.maskedDestination })}
          />
          {challenge.developmentCode ? (
            <Notice tone="warning">{t('vote.otpDev', { code: challenge.developmentCode })}</Notice>
          ) : null}
          <Field
            label={t('account.otp')}
            value={otp}
            onChangeText={setOtp}
            keyboardType="number-pad"
            maxLength={6}
            placeholder="123456"
          />
          <Button title={t('vote.verifyOtp')} onPress={verifyOtp} loading={busy} disabled={otp.trim().length !== 6} />
          <Button title={t('vote.resend')} kind="secondary" onPress={requestOtp} loading={busy} />
        </Card>
      ) : null}

      {stage === 'ballot' && ballotSession ? (
        <>
          <Card>
            <SectionTitle
              title={t('vote.ballotTitle')}
              subtitle={t('vote.selectedCount', { selected: selectedCount, total: totalSelectable })}
            />
            <Muted>{t('vote.selectOne')}</Muted>
            <View style={styles.progressTrack}>
              <View
                style={[
                  styles.progressFill,
                  { width: `${totalSelectable === 0 ? 0 : Math.round((selectedCount / totalSelectable) * 100)}%` },
                ]}
              />
            </View>
            <Muted>{ballotSession.voterName}</Muted>
          </Card>
          <BallotView
            posts={ballotSession.ballot.posts}
            panels={ballotSession.ballot.panels}
            selections={selections}
            onSelect={select}
            onApplyPanel={applyPanel}
          />
          <Button title={t('vote.review')} onPress={review} disabled={!allSelected} />
        </>
      ) : null}

      {stage === 'review' && ballotSession ? (
        <>
          <Card>
            <SectionTitle title={t('vote.reviewTitle')} subtitle={t('vote.reviewText')} />
            {selectedSummary.map((item) => (
              <View key={item.post} style={styles.reviewRow}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.reviewPost}>{item.post}</Text>
                  <Text style={styles.reviewChoice}>{item.choice}</Text>
                  {item.panel ? <Text style={styles.reviewPanel}>{item.panel}</Text> : null}
                </View>
                <Badge label="SELECTED" tone="success" />
              </View>
            ))}
            <Divider />
            <Button title={t('vote.edit')} kind="secondary" onPress={() => setStage('ballot')} />
            <Button title={t('vote.confirm')} onPress={requestConfirmation} loading={busy} />
          </Card>
        </>
      ) : null}

      {stage === 'confirm' && confirmation ? (
        <Card>
          <SectionTitle
            title={t('vote.confirmTitle')}
            subtitle={t('vote.confirmText', { destination: confirmation.maskedDestination })}
          />
          {confirmation.developmentCode ? (
            <Notice tone="warning">{t('vote.otpDev', { code: confirmation.developmentCode })}</Notice>
          ) : null}
          <Field
            label={t('vote.confirmCode')}
            value={confirmCode}
            onChangeText={setConfirmCode}
            keyboardType="number-pad"
            maxLength={6}
            placeholder="123456"
          />
          <Button
            title={t('vote.cast')}
            onPress={castVote}
            loading={busy}
            disabled={confirmCode.trim().length !== 6}
          />
          <Notice tone="info">If the code expires, return to review and request a new confirmation code.</Notice>
          <Button title={t('vote.edit')} kind="ghost" onPress={editBallot} loading={busy} />
        </Card>
      ) : null}

      {stage === 'receipt' && receipt ? (
        <>
          <Card>
            <View style={styles.successOrb}>
              <Text style={styles.successCheck}>✓</Text>
            </View>
            <SectionTitle title={t('vote.receiptTitle')} subtitle={t('vote.receiptText')} />
            <ReceiptView receipt={receipt} />
            {receipt.receiptCode ? (
              <Button
                title={t('vote.downloadReceipt')}
                onPress={() => downloadReceipt(receipt.receiptCode as string)}
                loading={busy}
              />
            ) : null}
            <Button title={t('vote.done')} kind="secondary" onPress={resetToJoin} />
          </Card>
        </>
      ) : null}
    </Screen>
  );
}

export function ReceiptView({ receipt }: { receipt: Receipt }) {
  const { t } = useLanguage();
  return (
    <View style={{ gap: 10 }}>
      <KeyValue label={t('vote.receiptId')} value={receipt.receiptId} mono />
      <KeyValue label={t('vote.receiptHash')} value={receipt.receiptHash} mono />
      <KeyValue label={t('vote.signature')} value={receipt.signature} mono />
      <KeyValue label="Election" value={receipt.electionTitle} />
      <KeyValue label="Cast at" value={formatDateTime(receipt.castAt)} />
      <KeyValue label="Posts voted" value={String(receipt.postsVoted)} />
      <KeyValue label={t('vote.verification')} value={receipt.verification} />
      {receipt.receiptCode ? <KeyValue label={t('vote.receiptCode')} value={receipt.receiptCode} mono /> : null}
      <Text style={styles.receiptNote}>
        {t('vote.receiptText')}
      </Text>
    </View>
  );
}

function extractElectionId(raw: string): string | null {
  const value = raw.trim();
  const direct = value.match(/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/);
  return direct ? direct[0] : null;
}

const styles = StyleSheet.create({
  heroArt: { flexDirection: 'row', gap: 13, alignItems: 'center', backgroundColor: '#f7fbfc', borderRadius: 16, padding: 12 },
  heroOrb: { width: 54, height: 54, borderRadius: 18, backgroundColor: theme.tealSoft, alignItems: 'center', justifyContent: 'center' },
  heroOrbText: { fontSize: 25 },
  heroKicker: { fontSize: 9, fontWeight: '900', letterSpacing: 1.3, color: theme.teal, marginBottom: 4 },
  electionRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 13, borderBottomWidth: 1, borderBottomColor: theme.border },
  electionTitle: { fontSize: 16, fontWeight: '900', color: theme.navy },
  electionMeta: { fontSize: 11, color: theme.muted, marginTop: 5 },
  backRow: { paddingVertical: 2 },
  backText: { fontSize: 14, fontWeight: '800', color: theme.teal },
  electionHeader: { flexDirection: 'row', gap: 10, alignItems: 'flex-start' },
  electionKicker: { fontSize: 10, fontWeight: '900', letterSpacing: 1.2, color: theme.teal, textTransform: 'uppercase' },
  electionHeading: { fontSize: 22, lineHeight: 28, fontWeight: '900', color: theme.navy, marginTop: 2 },
  methodRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  progressTrack: { height: 8, borderRadius: 5, backgroundColor: '#edf2f4', overflow: 'hidden' },
  progressFill: { height: '100%', backgroundColor: theme.teal, borderRadius: 5 },
  reviewRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 9, borderBottomWidth: 1, borderBottomColor: theme.border },
  reviewPost: { fontSize: 10, fontWeight: '900', letterSpacing: 0.8, color: theme.teal, textTransform: 'uppercase' },
  reviewChoice: { fontSize: 16, fontWeight: '800', color: theme.text, marginTop: 2 },
  reviewPanel: { fontSize: 12, color: theme.muted, marginTop: 2 },
  successOrb: { alignSelf: 'center', width: 68, height: 68, borderRadius: 24, backgroundColor: theme.successSoft, alignItems: 'center', justifyContent: 'center' },
  successCheck: { fontSize: 34, fontWeight: '900', color: theme.success },
  receiptNote: { fontSize: 11, lineHeight: 17, color: theme.muted, marginTop: 4 },
});
