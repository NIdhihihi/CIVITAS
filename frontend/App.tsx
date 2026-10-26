import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { StatusBar } from 'expo-status-bar';

const API_URL = (process.env.EXPO_PUBLIC_API_URL || 'http://10.0.2.2:8080').replace(/\/$/, '');

type Portal = 'resident' | 'candidate' | 'admin';
type Screen =
  | 'auth'
  | 'home'
  | 'tenant'
  | 'candidate-dashboard'
  | 'candidate-form'
  | 'elections'
  | 'election-create'
  | 'election-detail'
  | 'ballot'
  | 'vote-receipt'
  | 'verify-receipt'
  | 'audit';

type Member = { id: string; email: string; fullName: string; role: string; emailVerified: boolean };
type Tenant = { id: string; name: string; code: string; city: string; createdBy: string };
type Candidate = { id: string; tenantId: string; tenantName: string; memberEmail: string; fullName: string; contestingPost: string; statement: string; phone?: string; status: string; registeredAt: string };

type ElectionStatus = 'DRAFT' | 'CONFIGURED' | 'PUBLISHED' | 'VOTING_OPEN' | 'VOTING_CLOSED' | 'RESULTS_DECLARED' | 'ARCHIVED';

type Election = {
  id: string;
  tenantId: string;
  tenantName: string;
  title: string;
  description: string;
  status: ElectionStatus;
  votingStartTime?: string;
  votingEndTime?: string;
  createdByEmail: string;
  createdAt: string;
  totalVoters: number;
  eligibleVoters: number;
  votesCast: number;
};

type ElectionCandidate = {
  id: string;
  electionId: string;
  postId: string;
  postTitle: string;
  panelId?: string;
  panelName?: string;
  fullName: string;
  email: string;
  phone?: string;
  statement?: string;
  photoUrl?: string;
  status: string;
  registeredAt: string;
};

type ElectionPost = {
  id: string;
  electionId: string;
  title: string;
  description?: string;
  maxSelections: number;
  displayOrder: number;
  candidates: ElectionCandidate[];
};

type ElectionPanel = {
  id: string;
  electionId: string;
  name: string;
  motto?: string;
  colorHex?: string;
};

type VoterRollMember = {
  id: string;
  electionId: string;
  flatNumber: string;
  fullName: string;
  email: string;
  phoneNumber?: string;
  aadhaarReference?: string;
  eligible: boolean;
  disqualificationReason?: string;
  hasVoted: boolean;
  votedAt?: string;
};

type ElectionDetail = {
  election: Election;
  posts: ElectionPost[];
  panels: ElectionPanel[];
  voterRoll: VoterRollMember[];
};

type VoterRollUploadResult = {
  totalRowsParsed: number;
  addedCount: number;
  duplicateFlatsCount: number;
  duplicateEmailsCount: number;
  errors: string[];
};

type VoterBallotResponse = {
  electionId: string;
  title: string;
  societyName: string;
  status: string;
  isEligible: boolean;
  hasVoted: boolean;
  disqualificationReason?: string;
  posts: ElectionPost[];
  panels: ElectionPanel[];
};

type VoteReceiptResponse = {
  receiptCode: string;
  ballotHash: string;
  electionId: string;
  electionTitle: string;
  societyName: string;
  castAt: string;
  message: string;
};

type ReceiptVerifyResponse = {
  verified: boolean;
  receiptCode: string;
  ballotHash: string;
  electionTitle: string;
  societyName: string;
  castAt: string;
  isPanelVote: boolean;
  panelName?: string;
};

type AuditLogResponse = {
  sequenceNumber: number;
  electionId?: string;
  tenantId?: string;
  eventType: string;
  actor: string;
  actionDetails?: string;
  ipAddress?: string;
  deviceFingerprint?: string;
  timestamp: string;
  previousHash: string;
  entryHash: string;
};

type ChainIntegrityResponse = {
  valid: boolean;
  totalBlocks: number;
  latestHash: string;
  genesisHash: string;
  message: string;
  tamperedAtSequence?: number;
};

const DEFAULT_POSTS = ['Chairperson', 'Secretary', 'Treasurer', 'Committee Member'];

