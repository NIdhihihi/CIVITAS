import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type { BallotPanel, BallotPost, VoteSelection } from './types';
import { useLanguage } from './i18n';
import {
  Badge,
  Button,
  Card,
  ChoiceCard,
  Notice,
  Photo,
  SectionTitle,
  theme,
} from './ui';

interface Props {
  posts: BallotPost[];
  panels: BallotPanel[];
  selections: Record<string, VoteSelection>;
  onSelect?: (selection: VoteSelection) => void;
  onApplyPanel?: (panel: BallotPanel) => void;
  readOnly?: boolean;
}

export function BallotView({ posts, panels, selections, onSelect, onApplyPanel, readOnly }: Props) {
  const { t } = useLanguage();

  if (posts.length === 0) {
    return (
      <Notice tone="warning">
        This election has no posts yet. Ask the administrator to finish the ballot setup.
      </Notice>
    );
  }

  return (
    <View style={{ gap: 14 }}>
      {panels.length > 0 && !readOnly ? (
        <Card>
          <SectionTitle title={t('vote.selectFullPanel')} subtitle={t('vote.panel')} />
          {panels.map((panel) => (
            <View key={panel.id} style={styles.panelBox}>
              <View style={styles.panelHead}>
                <Photo uri={panel.photoUrl} name={panel.name} size={44} />
                <View style={{ flex: 1 }}>
                  <Text style={styles.panelName}>{panel.name}</Text>
                  {panel.description ? <Text style={styles.panelDescription}>{panel.description}</Text> : null}
                </View>
              </View>
              <Text style={styles.panelMembers}>
                {panel.members.map((member) => `${member.postTitle}: ${member.candidateName}`).join(' · ')}
              </Text>
              <Button
                title={t('vote.selectFullPanel')}
                kind="secondary"
                small
                onPress={() => onApplyPanel?.(panel)}
              />
            </View>
          ))}
        </Card>
      ) : null}

      {posts.map((post, index) => {
        const selected = selections[post.id];
        const panelOptions = panels.filter((panel) =>
          panel.members.some((member) => member.postId === post.id),
        );
        const showCandidates = post.votingMode !== 'PANEL';
        const showPanels = post.votingMode !== 'CANDIDATE' && panelOptions.length > 0;
        const hasOptions = (showCandidates && post.candidates.length > 0) || (showPanels && panelOptions.length > 0);

        return (
          <Card key={post.id}>
            <View style={styles.postHead}>
              <View style={styles.postNumber}>
                <Text style={styles.postNumberText}>{index + 1}</Text>
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.postTitle}>{post.title}</Text>
                {post.description ? <Text style={styles.postDescription}>{post.description}</Text> : null}
              </View>
              <Badge
                label={
                  post.votingMode === 'BOTH'
                    ? 'CANDIDATE / PANEL'
                    : post.votingMode === 'PANEL'
                      ? 'PANEL'
                      : 'CANDIDATE'
                }
                tone={post.votingMode === 'PANEL' ? 'teal' : 'neutral'}
              />
            </View>

            {!hasOptions ? (
              <Notice tone="info">No approved candidates or panels are contesting this post.</Notice>
            ) : null}

            {showCandidates ? (
              <View style={{ gap: 9 }}>
                {post.candidates.length === 0 && showPanels ? null : post.candidates.length === 0 ? null : (
                  post.candidates.map((candidate) => {
                    const isSelected = selected?.candidateId === candidate.id;
                    return (
                      <ChoiceCard
                        key={candidate.id}
                        selected={isSelected}
                        disabled={readOnly}
                        onPress={() => onSelect?.({ postId: post.id, candidateId: candidate.id })}
                      >
                        <View style={styles.candidateRow}>
                          <Photo uri={candidate.photoUrl} name={candidate.name} size={48} />
                          <View style={{ flex: 1 }}>
                            <Text style={styles.candidateName}>{candidate.name}</Text>
                            {candidate.panelName ? (
                              <Text style={styles.candidatePanel}>{candidate.panelName}</Text>
                            ) : null}
                            {candidate.statement ? (
                              <Text style={styles.candidateStatement}>{candidate.statement}</Text>
                            ) : null}
                          </View>
                        </View>
                      </ChoiceCard>
                    );
                  })
                )}
              </View>
            ) : null}

            {showPanels ? (
              <View style={{ gap: 9, marginTop: showCandidates && post.candidates.length > 0 ? 6 : 0 }}>
                <Text style={styles.optionLabel}>{t('vote.panelForPost')}</Text>
                {panelOptions.map((panel) => {
                  const member = panel.members.find((m) => m.postId === post.id);
                  const isSelected = selected?.panelId === panel.id;
                  return (
                    <ChoiceCard
                      key={panel.id}
                      selected={isSelected}
                      disabled={readOnly}
                      onPress={() => onSelect?.({ postId: post.id, panelId: panel.id })}
                    >
                      <View style={styles.candidateRow}>
                        <Photo uri={member?.photoUrl || panel.photoUrl} name={member?.candidateName || panel.name} size={48} />
                        <View style={{ flex: 1 }}>
                          <Text style={styles.candidateName}>{panel.name}</Text>
                          <Text style={styles.candidatePanel}>
                            {member ? `${post.title}: ${member.candidateName}` : panel.description}
                          </Text>
                        </View>
                      </View>
                    </ChoiceCard>
                  );
                })}
              </View>
            ) : null}

            {!readOnly && !selected && hasOptions ? (
              <Text style={styles.pending}>{t('vote.selectOne')}</Text>
            ) : null}
          </Card>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  postHead: { flexDirection: 'row', alignItems: 'flex-start', gap: 10 },
  postNumber: {
    width: 30,
    height: 30,
    borderRadius: 10,
    backgroundColor: theme.teal,
    alignItems: 'center',
    justifyContent: 'center',
  },
  postNumberText: { color: theme.white, fontSize: 14, fontWeight: '900' },
  postTitle: { fontSize: 18, fontWeight: '900', color: theme.navy },
  postDescription: { fontSize: 12, lineHeight: 18, color: theme.muted, marginTop: 3 },
  candidateRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  candidateName: { fontSize: 15, fontWeight: '800', color: theme.text },
  candidatePanel: { fontSize: 11, fontWeight: '700', color: theme.teal, marginTop: 2 },
  candidateStatement: { fontSize: 12, lineHeight: 18, color: theme.muted, marginTop: 4 },
  optionLabel: { fontSize: 10, fontWeight: '900', letterSpacing: 0.9, color: theme.teal, textTransform: 'uppercase' },
  pending: { fontSize: 11, color: theme.warning, fontWeight: '700' },
  panelBox: {
    borderWidth: 1,
    borderColor: theme.border,
    borderRadius: 17,
    padding: 13,
    gap: 10,
    backgroundColor: '#fbfdfd',
  },
  panelHead: { flexDirection: 'row', alignItems: 'center', gap: 11 },
  panelName: { fontSize: 15, fontWeight: '900', color: theme.navy },
  panelDescription: { fontSize: 12, lineHeight: 18, color: theme.muted, marginTop: 2 },
  panelMembers: { fontSize: 12, lineHeight: 18, color: theme.text },
});
