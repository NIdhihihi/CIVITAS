import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { api } from '../api';
import { useLanguage } from '../i18n';
import type { Candidate, ElectionPost, ElectionSummary, Panel, VotingMode } from '../types';
import {
  Badge,
  Button,
  Card,
  Chip,
  Field,
  Muted,
  Notice,
  Photo,
  SectionTitle,
  Spinner,
  styles as uiStyles,
  theme,
} from '../ui';

export function SetupTab({
  token,
  election,
  locked,
  onChanged,
}: {
  token: string;
  election: ElectionSummary;
  locked: boolean;
  onChanged: () => void;
}) {
  const { t } = useLanguage();
  const [posts, setPosts] = useState<ElectionPost[]>([]);
  const [panels, setPanels] = useState<Panel[]>([]);
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const [postTitle, setPostTitle] = useState('');
  const [postDescription, setPostDescription] = useState('');
  const [postMode, setPostMode] = useState<VotingMode>('BOTH');

  const [panelName, setPanelName] = useState('');
  const [panelDescription, setPanelDescription] = useState('');
  const [panelPhoto, setPanelPhoto] = useState<string | null>(null);

  const [candidatePostId, setCandidatePostId] = useState('');
  const [candidateName, setCandidateName] = useState('');
  const [candidateStatement, setCandidateStatement] = useState('');
  const [candidatePanelId, setCandidatePanelId] = useState('');
  const [candidatePhoto, setCandidatePhoto] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [postList, panelList, candidateList] = await Promise.all([
        api<ElectionPost[]>(`/api/elections/${election.id}/posts`, { token }),
        api<Panel[]>(`/api/elections/${election.id}/panels`, { token }),
        api<Candidate[]>(`/api/elections/${election.id}/candidates`, { token }),
      ]);
      setPosts(postList);
      setPanels(panelList);
      setCandidates(candidateList);
      if (!candidatePostId && postList.length > 0) setCandidatePostId(postList[0].id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load the ballot setup.');
    } finally {
      setLoading(false);
    }
  }, [token, election.id, candidatePostId]);

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, election.id]);

  async function run(action: () => Promise<void>, success: string) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action();
      setNotice(success);
      await load();
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The change could not be saved.');
    } finally {
      setBusy(false);
    }
  }

  async function addPost() {
    await run(async () => {
      await api(`/api/elections/${election.id}/posts`, {
        method: 'POST',
        token,
        body: { title: postTitle.trim(), description: postDescription.trim(), votingMode: postMode },
      });
      setPostTitle('');
      setPostDescription('');
      setPostMode('BOTH');
    }, 'Post added to the ballot.');
  }

  async function deletePost(post: ElectionPost) {
    Alert.alert('Delete Post', `Remove post "${post.title}" and its candidates?`, [
      { text: t('common.cancel'), style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: () =>
          run(async () => {
            await api(`/api/elections/${election.id}/posts/${post.id}`, {
              method: 'DELETE',
              token,
            });
          }, 'Post removed.'),
      },
    ]);
  }

  async function addPanel() {
    await run(async () => {
      await api(`/api/elections/${election.id}/panels`, {
        method: 'POST',
        token,
        body: {
          name: panelName.trim(),
          description: panelDescription.trim(),
          photoBase64: panelPhoto || undefined,
        },
      });
      setPanelName('');
      setPanelDescription('');
      setPanelPhoto(null);
    }, 'Panel added.');
  }

  async function deletePanel(panel: Panel) {
    Alert.alert('Delete Panel', `Remove panel "${panel.name}"? Candidates will remain as independents.`, [
      { text: t('common.cancel'), style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: () =>
          run(async () => {
            await api(`/api/elections/${election.id}/panels/${panel.id}`, {
              method: 'DELETE',
              token,
            });
          }, 'Panel removed.'),
      },
    ]);
  }

  async function addCandidate() {
    await run(async () => {
      await api(`/api/elections/${election.id}/candidates`, {
        method: 'POST',
        token,
        body: {
          postId: candidatePostId,
          name: candidateName.trim(),
          statement: candidateStatement.trim(),
          panelId: candidatePanelId || undefined,
          photoBase64: candidatePhoto || undefined,
        },
      });
      setCandidateName('');
      setCandidateStatement('');
      setCandidatePanelId('');
      setCandidatePhoto(null);
    }, 'Candidate added.');
  }

  async function deleteCandidate(candidate: Candidate) {
    Alert.alert('Delete Candidate', `Remove candidate "${candidate.name}" from ${candidate.postTitle}?`, [
      { text: t('common.cancel'), style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: () =>
          run(async () => {
            await api(`/api/elections/${election.id}/candidates/${candidate.id}`, {
              method: 'DELETE',
              token,
            });
          }, 'Candidate removed.'),
      },
    ]);
  }

  async function setCandidateStatus(candidate: Candidate, status: 'APPROVED' | 'REJECTED') {
    await run(async () => {
      await api(`/api/elections/${election.id}/candidates/${candidate.id}`, {
        method: 'PATCH',
        token,
        body: { status },
      });
    }, `Candidate ${status === 'APPROVED' ? 'approved' : 'rejected'}.`);
  }

  async function pickPhoto(target: 'panel' | 'candidate') {
    try {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) {
        Alert.alert('Photo permission needed', 'Allow photo library access to add a candidate or panel photo.');
        return;
      }
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        allowsEditing: true,
        aspect: [1, 1],
        quality: 0.55,
        base64: true,
      });
      if (result.canceled || !result.assets?.[0]?.base64) return;
      const asset = result.assets[0];
      const data = `data:${asset.mimeType || 'image/jpeg'};base64,${asset.base64}`;
      if (target === 'panel') setPanelPhoto(data);
      else setCandidatePhoto(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not open the photo library.');
    }
  }

  if (loading) {
    return (
      <Card>
        <Spinner label={t('common.loading')} />
      </Card>
    );
  }

  return (
    <>
      <Card>
        <SectionTitle title={t('admin.setup')} subtitle={locked ? t('admin.setup.locked') : undefined} />
        {error ? <Notice tone="danger">{error}</Notice> : null}
        {notice ? <Notice tone="success">{notice}</Notice> : null}
        <View style={styles.summaryRow}>
          <Badge label={`${posts.length} ${t('admin.stats.posts')}`} tone="teal" />
          <Badge label={`${panels.length} ${t('admin.setup.panels')}`} tone="info" />
          <Badge label={`${candidates.length} ${t('admin.stats.candidates')}`} tone="success" />
        </View>
      </Card>

      {!locked ? (
        <Card>
          <SectionTitle title={t('admin.setup.addPost')} />
          <Field label={t('admin.setup.postTitle')} value={postTitle} onChangeText={setPostTitle} placeholder="Chairperson" />
          <Field
            label={t('admin.description')}
            value={postDescription}
            onChangeText={setPostDescription}
            placeholder="What this post is responsible for"
          />
          <Text style={uiStyles.fieldLabel}>{t('admin.setup.votingMode')}</Text>
          <View style={styles.chipRow}>
            <Chip label={t('admin.setup.modeCandidate')} active={postMode === 'CANDIDATE'} onPress={() => setPostMode('CANDIDATE')} />
            <Chip label={t('admin.setup.modePanel')} active={postMode === 'PANEL'} onPress={() => setPostMode('PANEL')} />
            <Chip label={t('admin.setup.modeBoth')} active={postMode === 'BOTH'} onPress={() => setPostMode('BOTH')} />
          </View>
          <Button title={t('admin.setup.addPost')} onPress={addPost} loading={busy} disabled={!postTitle.trim()} />
        </Card>
      ) : null}

      <Card>
        <SectionTitle title={t('admin.setup.posts')} />
        {posts.length === 0 ? <Muted>{t('admin.setup.noPosts')}</Muted> : null}
        {posts.map((post, index) => (
          <View key={post.id} style={styles.itemRow}>
            <View style={styles.numberBadge}>
              <Text style={styles.numberText}>{index + 1}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.itemTitle}>{post.title}</Text>
              <Text style={uiStyles.muted}>{post.description || '—'}</Text>
              <Text style={styles.itemMeta}>
                {post.votingMode} · {post.candidateCount} candidates
              </Text>
            </View>
            {!locked ? (
              <Button title="Delete" kind="ghost" small onPress={() => deletePost(post)} />
            ) : null}
          </View>
        ))}
      </Card>

      {!locked ? (
        <Card>
          <SectionTitle title={t('admin.setup.addPanel')} />
          <Field label={t('admin.setup.panelName')} value={panelName} onChangeText={setPanelName} placeholder="Progressive Panel" />
          <Field
            label={t('admin.description')}
            value={panelDescription}
            onChangeText={setPanelDescription}
            placeholder="What the panel stands for"
            multiline
          />
          <PhotoField
            label={t('admin.setup.photo')}
            photo={panelPhoto}
            name={panelName || 'Panel'}
            onPick={() => pickPhoto('panel')}
            onClear={() => setPanelPhoto(null)}
          />
          <Button title={t('admin.setup.addPanel')} onPress={addPanel} loading={busy} disabled={!panelName.trim()} />
        </Card>
      ) : null}

      <Card>
        <SectionTitle title={t('admin.setup.panels')} />
        {panels.length === 0 ? <Muted>No panels yet.</Muted> : null}
        {panels.map((panel) => (
          <View key={panel.id} style={styles.panelCard}>
            <View style={styles.panelHead}>
              <Photo uri={panel.photoUrl} name={panel.name} size={44} />
              <View style={{ flex: 1 }}>
                <Text style={styles.itemTitle}>{panel.name}</Text>
                <Text style={uiStyles.muted}>{panel.description || '—'}</Text>
              </View>
              {!locked ? (
                <Button title="Delete" kind="ghost" small onPress={() => deletePanel(panel)} />
              ) : null}
            </View>
            {panel.candidates.length === 0 ? (
              <Muted>No candidates assigned to this panel yet.</Muted>
            ) : (
              panel.candidates.map((member) => (
                <Text key={member.id} style={styles.panelMember}>
                  {member.postTitle}: {member.name}
                </Text>
              ))
            )}
          </View>
        ))}
      </Card>

      {!locked ? (
        <Card>
          <SectionTitle title={t('admin.setup.addCandidate')} />
          <Text style={uiStyles.fieldLabel}>{t('admin.setup.post')}</Text>
          <View style={styles.chipRow}>
            {posts.map((post) => (
              <Chip
                key={post.id}
                label={post.title}
                active={candidatePostId === post.id}
                onPress={() => setCandidatePostId(post.id)}
              />
            ))}
          </View>
          <Field label={t('admin.setup.candidateName')} value={candidateName} onChangeText={setCandidateName} placeholder="Full name" />
          <Field
            label={t('admin.setup.statement')}
            value={candidateStatement}
            onChangeText={setCandidateStatement}
            placeholder="Why members should vote for this candidate"
            multiline
          />
          <Text style={uiStyles.fieldLabel}>{t('admin.setup.panelOptional')}</Text>
          <View style={styles.chipRow}>
            <Chip label={t('common.none')} active={!candidatePanelId} onPress={() => setCandidatePanelId('')} />
            {panels.map((panel) => (
              <Chip
                key={panel.id}
                label={panel.name}
                active={candidatePanelId === panel.id}
                onPress={() => setCandidatePanelId(panel.id)}
              />
            ))}
          </View>
          <PhotoField
            label={t('admin.setup.photo')}
            photo={candidatePhoto}
            name={candidateName || 'Candidate'}
            onPick={() => pickPhoto('candidate')}
            onClear={() => setCandidatePhoto(null)}
          />
          <Button
            title={t('admin.setup.addCandidate')}
            onPress={addCandidate}
            loading={busy}
            disabled={!candidatePostId || !candidateName.trim()}
          />
        </Card>
      ) : null}

      <Card>
        <SectionTitle title={t('admin.setup.candidates')} />
        {candidates.length === 0 ? <Muted>No candidates yet.</Muted> : null}
        {candidates.map((candidate) => (
          <View key={candidate.id} style={styles.candidateRow}>
            <Photo uri={candidate.photoUrl} name={candidate.name} size={46} />
            <View style={{ flex: 1 }}>
              <Text style={styles.itemTitle}>{candidate.name}</Text>
              <Text style={styles.itemMeta}>
                {candidate.postTitle}
                {candidate.panelName ? ` · ${candidate.panelName}` : ''}
              </Text>
              {candidate.statement ? <Text style={uiStyles.muted}>{candidate.statement}</Text> : null}
              <View style={styles.candidateActions}>
                <Badge
                  label={candidate.status}
                  tone={candidate.status === 'APPROVED' ? 'success' : candidate.status === 'PENDING' ? 'warning' : 'danger'}
                />
                {!locked && candidate.status !== 'APPROVED' ? (
                  <Button title="Approve" kind="success" small onPress={() => setCandidateStatus(candidate, 'APPROVED')} />
                ) : null}
                {!locked && candidate.status === 'APPROVED' ? (
                  <Button title="Reject" kind="ghost" small onPress={() => setCandidateStatus(candidate, 'REJECTED')} />
                ) : null}
                {!locked ? (
                  <Button title="Delete" kind="ghost" small onPress={() => deleteCandidate(candidate)} />
                ) : null}
              </View>
            </View>
          </View>
        ))}
      </Card>
    </>
  );
}