async function api<T>(path: string, method = 'GET', body?: unknown, token?: string): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch(`${API_URL}${path}`, {
      method,
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {})
    });
    const raw = await response.text();
    let data: any = {};
    try {
      data = raw ? JSON.parse(raw) : {};
    } catch {
      data = { message: raw };
    }
    if (!response.ok) {
      const serverMessage = data.detail || data.message || data.error || data.title;
      throw new Error(serverMessage || `Request failed (${response.status})`);
    }
    return data as T;
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw new Error(`The server at ${API_URL} did not respond in time.`);
    if (error instanceof TypeError) throw new Error(`Can't reach CIVITAS backend at ${API_URL}. Check if the server is running.`);
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export default function App() {
  const [token, setToken] = useState('');
  const [member, setMember] = useState<Member | null>(null);
  const [booting, setBooting] = useState(true);
  const [screen, setScreen] = useState<Screen>('auth');
  const [portal, setPortal] = useState<Portal>('resident');
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [otp, setOtp] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');

  // Societies
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [availableTenants, setAvailableTenants] = useState<Tenant[]>([]);
  const [tenantName, setTenantName] = useState('');
  const [tenantCode, setTenantCode] = useState('');
  const [city, setCity] = useState('');

  // Applications
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [adminApplications, setAdminApplications] = useState<Candidate[]>([]);
  const [tenantId, setTenantId] = useState('');
  const [post, setPost] = useState(DEFAULT_POSTS[0]);
  const [statement, setStatement] = useState('');
  const [phone, setPhone] = useState('');
  const [candidateStep, setCandidateStep] = useState(1);

  // Elections (Phase 1)
  const [elections, setElections] = useState<Election[]>([]);
  const [activeElection, setActiveElection] = useState<ElectionDetail | null>(null);
  const [electionTitle, setElectionTitle] = useState('');
  const [electionDesc, setElectionDesc] = useState('');
  const [electionTenantId, setElectionTenantId] = useState('');
  const [electionStartTime, setElectionStartTime] = useState('');
  const [electionEndTime, setElectionEndTime] = useState('');
  const [detailTab, setDetailTab] = useState<'posts' | 'panels' | 'candidates' | 'voters'>('posts');

  // Posts & Panels Management
  const [newPostTitle, setNewPostTitle] = useState('');
  const [newPostDesc, setNewPostDesc] = useState('');
  const [newPostMaxSel, setNewPostMaxSel] = useState('1');
  const [newPanelName, setNewPanelName] = useState('');
  const [newPanelMotto, setNewPanelMotto] = useState('');
  const [newPanelColor, setNewPanelColor] = useState('#0e7490');

  // Candidate Nominations in Election
  const [nomPostId, setNomPostId] = useState('');
  const [nomPanelId, setNomPanelId] = useState('');
  const [nomName, setNomName] = useState('');
  const [nomEmail, setNomEmail] = useState('');
  const [nomPhone, setNomPhone] = useState('');
  const [nomStatement, setNomStatement] = useState('');
  const [nomPhoto, setNomPhoto] = useState('');

  // Voter Roll Upload & Single Add
  const [voterCsv, setVoterCsv] = useState('');
  const [uploadResult, setUploadResult] = useState<VoterRollUploadResult | null>(null);
  const [voterFlat, setVoterFlat] = useState('');
  const [voterName, setVoterName] = useState('');
  const [voterEmail, setVoterEmail] = useState('');
  const [voterPhone, setVoterPhone] = useState('');
  const [voterAadhaar, setVoterAadhaar] = useState('');

  // Phase 2: Voting & Receipt Verification
  const [activeBallot, setActiveBallot] = useState<VoterBallotResponse | null>(null);
  const [selectedPanelId, setSelectedPanelId] = useState<string>('');
  const [selectedCandidates, setSelectedCandidates] = useState<Record<string, string[]>>({});
  const [dualFactorConfirmed, setDualFactorConfirmed] = useState(false);
  const [voteReceipt, setVoteReceipt] = useState<VoteReceiptResponse | null>(null);
  const [verifyCodeInput, setVerifyCodeInput] = useState('');
  const [verifiedReceipt, setVerifiedReceipt] = useState<ReceiptVerifyResponse | null>(null);

  // Phase 3: Cryptographic Audit Trail & Compliance
  const [auditLogs, setAuditLogs] = useState<AuditLogResponse[]>([]);
  const [chainIntegrity, setChainIntegrity] = useState<ChainIntegrityResponse | null>(null);
  const [auditFilterType, setAuditFilterType] = useState('ALL');
  const [auditSearch, setAuditSearch] = useState('');

  useEffect(() => {
    (async () => {
      const saved = await AsyncStorage.getItem('civitas_token');
      if (saved) {
        try {
          const profile = await api<Member>('/api/auth/me', 'GET', undefined, saved);
          const savedPortal = await AsyncStorage.getItem('civitas_portal');
          const isCandidate = profile.role === 'CANDIDATE' || savedPortal === 'candidate';
          setToken(saved);
          setEmail(profile.email);
          setMember(profile);
          setPortal(isCandidate ? 'candidate' : profile.role === 'PLATFORM_ADMIN' ? 'admin' : 'resident');
          setScreen(isCandidate ? 'candidate-dashboard' : 'home');
        } catch {
          await AsyncStorage.removeItem('civitas_token');
          await AsyncStorage.removeItem('civitas_portal');
        }
      }
      setBooting(false);
    })();
  }, []);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setNotice('');
    try {
      await action();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Something went wrong. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  async function requestOtp() {
    await run(async () => {
      const result = await api<{ developmentOtp?: string }>('/api/auth/register', 'POST', {
        email: email.trim(),
        fullName: fullName.trim(),
        password
      });
      setOtpSent(true);
      if (result.developmentOtp) setOtp(result.developmentOtp);
      setNotice(result.developmentOtp ? 'Development code filled in.' : 'Verification code sent to your email.');
    });
  }

  async function verifyOtp() {
    await run(async () => {
      const result = await api<{ token: string; member: Member }>('/api/auth/verify-email', 'POST', {
        email: email.trim(),
        code: otp.trim()
      });
      await AsyncStorage.setItem('civitas_token', result.token);
      await AsyncStorage.setItem('civitas_portal', portal);
      setToken(result.token);
      setMember(result.member);
      setScreen(portal === 'candidate' ? 'candidate-dashboard' : 'home');
      setOtpSent(false);
      setOtp('');
      setNotice('Email verified successfully.');
    });
  }

  async function login() {
    await run(async () => {
      const path = portal === 'admin' ? '/api/auth/admin/login' : '/api/auth/login';
      const result = await api<{ token: string; member: Member }>(path, 'POST', { email: email.trim(), password });
      await AsyncStorage.setItem('civitas_token', result.token);
      await AsyncStorage.setItem('civitas_portal', portal);
      setToken(result.token);
      setMember(result.member);
      setScreen(portal === 'candidate' ? 'candidate-dashboard' : 'home');
      setNotice('Signed in successfully.');
    });
  }

  async function loadResidentData() {
    if (!token) return;
    await run(async () => {
      const [societies, available, applications] = await Promise.all([
        api<Tenant[]>('/api/tenants/mine', 'GET', undefined, token),
        api<Tenant[]>('/api/tenants', 'GET', undefined, token),
        api<Candidate[]>('/api/candidates/mine', 'GET', undefined, token)
      ]);
      setTenants(societies);
      setAvailableTenants(available);
      setCandidates(applications);

      if (societies.length > 0) {
        const electionLists = await Promise.all(
          societies.map(s => api<Election[]>(`/api/elections/tenant/${s.id}`, 'GET', undefined, token).catch(() => []))
        );
        setElections(electionLists.flat());
      }
    });
  }

  async function loadAdminData() {
    if (!token) return;
    await run(async () => {
      const [societies, applications] = await Promise.all([
        api<Tenant[]>('/api/tenants/mine', 'GET', undefined, token),
        api<Candidate[]>('/api/candidates/admin', 'GET', undefined, token)
      ]);
      setTenants(societies);
      setAdminApplications(applications);
      if (societies.length > 0 && !electionTenantId) {
        setElectionTenantId(societies[0].id);
      }
      if (societies.length > 0) {
        const electionLists = await Promise.all(
          societies.map(s => api<Election[]>(`/api/elections/tenant/${s.id}`, 'GET', undefined, token).catch(() => []))
        );
        setElections(electionLists.flat());
      }
    });
  }

  async function loadAuditData() {
    if (!token) return;
    await run(async () => {
      const [logs, integrity] = await Promise.all([
        api<AuditLogResponse[]>('/api/audit/logs', 'GET', undefined, token),
        api<ChainIntegrityResponse>('/api/audit/verify', 'GET', undefined, token)
      ]);
      setAuditLogs(logs);
      setChainIntegrity(integrity);
    });
  }

  async function loadCandidateData() {
    if (!token) return;
    await run(async () => {
      const [societies, applications] = await Promise.all([
        api<Tenant[]>('/api/tenants', 'GET', undefined, token),
        api<Candidate[]>('/api/candidates/mine', 'GET', undefined, token)
      ]);
      setTenants(societies);
      setCandidates(applications);
      if (!tenantId && societies.length) setTenantId(societies[0].id);
    });
  }

  useEffect(() => {
    if (token && screen === 'home' && member?.role === 'PLATFORM_ADMIN') loadAdminData();
    else if (token && screen === 'home') loadResidentData();
    if (token && (screen === 'candidate-dashboard' || screen === 'candidate-form')) loadCandidateData();
    if (token && screen === 'elections') loadAdminData();
    if (token && screen === 'audit') loadAuditData();
  }, [token, screen]);

  // --- Phase 1: Election Admin Operations ---
  async function createElection() {
    if (!electionTenantId || !electionTitle.trim()) return;
    await run(async () => {
      const created = await api<Election>('/api/elections', 'POST', {
        tenantId: electionTenantId,
        title: electionTitle.trim(),
        description: electionDesc.trim() || undefined,
        votingStartTime: electionStartTime.trim() || undefined,
        votingEndTime: electionEndTime.trim() || undefined
      }, token);
      setElections(prev => [created, ...prev]);
      setElectionTitle('');
      setElectionDesc('');
      setElectionStartTime('');
      setElectionEndTime('');
      setNotice(`Election "${created.title}" created in DRAFT status.`);
      openElectionDetail(created.id);
    });
  }

  async function openElectionDetail(electionId: string) {
    await run(async () => {
      const detail = await api<ElectionDetail>(`/api/elections/${electionId}`, 'GET', undefined, token);
      setActiveElection(detail);
      setScreen('election-detail');
      if (detail.posts.length > 0) setNomPostId(detail.posts[0].id);
      if (detail.panels.length > 0) setNomPanelId(detail.panels[0].id);
    });
  }

  async function changeElectionStatus(newStatus: ElectionStatus) {
    if (!activeElection) return;
    await run(async () => {
      const updated = await api<Election>(`/api/elections/${activeElection.election.id}/status`, 'PATCH', {
        status: newStatus
      }, token);
      setActiveElection(prev => prev ? { ...prev, election: updated } : null);
      setNotice(`Election status advanced to ${newStatus}.`);
    });
  }

  async function addPost() {
    if (!activeElection || !newPostTitle.trim()) return;
    await run(async () => {
      const created = await api<ElectionPost>(`/api/elections/${activeElection.election.id}/posts`, 'POST', {
        title: newPostTitle.trim(),
        description: newPostDesc.trim() || undefined,
        maxSelections: parseInt(newPostMaxSel, 10) || 1,
        displayOrder: activeElection.posts.length
      }, token);
      setActiveElection(prev => prev ? { ...prev, posts: [...prev.posts, created] } : null);
      setNewPostTitle('');
      setNewPostDesc('');
      setNewPostMaxSel('1');
      setNotice(`Post "${created.title}" added to election.`);
    });
  }

  async function addPanel() {
    if (!activeElection || !newPanelName.trim()) return;
    await run(async () => {
      const created = await api<ElectionPanel>(`/api/elections/${activeElection.election.id}/panels`, 'POST', {
        name: newPanelName.trim(),
        motto: newPanelMotto.trim() || undefined,
        colorHex: newPanelColor.trim() || '#0e7490'
      }, token);
      setActiveElection(prev => prev ? { ...prev, panels: [...prev.panels, created] } : null);
      setNewPanelName('');
      setNewPanelMotto('');
      setNotice(`Panel "${created.name}" registered.`);
    });
  }

  async function nominateCandidateForElection() {
    if (!activeElection || !nomPostId || !nomName.trim() || !nomEmail.trim()) return;
    await run(async () => {
      const created = await api<ElectionCandidate>(`/api/elections/${activeElection.election.id}/candidates`, 'POST', {
        postId: nomPostId,
        panelId: nomPanelId || undefined,
        fullName: nomName.trim(),
        email: nomEmail.trim(),
        phone: nomPhone.trim() || undefined,
        statement: nomStatement.trim() || undefined,
        photoUrl: nomPhoto.trim() || undefined
      }, token);
      const refreshed = await api<ElectionDetail>(`/api/elections/${activeElection.election.id}`, 'GET', undefined, token);
      setActiveElection(refreshed);
      setNomName('');
      setNomEmail('');
      setNomPhone('');
      setNomStatement('');
      setNomPhoto('');
      setNotice(`Candidate "${created.fullName}" nominated for ${created.postTitle}.`);
    });
  }

  async function updateCandidateStatus(candidateId: string, status: 'APPROVED' | 'REJECTED') {
    if (!activeElection) return;
    await run(async () => {
      await api<ElectionCandidate>(`/api/elections/candidates/${candidateId}/status`, 'PATCH', { status }, token);
      const refreshed = await api<ElectionDetail>(`/api/elections/${activeElection.election.id}`, 'GET', undefined, token);
      setActiveElection(refreshed);
      setNotice(`Nomination marked as ${status}.`);
    });
  }

  async function uploadVoterRoll() {
    if (!activeElection || !voterCsv.trim()) return;
    await run(async () => {
      const result = await api<VoterRollUploadResult>(`/api/elections/${activeElection.election.id}/voter-roll/upload`, 'POST', {
        csvData: voterCsv.trim()
      }, token);
      setUploadResult(result);
      const refreshed = await api<ElectionDetail>(`/api/elections/${activeElection.election.id}`, 'GET', undefined, token);
      setActiveElection(refreshed);
      setNotice(`Voter roll processed: ${result.addedCount} added, ${result.duplicateFlatsCount + result.duplicateEmailsCount} duplicates flagged.`);
    });
  }

  async function addIndividualVoter() {
    if (!activeElection || !voterFlat.trim() || !voterName.trim() || !voterEmail.trim()) return;
    await run(async () => {
      const added = await api<VoterRollMember>(`/api/elections/${activeElection.election.id}/voter-roll`, 'POST', {
        flatNumber: voterFlat.trim(),
        fullName: voterName.trim(),
        email: voterEmail.trim(),
        phoneNumber: voterPhone.trim() || undefined,
        aadhaarReference: voterAadhaar.trim() || undefined,
        eligible: true
      }, token);
      setActiveElection(prev => prev ? {
        ...prev,
        voterRoll: [...prev.voterRoll, added],
        election: { ...prev.election, totalVoters: prev.election.totalVoters + 1, eligibleVoters: prev.election.eligibleVoters + 1 }
      } : null);
      setVoterFlat('');
      setVoterName('');
      setVoterEmail('');
      setVoterPhone('');
      setVoterAadhaar('');
      setNotice(`Voter for Flat ${added.flatNumber} added.`);
    });
  }

  function insertSampleCsv() {
    const sample = `flatNumber,fullName,email,phone,aadhaarRef,eligible
A-101,Rajesh Sharma,rajesh.sharma@example.com,+919876543210,XXXX-XXXX-1122,true
A-102,Priya Patel,priya.patel@example.com,+919876543211,XXXX-XXXX-3344,true
B-201,Devank Kolpe,devank.kolpe24@spit.ac.in,+919876543212,XXXX-XXXX-5566,true
B-202,Nidhi Mahadik,nidhi.mahadik24@spit.ac.in,+919876543213,XXXX-XXXX-7788,true
C-301,Amit Deshmukh,amit.deshmukh@example.com,+919876543214,XXXX-XXXX-9900,false`;
    setVoterCsv(sample);
  }

  // --- Phase 2: Visual Ballot & Secret Voting Engine ---
  async function openBallot(electionId: string) {
    await run(async () => {
      const ballotData = await api<VoterBallotResponse>(`/api/elections/${electionId}/ballot`, 'GET', undefined, token);
      setActiveBallot(ballotData);
      setSelectedPanelId('');
      setSelectedCandidates({});
      setDualFactorConfirmed(false);
      setScreen('ballot');
    });
  }

  function toggleCandidateSelection(postId: string, candidateId: string, maxSelections: number) {
    setSelectedPanelId('');
    setSelectedCandidates(prev => {
      const current = prev[postId] || [];
      const exists = current.includes(candidateId);
      if (exists) {
        return { ...prev, [postId]: current.filter(id => id !== candidateId) };
      }
      if (current.length >= maxSelections) {
        if (maxSelections === 1) return { ...prev, [postId]: [candidateId] };
        return prev;
      }
      return { ...prev, [postId]: [...current, candidateId] };
    });
  }

  function selectPanelVote(panelId: string) {
    if (!activeBallot) return;
    if (selectedPanelId === panelId) {
      setSelectedPanelId('');
      setSelectedCandidates({});
      return;
    }
    setSelectedPanelId(panelId);
    const autoMap: Record<string, string[]> = {};
    for (const p of activeBallot.posts) {
      const match = p.candidates.find(c => c.panelId === panelId);
      if (match) autoMap[p.id] = [match.id];
    }
    setSelectedCandidates(autoMap);
  }

  async function submitSecretBallot() {
    if (!activeBallot || !dualFactorConfirmed) return;
    await run(async () => {
      const selectionsPayload = Object.entries(selectedCandidates)
        .filter(([_, list]) => list.length > 0)
        .map(([postId, candidateIds]) => ({ postId, candidateIds }));

      const receipt = await api<VoteReceiptResponse>(`/api/elections/${activeBallot.electionId}/vote`, 'POST', {
        panelId: selectedPanelId || undefined,
        selections: selectionsPayload,
        dualFactorConfirmed: true
      }, token);

      setVoteReceipt(receipt);
      setScreen('vote-receipt');
      loadResidentData();
    });
  }

  async function verifyReceipt(code?: string) {
    const targetCode = (code || verifyCodeInput).trim().toUpperCase();
    if (!targetCode) return;
    await run(async () => {
      const res = await api<ReceiptVerifyResponse>(`/api/elections/receipts/${targetCode}`, 'GET');
      setVerifiedReceipt(res);
      setScreen('verify-receipt');
    });
  }

  async function runChainVerification() {
    await run(async () => {
      const integrity = await api<ChainIntegrityResponse>('/api/audit/verify', 'GET', undefined, token);
      setChainIntegrity(integrity);
      setNotice(integrity.valid ? `✓ Blockchain Integrity Confirmed: All ${integrity.totalBlocks} blocks valid.` : `⚠ Warning: ${integrity.message}`);
    });
  }

  async function createTenant() {
    await run(async () => {
      const tenant = await api<Tenant>('/api/tenants', 'POST', {
        name: tenantName.trim(),
        code: tenantCode.trim(),
        city: city.trim()
      }, token);
      setTenants(old => [tenant, ...old]);
      setTenantName('');
      setTenantCode('');
      setCity('');
      setNotice(`${tenant.name} was created.`);
      setScreen('home');
    });
  }

  async function joinSociety(id: string) {
    await run(async () => {
      const joined = await api<Tenant>(`/api/tenants/${id}/join`, 'POST', {}, token);
      setTenants(old => old.some(item => item.id === joined.id) ? old : [joined, ...old]);
      setNotice(`You joined ${joined.name}.`);
    });
  }

  async function logout() {
    await AsyncStorage.removeItem('civitas_token');
    await AsyncStorage.removeItem('civitas_portal');
    setToken('');
    setMember(null);
    setScreen('auth');
    setPortal('resident');
    setOtpSent(false);
    setNotice('You have signed out.');
  }

  if (booting) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#0e7490" />
        <Text style={styles.muted}>Loading CIVITAS…</Text>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <StatusBar style="dark" />
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">

        {/* 1. AUTH SCREEN */}
        {screen === 'auth' ? (
          <>
            <Brand />
            <View style={styles.authHero}>
              <Text style={styles.eyebrow}>CIVITAS ELECTIONS</Text>
              <Text style={styles.authTitle}>Secure housing elections.</Text>
              <Text style={styles.muted}>Sign in or select your portal to continue.</Text>
            </View>
            {!!notice && <Text style={styles.notice}>{notice}</Text>}
            <View style={styles.card}>
              <Text style={styles.fieldLabel}>CONTINUE AS</Text>
              <View style={styles.portalRow}>
                {([['resident', 'Resident'], ['candidate', 'Candidate'], ['admin', 'Admin']] as [Portal, string][]).map(([key, label]) => (
                  <Pressable
                    key={key}
                    onPress={() => {
                      setPortal(key);
                      setAuthMode('login');
                      setOtpSent(false);
                      setOtp('');
                      setNotice('');
                    }}
                    style={[styles.portalButton, portal === key && styles.portalButtonActive]}
                  >
                    <Text style={[styles.portalText, portal === key && styles.portalTextActive]}>{label}</Text>
                  </Pressable>
                ))}
              </View>
              <Text style={styles.sectionTitle}>
                {otpSent ? 'Verify your email' : portal === 'admin' ? 'Administrator sign in' : authMode === 'login' ? 'Sign in' : 'Create account'}
              </Text>
              {!otpSent ? (
                <>
                  {portal !== 'admin' && authMode === 'register' && (
                    <Field label="Full name" value={fullName} onChangeText={setFullName} placeholder="Your full name" />
                  )}
                  <Field label="Email address" value={email} onChangeText={setEmail} placeholder="you@example.com" keyboardType="email-address" autoCapitalize="none" />
                  <Field label="Password" value={password} onChangeText={setPassword} placeholder="At least 8 characters" secureTextEntry />
                  <Button
                    title={portal === 'admin' ? 'Sign in as administrator' : authMode === 'login' ? 'Sign in' : 'Create account'}
                    onPress={portal === 'admin' ? login : authMode === 'login' ? login : requestOtp}
                    disabled={busy || !email.includes('@') || password.length < 8 || (portal !== 'admin' && authMode === 'register' && !fullName.trim())}
                  />
                  {portal !== 'admin' && (
                    <Button title={authMode === 'login' ? 'Create an account' : 'Already registered? Sign in'} kind="secondary" onPress={() => setAuthMode(authMode === 'login' ? 'register' : 'login')} />
                  )}
                </>
              ) : (
                <>
                  <Field label="6-digit verification code" value={otp} onChangeText={setOtp} placeholder="123456" keyboardType="number-pad" maxLength={6} />
                  <Button title="Verify email" onPress={verifyOtp} disabled={busy || otp.length !== 6} />
                  <Button title="Back to sign in" kind="secondary" onPress={() => { setOtpSent(false); setOtp(''); }} />
                </>
              )}
            </View>
            <Footer />
          </>
        ) : screen === 'ballot' && activeBallot ? (
          <>
            {/* 2. VISUAL BALLOT SCREEN (Phase 2) */}
            <Pressable onPress={() => setScreen('home')} style={styles.backButton}>
              <Text style={styles.backText}>‹ Exit Ballot</Text>
            </Pressable>

            <View style={styles.card}>
              <View style={styles.ballotHeaderRow}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.eyebrow}>OFFICIAL SECRET BALLOT</Text>
                  <Text style={styles.formTitle}>{activeBallot.title}</Text>
                  <Text style={styles.muted}>{activeBallot.societyName} · Sec 73CB</Text>
                </View>
                <View style={styles.zkpBadge}>
                  <Text style={styles.zkpBadgeText}>🔒 ZKP ANONYMOUS</Text>
                </View>
              </View>

              {!activeBallot.isEligible ? (
                <View style={[styles.workflowBox, { backgroundColor: '#fef2f2', borderColor: '#fecaca' }]}>
                  <Text style={{ fontWeight: '800', color: '#991b1b' }}>Ineligible to Vote</Text>
                  <Text style={[styles.small, { color: '#b91c1c' }]}>{activeBallot.disqualificationReason || 'You are not eligible to vote in this election.'}</Text>
                </View>
              ) : activeBallot.hasVoted ? (
                <View style={[styles.workflowBox, { backgroundColor: '#f0fdf4', borderColor: '#bbf7d0' }]}>
                  <Text style={{ fontWeight: '800', color: '#166534' }}>✓ Ballot Already Cast</Text>
                  <Text style={[styles.small, { color: '#15803d' }]}>You have already cast your ballot for this election. Duplicate votes are strictly prohibited.</Text>
                </View>
              ) : null}

              {activeBallot.panels.length > 0 && activeBallot.isEligible && !activeBallot.hasVoted && (
                <View style={styles.panelVoteBox}>
                  <Text style={styles.fieldLabel}>OPTION 1: VOTE UNIFIED COALITION / SLATE</Text>
                  <Text style={styles.small}>One-tap straight-ticket voting for an entire panel team.</Text>
                  <View style={styles.postGrid}>
                    {activeBallot.panels.map(p => {
                      const isSel = selectedPanelId === p.id;
                      return (
                        <Pressable
                          key={p.id}
                          onPress={() => selectPanelVote(p.id)}
                          style={[styles.panelCardOption, isSel && styles.panelCardOptionSelected]}
                        >
                          <View style={{ width: 12, height: 12, borderRadius: 6, backgroundColor: p.colorHex || '#0e7490' }} />
                          <View style={{ flex: 1 }}>
                            <Text style={[styles.itemTitle, isSel && { color: '#0e7490' }]}>{p.name}</Text>
                            {!!p.motto && <Text style={styles.small}>{p.motto}</Text>}
                          </View>
                          <View style={[styles.radioOuter, isSel && styles.radioOuterSelected]}>
                            {isSel && <View style={styles.radioInner} />}
                          </View>
                        </Pressable>
                      );
                    })}
                  </View>
                </View>
              )}
            </View>

            {/* Contested Posts */}
            {activeBallot.posts.map(p => {
              const selectedList = selectedCandidates[p.id] || [];
              return (
                <View key={p.id} style={styles.card}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                    <View>
                      <Text style={styles.sectionTitle}>{p.title}</Text>
                      <Text style={styles.small}>Select up to {p.maxSelections} candidate(s)</Text>
                    </View>
                    <View style={styles.selectionCounter}>
                      <Text style={styles.selectionCounterText}>
                        {selectedList.length} / {p.maxSelections} SELECTED
                      </Text>
                    </View>
                  </View>

                  <View style={{ marginTop: 12 }}>
                    {p.candidates.length === 0 ? (
                      <Text style={[styles.muted, { fontStyle: 'italic' }]}>No candidates contesting for this post.</Text>
                    ) : (
                      p.candidates.map(c => {
                        const isChosen = selectedList.includes(c.id);
                        return (
                          <Pressable
                            key={c.id}
                            disabled={!activeBallot.isEligible || activeBallot.hasVoted}
                            onPress={() => toggleCandidateSelection(p.id, c.id, p.maxSelections)}
                            style={[styles.candidateVoteCard, isChosen && styles.candidateVoteCardSelected]}
                          >
                            <View style={styles.candidateAvatar}>
                              <Text style={styles.candidateAvatarText}>{c.fullName.slice(0, 1).toUpperCase()}</Text>
                            </View>
                            <View style={{ flex: 1 }}>
                              <Text style={[styles.itemTitle, isChosen && { color: '#0e7490' }]}>{c.fullName}</Text>
                              {c.panelName ? (
                                <View style={styles.panelTag}>
                                  <Text style={styles.panelTagText}>{c.panelName}</Text>
                                </View>
                              ) : null}
                              {!!c.statement && <Text style={styles.statementText}>"{c.statement}"</Text>}
                            </View>
                            <View style={[styles.checkboxOuter, isChosen && styles.checkboxOuterSelected]}>
                              {isChosen && <Text style={styles.checkMark}>✓</Text>}
                            </View>
                          </Pressable>
                        );
                      })
                    )}
                  </View>
                </View>
              );
            })}

            {activeBallot.isEligible && !activeBallot.hasVoted && (
              <View style={styles.card}>
                <Text style={styles.sectionTitle}>Review & Dual-Factor Authorization</Text>
                <Text style={styles.muted}>Under MCS Act Section 73CB, once submitted your vote is permanent and cannot be modified.</Text>

                <Pressable
                  onPress={() => setDualFactorConfirmed(!dualFactorConfirmed)}
                  style={styles.dualFactorRow}
                >
                  <View style={[styles.checkboxOuter, dualFactorConfirmed && styles.checkboxOuterSelected]}>
                    {dualFactorConfirmed && <Text style={styles.checkMark}>✓</Text>}
                  </View>
                  <Text style={styles.dualFactorText}>
                    I solemnly confirm these are my final selections and authorize the system to record my anonymous secret ballot.
                  </Text>
                </Pressable>

                <Button
                  title="Cast Secret Ballot & Generate Receipt"
                  onPress={submitSecretBallot}
                  disabled={busy || !dualFactorConfirmed || Object.values(selectedCandidates).flat().length === 0}
                />
              </View>
            )}

            <Footer />
          </>
        ) : screen === 'vote-receipt' && voteReceipt ? (
          <>
            {/* 3. VOTE RECEIPT SCREEN (Phase 2) */}
            <View style={styles.card}>
              <View style={styles.receiptSuccessIcon}>
                <Text style={{ fontSize: 32, color: '#0f766e' }}>✓</Text>
              </View>
              <Text style={[styles.formTitle, { textAlign: 'center', marginTop: 10 }]}>Ballot Cast Successfully</Text>
              <Text style={[styles.muted, { textAlign: 'center', marginBottom: 18 }]}>
                {voteReceipt.message}
              </Text>

              <View style={styles.receiptCodeCard}>
                <Text style={styles.fieldLabel}>CRYPTOGRAPHIC VOTE RECEIPT CODE</Text>
                <Text selectable style={styles.receiptCodeText}>{voteReceipt.receiptCode}</Text>
                <Text style={[styles.fieldLabel, { marginTop: 14 }]}>SHA-256 BALLOT HASH</Text>
                <Text selectable style={styles.receiptHashText}>{voteReceipt.ballotHash}</Text>
                <Text style={[styles.small, { marginTop: 10 }]}>TIMESTAMP: {voteReceipt.castAt}</Text>
                <Text style={styles.small}>ELECTION: {voteReceipt.electionTitle}</Text>
              </View>

              <View style={styles.workflowBox}>
                <Text style={styles.workflowTitle}>BALLOT SECRECY & AUDIT GUARANTEE</Text>
                <Text style={styles.small}>
                  Your personal identity has been completely decoupled from your choices. This receipt proves your vote is in the tally box without revealing who you voted for.
                </Text>
              </View>

              <Button
                title="Verify Receipt in Audit Ledger"
                onPress={() => verifyReceipt(voteReceipt.receiptCode)}
              />
              <Button
                title="Done / Back to Dashboard"
                kind="secondary"
                onPress={() => setScreen('home')}
              />
            </View>
            <Footer />
          </>
        ) : screen === 'verify-receipt' ? (
          <>
            {/* 4. VERIFY RECEIPT SCREEN */}
            <Pressable onPress={() => setScreen('home')} style={styles.backButton}>
              <Text style={styles.backText}>‹ Back to Dashboard</Text>
            </Pressable>

            <View style={styles.card}>
              <Text style={styles.formTitle}>Audit Ledger Verification</Text>
              <Text style={styles.muted}>Cryptographic proof of ballot presence in the official ledger.</Text>

              {verifiedReceipt ? (
                <View style={{ marginTop: 16 }}>
                  <View style={[styles.workflowBox, { backgroundColor: '#f0fdf4', borderColor: '#bbf7d0' }]}>
                    <Text style={{ fontWeight: '800', color: '#166534', fontSize: 16 }}>✓ Ballot Verified in Ledger</Text>
                    <Text style={[styles.small, { color: '#15803d', marginTop: 4 }]}>
                      This ballot is confirmed present and untampered in the election tally.
                    </Text>
                  </View>

                  <View style={[styles.receiptCodeCard, { marginTop: 14 }]}>
                    <Text style={styles.fieldLabel}>RECEIPT CODE</Text>
                    <Text selectable style={styles.receiptCodeText}>{verifiedReceipt.receiptCode}</Text>
                    <Text style={[styles.fieldLabel, { marginTop: 12 }]}>ELECTION</Text>
                    <Text style={styles.itemTitle}>{verifiedReceipt.electionTitle}</Text>
                    <Text style={styles.small}>Society: {verifiedReceipt.societyName}</Text>
                    <Text style={[styles.small, { marginTop: 6 }]}>Cast At: {verifiedReceipt.castAt}</Text>
                  </View>
                </View>
              ) : (
                <View style={{ marginTop: 16 }}>
                  <Field
                    label="Enter Receipt Code"
                    value={verifyCodeInput}
                    onChangeText={setVerifyCodeInput}
                    placeholder="e.g. CVT-A1B2-C3D4-E5F6"
                    autoCapitalize="characters"
                  />
                  <Button
                    title="Verify Receipt Code"
                    onPress={() => verifyReceipt()}
                    disabled={busy || !verifyCodeInput.trim()}
                  />
                </View>
              )}
            </View>
            <Footer />
          </>
        ) : member?.role === 'PLATFORM_ADMIN' || portal === 'admin' ? (
          <>
            {/* 5. ADMIN & AUDITOR PORTAL (Phase 1, 2 & 3) */}
            <Brand />
            <View style={styles.welcome}>
              <Text style={styles.eyebrow}>ELECTION ADMINISTRATION & AUDIT</Text>
              <Text style={styles.formTitle}>Hello, {member?.fullName || email}</Text>
              <Text style={styles.muted}>Manage elections, voter rolls, and tamper-evident audit ledger.</Text>
            </View>
            {!!notice && <Text style={styles.notice}>{notice}</Text>}

            {/* Admin Nav Bar */}
            <View style={styles.nav}>
              <Pressable style={[styles.navItem, screen === 'home' && styles.navActive]} onPress={() => setScreen('home')}>
                <Text style={[styles.navText, screen === 'home' && styles.navTextActive]}>Overview</Text>
              </Pressable>
              <Pressable style={[styles.navItem, (screen === 'elections' || screen === 'election-detail' || screen === 'election-create') && styles.navActive]} onPress={() => setScreen('elections')}>
                <Text style={[styles.navText, (screen === 'elections' || screen === 'election-detail' || screen === 'election-create') && styles.navTextActive]}>Elections</Text>
              </Pressable>
              <Pressable style={[styles.navItem, screen === 'audit' && styles.navActive]} onPress={() => setScreen('audit')}>
                <Text style={[styles.navText, screen === 'audit' && styles.navTextActive]}>Audit Trail</Text>
              </Pressable>
              <Pressable style={[styles.navItem, screen === 'tenant' && styles.navActive]} onPress={() => setScreen('tenant')}>
                <Text style={[styles.navText, screen === 'tenant' && styles.navTextActive]}>Societies</Text>
              </Pressable>
            </View>

            {/* SCREEN: AUDIT TRAIL (Phase 3) */}
            {screen === 'audit' && (
              <>
                <View style={styles.card}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                    <View>
                      <Text style={styles.sectionTitle}>Cryptographic Audit Trail</Text>
                      <Text style={styles.muted}>Immutable hash-chained ledger (Sec 73CB Legal Compliance)</Text>
                    </View>
                    <Pressable onPress={runChainVerification} style={styles.refreshPill}>
                      <Text style={styles.refreshText}>↻ Verify Chain</Text>
                    </Pressable>
                  </View>

                  {/* Chain Integrity Status Card */}
                  {chainIntegrity && (
                    <View style={[styles.workflowBox, { backgroundColor: chainIntegrity.valid ? '#f0fdf4' : '#fef2f2', borderColor: chainIntegrity.valid ? '#bbf7d0' : '#fecaca', marginTop: 12 }]}>
                      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                        <Text style={{ fontWeight: '900', color: chainIntegrity.valid ? '#166534' : '#991b1b', fontSize: 14 }}>
                          {chainIntegrity.valid ? '✓ HASH CHAIN INTEGRITY: 100% SOUND' : '⚠ TAMPERING DETECTED IN CHAIN'}
                        </Text>
                        <View style={[styles.statusPill, { backgroundColor: chainIntegrity.valid ? '#dcfce7' : '#fee2e2' }]}>
                          <Text style={[styles.statusText, { color: chainIntegrity.valid ? '#15803d' : '#b91c1c' }]}>
                            {chainIntegrity.totalBlocks} BLOCKS
                          </Text>
                        </View>
                      </View>
                      <Text style={[styles.small, { marginTop: 4, color: '#334155' }]}>{chainIntegrity.message}</Text>
                      <Text style={[styles.small, { marginTop: 2, fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace', color: '#64748b' }]}>
                        LATEST BLOCK HASH: {chainIntegrity.latestHash.slice(0, 24)}…
                      </Text>
                    </View>
                  )}

                  {/* Filter & Search Bar */}
                  <View style={{ marginTop: 14 }}>
                    <Field
                      label="SEARCH AUDIT LOGS"
                      value={auditSearch}
                      onChangeText={setAuditSearch}
                      placeholder="Filter by actor, action details, or IP..."
                    />
                    <Text style={[styles.fieldLabel, { marginTop: 10 }]}>EVENT TYPE FILTER</Text>
                    <View style={styles.postGrid}>
                      {['ALL', 'VOTE_CAST', 'LOGIN_SUCCESS', 'LOGIN_FAILED', 'STATUS_CHANGED', 'VOTER_ROLL_IMPORTED', 'SUSPICIOUS_ACTIVITY_FLAGGED'].map(type => (
                        <Pressable
                          key={type}
                          onPress={() => setAuditFilterType(type)}
                          style={[styles.postOption, auditFilterType === type && styles.postOptionSelected]}
                        >
                          <Text style={[styles.postOptionText, auditFilterType === type && styles.postOptionTextSelected]}>
                            {type.replace('_', ' ')}
                          </Text>
                        </Pressable>
                      ))}
                    </View>
                  </View>
                </View>

                {/* Audit Entries List */}
                <View style={styles.card}>
                  <Text style={styles.sectionTitle}>
                    Ledger Events ({
                      auditLogs
                        .filter(l => auditFilterType === 'ALL' || l.eventType === auditFilterType)
                        .filter(l => !auditSearch.trim() || (l.actor + l.actionDetails + l.ipAddress).toLowerCase().includes(auditSearch.toLowerCase()))
                        .length
                    })
                  </Text>

                  {auditLogs.length === 0 ? (
                    <Text style={[styles.muted, { marginVertical: 12 }]}>No audit events recorded yet.</Text>
                  ) : (
                    auditLogs
                      .filter(l => auditFilterType === 'ALL' || l.eventType === auditFilterType)
                      .filter(l => !auditSearch.trim() || (l.actor + l.actionDetails + l.ipAddress).toLowerCase().includes(auditSearch.toLowerCase()))
                      .map(log => {
                        const isSuspicious = log.eventType === 'SUSPICIOUS_ACTIVITY_FLAGGED';
                        const isVote = log.eventType === 'VOTE_CAST';
                        const isLoginFail = log.eventType === 'LOGIN_FAILED';

                        return (
                          <View key={log.sequenceNumber} style={[styles.auditRowItem, isSuspicious && styles.auditRowSuspicious]}>
                            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                                <View style={styles.sequenceBadge}>
                                  <Text style={styles.sequenceBadgeText}>#{log.sequenceNumber}</Text>
                                </View>
                                <View style={[
                                  styles.statusPill,
                                  isSuspicious ? { backgroundColor: '#fee2e2' } : isVote ? { backgroundColor: '#e0f2fe' } : isLoginFail ? { backgroundColor: '#fef3c7' } : { backgroundColor: '#f1f5f9' }
                                ]}>
                                  <Text style={[
                                    styles.statusText,
                                    isSuspicious ? { color: '#991b1b' } : isVote ? { color: '#0369a1' } : isLoginFail ? { color: '#92400e' } : { color: '#475569' }
                                  ]}>
                                    {log.eventType}
                                  </Text>
                                </View>
                              </View>
                              <Text style={styles.small}>{formatDate(log.timestamp)}</Text>
                            </View>

                            <Text style={[styles.itemTitle, { marginTop: 6, fontSize: 13 }]}>{log.actionDetails}</Text>
                            <Text style={styles.small}>Actor: <Text style={{ fontWeight: '700' }}>{log.actor}</Text> · IP: {log.ipAddress} · Client: {log.deviceFingerprint}</Text>

                            <View style={styles.hashChainBox}>
                              <Text style={styles.hashChainText}>PREV: {log.previousHash.slice(0, 16)}…  →  HASH: {log.entryHash.slice(0, 16)}…</Text>
                            </View>
                          </View>
                        );
                      })
                  )}
                </View>
              </>
            )}

            {/* SCREEN: HOME (Overview) */}
            {screen === 'home' && (
              <>
                <View style={styles.stats}>
                  <View style={styles.stat}>
                    <Text style={styles.statNum}>{tenants.length}</Text>
                    <Text style={styles.muted}>Societies</Text>
                  </View>
                  <View style={styles.stat}>
                    <Text style={styles.statNum}>{elections.length}</Text>
                    <Text style={styles.muted}>Elections</Text>
                  </View>
                  <View style={styles.stat}>
                    <Text style={styles.statNum}>{auditLogs.length}</Text>
                    <Text style={styles.muted}>Audit Blocks</Text>
                  </View>
                </View>

                <View style={styles.card}>
                  <View style={styles.sectionHeader}>
                    <Text style={styles.sectionTitle}>Active Elections</Text>
                    <Pressable onPress={() => setScreen('election-create')} style={styles.refreshPill}>
                      <Text style={styles.refreshText}>+ New Election</Text>
                    </Pressable>
                  </View>
                  {elections.length === 0 ? (
                    <Text style={styles.muted}>No elections created yet. Go to the Elections tab to schedule one.</Text>
                  ) : (
                    elections.map(el => (
                      <Pressable key={el.id} onPress={() => openElectionDetail(el.id)} style={styles.electionRow}>
                        <View style={{ flex: 1 }}>
                          <Text style={styles.itemTitle}>{el.title}</Text>
                          <Text style={styles.muted}>{el.tenantName} · {el.totalVoters} voters · {el.votesCast} voted</Text>
                        </View>
                        <ElectionStatusPill status={el.status} />
                      </Pressable>
                    ))
                  )}
                </View>
                <Button title="Sign out" kind="secondary" onPress={logout} />
              </>
            )}

            {/* SCREEN: ELECTIONS LIST */}
            {screen === 'elections' && (
              <>
                <View style={styles.sectionHeader}>
                  <View>
                    <Text style={styles.sectionTitle}>Elections</Text>
                    <Text style={styles.muted}>Manage election lifecycle and configure posts</Text>
                  </View>
                  <Pressable onPress={() => setScreen('election-create')} style={styles.refreshPill}>
                    <Text style={styles.refreshText}>+ Create Election</Text>
                  </Pressable>
                </View>

                {elections.length === 0 ? (
                  <View style={styles.emptyCard}>
                    <Text style={styles.emptyTitle}>No Elections Configured</Text>
                    <Text style={styles.muted}>Create an election to configure contested posts, panels, and voter rolls.</Text>
                    <Button title="Schedule an Election" onPress={() => setScreen('election-create')} />
                  </View>
                ) : (
                  elections.map(el => (
                    <View key={el.id} style={styles.card}>
                      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                        <Text style={styles.itemTitle}>{el.title}</Text>
                        <ElectionStatusPill status={el.status} />
                      </View>
                      <Text style={[styles.muted, { marginTop: 4 }]}>Society: {el.tenantName}</Text>
                      {!!el.description && <Text style={{ fontSize: 13, color: '#435b6d', marginVertical: 6 }}>{el.description}</Text>}
                      <View style={styles.electionMetrics}>
                        <Text style={styles.small}>TOTAL VOTERS: <Text style={{ fontWeight: '800' }}>{el.totalVoters}</Text></Text>
                        <Text style={styles.small}>ELIGIBLE: <Text style={{ fontWeight: '800' }}>{el.eligibleVoters}</Text></Text>
                        <Text style={styles.small}>VOTES CAST: <Text style={{ fontWeight: '800' }}>{el.votesCast}</Text></Text>
                      </View>
                      <Button title="Manage & Configure →" onPress={() => openElectionDetail(el.id)} />
                    </View>
                  ))
                )}
              </>
            )}

            {/* SCREEN: CREATE ELECTION */}
            {screen === 'election-create' && (
              <View style={styles.card}>
                <Text style={styles.formTitle}>Schedule New Election</Text>
                <Text style={styles.muted}>Set up a committee election cycle for a housing society.</Text>

                <Text style={[styles.fieldLabel, { marginTop: 16 }]}>SELECT SOCIETY</Text>
                {tenants.map(s => (
                  <Pressable
                    key={s.id}
                    onPress={() => setElectionTenantId(s.id)}
                    style={[styles.societyOption, electionTenantId === s.id && styles.societyOptionSelected]}
                  >
                    <View style={styles.societyInfo}>
                      <Text style={styles.itemTitle}>{s.name}</Text>
                      <Text style={styles.muted}>{s.city} · {s.code}</Text>
                    </View>
                    <View style={[styles.radioOuter, electionTenantId === s.id && styles.radioOuterSelected]}>
                      {electionTenantId === s.id && <View style={styles.radioInner} />}
                    </View>
                  </Pressable>
                ))}

                <Field label="ELECTION TITLE" value={electionTitle} onChangeText={setElectionTitle} placeholder="e.g. Managing Committee Election 2026-2031" />
                <Field label="DESCRIPTION (OPTIONAL)" value={electionDesc} onChangeText={setElectionDesc} placeholder="Section 73CB election for 5-year term" multiline />
                <Field label="START TIME (ISO-8601, OPTIONAL)" value={electionStartTime} onChangeText={setElectionStartTime} placeholder="2026-11-01T08:00:00Z" />
                <Field label="END TIME (ISO-8601, OPTIONAL)" value={electionEndTime} onChangeText={setElectionEndTime} placeholder="2026-11-01T18:00:00Z" />

                <Button title="Initialize Election (Draft)" onPress={createElection} disabled={busy || !electionTenantId || !electionTitle.trim()} />
                <Button title="Cancel" kind="secondary" onPress={() => setScreen('elections')} />
              </View>
            )}

            {/* SCREEN: ELECTION DETAIL & CONFIGURATION */}
            {screen === 'election-detail' && activeElection && (
              <>
                <Pressable onPress={() => setScreen('elections')} style={styles.backButton}>
                  <Text style={styles.backText}>‹ Back to Elections</Text>
                </Pressable>

                <View style={styles.card}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.formTitle}>{activeElection.election.title}</Text>
                      <Text style={styles.muted}>{activeElection.election.tenantName}</Text>
                    </View>
                    <ElectionStatusPill status={activeElection.election.status} />
                  </View>

                  <View style={styles.workflowBox}>
                    <Text style={styles.workflowTitle}>LIFECYCLE CONTROLS</Text>
                    <Text style={styles.small}>Current Status: {activeElection.election.status}</Text>
                    <View style={{ flexDirection: 'row', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
                      {activeElection.election.status === 'DRAFT' && (
                        <Button title="Mark Configured" onPress={() => changeElectionStatus('CONFIGURED')} disabled={busy || activeElection.posts.length === 0} full />
                      )}
                      {activeElection.election.status === 'CONFIGURED' && (
                        <Button title="Publish Election" onPress={() => changeElectionStatus('PUBLISHED')} disabled={busy} full />
                      )}
                      {activeElection.election.status === 'PUBLISHED' && (
                        <Button title="Open Voting Now" onPress={() => changeElectionStatus('VOTING_OPEN')} disabled={busy} full />
                      )}
                      {activeElection.election.status === 'VOTING_OPEN' && (
                        <Button title="Close Voting Window" onPress={() => changeElectionStatus('VOTING_CLOSED')} disabled={busy} full />
                      )}
                    </View>
                  </View>

                  <View style={[styles.nav, { marginTop: 18 }]}>
                    <Pressable style={[styles.navItem, detailTab === 'posts' && styles.navActive]} onPress={() => setDetailTab('posts')}>
                      <Text style={[styles.navText, detailTab === 'posts' && styles.navTextActive]}>Posts ({activeElection.posts.length})</Text>
                    </Pressable>
                    <Pressable style={[styles.navItem, detailTab === 'panels' && styles.navActive]} onPress={() => setDetailTab('panels')}>
                      <Text style={[styles.navText, detailTab === 'panels' && styles.navTextActive]}>Panels ({activeElection.panels.length})</Text>
                    </Pressable>
                    <Pressable style={[styles.navItem, detailTab === 'candidates' && styles.navActive]} onPress={() => setDetailTab('candidates')}>
                      <Text style={[styles.navText, detailTab === 'candidates' && styles.navTextActive]}>Candidates</Text>
                    </Pressable>
                    <Pressable style={[styles.navItem, detailTab === 'voters' && styles.navActive]} onPress={() => setDetailTab('voters')}>
                      <Text style={[styles.navText, detailTab === 'voters' && styles.navTextActive]}>Voter Roll ({activeElection.voterRoll.length})</Text>
                    </Pressable>
                  </View>
                </View>

                {/* TAB 1: POSTS */}
                {detailTab === 'posts' && (
                  <View style={styles.card}>
                    <Text style={styles.sectionTitle}>Contested Posts</Text>
                    <Text style={styles.muted}>Positions to be elected by society members.</Text>
                    {activeElection.posts.length === 0 ? (
                      <Text style={[styles.muted, { marginVertical: 12 }]}>No posts added yet. Add positions like Chairman, Secretary, Treasurer.</Text>
                    ) : (
                      activeElection.posts.map(p => (
                        <View key={p.id} style={styles.listItem}>
                          <View style={{ flex: 1 }}>
                            <Text style={styles.itemTitle}>{p.title}</Text>
                            <Text style={styles.muted}>Max selections: {p.maxSelections} · {p.candidates.length} candidates</Text>
                          </View>
                        </View>
                      ))
                    )}

                    {activeElection.election.status === 'DRAFT' || activeElection.election.status === 'CONFIGURED' ? (
                      <View style={{ marginTop: 16, paddingTop: 16, borderTopWidth: 1, borderTopColor: '#edf1f3' }}>
                        <Text style={styles.fieldLabel}>ADD CONTESTED POST</Text>
                        <Field label="Post Title" value={newPostTitle} onChangeText={setNewPostTitle} placeholder="e.g. Chairman" />
                        <Field label="Description (Optional)" value={newPostDesc} onChangeText={setNewPostDesc} placeholder="Head of Managing Committee" />
                        <Field label="Max Selections" value={newPostMaxSel} onChangeText={setNewPostMaxSel} placeholder="1" keyboardType="number-pad" />
                        <Button title="+ Add Post" onPress={addPost} disabled={busy || !newPostTitle.trim()} />
                      </View>
                    ) : null}
                  </View>
                )}

                {/* TAB 2: PANELS */}
                {detailTab === 'panels' && (
                  <View style={styles.card}>
                    <Text style={styles.sectionTitle}>Election Panels (Coalitions)</Text>
                    <Text style={styles.muted}>Unified teams contesting across multiple posts.</Text>
                    {activeElection.panels.length === 0 ? (
                      <Text style={[styles.muted, { marginVertical: 12 }]}>No panels created yet. Panels enable straight-ticket coalition voting.</Text>
                    ) : (
                      activeElection.panels.map(pan => (
                        <View key={pan.id} style={styles.listItem}>
                          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                            <View style={{ width: 14, height: 14, borderRadius: 7, backgroundColor: pan.colorHex || '#0e7490' }} />
                            <View>
                              <Text style={styles.itemTitle}>{pan.name}</Text>
                              {!!pan.motto && <Text style={styles.muted}>{pan.motto}</Text>}
                            </View>
                          </View>
                        </View>
                      ))
                    )}

                    {activeElection.election.status === 'DRAFT' || activeElection.election.status === 'CONFIGURED' ? (
                      <View style={{ marginTop: 16, paddingTop: 16, borderTopWidth: 1, borderTopColor: '#edf1f3' }}>
                        <Text style={styles.fieldLabel}>REGISTER NEW PANEL</Text>
                        <Field label="Panel Name" value={newPanelName} onChangeText={setNewPanelName} placeholder="e.g. Progressive Residents Slate" />
                        <Field label="Motto / Theme" value={newPanelMotto} onChangeText={setNewPanelMotto} placeholder="e.g. Transparency & Good Governance" />
                        <Field label="Theme Color Hex" value={newPanelColor} onChangeText={setNewPanelColor} placeholder="#0e7490" />
                        <Button title="+ Register Panel" onPress={addPanel} disabled={busy || !newPanelName.trim()} />
                      </View>
                    ) : null}
                  </View>
                )}

                {/* TAB 3: CANDIDATES */}
                {detailTab === 'candidates' && (
                  <View style={styles.card}>
                    <Text style={styles.sectionTitle}>Candidates for this Election</Text>
                    <Text style={styles.muted}>Candidates nominated per post and panel.</Text>

                    {activeElection.posts.map(p => (
                      <View key={p.id} style={{ marginVertical: 12 }}>
                        <Text style={{ fontSize: 16, fontWeight: '800', color: '#0e7490', marginBottom: 8 }}>{p.title}</Text>
                        {p.candidates.length === 0 ? (
                          <Text style={[styles.muted, { fontStyle: 'italic', marginBottom: 8 }]}>No candidates nominated for this post yet.</Text>
                        ) : (
                          p.candidates.map(c => (
                            <View key={c.id} style={styles.candidateCardItem}>
                              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                                <View style={{ flex: 1 }}>
                                  <Text style={styles.itemTitle}>{c.fullName}</Text>
                                  <Text style={styles.muted}>{c.email} {c.panelName ? `· Panel: ${c.panelName}` : ''}</Text>
                                  {!!c.statement && <Text style={{ fontSize: 13, color: '#435b6d', marginTop: 4 }}>"{c.statement}"</Text>}
                                </View>
                                <StatusPill status={c.status} />
                              </View>
                              {c.status === 'PENDING' && (
                                <View style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
                                  <Button title="Approve" onPress={() => updateCandidateStatus(c.id, 'APPROVED')} full />
                                  <Button title="Reject" kind="secondary" onPress={() => updateCandidateStatus(c.id, 'REJECTED')} full />
                                </View>
                              )}
                            </View>
                          ))
                        )}
                      </View>
                    ))}

                    {activeElection.posts.length > 0 && (activeElection.election.status === 'DRAFT' || activeElection.election.status === 'CONFIGURED') && (
                      <View style={{ marginTop: 16, paddingTop: 16, borderTopWidth: 1, borderTopColor: '#edf1f3' }}>
                        <Text style={styles.fieldLabel}>NOMINATE CANDIDATE</Text>
                        <Text style={styles.small}>Select Post:</Text>
                        <View style={styles.postGrid}>
                          {activeElection.posts.map(p => (
                            <Pressable key={p.id} onPress={() => setNomPostId(p.id)} style={[styles.postOption, nomPostId === p.id && styles.postOptionSelected]}>
                              <Text style={[styles.postOptionText, nomPostId === p.id && styles.postOptionTextSelected]}>{p.title}</Text>
                            </Pressable>
                          ))}
                        </View>

                        {activeElection.panels.length > 0 && (
                          <>
                            <Text style={[styles.small, { marginTop: 8 }]}>Select Panel (Optional):</Text>
                            <View style={styles.postGrid}>
                              <Pressable onPress={() => setNomPanelId('')} style={[styles.postOption, !nomPanelId && styles.postOptionSelected]}>
                                <Text style={[styles.postOptionText, !nomPanelId && styles.postOptionTextSelected]}>Independent</Text>
                              </Pressable>
                              {activeElection.panels.map(pan => (
                                <Pressable key={pan.id} onPress={() => setNomPanelId(pan.id)} style={[styles.postOption, nomPanelId === pan.id && styles.postOptionSelected]}>
                                  <Text style={[styles.postOptionText, nomPanelId === pan.id && styles.postOptionTextSelected]}>{pan.name}</Text>
                                </Pressable>
                              ))}
                            </View>
                          </>
                        )}

                        <Field label="Candidate Full Name" value={nomName} onChangeText={setNomName} placeholder="Full Name" />
                        <Field label="Email Address" value={nomEmail} onChangeText={setNomEmail} placeholder="candidate@example.com" keyboardType="email-address" autoCapitalize="none" />
                        <Field label="Phone (Optional)" value={nomPhone} onChangeText={setNomPhone} placeholder="+91 98765 43210" keyboardType="phone-pad" />
                        <Field label="Manifesto / Statement" value={nomStatement} onChangeText={setNomStatement} placeholder="Brief summary of candidate objectives" multiline />

                        <Button title="+ Nominate Candidate" onPress={nominateCandidateForElection} disabled={busy || !nomPostId || !nomName.trim() || !nomEmail.trim()} />
                      </View>
                    )}
                  </View>
                )}

                {/* TAB 4: VOTER ROLL */}
                {detailTab === 'voters' && (
                  <View style={styles.card}>
                    <Text style={styles.sectionTitle}>Validated Society Voter Roll</Text>
                    <Text style={styles.muted}>Eligible flat members registered to cast a vote in this election.</Text>

                    <View style={styles.stats}>
                      <View style={styles.stat}>
                        <Text style={styles.statNum}>{activeElection.voterRoll.length}</Text>
                        <Text style={styles.muted}>Total Flats</Text>
                      </View>
                      <View style={styles.stat}>
                        <Text style={styles.statNum}>{activeElection.voterRoll.filter(v => v.eligible).length}</Text>
                        <Text style={styles.muted}>Eligible</Text>
                      </View>
                      <View style={styles.stat}>
                        <Text style={styles.statNum}>{activeElection.voterRoll.filter(v => v.hasVoted).length}</Text>
                        <Text style={styles.muted}>Voted</Text>
                      </View>
                    </View>

                    <View style={styles.csvBox}>
                      <Text style={styles.fieldLabel}>BULK CSV VOTER ROLL IMPORT</Text>
                      <Text style={styles.small}>Format: flatNumber,fullName,email,phone,aadhaarRef,eligible</Text>
                      <Field label="CSV Data" value={voterCsv} onChangeText={setVoterCsv} placeholder="Paste CSV text here..." multiline />
                      <View style={{ flexDirection: 'row', gap: 8 }}>
                        <Button title="Load Sample CSV" kind="secondary" onPress={insertSampleCsv} full />
                        <Button title="Import & Deduplicate" onPress={uploadVoterRoll} disabled={busy || !voterCsv.trim()} full />
                      </View>

                      {uploadResult && (
                        <View style={styles.uploadResultBox}>
                          <Text style={{ fontWeight: '800', color: '#0e7490' }}>Import Summary:</Text>
                          <Text style={styles.small}>✓ Rows parsed: {uploadResult.totalRowsParsed}</Text>
                          <Text style={styles.small}>✓ Successfully added: {uploadResult.addedCount}</Text>
                          {uploadResult.duplicateFlatsCount > 0 && <Text style={[styles.small, { color: '#c05621' }]}>⚠ Duplicate flats flagged: {uploadResult.duplicateFlatsCount}</Text>}
                          {uploadResult.duplicateEmailsCount > 0 && <Text style={[styles.small, { color: '#c05621' }]}>⚠ Duplicate emails flagged: {uploadResult.duplicateEmailsCount}</Text>}
                          {uploadResult.errors.map((err, i) => <Text key={i} style={[styles.small, { color: '#e53e3e' }]}>• {err}</Text>)}
                        </View>
                      )}
                    </View>

                    <View style={{ marginTop: 18, paddingTop: 16, borderTopWidth: 1, borderTopColor: '#edf1f3' }}>
                      <Text style={styles.fieldLabel}>ADD INDIVIDUAL VOTER</Text>
                      <View style={{ flexDirection: 'row', gap: 8 }}>
                        <View style={{ flex: 1 }}>
                          <Field label="Flat No" value={voterFlat} onChangeText={setVoterFlat} placeholder="A-402" autoCapitalize="characters" />
                        </View>
                        <View style={{ flex: 2 }}>
                          <Field label="Full Name" value={voterName} onChangeText={setVoterName} placeholder="Member Name" />
                        </View>
                      </View>
                      <Field label="Email Address" value={voterEmail} onChangeText={setVoterEmail} placeholder="voter@example.com" keyboardType="email-address" autoCapitalize="none" />
                      <Button title="+ Add to Voter Roll" onPress={addIndividualVoter} disabled={busy || !voterFlat.trim() || !voterName.trim() || !voterEmail.trim()} />
                    </View>

                    <Text style={[styles.fieldLabel, { marginTop: 24 }]}>CURRENT VOTER ROSTER</Text>
                    {activeElection.voterRoll.length === 0 ? (
                      <Text style={[styles.muted, { marginVertical: 8 }]}>Voter roll is currently empty.</Text>
                    ) : (
                      activeElection.voterRoll.map(v => (
                        <View key={v.id} style={styles.voterRow}>
                          <View style={styles.voterFlatBadge}>
                            <Text style={styles.voterFlatText}>{v.flatNumber}</Text>
                          </View>
                          <View style={{ flex: 1 }}>
                            <Text style={styles.itemTitle}>{v.fullName}</Text>
                            <Text style={styles.muted}>{v.email}</Text>
                          </View>
                          <View style={{ alignItems: 'flex-end', gap: 4 }}>
                            <View style={[styles.eligibilityPill, v.eligible ? styles.eligibilityPillActive : styles.eligibilityPillInactive]}>
                              <Text style={[styles.eligibilityText, v.eligible ? styles.eligibilityTextActive : styles.eligibilityTextInactive]}>
                                {v.eligible ? 'ELIGIBLE' : 'DISQUALIFIED'}
                              </Text>
                            </View>
                            {v.hasVoted && <Text style={{ fontSize: 9, fontWeight: '800', color: '#287448' }}>VOTED</Text>}
                          </View>
                        </View>
                      ))
                    )}
                  </View>
                )}
              </>
            )}

            {/* SCREEN: TENANT (Societies) */}
            {screen === 'tenant' && (
              <View style={styles.card}>
                <Text style={styles.formTitle}>Housing Societies</Text>
                <Text style={styles.muted}>All provisioned housing communities.</Text>
                {tenants.map(item => (
                  <View key={item.id} style={styles.listItem}>
                    <Text style={styles.itemTitle}>{item.name}</Text>
                    <Text style={styles.muted}>{item.city} · Code: {item.code}</Text>
                    <Text selectable style={styles.small}>UUID: {item.id}</Text>
                  </View>
                ))}
                <View style={{ marginTop: 20, paddingTop: 16, borderTopWidth: 1, borderTopColor: '#edf1f3' }}>
                  <Text style={styles.fieldLabel}>ADD NEW SOCIETY</Text>
                  <Field label="Society Name" value={tenantName} onChangeText={setTenantName} placeholder="Green Meadows CHS" />
                  <Field label="Tenant Code" value={tenantCode} onChangeText={setTenantCode} placeholder="GREENMEADOWS" autoCapitalize="characters" />
                  <Field label="City" value={city} onChangeText={setCity} placeholder="Mumbai" />
                  <Button title="Create Society" onPress={createTenant} disabled={busy || !tenantName.trim() || tenantCode.trim().length < 3 || !city.trim()} />
                </View>
              </View>
            )}

            <Footer />
          </>
        ) : (
          <>
            {/* 6. RESIDENT PORTAL */}
            <Brand />
            <View style={styles.welcome}>
              <Text style={styles.eyebrow}>RESIDENT VOTER PORTAL</Text>
              <Text style={styles.formTitle}>Hello, {member?.fullName || email}</Text>
              <Text style={styles.muted}>Participate in your society's official management elections.</Text>
            </View>
            {!!notice && <Text style={styles.notice}>{notice}</Text>}

            <View style={styles.card}>
              <Text style={styles.sectionTitle}>Elections in Your Societies</Text>
              <Text style={styles.muted}>Cast your ballot securely or review past election results.</Text>
              {elections.length === 0 ? (
                <Text style={[styles.muted, { marginTop: 12 }]}>No elections announced in your societies yet.</Text>
              ) : (
                elections.map(el => (
                  <View key={el.id} style={{ paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: '#edf1f3' }}>
                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Text style={styles.itemTitle}>{el.title}</Text>
                      <ElectionStatusPill status={el.status} />
                    </View>
                    <Text style={styles.muted}>{el.tenantName}</Text>
                    {el.votingStartTime && (
                      <Text style={styles.small}>POLLING WINDOW: {formatDate(el.votingStartTime)} – {formatDate(el.votingEndTime || '')}</Text>
                    )}

                    {el.status === 'VOTING_OPEN' && (
                      <View style={{ marginTop: 10 }}>
                        <Button
                          title="Cast Secret Ballot →"
                          onPress={() => openBallot(el.id)}
                        />
                      </View>
                    )}
                  </View>
                ))
              )}
            </View>

            <View style={styles.card}>
              <Text style={styles.sectionTitle}>Audit & Receipt Verification</Text>
              <Text style={styles.muted}>Verify that your ballot hash exists in the tamper-evident ledger.</Text>
              <Field
                label="Receipt Code"
                value={verifyCodeInput}
                onChangeText={setVerifyCodeInput}
                placeholder="CVT-XXXX-XXXX-XXXX"
                autoCapitalize="characters"
              />
              <Button
                title="Verify Receipt Code"
                kind="secondary"
                onPress={() => verifyReceipt()}
                disabled={busy || !verifyCodeInput.trim()}
              />
            </View>

            <View style={styles.card}>
              <Text style={styles.sectionTitle}>Your Societies</Text>
              {tenants.length === 0 ? (
                <Text style={styles.muted}>You have not joined any society yet.</Text>
              ) : (
                tenants.map(item => (
                  <View key={item.id} style={styles.listItem}>
                    <Text style={styles.itemTitle}>{item.name}</Text>
                    <Text style={styles.muted}>{item.city} · {item.code}</Text>
                  </View>
                ))
              )}
            </View>

            <View style={styles.card}>
              <Text style={styles.sectionTitle}>Join a Housing Society</Text>
              <Text style={styles.muted}>Join to be listed on your community's roster.</Text>
              {availableTenants.map(item => {
                const joined = tenants.some(t => t.id === item.id);
                return (
                  <View key={item.id} style={styles.listItem}>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.itemTitle}>{item.name}</Text>
                      <Text style={styles.muted}>{item.city} · {item.code}</Text>
                    </View>
                    <Pressable
                      disabled={joined || busy}
                      onPress={() => joinSociety(item.id)}
                      style={[styles.joinButton, joined && styles.joinButtonDisabled]}
                    >
                      <Text style={[styles.joinButtonText, joined && styles.joinButtonTextDisabled]}>
                        {joined ? 'Joined' : 'Join'}
                      </Text>
                    </Pressable>
                  </View>
                );
              })}
            </View>

            <Button title="Sign out" kind="secondary" onPress={logout} />
            <Footer />
          </>
        )}

        {busy && (
          <View style={styles.loading}>
            <ActivityIndicator color="#0e7490" />
            <Text style={styles.muted}>Working…</Text>
          </View>
        )}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <View style={[styles.brand, compact && styles.brandCompact]}>
      <View style={styles.logo}>
        <Text style={styles.logoText}>C</Text>
      </View>
      <View>
        <Text style={styles.brandTitle}>CIVITAS</Text>
        <Text style={styles.brandSub}>Housing Society Election Platform</Text>
      </View>
    </View>
  );
}

function Footer() {
  return <Text style={styles.footer}>CIVITAS · SPIT · Maharashtra Co-op Societies Act Sec 73CB</Text>;
}

function formatDate(value: string) {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

function StatusPill({ status }: { status: string }) {
  const pending = status.toUpperCase() === 'PENDING';
  const approved = status.toUpperCase() === 'APPROVED';
  return (
    <View style={[styles.statusPill, pending ? styles.statusPending : approved ? styles.statusApproved : styles.statusOther]}>
      <Text style={[styles.statusText, pending ? styles.statusTextPending : approved ? styles.statusTextApproved : styles.statusTextOther]}>
        {status}
      </Text>
    </View>
  );
}

function ElectionStatusPill({ status }: { status: ElectionStatus }) {
  const isOpen = status === 'VOTING_OPEN';
  const isClosed = status === 'VOTING_CLOSED';
  const isPublished = status === 'PUBLISHED';

  const bg = isOpen ? '#def7ec' : isClosed ? '#fde8e8' : isPublished ? '#e1effe' : '#f3f4f6';
  const color = isOpen ? '#03543f' : isClosed ? '#9b1c1c' : isPublished ? '#1e429f' : '#374151';

  return (
    <View style={[styles.statusPill, { backgroundColor: bg }]}>
      <Text style={[styles.statusText, { color }]}>{status.replace('_', ' ')}</Text>
    </View>
  );
}

function Field(props: any) {
  const [visible, setVisible] = useState(false);
  const { label, secureTextEntry, ...inputProps } = props;
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <View style={secureTextEntry ? styles.passwordRow : undefined}>
        <TextInput
          {...inputProps}
          secureTextEntry={secureTextEntry ? !visible : undefined}
          style={[styles.input, secureTextEntry && styles.passwordInput, props.multiline && styles.multiline]}
          placeholderTextColor="#93a4b5"
        />
        {secureTextEntry && (
          <Pressable onPress={() => setVisible(v => !v)} style={styles.passwordToggle}>
            <Text style={styles.passwordToggleText}>{visible ? 'Hide' : 'Show'}</Text>
          </Pressable>
        )}
      </View>
    </View>
  );
}

function Button({
  title,
  onPress,
  disabled,
  kind = 'primary',
  full = false
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  kind?: 'primary' | 'secondary';
  full?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={[
        styles.button,
        kind === 'secondary' && styles.buttonSecondary,
        disabled && styles.buttonDisabled,
        full && styles.buttonFull
      ]}
    >
      <Text style={[styles.buttonText, kind === 'secondary' && styles.buttonSecondaryText]}>{title}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#f4f7f8' },
  container: {
    padding: 20,
    paddingTop: Platform.OS === 'android' ? 40 : 54,
    paddingBottom: 40,
    maxWidth: 640,
    width: '100%',
    alignSelf: 'center'
  },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12, backgroundColor: '#f4f7f8' },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 20 },
  brandCompact: { marginBottom: 0 },
  logo: { width: 44, height: 44, borderRadius: 14, backgroundColor: '#0e7490', alignItems: 'center', justifyContent: 'center' },
  logoText: { fontSize: 24, fontWeight: '900', color: '#fff' },
  brandTitle: { fontSize: 20, fontWeight: '900', letterSpacing: 2, color: '#102a43' },
  brandSub: { fontSize: 11, color: '#72869a', marginTop: 2 },
  authHero: { marginBottom: 18 },
  eyebrow: { fontSize: 11, fontWeight: '900', letterSpacing: 1.5, color: '#0e7490', marginBottom: 6 },
  authTitle: { fontSize: 28, lineHeight: 34, fontWeight: '900', color: '#102a43', marginBottom: 6 },
  formTitle: { fontSize: 24, lineHeight: 30, fontWeight: '900', color: '#102a43', marginBottom: 4 },
  card: { backgroundColor: '#fff', borderRadius: 20, padding: 18, marginBottom: 16, borderWidth: 1, borderColor: '#e4ebef' },
  sectionTitle: { fontSize: 18, fontWeight: '800', color: '#102a43', marginBottom: 6 },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  refreshPill: { backgroundColor: '#e6f3f4', paddingVertical: 8, paddingHorizontal: 12, borderRadius: 20 },
  refreshText: { fontSize: 11, fontWeight: '800', color: '#0e7490' },
  muted: { fontSize: 13, lineHeight: 19, color: '#6c8297' },
  notice: { backgroundColor: '#e0f2fe', borderRadius: 12, padding: 12, marginBottom: 14, color: '#075985', lineHeight: 18, fontWeight: '600', fontSize: 13 },
  portalRow: { flexDirection: 'row', gap: 6, marginVertical: 12 },
  portalButton: { flex: 1, paddingVertical: 10, alignItems: 'center', backgroundColor: '#f0f5f7', borderRadius: 10 },
  portalButtonActive: { backgroundColor: '#0e7490' },
  portalText: { fontSize: 12, fontWeight: '800', color: '#486175' },
  portalTextActive: { color: '#fff' },
  field: { marginTop: 14 },
  fieldLabel: { fontSize: 10, fontWeight: '900', letterSpacing: 0.8, color: '#50677b', marginBottom: 6 },
  input: { borderWidth: 1, borderColor: '#d8e2e8', borderRadius: 12, paddingHorizontal: 12, paddingVertical: 11, fontSize: 14, color: '#102a43', backgroundColor: '#fbfdfe' },
  passwordRow: { position: 'relative', justifyContent: 'center' },
  passwordInput: { paddingRight: 60 },
  passwordToggle: { position: 'absolute', right: 10, paddingVertical: 8, paddingHorizontal: 6 },
  passwordToggleText: { fontSize: 12, fontWeight: '800', color: '#0e7490' },
  multiline: { minHeight: 90, textAlignVertical: 'top' },
  button: { backgroundColor: '#0e7490', borderRadius: 12, paddingVertical: 13, paddingHorizontal: 14, alignItems: 'center', marginTop: 14, minHeight: 46, justifyContent: 'center' },
  buttonText: { color: '#fff', fontSize: 13, fontWeight: '800' },
  buttonSecondary: { backgroundColor: '#f0f5f7', borderWidth: 1, borderColor: '#d9e4e9' },
  buttonSecondaryText: { color: '#365266' },
  buttonDisabled: { opacity: 0.4 },
  buttonFull: { flex: 1 },
  nav: { flexDirection: 'row', padding: 4, backgroundColor: '#e7eef2', borderRadius: 12, marginBottom: 16 },
  navItem: { flex: 1, paddingVertical: 9, alignItems: 'center', borderRadius: 8 },
  navActive: { backgroundColor: '#fff' },
  navText: { fontSize: 12, fontWeight: '800', color: '#708397' },
  navTextActive: { color: '#0e7490' },
  welcome: { marginBottom: 16 },
  stats: { flexDirection: 'row', gap: 10, marginBottom: 14 },
  stat: { flex: 1, backgroundColor: '#fff', padding: 14, borderRadius: 14, borderWidth: 1, borderColor: '#e4ebef' },
  statNum: { fontSize: 24, fontWeight: '900', color: '#0e7490', marginBottom: 2 },
  listItem: { paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#edf1f3' },
  itemTitle: { fontSize: 14, fontWeight: '800', color: '#1c3448', marginBottom: 2 },
  small: { fontSize: 10, color: '#8799a8', marginTop: 4 },
  backButton: { alignSelf: 'flex-start', paddingVertical: 6, marginBottom: 12 },
  backText: { fontSize: 13, fontWeight: '800', color: '#0e7490' },
  emptyCard: { alignItems: 'center', backgroundColor: '#fff', padding: 22, borderRadius: 18, borderWidth: 1, borderColor: '#e3ebef', marginBottom: 16 },
  emptyTitle: { fontSize: 16, fontWeight: '800', color: '#17364a', marginBottom: 6, textAlign: 'center' },
  statusPill: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 12 },
  statusPending: { backgroundColor: '#fff4d8' },
  statusApproved: { backgroundColor: '#def7ec' },
  statusOther: { backgroundColor: '#edf2f7' },
  statusText: { fontSize: 9, fontWeight: '900', textTransform: 'uppercase' },
  statusTextPending: { color: '#8a5d00' },
  statusTextApproved: { color: '#03543f' },
  statusTextOther: { color: '#4a5568' },
  electionRow: { paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#edf1f3', flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  electionMetrics: { flexDirection: 'row', gap: 12, marginTop: 8 },
  workflowBox: { backgroundColor: '#f0faf9', borderRadius: 12, padding: 12, marginTop: 12, borderWidth: 1, borderColor: '#ccebe8' },
  workflowTitle: { fontSize: 10, fontWeight: '900', letterSpacing: 0.8, color: '#0f766e', marginBottom: 2 },
  postGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 6 },
  postOption: { paddingHorizontal: 10, paddingVertical: 8, borderWidth: 1, borderColor: '#dbe5e9', borderRadius: 10 },
  postOptionSelected: { borderColor: '#0e7490', backgroundColor: '#eaf7f6' },
  postOptionText: { fontSize: 11, fontWeight: '700', color: '#526b7d' },
  postOptionTextSelected: { color: '#0c6474' },
  candidateCardItem: { backgroundColor: '#fbfdfe', padding: 12, borderRadius: 12, borderWidth: 1, borderColor: '#e4ebef', marginBottom: 8 },
  csvBox: { backgroundColor: '#f8fafc', padding: 12, borderRadius: 12, borderWidth: 1, borderColor: '#e2e8f0', marginTop: 12 },
  uploadResultBox: { marginTop: 10, padding: 10, backgroundColor: '#f0fdf4', borderRadius: 8, borderWidth: 1, borderColor: '#bbf7d0' },
  voterRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#edf1f3' },
  voterFlatBadge: { backgroundColor: '#e0f2fe', width: 44, height: 44, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  voterFlatText: { fontSize: 12, fontWeight: '900', color: '#0369a1' },
  eligibilityPill: { paddingHorizontal: 6, paddingVertical: 3, borderRadius: 8 },
  eligibilityPillActive: { backgroundColor: '#def7ec' },
  eligibilityPillInactive: { backgroundColor: '#fde8e8' },
  eligibilityText: { fontSize: 8, fontWeight: '900' },
  eligibilityTextActive: { color: '#03543f' },
  eligibilityTextInactive: { color: '#9b1c1c' },
  societyOption: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 10, borderRadius: 12, borderWidth: 1, borderColor: '#e1e9ed', marginTop: 8 },
  societyOptionSelected: { borderColor: '#0e7490', backgroundColor: '#f0faf9' },
  societyInfo: { flex: 1 },
  radioOuter: { width: 18, height: 18, borderRadius: 9, borderWidth: 1.5, borderColor: '#c4d1d8', alignItems: 'center', justifyContent: 'center' },
  radioOuterSelected: { borderColor: '#0e7490' },
  radioInner: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#0e7490' },
  joinButton: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 16, backgroundColor: '#e8f4f5' },
  joinButtonDisabled: { backgroundColor: '#e5f3e9' },
  joinButtonText: { fontSize: 11, fontWeight: '800', color: '#0e7490' },
  joinButtonTextDisabled: { color: '#287448' },
  footer: { fontSize: 10, textAlign: 'center', color: '#97a7b3', marginTop: 22, lineHeight: 16 },
  loading: { alignItems: 'center', padding: 14, gap: 6 },

  // Phase 2: Voting Styles
  ballotHeaderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 },
  zkpBadge: { backgroundColor: '#e0f2fe', paddingHorizontal: 8, paddingVertical: 5, borderRadius: 10, borderWidth: 1, borderColor: '#bae6fd' },
  zkpBadgeText: { fontSize: 9, fontWeight: '900', color: '#0284c7', letterSpacing: 0.5 },
  panelVoteBox: { marginTop: 14, paddingTop: 14, borderTopWidth: 1, borderTopColor: '#edf1f3' },
  panelCardOption: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 12, borderRadius: 12, borderWidth: 1, borderColor: '#e1e9ed', marginTop: 8, backgroundColor: '#fbfdfe' },
  panelCardOptionSelected: { borderColor: '#0e7490', backgroundColor: '#f0faf9' },
  selectionCounter: { backgroundColor: '#f0f9ff', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 8, borderWidth: 1, borderColor: '#e0f2fe' },
  selectionCounterText: { fontSize: 9, fontWeight: '900', color: '#0284c7' },
  candidateVoteCard: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12, borderRadius: 12, borderWidth: 1, borderColor: '#e2e8f0', marginTop: 8, backgroundColor: '#fff' },
  candidateVoteCardSelected: { borderColor: '#0e7490', backgroundColor: '#f0faf9' },
  candidateAvatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#e0f2fe', alignItems: 'center', justifyContent: 'center' },
  candidateAvatarText: { fontSize: 16, fontWeight: '900', color: '#0e7490' },
  panelTag: { alignSelf: 'flex-start', backgroundColor: '#f1f5f9', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 6, marginTop: 2 },
  panelTagText: { fontSize: 9, fontWeight: '800', color: '#475569' },
  statementText: { fontSize: 12, color: '#475569', marginTop: 4, fontStyle: 'italic', lineHeight: 17 },
  checkboxOuter: { width: 22, height: 22, borderRadius: 6, borderWidth: 1.5, borderColor: '#cbd5e1', alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff' },
  checkboxOuterSelected: { borderColor: '#0e7490', backgroundColor: '#0e7490' },
  checkMark: { color: '#fff', fontSize: 13, fontWeight: '900' },
  dualFactorRow: { flexDirection: 'row', alignItems: 'center', gap: 10, marginVertical: 14, padding: 10, backgroundColor: '#f8fafc', borderRadius: 10, borderWidth: 1, borderColor: '#e2e8f0' },
  dualFactorText: { flex: 1, fontSize: 11, color: '#334155', fontWeight: '600', lineHeight: 16 },
  receiptSuccessIcon: { width: 64, height: 64, borderRadius: 32, backgroundColor: '#ccfbf1', alignSelf: 'center', alignItems: 'center', justifyContent: 'center', marginTop: 8 },
  receiptCodeCard: { backgroundColor: '#f8fafc', padding: 16, borderRadius: 14, borderWidth: 1, borderColor: '#e2e8f0', marginVertical: 12 },
  receiptCodeText: { fontSize: 18, fontWeight: '900', color: '#0e7490', letterSpacing: 1.5, marginVertical: 4, fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace' },
  receiptHashText: { fontSize: 10, color: '#64748b', fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace', marginTop: 2 },

  // Phase 3: Audit Trail Styles
  auditRowItem: { paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#edf1f3' },
  auditRowSuspicious: { backgroundColor: '#fef2f2', paddingHorizontal: 10, borderRadius: 10, marginVertical: 4 },
  sequenceBadge: { backgroundColor: '#e2e8f0', paddingHorizontal: 6, paddingVertical: 3, borderRadius: 6 },
  sequenceBadgeText: { fontSize: 10, fontWeight: '900', color: '#334155', fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace' },
  hashChainBox: { backgroundColor: '#f1f5f9', padding: 6, borderRadius: 6, marginTop: 6 },
  hashChainText: { fontSize: 9, color: '#475569', fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace' }
});
