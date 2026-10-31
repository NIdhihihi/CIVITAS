export type Language = 'en' | 'hi' | 'mr';

export type Role = 'USER' | 'CANDIDATE' | 'TENANT_ADMIN' | 'PLATFORM_ADMIN' | 'AUDITOR';

export interface Member {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  emailVerified: boolean;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  member: Member;
}

export interface Tenant {
  id: string;
  name: string;
  code: string;
  city: string;
  createdBy: string;
}

export type ElectionStatus = 'DRAFT' | 'SCHEDULED' | 'ACTIVE' | 'CLOSED' | 'COUNTED' | 'CANCELLED';
export type VotingMode = 'CANDIDATE' | 'PANEL' | 'BOTH';

export interface ElectionSummary {
  id: string;
  tenantId: string;
  tenantName: string;
  tenantCode: string;
  title: string;
  description: string;
  startsAt: string;
  endsAt: string;
  status: ElectionStatus;
  notifyVoters: boolean;
  createdAt: string;
  countedAt: string | null;
  posts: number;
  candidates: number;
  voters: number;
  votesCast: number;
}

export interface ElectionPost {
  id: string;
  title: string;
  description: string;
  votingMode: VotingMode;
  sortOrder: number;
  candidateCount: number;
}

export interface Candidate {
  id: string;
  postId: string;
  postTitle: string;
  panelId: string | null;
  panelName: string | null;
  name: string;
  statement: string;
  photoUrl: string | null;
  status: 'APPROVED' | 'PENDING' | 'REJECTED' | 'WITHDRAWN';
  sortOrder: number;
  memberEmail: string | null;
}

export interface Panel {
  id: string;
  name: string;
  description: string;
  photoUrl: string | null;
  candidates: Candidate[];
}

export interface Voter {
  id: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  aadhaarLast4: string | null;
  eligiblePostIds: string;
  voted: boolean;
  votedAt: string | null;
  createdAt: string;
}

export interface VoterImportError {
  row: number;
  field: string;
  message: string;
}

export interface VoterImportResult {
  imported: number;
  skipped: number;
  errors: VoterImportError[];
  totalVoters: number;
}

export interface BallotCandidate {
  id: string;
  name: string;
  statement: string;
  photoUrl: string | null;
  panelId: string | null;
  panelName: string | null;
}

export interface BallotPost {
  id: string;
  title: string;
  description: string;
  votingMode: VotingMode;
  candidates: BallotCandidate[];
}

export interface BallotPanelMember {
  postId: string;
  postTitle: string;
  candidateId: string;
  candidateName: string;
  photoUrl: string | null;
}

export interface BallotPanel {
  id: string;
  name: string;
  description: string;
  photoUrl: string | null;
  members: BallotPanelMember[];
}

export interface Ballot {
  electionId: string;
  title: string;
  description: string;
  startsAt: string;
  endsAt: string;
  status: ElectionStatus;
  eligibleVoters: number;
  posts: BallotPost[];
  panels: BallotPanel[];
}

export interface PublicElection {
  id: string;
  title: string;
  society: string;
  description: string;
  startsAt: string;
  endsAt: string;
}

export interface PublicElectionDetail {
  id: string;
  title: string;
  society: string;
  description: string;
  startsAt: string;
  endsAt: string;
  status: ElectionStatus;
  eligibleVoters: number;
  posts: BallotPost[];
  panels: BallotPanel[];
}

export interface OtpChallenge {
  challengeId: string;
  electionId: string;
  method: string;
  maskedDestination: string;
  expiresAt: string;
  developmentCode: string | null;
}

export interface VotingSession {
  token: string;
  electionId: string;
  electionTitle: string;
  voterName: string;
  method: string;
  authenticatedAt: string;
  expiresAt: string;
  elapsedSeconds: number;
}

export interface BallotSession {
  electionId: string;
  electionTitle: string;
  voterName: string;
  ballot: Ballot;
}

export interface VoteSelection {
  postId: string;
  candidateId?: string;
  panelId?: string;
}

export interface Confirmation {
  confirmationRequired: boolean;
  maskedDestination: string;
  expiresAt: string;
  developmentCode: string | null;
}

export interface Receipt {
  receiptId: string;
  receiptCode: string | null;
  electionId: string;
  electionTitle: string;
  castAt: string;
  postsVoted: number;
  receiptHash: string;
  signature: string;
  verification: string;
}

export interface ContestantResult {
  id: string;
  name: string;
  photoUrl: string | null;
  votes: number;
  percentage: number;
}

export interface PostResult {
  postId: string;
  title: string;
  totalVotes: number;
  candidates: ContestantResult[];
  panels: ContestantResult[];
}

export interface Results {
  electionId: string;
  title: string;
  society: string;
  tenantId: string;
  status: ElectionStatus;
  countedAt: string | null;
  totalBallots: number;
  eligibleVoters: number;
  posts: PostResult[];
  resultHash: string;
  resultSignature: string;
  auditChainValid: boolean;
}

export interface AuditEntry {
  sequence: number;
  createdAt: string;
  action: string;
  actorType: string;
  actorHash: string;
  electionId: string | null;
  tenantId: string | null;
  resourceType: string | null;
  resourceId: string | null;
  ipHash: string | null;
  deviceHash: string | null;
  previousHash: string;
  entryHash: string;
  signature: string;
  metadata: string;
}

export interface AuditVerification {
  valid: boolean;
  entriesChecked: number;
  headSequence: number;
  message: string;
}

export interface FraudAlert {
  id: string;
  type: string;
  severity: string;
  details: string;
  electionId: string | null;
  createdAt: string;
  resolved: boolean;
  resolvedAt: string | null;
}

export interface Communication {
  id: string;
  channel: 'EMAIL' | 'SMS' | 'WHATSAPP';
  subject: string;
  template: string;
  status: string;
  scheduledAt: string;
  sentAt: string | null;
  sentCount: number;
  failedCount: number;
  automated: boolean;
}

export interface EligibleElection {
  electionId: string;
  title: string;
  society: string;
  description: string;
  startsAt: string;
  endsAt: string;
  status: ElectionStatus;
  voted: boolean;
  posts: number;
}