function PhotoField({
  label,
  photo,
  name,
  onPick,
  onClear,
}: {
  label: string;
  photo: string | null;
  name: string;
  onPick: () => void;
  onClear: () => void;
}) {
  const { t } = useLanguage();
  return (
    <View style={styles.photoField}>
      <Photo uri={photo} name={name} size={58} />
      <View style={{ flex: 1 }}>
        <Text style={uiStyles.fieldLabel}>{label}</Text>
        <View style={styles.photoActions}>
          <Button title={t('admin.setup.choosePhoto')} kind="secondary" small onPress={onPick} />
          {photo ? <Button title={t('admin.setup.removePhoto')} kind="ghost" small onPress={onClear} /> : null}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  summaryRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  itemRow: { flexDirection: 'row', gap: 11, paddingVertical: 11, borderBottomWidth: 1, borderBottomColor: theme.border, alignItems: 'center' },
  numberBadge: { width: 28, height: 28, borderRadius: 9, backgroundColor: theme.tealSoft, alignItems: 'center', justifyContent: 'center' },
  numberText: { fontSize: 13, fontWeight: '900', color: theme.tealDark },
  itemTitle: { fontSize: 15, fontWeight: '900', color: theme.navy },
  itemMeta: { fontSize: 11, fontWeight: '700', color: theme.teal, marginTop: 3 },
  panelCard: { borderWidth: 1, borderColor: theme.border, borderRadius: 16, padding: 12, gap: 8, backgroundColor: '#fbfdfd' },
  panelHead: { flexDirection: 'row', gap: 10, alignItems: 'center' },
  panelMember: { fontSize: 12, color: theme.text },
  candidateRow: { flexDirection: 'row', gap: 11, paddingVertical: 11, borderBottomWidth: 1, borderBottomColor: theme.border, alignItems: 'flex-start' },
  candidateActions: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 7, flexWrap: 'wrap' },
  photoField: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 4 },
  photoActions: { flexDirection: 'row', gap: 6, flexWrap: 'wrap', marginTop: 5 },
});
