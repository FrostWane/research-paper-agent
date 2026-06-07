import { useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { getDocument, PDFWorker } from 'pdfjs-dist';
import pdfWorkerSrc from 'pdfjs-dist/build/pdf.worker.mjs?url';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  Activity,
  BarChart3,
  BookOpen,
  Bot,
  Brain,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleStop,
  Clock3,
  Database,
  FileText,
  HardDrive,
  History,
  Layers,
  Loader2,
  LogIn,
  LogOut,
  MessageSquareText,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Send,
  ServerCog,
  ShieldCheck,
  SlidersHorizontal,
  ThumbsDown,
  ThumbsUp,
  Trash2,
  UserCog,
  Users,
  UploadCloud,
  ZoomIn,
  ZoomOut
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import {
  createAnswerPromptTemplate,
  createIntentRoute,
  createModelTarget,
  createSamplePrompt,
  createQueryTermMapping,
  deleteAnswerPromptTemplate,
  deleteIntentRoute,
  deleteModelTarget,
  deleteSamplePrompt,
  deleteQueryTermMapping,
  fetchAdminChunks,
  fetchAdminOverview,
  fetchAdminParseJobs,
  fetchAdminTraces,
  fetchAnswerPromptTemplates,
  fetchAgentToolExecutions,
  fetchAgentPipelineNodes,
  fetchAgentTools,
  fetchIntentRoutes,
  fetchIngestionPipelineNodes,
  fetchModelTargets,
  fetchRagSettings,
  fetchRetrievalChannels,
  fetchRetrievalProcessors,
  fetchSamplePrompts,
  fetchAdminUsers,
  fetchQueryTermMappings,
  updateAgentPipelineNodeEnabled,
  updateAnswerPromptTemplate,
  updateIntentRoute,
  updateModelTarget,
  updateSamplePrompt,
  updateAdminUserStatus,
  updateAdminChunkEnabled,
  updateAgentToolEnabled,
  updateAgentToolMinimumRole,
  updateRagSettings,
  updateQueryTermMapping
} from './api/admin';
import {
  createChatSession,
  listChats,
  listChatSessions,
  listLibraryChats,
  listSamplePrompts,
  listSessionChats,
  streamAskAgent,
  submitChatFeedback,
  updateChatSession
} from './api/agent';
import { login, me, register } from './api/auth';
import { fetchPdfPreview, uploadPaperFile } from './api/files';
import { clearToken, getToken, setToken } from './api/request';
import { createPaper, deletePaper, listPapers, parsePaper, unparsePaper, updatePaperStatus } from './api/papers';
import type { AdminAgentPipelineNode, AdminAgentTool, AdminAgentToolExecution, AdminChatRateLimit, AdminChunk, AdminIngestionPipelineNode, AdminModelHealth, AdminOverview, AdminParseJob, AdminRetrievalChannelCatalog, AdminRetrievalProcessorCatalog, AdminTrace, AdminUser, AnswerPromptTemplate, ChatRecord, ChatResponse, ChatSession, ChatStreamTask, IntentRoute, ModelTarget, PageResponse, Paper, PaperForm, QueryTermMapping, RagSettings, SamplePrompt, SourceResponse, User } from './types';

const markdownPlugins = [remarkGfm];
const PDF_CACHE_DB = 'research-paper-agent-cache';
const PDF_CACHE_STORE = 'pdf-previews';
const PDF_CACHE_VERSION = 1;
const PDF_WORKER_CACHE_VERSION = '2026-06-04-2';

function createPdfLoadingTask(data: ArrayBuffer | ArrayBufferView) {
  const worker = createPdfWorker();
  const task = getDocument({
    data: new Uint8Array(cloneArrayBuffer(data)),
    disableAutoFetch: true,
    disableStream: true,
    worker
  });
  return { task, worker };
}

function createPdfWorker() {
  const workerUrl = new URL(pdfWorkerSrc, window.location.href);
  workerUrl.searchParams.set('v', PDF_WORKER_CACHE_VERSION);
  const port = new Worker(workerUrl, {
    type: 'module',
    name: 'paper-agent-pdf-worker'
  });
  return PDFWorker.create({ port });
}

const emptyForm: PaperForm = {
  title: '',
  authors: '',
  venue: '',
  year: String(new Date().getFullYear()),
  keywords: '',
  abstractText: '',
  note: ''
};

const quickPrompts = [
  '请概括这篇论文的研究问题、方法和主要结论。',
  '请提炼本文的三个创新点，并说明依据。',
  '请总结实验设计、数据集、评价指标和结果。',
  '请指出这篇论文可能的局限性和后续研究方向。'
];

const libraryQuickPrompts = [
  '请比较这些论文的核心方法差异。',
  '请梳理当前文献库围绕的主要研究问题。',
  '请总结这些论文中常见的数据集、指标和实验结论。',
  '请给出一份适合写综述的结构化大纲。'
];

type ViewKey = 'library' | 'upload' | 'reader' | 'libraryChat' | 'history' | 'admin';
type AuthMode = 'login' | 'register';
type ReaderScope = 'paper' | 'library';
type PdfJump = { paperId: number; page: number; signal: number };
type SamplePromptInput = {
  scope: 'PAPER' | 'LIBRARY';
  title: string;
  prompt: string;
  description: string;
  sortOrder: number;
};
type IntentRouteInput = {
  intentCode: string;
  label: string;
  description: string;
  keywords: string;
  searchHint: string;
  answerStrategy: string;
  answerContract: string;
  boundToolName: string;
  comparisonEnabled: boolean;
  sortOrder: number;
};
type AnswerPromptTemplateInput = {
  code: string;
  name: string;
  description: string;
  systemPrompt: string;
  userPromptTemplate: string;
  defaultTemplate: boolean;
  sortOrder: number;
};
type ModelTargetInput = {
  code: string;
  provider: string;
  taskType: string;
  modelName: string;
  description: string;
  baseUrl: string;
  apiKey: string;
  priority: number;
  timeoutSeconds: number;
};
type RagSettingsInput = {
  candidateLimit: number;
  resultLimit: number;
  sourceExcerptChars: number;
  vectorWeight: number;
  keywordWeight: number;
  memoryHistoryTurns: number;
  memoryMaxChars: number;
  memorySummaryEnabled: boolean;
  memorySummaryStartTurns: number;
  memorySummaryMaxChars: number;
  queryRewriteEnabled: boolean;
  queryRewriteMaxSubQuestions: number;
  answerQualityJudgeEnabled: boolean;
  rerankModelEnabled: boolean;
  rerankModelMaxCandidates: number;
  chatRateLimitEnabled: boolean;
  chatRateLimitGlobalConcurrency: number;
  chatRateLimitUserConcurrency: number;
  chatRateLimitUserPerMinute: number;
};
type RagSettingsFormState = Omit<Record<keyof RagSettingsInput, string>, 'memorySummaryEnabled' | 'queryRewriteEnabled' | 'answerQualityJudgeEnabled' | 'rerankModelEnabled' | 'chatRateLimitEnabled'> & {
  memorySummaryEnabled: boolean;
  queryRewriteEnabled: boolean;
  answerQualityJudgeEnabled: boolean;
  rerankModelEnabled: boolean;
  chatRateLimitEnabled: boolean;
};
type AdminTraceFilters = {
  status: string;
  scope: string;
  sessionId: string;
  keyword: string;
};
type AdminAgentToolExecutionFilters = {
  toolName: string;
  status: string;
  keyword: string;
};
type AdminChunkFilters = {
  paperId: string;
  keyword: string;
};
type AdminParseJobFilters = {
  status: string;
  keyword: string;
};

const defaultRagSettingsInput: RagSettingsInput = {
  candidateLimit: 10,
  resultLimit: 5,
  sourceExcerptChars: 520,
  vectorWeight: 1,
  keywordWeight: 0.78,
  memoryHistoryTurns: 4,
  memoryMaxChars: 2400,
  memorySummaryEnabled: true,
  memorySummaryStartTurns: 6,
  memorySummaryMaxChars: 1800,
  queryRewriteEnabled: true,
  queryRewriteMaxSubQuestions: 3,
  answerQualityJudgeEnabled: true,
  rerankModelEnabled: false,
  rerankModelMaxCandidates: 8,
  chatRateLimitEnabled: true,
  chatRateLimitGlobalConcurrency: 12,
  chatRateLimitUserConcurrency: 2,
  chatRateLimitUserPerMinute: 20
};
const defaultTraceFilters: AdminTraceFilters = {
  status: '',
  scope: '',
  sessionId: '',
  keyword: ''
};
const defaultAgentToolExecutionFilters: AdminAgentToolExecutionFilters = {
  toolName: '',
  status: '',
  keyword: ''
};
const defaultChunkFilters: AdminChunkFilters = {
  paperId: '',
  keyword: ''
};
const defaultParseJobFilters: AdminParseJobFilters = {
  status: '',
  keyword: ''
};

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [credentials, setCredentials] = useState({ username: '', email: '', account: '', password: '' });
  const [activeView, setActiveView] = useState<ViewKey>('library');
  const [papers, setPapers] = useState<Paper[]>([]);
  const [selectedPaperId, setSelectedPaperId] = useState<number | null>(null);
  const [readerScope, setReaderScope] = useState<ReaderScope>('paper');
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [form, setForm] = useState<PaperForm>(emptyForm);
  const [file, setFile] = useState<File | null>(null);
  const [metadataStatus, setMetadataStatus] = useState('');
  const [chatSessions, setChatSessions] = useState<ChatSession[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);
  const [chats, setChats] = useState<ChatRecord[]>([]);
  const [question, setQuestion] = useState('');
  const [agentStreaming, setAgentStreaming] = useState(false);
  const [streamStatus, setStreamStatus] = useState('');
  const [pdfJump, setPdfJump] = useState<PdfJump | null>(null);
  const [adminOverview, setAdminOverview] = useState<AdminOverview | null>(null);
  const [adminTracePage, setAdminTracePage] = useState<PageResponse<AdminTrace> | null>(null);
  const [adminTraceFilters, setAdminTraceFilters] = useState<AdminTraceFilters>(defaultTraceFilters);
  const [agentToolExecutionPage, setAgentToolExecutionPage] = useState<PageResponse<AdminAgentToolExecution> | null>(null);
  const [agentToolExecutionFilters, setAgentToolExecutionFilters] = useState<AdminAgentToolExecutionFilters>(defaultAgentToolExecutionFilters);
  const [adminChunkPage, setAdminChunkPage] = useState<PageResponse<AdminChunk> | null>(null);
  const [adminChunkFilters, setAdminChunkFilters] = useState<AdminChunkFilters>(defaultChunkFilters);
  const [adminParseJobPage, setAdminParseJobPage] = useState<PageResponse<AdminParseJob> | null>(null);
  const [adminParseJobFilters, setAdminParseJobFilters] = useState<AdminParseJobFilters>(defaultParseJobFilters);
  const [adminUsers, setAdminUsers] = useState<AdminUser[]>([]);
  const [agentPipelineNodes, setAgentPipelineNodes] = useState<AdminAgentPipelineNode[]>([]);
  const [ingestionPipelineNodes, setIngestionPipelineNodes] = useState<AdminIngestionPipelineNode[]>([]);
  const [retrievalChannels, setRetrievalChannels] = useState<AdminRetrievalChannelCatalog[]>([]);
  const [retrievalProcessors, setRetrievalProcessors] = useState<AdminRetrievalProcessorCatalog[]>([]);
  const [agentTools, setAgentTools] = useState<AdminAgentTool[]>([]);
  const [queryMappings, setQueryMappings] = useState<QueryTermMapping[]>([]);
  const [intentRoutes, setIntentRoutes] = useState<IntentRoute[]>([]);
  const [answerPromptTemplates, setAnswerPromptTemplates] = useState<AnswerPromptTemplate[]>([]);
  const [modelTargets, setModelTargets] = useState<ModelTarget[]>([]);
  const [samplePrompts, setSamplePrompts] = useState<SamplePrompt[]>([]);
  const [ragSettings, setRagSettings] = useState<RagSettings | null>(null);
  const [paperPrompts, setPaperPrompts] = useState<string[]>(quickPrompts);
  const [libraryPrompts, setLibraryPrompts] = useState<string[]>(libraryQuickPrompts);
  const [adminLoading, setAdminLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busyText, setBusyText] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const noticeTimerRef = useRef<number | null>(null);
  const streamAbortRef = useRef<(() => void) | null>(null);

  const selectedPaper = useMemo(
    () => papers.find((paper) => paper.id === selectedPaperId) || papers[0] || null,
    [papers, selectedPaperId]
  );

  const stats = useMemo(() => {
    const indexed = papers.filter((paper) => paper.processStatus === 'INDEXED').length;
    const intensive = papers.filter((paper) => paper.status === 'INTENSIVE_READ').length;
    return { total: papers.length, intensive, indexed, chats: chats.length };
  }, [papers, chats.length]);

  useEffect(() => {
    void bootstrap();
    return () => {
      if (noticeTimerRef.current) {
        window.clearTimeout(noticeTimerRef.current);
      }
      streamAbortRef.current?.();
    };
  }, []);

  useEffect(() => {
    if (activeView === 'libraryChat') {
      void loadSessionList(null);
    } else if (activeView === 'reader' && selectedPaper?.id) {
      void loadSessionList(selectedPaper.id);
    } else if (activeView === 'history' && readerScope === 'library') {
      void loadLibraryChatList();
    } else if (activeView === 'history' && selectedPaper?.id) {
      void loadChatList(selectedPaper.id);
    } else {
      setChatSessions([]);
      setSelectedSessionId(null);
      setChats([]);
    }
  }, [activeView, readerScope, selectedPaper?.id]);

  useEffect(() => {
    if (activeView === 'admin' && user?.role === 'ADMIN') {
      void loadAdminData();
    }
  }, [activeView, user?.role]);

  async function bootstrap() {
    try {
      setLoading(true);
      setError('');
      if (getToken()) {
        const current = await me();
        setUser(current);
        await loadPaperList();
        await loadPromptPresets();
      }
    } catch (err) {
      clearToken();
      setUser(null);
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  async function loadPaperList() {
    const result = await listPapers({ keyword: query, status: statusFilter, page: 1, pageSize: 60 });
    setPapers(result.items);
    setSelectedPaperId((current) => current ?? result.items[0]?.id ?? null);
  }

  async function loadChatList(paperId: number) {
    try {
      const result = await listChats(paperId);
      setChats(result);
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }

  async function loadLibraryChatList() {
    try {
      const result = await listLibraryChats();
      setChats(result);
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }

  async function loadSessionList(paperId: number | null, preferredSessionId = selectedSessionId) {
    try {
      const sessions = await listChatSessions(paperId);
      setChatSessions(sessions);
      const nextSessionId =
        preferredSessionId && sessions.some((session) => session.id === preferredSessionId)
          ? preferredSessionId
          : sessions[0]?.id ?? null;
      setSelectedSessionId(nextSessionId);
      if (nextSessionId) {
        await loadSessionChatList(nextSessionId);
      } else {
        setChats([]);
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }

  async function loadSessionChatList(sessionId: number) {
    try {
      const result = await listSessionChats(sessionId);
      setChats(result);
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }

  async function loadPromptPresets() {
    try {
      const [paper, library] = await Promise.all([listSamplePrompts('PAPER'), listSamplePrompts('LIBRARY')]);
      setPaperPrompts(promptTexts(paper, quickPrompts));
      setLibraryPrompts(promptTexts(library, libraryQuickPrompts));
    } catch {
      setPaperPrompts(quickPrompts);
      setLibraryPrompts(libraryQuickPrompts);
    }
  }

  async function handleAuthSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setBusyText(authMode === 'login' ? '正在登录...' : '正在创建账号...');
    try {
      const response =
        authMode === 'login'
          ? await login(credentials.account.trim(), credentials.password)
          : await register(credentials.username.trim(), credentials.email.trim(), credentials.password);
      setToken(response.token);
      setUser(response.user);
      setCredentials({ username: '', email: '', account: '', password: '' });
      await loadPaperList();
      await loadPromptPresets();
      showNotice('已进入 Research Paper Agent 工作台。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  function signOut() {
    streamAbortRef.current?.();
    streamAbortRef.current = null;
    clearToken();
    setUser(null);
    setPapers([]);
    setSelectedPaperId(null);
    setReaderScope('paper');
    setChatSessions([]);
    setSelectedSessionId(null);
    setChats([]);
    setAdminOverview(null);
    setAdminTracePage(null);
    setAdminTraceFilters(defaultTraceFilters);
    setAgentToolExecutionPage(null);
    setAgentToolExecutionFilters(defaultAgentToolExecutionFilters);
    setAdminChunkPage(null);
    setAdminChunkFilters(defaultChunkFilters);
    setAdminParseJobPage(null);
    setAdminParseJobFilters(defaultParseJobFilters);
    setAdminUsers([]);
    setAgentPipelineNodes([]);
    setIngestionPipelineNodes([]);
    setRetrievalChannels([]);
    setRetrievalProcessors([]);
    setAgentTools([]);
    setQueryMappings([]);
    setIntentRoutes([]);
    setAnswerPromptTemplates([]);
    setModelTargets([]);
    setSamplePrompts([]);
    setRagSettings(null);
    setAgentStreaming(false);
    setStreamStatus('');
    setPaperPrompts(quickPrompts);
    setLibraryPrompts(libraryQuickPrompts);
    setActiveView('library');
    showNotice('已退出登录。');
  }

  async function handleSearch(event?: React.FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    try {
      setBusyText('正在刷新文献库...');
      await loadPaperList();
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleSelectFile(nextFile: File | null) {
    setFile(nextFile);
    setError('');
    setMetadataStatus('');
    if (!nextFile) {
      return;
    }
    if (!isPdfFile(nextFile)) {
      setError('请上传 PDF 文件。');
      return;
    }
    setMetadataStatus('正在识别 PDF 题录信息...');
    try {
      const metadata = await extractPdfMetadata(nextFile);
      setForm((current) => ({
        ...current,
        title: current.title || metadata.title,
        authors: current.authors || metadata.authors,
        keywords: current.keywords || metadata.keywords,
        year: current.year || String(metadata.year || new Date().getFullYear())
      }));
      setMetadataStatus(metadata.extractedFromPdf ? '已自动识别题录信息，可继续微调。' : '已根据文件名填充标题，可手动补充作者等信息。');
    } catch (err) {
      setForm((current) => ({ ...current, title: current.title || titleFromFileName(nextFile.name) }));
      setMetadataStatus('自动识别失败，已根据文件名填充标题。');
    }
  }

  async function handleUpload(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    if (!file) {
      setError('请先选择一篇 PDF 文献。');
      return;
    }
    try {
      setBusyText('正在上传 PDF...');
      const uploaded = await uploadPaperFile(file);
      setBusyText('正在创建文献记录...');
      const paper = await createPaper({ ...form, title: form.title || titleFromFileName(file.name) }, uploaded.fileId);
      setBusyText('正在解析 PDF 正文...');
      try {
        await parsePaper(paper.id);
      } catch {
        showNotice('文献已创建，PDF 解析稍后可在阅读页重试。');
      }
      setForm(emptyForm);
      setFile(null);
      setMetadataStatus('');
      await loadPaperList();
      setSelectedPaperId(paper.id);
      setReaderScope('paper');
      setActiveView('reader');
      showNotice('文献已加入工作台。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleAsk(prompt = question) {
    const isLibraryQuestion = activeView === 'libraryChat' || readerScope === 'library';
    const promptText = prompt.trim();
    if (agentStreaming || (!isLibraryQuestion && !selectedPaper) || !promptText) {
      return;
    }
    const paperId = isLibraryQuestion ? null : selectedPaper!.id;
    const pendingId = -Date.now();
    const finalResponseRef: { current: ChatResponse | null } = { current: null };
    let cancelledByServer = false;
    try {
      setError('');
      setQuestion('');
      setAgentStreaming(true);
      setStreamStatus('正在连接流式问答服务...');
      setBusyText('多 Agent 正在检索、生成并校验引用...');
      setChats((current) => [
        ...current,
        pendingChatRecord(pendingId, paperId, selectedSessionId, promptText, '正在连接流式问答服务...')
      ]);
      const stream = streamAskAgent(paperId, promptText, true, selectedSessionId, {
        onEvent: (eventName, payload) => {
          const nextMessage = payload.message || streamEventMessage(eventName);
          if (eventName === 'cancelled' || payload.phase === 'cancelled' || payload.cancelled) {
            cancelledByServer = true;
          }
          if (payload.response) {
            finalResponseRef.current = payload.response;
          }
          setStreamStatus(nextMessage);
          setChats((current) =>
            current.map((chat) =>
              chat.id === pendingId
                ? {
                    ...chat,
                    answer: payload.response ? payload.response.answer : finalResponseRef.current ? chat.answer : streamChatContent(nextMessage),
                    sources: payload.response?.sources ?? chat.sources,
                    modelName: payload.response?.modelName ?? chat.modelName,
                    latencyMs: payload.response?.latencyMs ?? chat.latencyMs
                  }
                : chat
            )
          );
        }
      });
      streamAbortRef.current = stream.abort;
      await stream.done;
      const finalResponse = finalResponseRef.current;
      if (!finalResponse) {
        if (cancelledByServer) {
          const message = '已停止等待本次流式回答。';
          setChats((current) =>
            current.map((chat) => (chat.id === pendingId ? { ...chat, answer: streamChatContent(message) } : chat))
          );
          showNotice(message);
          return;
        }
        const message = '流式问答结束，但没有返回最终回答。';
        setError(message);
        setChats((current) =>
          current.map((chat) => (chat.id === pendingId ? { ...chat, answer: streamChatContent(message) } : chat))
        );
        return;
      }
      if (activeView === 'libraryChat' || activeView === 'reader') {
        await loadSessionList(paperId, finalResponse.sessionId);
      } else if (isLibraryQuestion) {
        await loadLibraryChatList();
      } else {
        await loadChatList(selectedPaper!.id);
      }
      showNotice('回答已保存到问答历史。');
    } catch (err) {
      if (isAbortError(err)) {
        const message = '已停止等待本次流式回答。';
        setChats((current) =>
          current.map((chat) => (chat.id === pendingId ? { ...chat, answer: streamChatContent(message) } : chat))
        );
        showNotice(message);
      } else {
        const message = extractErrorMessage(err);
        setError(message);
        setChats((current) =>
          current.map((chat) => (chat.id === pendingId ? { ...chat, answer: streamChatContent(`回答失败：${message}`) } : chat))
        );
      }
    } finally {
      streamAbortRef.current = null;
      setAgentStreaming(false);
      setStreamStatus('');
      setBusyText('');
    }
  }

  function handleCancelAgentStream() {
    streamAbortRef.current?.();
  }

  async function handleChatFeedback(chat: ChatRecord, score: 1 | -1) {
    const nextScore = chat.feedbackScore === score ? null : score;
    try {
      const updated = await submitChatFeedback(chat.id, nextScore);
      setChats((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      if (activeView === 'admin' && user?.role === 'ADMIN') {
        setAdminOverview(await fetchAdminOverview());
      }
      showNotice(nextScore === null ? '已取消反馈。' : '反馈已记录。');
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }

  async function handleCreateChatSession() {
    const paperId = activeView === 'libraryChat' || readerScope === 'library' ? null : selectedPaper?.id ?? null;
    try {
      setBusyText('正在创建新会话...');
      const session = await createChatSession(paperId, '新对话');
      setChatSessions((current) => [session, ...current.filter((item) => item.id !== session.id)]);
      setSelectedSessionId(session.id);
      setChats([]);
      showNotice('新会话已创建。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleSelectChatSession(session: ChatSession) {
    try {
      setSelectedSessionId(session.id);
      await loadSessionChatList(session.id);
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }

  async function handleRenameChatSession(session: ChatSession) {
    const title = window.prompt('请输入新的会话标题', session.title);
    if (title == null) {
      return;
    }
    try {
      const updated = await updateChatSession(session.id, { title });
      setChatSessions((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      showNotice('会话标题已更新。');
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }

  async function handleArchiveChatSession(session: ChatSession) {
    const confirmed = window.confirm('归档后该会话会从当前会话列表隐藏，历史问答仍然保留。确定继续吗？');
    if (!confirmed) {
      return;
    }
    const paperId = session.paperId ?? null;
    try {
      setBusyText('正在归档会话...');
      await updateChatSession(session.id, { archived: true });
      await loadSessionList(paperId, session.id === selectedSessionId ? null : selectedSessionId);
      showNotice('会话已归档。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleToggleRead(paper: Paper) {
    try {
      const next = paper.status === 'INTENSIVE_READ' ? 'TO_READ' : 'INTENSIVE_READ';
      await updatePaperStatus(paper.id, next);
      await loadPaperList();
      showNotice(next === 'INTENSIVE_READ' ? '已标记为已精读。' : '已标记为待阅读。');
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }

  async function handleDeletePaper(paper: Paper) {
    try {
      await deletePaper(paper.id);
      await loadPaperList();
      showNotice('文献已删除。');
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }

  async function handleParse(paper: Paper) {
    try {
      setBusyText('正在解析 PDF 正文...');
      await parsePaper(paper.id);
      await loadPaperList();
      showNotice('PDF 已解析完成。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleUnparse(paper: Paper) {
    const confirmed = window.confirm('取消解析会从知识库移除这篇论文的文本片段和向量索引，但不会删除 PDF 文件。确定继续吗？');
    if (!confirmed) {
      return;
    }
    try {
      setBusyText('正在从知识库移除解析结果...');
      await unparsePaper(paper.id);
      await loadPaperList();
      showNotice('已取消解析，并从知识库移除该论文片段。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function loadAdminData() {
    try {
      setAdminLoading(true);
      setError('');
      const [overview, traces, toolExecutions, chunks, parseJobs, users, pipelineNodes, ingestionNodes, retrievalChannelItems, retrievalProcessorItems, tools, mappings, routes, templates, targets, prompts, settings] = await Promise.all([
        fetchAdminOverview(),
        fetchAdminTraces({ ...adminTraceFilters, page: adminTracePage?.page ?? 1, pageSize: adminTracePage?.pageSize ?? 12 }),
        fetchAgentToolExecutions({ ...agentToolExecutionFilters, page: agentToolExecutionPage?.page ?? 1, pageSize: agentToolExecutionPage?.pageSize ?? 10 }),
        fetchAdminChunks({ ...adminChunkFilters, page: adminChunkPage?.page ?? 1, pageSize: adminChunkPage?.pageSize ?? 8 }),
        fetchAdminParseJobs({ ...adminParseJobFilters, page: adminParseJobPage?.page ?? 1, pageSize: adminParseJobPage?.pageSize ?? 10 }),
        fetchAdminUsers(),
        fetchAgentPipelineNodes(),
        fetchIngestionPipelineNodes(),
        fetchRetrievalChannels(),
        fetchRetrievalProcessors(),
        fetchAgentTools(),
        fetchQueryTermMappings(),
        fetchIntentRoutes(),
        fetchAnswerPromptTemplates(),
        fetchModelTargets(),
        fetchSamplePrompts(),
        fetchRagSettings()
      ]);
      setAdminOverview(overview);
      setAdminTracePage(traces);
      setAgentToolExecutionPage(toolExecutions);
      setAdminChunkPage(chunks);
      setAdminParseJobPage(parseJobs);
      setAdminUsers(users);
      setAgentPipelineNodes(pipelineNodes);
      setIngestionPipelineNodes(ingestionNodes);
      setRetrievalChannels(retrievalChannelItems);
      setRetrievalProcessors(retrievalProcessorItems);
      setAgentTools(tools);
      setQueryMappings(mappings);
      setIntentRoutes(routes);
      setAnswerPromptTemplates(templates);
      setModelTargets(targets);
      setSamplePrompts(prompts);
      setRagSettings(settings);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setAdminLoading(false);
    }
  }

  async function loadAdminTracePage(nextFilters = adminTraceFilters, page = adminTracePage?.page ?? 1) {
    try {
      setAdminLoading(true);
      setError('');
      const traces = await fetchAdminTraces({ ...nextFilters, page, pageSize: adminTracePage?.pageSize ?? 12 });
      setAdminTracePage(traces);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setAdminLoading(false);
    }
  }

  async function handleAdminTraceFilterChange(patch: Partial<AdminTraceFilters>) {
    const nextFilters = { ...adminTraceFilters, ...patch };
    setAdminTraceFilters(nextFilters);
    await loadAdminTracePage(nextFilters, 1);
  }

  async function loadAgentToolExecutionPage(nextFilters = agentToolExecutionFilters, page = agentToolExecutionPage?.page ?? 1) {
    try {
      setAdminLoading(true);
      setError('');
      const executions = await fetchAgentToolExecutions({ ...nextFilters, page, pageSize: agentToolExecutionPage?.pageSize ?? 10 });
      setAgentToolExecutionPage(executions);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setAdminLoading(false);
    }
  }

  async function handleAgentToolExecutionFilterChange(patch: Partial<AdminAgentToolExecutionFilters>) {
    const nextFilters = { ...agentToolExecutionFilters, ...patch };
    setAgentToolExecutionFilters(nextFilters);
    await loadAgentToolExecutionPage(nextFilters, 1);
  }

  async function loadAdminChunkPage(nextFilters = adminChunkFilters, page = adminChunkPage?.page ?? 1) {
    try {
      setAdminLoading(true);
      setError('');
      const chunks = await fetchAdminChunks({ ...nextFilters, page, pageSize: adminChunkPage?.pageSize ?? 8 });
      setAdminChunkPage(chunks);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setAdminLoading(false);
    }
  }

  async function handleAdminChunkFilterChange(patch: Partial<AdminChunkFilters>) {
    const nextFilters = { ...adminChunkFilters, ...patch };
    setAdminChunkFilters(nextFilters);
    await loadAdminChunkPage(nextFilters, 1);
  }

  async function loadAdminParseJobPage(nextFilters = adminParseJobFilters, page = adminParseJobPage?.page ?? 1) {
    try {
      setAdminLoading(true);
      setError('');
      const parseJobs = await fetchAdminParseJobs({ ...nextFilters, page, pageSize: adminParseJobPage?.pageSize ?? 10 });
      setAdminParseJobPage(parseJobs);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setAdminLoading(false);
    }
  }

  async function handleAdminParseJobFilterChange(patch: Partial<AdminParseJobFilters>) {
    const nextFilters = { ...adminParseJobFilters, ...patch };
    setAdminParseJobFilters(nextFilters);
    await loadAdminParseJobPage(nextFilters, 1);
  }

  async function handleAdminChunkEnabled(chunk: AdminChunk, enabled: boolean) {
    try {
      setBusyText(enabled ? '正在启用知识片段...' : '正在禁用知识片段...');
      const updated = await updateAdminChunkEnabled(chunk.id, enabled);
      setAdminChunkPage((current) => current
        ? {
            ...current,
            items: current.items.map((item) => (item.id === updated.id ? updated : item))
          }
        : current);
      showNotice(enabled ? '知识片段已重新参与检索。' : '知识片段已从检索召回中下线。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleAdminAgentToolEnabled(tool: AdminAgentTool, enabled: boolean) {
    try {
      setBusyText(enabled ? '正在启用 Agent 工具...' : '正在停用 Agent 工具...');
      const updated = await updateAgentToolEnabled(tool.name, enabled);
      setAgentTools((current) => current.map((item) => (item.name === updated.name ? updated : item)));
      setAdminOverview(await fetchAdminOverview());
      showNotice(enabled ? 'Agent 工具已启用，会重新参与问答链路。' : 'Agent 工具已停用，后续问答会跳过它。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleAdminAgentToolMinimumRole(tool: AdminAgentTool, minimumRole: 'USER' | 'ADMIN') {
    try {
      setBusyText(minimumRole === 'ADMIN' ? '正在限制 Agent 工具权限...' : '正在开放 Agent 工具权限...');
      const updated = await updateAgentToolMinimumRole(tool.name, minimumRole);
      setAgentTools((current) => current.map((item) => (item.name === updated.name ? updated : item)));
      setAdminOverview(await fetchAdminOverview());
      showNotice(minimumRole === 'ADMIN' ? 'Agent 工具已限制为仅管理员可调用。' : 'Agent 工具已开放给所有登录用户调用。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleAdminAgentPipelineNodeEnabled(node: AdminAgentPipelineNode, enabled: boolean) {
    try {
      setBusyText(enabled ? '正在启用 Agent Pipeline 节点...' : '正在停用 Agent Pipeline 节点...');
      const updated = await updateAgentPipelineNodeEnabled(node.name, enabled);
      setAgentPipelineNodes((current) => current.map((item) => (item.name === updated.name ? updated : item)));
      setAdminOverview(await fetchAdminOverview());
      showNotice(enabled ? 'Agent Pipeline 节点已启用。' : 'Agent Pipeline 节点已停用，后续问答会记录为跳过。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleAdminUserStatus(userItem: AdminUser, status: 'NORMAL' | 'DISABLED') {
    try {
      setBusyText(status === 'DISABLED' ? '正在禁用用户...' : '正在恢复用户...');
      const updated = await updateAdminUserStatus(userItem.id, status);
      setAdminUsers((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setAdminOverview(await fetchAdminOverview());
      showNotice(status === 'DISABLED' ? '用户已禁用。' : '用户已恢复。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleCreateQueryMapping(input: { term: string; expansions: string }) {
    try {
      setBusyText('正在保存术语映射...');
      const created = await createQueryTermMapping({ ...input, enabled: true });
      setQueryMappings((current) => [created, ...current]);
      setAdminOverview(await fetchAdminOverview());
      showNotice('术语映射已添加。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleUpdateQueryMapping(mapping: QueryTermMapping, patch: Partial<Pick<QueryTermMapping, 'term' | 'expansions' | 'enabled'>>) {
    try {
      setBusyText('正在更新术语映射...');
      const updated = await updateQueryTermMapping(mapping.id, {
        term: patch.term ?? mapping.term,
        expansions: patch.expansions ?? mapping.expansions,
        enabled: patch.enabled ?? mapping.enabled
      });
      setQueryMappings((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setAdminOverview(await fetchAdminOverview());
      showNotice('术语映射已更新。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleDeleteQueryMapping(mapping: QueryTermMapping) {
    const confirmed = window.confirm(`删除术语映射「${mapping.term}」？`);
    if (!confirmed) {
      return;
    }
    try {
      setBusyText('正在删除术语映射...');
      await deleteQueryTermMapping(mapping.id);
      setQueryMappings((current) => current.filter((item) => item.id !== mapping.id));
      setAdminOverview(await fetchAdminOverview());
      showNotice('术语映射已删除。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleCreateIntentRoute(input: IntentRouteInput) {
    try {
      setBusyText('正在保存意图路由...');
      const created = await createIntentRoute({ ...input, enabled: true });
      setIntentRoutes((current) => [...current, created].sort(compareIntentRoutes));
      setAdminOverview(await fetchAdminOverview());
      showNotice('意图路由已添加。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleUpdateIntentRoute(route: IntentRoute, patch: Partial<IntentRouteInput & { enabled: boolean }>) {
    try {
      setBusyText('正在更新意图路由...');
      const updated = await updateIntentRoute(route.id, {
        intentCode: patch.intentCode ?? route.intentCode,
        label: patch.label ?? route.label,
        description: patch.description ?? route.description ?? '',
        keywords: patch.keywords ?? route.keywords,
        searchHint: patch.searchHint ?? route.searchHint ?? '',
        answerStrategy: patch.answerStrategy ?? route.answerStrategy,
        answerContract: patch.answerContract ?? route.answerContract ?? '',
        boundToolName: patch.boundToolName ?? route.boundToolName ?? '',
        comparisonEnabled: patch.comparisonEnabled ?? route.comparisonEnabled,
        enabled: patch.enabled ?? route.enabled,
        sortOrder: patch.sortOrder ?? route.sortOrder
      });
      setIntentRoutes((current) => current.map((item) => (item.id === updated.id ? updated : item)).sort(compareIntentRoutes));
      setAdminOverview(await fetchAdminOverview());
      showNotice('意图路由已更新。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleDeleteIntentRoute(route: IntentRoute) {
    const confirmed = window.confirm(`删除意图路由「${route.label}」？`);
    if (!confirmed) {
      return;
    }
    try {
      setBusyText('正在删除意图路由...');
      await deleteIntentRoute(route.id);
      setIntentRoutes((current) => current.filter((item) => item.id !== route.id));
      setAdminOverview(await fetchAdminOverview());
      showNotice('意图路由已删除。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleCreateAnswerPromptTemplate(input: AnswerPromptTemplateInput) {
    try {
      setBusyText('正在保存回答模板...');
      const created = await createAnswerPromptTemplate({ ...input, enabled: true });
      setAnswerPromptTemplates((current) => [...current, created].sort(compareAnswerPromptTemplates));
      setAdminOverview(await fetchAdminOverview());
      showNotice('回答模板已添加。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleUpdateAnswerPromptTemplate(template: AnswerPromptTemplate, patch: Partial<AnswerPromptTemplateInput & { enabled: boolean }>) {
    try {
      setBusyText('正在更新回答模板...');
      const updated = await updateAnswerPromptTemplate(template.id, {
        code: patch.code ?? template.code,
        name: patch.name ?? template.name,
        description: patch.description ?? template.description ?? '',
        systemPrompt: patch.systemPrompt ?? template.systemPrompt,
        userPromptTemplate: patch.userPromptTemplate ?? template.userPromptTemplate,
        defaultTemplate: patch.defaultTemplate ?? template.defaultTemplate,
        enabled: patch.enabled ?? template.enabled,
        sortOrder: patch.sortOrder ?? template.sortOrder
      });
      setAnswerPromptTemplates((current) => current.map((item) => (item.id === updated.id ? updated : item)).sort(compareAnswerPromptTemplates));
      setAdminOverview(await fetchAdminOverview());
      showNotice('回答模板已更新。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleDeleteAnswerPromptTemplate(template: AnswerPromptTemplate) {
    const confirmed = window.confirm(`删除回答模板「${template.name}」？`);
    if (!confirmed) {
      return;
    }
    try {
      setBusyText('正在删除回答模板...');
      await deleteAnswerPromptTemplate(template.id);
      setAnswerPromptTemplates((current) => current.filter((item) => item.id !== template.id));
      setAdminOverview(await fetchAdminOverview());
      showNotice('回答模板已删除。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleCreateModelTarget(input: ModelTargetInput) {
    try {
      setBusyText('正在保存模型目标...');
      const created = await createModelTarget({ ...input, enabled: true });
      setModelTargets((current) => [...current, created].sort(compareModelTargets));
      setAdminOverview(await fetchAdminOverview());
      showNotice('模型目标已添加。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleUpdateModelTarget(target: ModelTarget, patch: Partial<ModelTargetInput & { enabled: boolean }>) {
    try {
      setBusyText('正在更新模型目标...');
      const updated = await updateModelTarget(target.id, {
        code: patch.code ?? target.code,
        provider: patch.provider ?? target.provider,
        taskType: patch.taskType ?? target.taskType ?? 'GENERAL',
        modelName: patch.modelName ?? target.modelName,
        description: patch.description ?? target.description ?? '',
        baseUrl: patch.baseUrl ?? target.baseUrl ?? '',
        apiKey: patch.apiKey ?? '',
        enabled: patch.enabled ?? target.enabled,
        priority: patch.priority ?? target.priority,
        timeoutSeconds: patch.timeoutSeconds ?? target.timeoutSeconds
      });
      setModelTargets((current) => current.map((item) => (item.id === updated.id ? updated : item)).sort(compareModelTargets));
      setAdminOverview(await fetchAdminOverview());
      showNotice('模型目标已更新。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleDeleteModelTarget(target: ModelTarget) {
    const confirmed = window.confirm(`删除模型目标「${target.code}」？`);
    if (!confirmed) {
      return;
    }
    try {
      setBusyText('正在删除模型目标...');
      await deleteModelTarget(target.id);
      setModelTargets((current) => current.filter((item) => item.id !== target.id));
      setAdminOverview(await fetchAdminOverview());
      showNotice('模型目标已删除。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleCreateSamplePrompt(input: SamplePromptInput) {
    try {
      setBusyText('正在保存示例问题...');
      const created = await createSamplePrompt({ ...input, enabled: true });
      setSamplePrompts((current) => [...current, created].sort(compareSamplePrompts));
      setAdminOverview(await fetchAdminOverview());
      await loadPromptPresets();
      showNotice('示例问题已添加。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleUpdateSamplePrompt(prompt: SamplePrompt, patch: Partial<SamplePromptInput & { enabled: boolean }>) {
    try {
      setBusyText('正在更新示例问题...');
      const updated = await updateSamplePrompt(prompt.id, {
        scope: patch.scope ?? normalizePromptScope(prompt.scope),
        title: patch.title ?? prompt.title,
        prompt: patch.prompt ?? prompt.prompt,
        description: patch.description ?? prompt.description ?? '',
        sortOrder: patch.sortOrder ?? prompt.sortOrder,
        enabled: patch.enabled ?? prompt.enabled
      });
      setSamplePrompts((current) => current.map((item) => (item.id === updated.id ? updated : item)).sort(compareSamplePrompts));
      setAdminOverview(await fetchAdminOverview());
      await loadPromptPresets();
      showNotice('示例问题已更新。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleDeleteSamplePrompt(prompt: SamplePrompt) {
    const confirmed = window.confirm(`删除示例问题「${prompt.title}」？`);
    if (!confirmed) {
      return;
    }
    try {
      setBusyText('正在删除示例问题...');
      await deleteSamplePrompt(prompt.id);
      setSamplePrompts((current) => current.filter((item) => item.id !== prompt.id));
      setAdminOverview(await fetchAdminOverview());
      await loadPromptPresets();
      showNotice('示例问题已删除。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleUpdateRagSettings(input: RagSettingsInput) {
    try {
      setBusyText('正在保存 RAG 与限流参数...');
      const updated = await updateRagSettings(input);
      setRagSettings(updated);
      setAdminOverview(await fetchAdminOverview());
      showNotice('RAG 与限流参数已更新。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  function handleSourceJump(source: SourceResponse) {
    if (!source.paperId) {
      return;
    }
    setSelectedPaperId(source.paperId);
    setReaderScope('paper');
    setActiveView('reader');
    setPdfJump({ paperId: source.paperId, page: Math.max(1, source.page || 1), signal: Date.now() });
  }

  function showNotice(message: string) {
    setNotice(message);
    if (noticeTimerRef.current) {
      window.clearTimeout(noticeTimerRef.current);
    }
    noticeTimerRef.current = window.setTimeout(() => setNotice(''), 3000);
  }

  function updateForm(key: keyof PaperForm, value: string) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  if (loading) {
    return (
      <div className="center-screen">
        <Loader2 className="spin" />
        <span>正在准备科研工作台...</span>
      </div>
    );
  }

  if (!user) {
    return (
      <AuthScreen
        mode={authMode}
        credentials={credentials}
        busyText={busyText}
        error={error}
        onModeChange={setAuthMode}
        onCredentialsChange={setCredentials}
        onSubmit={handleAuthSubmit}
      />
    );
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">
            <Bot size={22} />
          </div>
          <div>
            <strong>Research Paper Agent</strong>
            <span>Spring AI 科研阅读工作台</span>
          </div>
        </div>

        <nav className="nav-list" aria-label="主导航">
          <NavButton icon={BookOpen} label="文献库" active={activeView === 'library'} onClick={() => setActiveView('library')} />
          <NavButton icon={UploadCloud} label="上传文献" active={activeView === 'upload'} onClick={() => setActiveView('upload')} />
          <NavButton
            icon={MessageSquareText}
            label="Agent 阅读"
            active={activeView === 'reader'}
            onClick={() => {
              setReaderScope('paper');
              setActiveView('reader');
            }}
          />
          <NavButton
            icon={Brain}
            label="全库问答"
            active={activeView === 'libraryChat'}
            onClick={() => {
              setReaderScope('library');
              setActiveView('libraryChat');
            }}
          />
          <NavButton icon={History} label="问答历史" active={activeView === 'history'} onClick={() => setActiveView('history')} />
          {user.role === 'ADMIN' && (
            <NavButton icon={UserCog} label="管理后台" active={activeView === 'admin'} onClick={() => setActiveView('admin')} />
          )}
        </nav>

        <div className="system-panel">
          <span><ShieldCheck size={15} /> 登录用户</span>
          <strong>{user.username}</strong>
          <span><Database size={15} /> 数据库</span>
          <strong>PostgreSQL + pgvector</strong>
          <span><Brain size={15} /> Agent</span>
          <strong>Spring AI Lite</strong>
        </div>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div>
            <h1>科研文献阅读 Agent</h1>
            <p>管理论文 PDF，解析正文片段，并用多 Agent Lite 辅助摘要、实验解读和引用核查。</p>
          </div>
          <div className="topbar-actions">
            <button className="icon-button" type="button" title="刷新" onClick={() => void handleSearch()}>
              <RefreshCw size={18} />
            </button>
            <button className="icon-button" type="button" title="退出登录" onClick={signOut}>
              <LogOut size={18} />
            </button>
          </div>
        </header>

        {error && <div className="notice error">{error}</div>}
        {notice && (
          <div className="notice success">
            <CheckCircle2 size={17} />
            {notice}
          </div>
        )}
        {busyText && (
          <div className="notice progress">
            <Loader2 className="spin" size={17} />
            {busyText}
          </div>
        )}

        {activeView !== 'admin' && (
          <section className="metrics">
            <Metric icon={FileText} label="文献总数" value={stats.total} />
            <Metric icon={CheckCircle2} label="已精读" value={stats.intensive} />
            <Metric icon={Layers} label="已解析" value={stats.indexed} />
            <Metric icon={History} label="当前问答" value={stats.chats} />
          </section>
        )}

        {activeView === 'library' && (
          <LibraryView
            papers={papers}
            selectedPaperId={selectedPaper?.id ?? null}
            query={query}
            statusFilter={statusFilter}
            onQueryChange={setQuery}
            onStatusFilterChange={setStatusFilter}
            onSearch={handleSearch}
            onSelect={(paper) => {
              setSelectedPaperId(paper.id);
              setReaderScope('paper');
              setPdfJump(null);
              setActiveView('reader');
            }}
            onUpload={() => setActiveView('upload')}
            onToggleRead={(paper) => void handleToggleRead(paper)}
            onDelete={(paper) => void handleDeletePaper(paper)}
          />
        )}

        {activeView === 'upload' && (
          <UploadView
            form={form}
            file={file}
            metadataStatus={metadataStatus}
            onFileChange={(nextFile) => void handleSelectFile(nextFile)}
            onFormChange={updateForm}
            onSubmit={handleUpload}
          />
        )}

        {activeView === 'reader' && (
          <ReaderView
            papers={papers}
            scope="paper"
            selectedPaper={selectedPaper}
            sessions={chatSessions}
            selectedSessionId={selectedSessionId}
            chats={chats}
            prompts={paperPrompts}
            question={question}
            isStreaming={agentStreaming}
            streamStatus={streamStatus}
            onQuestionChange={setQuestion}
            onSelectScope={(scope, id) => {
              setReaderScope(scope);
              if (id) {
                setSelectedPaperId(id);
                setPdfJump(null);
              }
            }}
            onAsk={(prompt) => void handleAsk(prompt)}
            onCancelStream={handleCancelAgentStream}
            onCreateSession={() => void handleCreateChatSession()}
            onSelectSession={(session) => void handleSelectChatSession(session)}
            onRenameSession={(session) => void handleRenameChatSession(session)}
            onArchiveSession={(session) => void handleArchiveChatSession(session)}
            onToggleRead={(paper) => void handleToggleRead(paper)}
            onParse={(paper) => void handleParse(paper)}
            onUnparse={(paper) => void handleUnparse(paper)}
            onSourceClick={handleSourceJump}
            onFeedback={(chat, score) => void handleChatFeedback(chat, score)}
            pdfJump={pdfJump}
          />
        )}

        {activeView === 'libraryChat' && (
          <LibraryChatView
            papers={papers}
            sessions={chatSessions}
            selectedSessionId={selectedSessionId}
            chats={chats}
            prompts={libraryPrompts}
            question={question}
            isStreaming={agentStreaming}
            streamStatus={streamStatus}
            onQuestionChange={setQuestion}
            onAsk={(prompt) => void handleAsk(prompt)}
            onCancelStream={handleCancelAgentStream}
            onCreateSession={() => void handleCreateChatSession()}
            onSelectSession={(session) => void handleSelectChatSession(session)}
            onRenameSession={(session) => void handleRenameChatSession(session)}
            onArchiveSession={(session) => void handleArchiveChatSession(session)}
            onSelectPaper={(paper) => {
              setSelectedPaperId(paper.id);
              setReaderScope('paper');
              setPdfJump(null);
              setActiveView('reader');
            }}
            onUpload={() => setActiveView('upload')}
            onSourceClick={handleSourceJump}
            onFeedback={(chat, score) => void handleChatFeedback(chat, score)}
          />
        )}

        {activeView === 'history' && (
          <HistoryView
            papers={papers}
            scope={readerScope}
            selectedPaper={selectedPaper}
            chats={chats}
            onSelectScope={(scope, id) => {
              setReaderScope(scope);
              if (id) {
                setSelectedPaperId(id);
                setPdfJump(null);
              }
            }}
            onSourceClick={handleSourceJump}
            onFeedback={(chat, score) => void handleChatFeedback(chat, score)}
          />
        )}

        {activeView === 'admin' && user.role === 'ADMIN' && (
          <AdminView
            overview={adminOverview}
            tracePage={adminTracePage}
            traceFilters={adminTraceFilters}
            agentToolExecutionPage={agentToolExecutionPage}
            agentToolExecutionFilters={agentToolExecutionFilters}
            chunkPage={adminChunkPage}
            chunkFilters={adminChunkFilters}
            parseJobPage={adminParseJobPage}
            parseJobFilters={adminParseJobFilters}
            users={adminUsers}
            agentPipelineNodes={agentPipelineNodes}
            ingestionPipelineNodes={ingestionPipelineNodes}
            retrievalChannels={retrievalChannels}
            retrievalProcessors={retrievalProcessors}
            agentTools={agentTools}
            queryMappings={queryMappings}
            intentRoutes={intentRoutes}
            answerPromptTemplates={answerPromptTemplates}
            modelTargets={modelTargets}
            samplePrompts={samplePrompts}
            ragSettings={ragSettings}
            loading={adminLoading}
            currentUserId={user.id}
            onRefresh={() => void loadAdminData()}
            onTraceFilterChange={(patch) => void handleAdminTraceFilterChange(patch)}
            onTracePageChange={(page) => void loadAdminTracePage(adminTraceFilters, page)}
            onAgentToolExecutionFilterChange={(patch) => void handleAgentToolExecutionFilterChange(patch)}
            onAgentToolExecutionPageChange={(page) => void loadAgentToolExecutionPage(agentToolExecutionFilters, page)}
            onChunkFilterChange={(patch) => void handleAdminChunkFilterChange(patch)}
            onChunkPageChange={(page) => void loadAdminChunkPage(adminChunkFilters, page)}
            onParseJobFilterChange={(patch) => void handleAdminParseJobFilterChange(patch)}
            onParseJobPageChange={(page) => void loadAdminParseJobPage(adminParseJobFilters, page)}
            onChunkEnabledChange={(chunk, enabled) => void handleAdminChunkEnabled(chunk, enabled)}
            onAgentPipelineNodeEnabledChange={(node, enabled) => void handleAdminAgentPipelineNodeEnabled(node, enabled)}
            onAgentToolEnabledChange={(tool, enabled) => void handleAdminAgentToolEnabled(tool, enabled)}
            onAgentToolMinimumRoleChange={(tool, minimumRole) => void handleAdminAgentToolMinimumRole(tool, minimumRole)}
            onUserStatusChange={(userItem, status) => void handleAdminUserStatus(userItem, status)}
            onCreateQueryMapping={(input) => void handleCreateQueryMapping(input)}
            onUpdateQueryMapping={(mapping, patch) => void handleUpdateQueryMapping(mapping, patch)}
            onDeleteQueryMapping={(mapping) => void handleDeleteQueryMapping(mapping)}
            onCreateIntentRoute={(input) => void handleCreateIntentRoute(input)}
            onUpdateIntentRoute={(route, patch) => void handleUpdateIntentRoute(route, patch)}
            onDeleteIntentRoute={(route) => void handleDeleteIntentRoute(route)}
            onCreateAnswerPromptTemplate={(input) => void handleCreateAnswerPromptTemplate(input)}
            onUpdateAnswerPromptTemplate={(template, patch) => void handleUpdateAnswerPromptTemplate(template, patch)}
            onDeleteAnswerPromptTemplate={(template) => void handleDeleteAnswerPromptTemplate(template)}
            onCreateModelTarget={(input) => void handleCreateModelTarget(input)}
            onUpdateModelTarget={(target, patch) => void handleUpdateModelTarget(target, patch)}
            onDeleteModelTarget={(target) => void handleDeleteModelTarget(target)}
            onCreateSamplePrompt={(input) => void handleCreateSamplePrompt(input)}
            onUpdateSamplePrompt={(prompt, patch) => void handleUpdateSamplePrompt(prompt, patch)}
            onDeleteSamplePrompt={(prompt) => void handleDeleteSamplePrompt(prompt)}
            onUpdateRagSettings={(input) => void handleUpdateRagSettings(input)}
          />
        )}
      </main>
    </div>
  );
}

function AuthScreen({
  mode,
  credentials,
  busyText,
  error,
  onModeChange,
  onCredentialsChange,
  onSubmit
}: {
  mode: AuthMode;
  credentials: { username: string; email: string; account: string; password: string };
  busyText: string;
  error: string;
  onModeChange: (mode: AuthMode) => void;
  onCredentialsChange: (next: { username: string; email: string; account: string; password: string }) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={onSubmit}>
        <div className="auth-mark">
          <Bot size={24} />
        </div>
        <div>
          <h1>Research Paper Agent</h1>
          <p>登录后进入科研文献阅读工作台。</p>
        </div>
        <div className="segmented">
          <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => onModeChange('login')}>
            登录
          </button>
          <button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => onModeChange('register')}>
            注册
          </button>
        </div>
        {mode === 'register' && (
          <>
            <Field label="用户名" value={credentials.username} onChange={(username) => onCredentialsChange({ ...credentials, username })} required />
            <Field label="邮箱" value={credentials.email} onChange={(email) => onCredentialsChange({ ...credentials, email })} required />
          </>
        )}
        {mode === 'login' && (
          <Field label="账号或邮箱" value={credentials.account} onChange={(account) => onCredentialsChange({ ...credentials, account })} required />
        )}
        <Field
          label="密码"
          type="password"
          value={credentials.password}
          onChange={(password) => onCredentialsChange({ ...credentials, password })}
          required
        />
        {error && <div className="notice error compact">{error}</div>}
        {busyText && <div className="notice progress compact"><Loader2 className="spin" size={16} />{busyText}</div>}
        <button className="primary-button" type="submit">
          <LogIn size={17} />
          {mode === 'login' ? '进入工作台' : '创建账号'}
        </button>
      </form>
    </main>
  );
}

function LibraryView({
  papers,
  selectedPaperId,
  query,
  statusFilter,
  onQueryChange,
  onStatusFilterChange,
  onSearch,
  onSelect,
  onUpload,
  onToggleRead,
  onDelete
}: {
  papers: Paper[];
  selectedPaperId: number | null;
  query: string;
  statusFilter: string;
  onQueryChange: (value: string) => void;
  onStatusFilterChange: (value: string) => void;
  onSearch: (event?: FormEvent<HTMLFormElement>) => void;
  onSelect: (paper: Paper) => void;
  onUpload: () => void;
  onToggleRead: (paper: Paper) => void;
  onDelete: (paper: Paper) => void;
}) {
  return (
    <section className="panel view-panel">
      <div className="section-head">
        <div>
          <h2>文献库</h2>
          <p>按题录、关键词和阅读状态筛选文献。</p>
        </div>
        <button className="primary-button" type="button" onClick={onUpload}>
          <Plus size={17} />
          上传 PDF
        </button>
      </div>
      <form className="filters" onSubmit={onSearch}>
        <label className="search-box">
          <Search size={18} />
          <input value={query} placeholder="搜索标题、作者、关键词或摘要" onChange={(event) => onQueryChange(event.target.value)} />
        </label>
        <select value={statusFilter} onChange={(event) => onStatusFilterChange(event.target.value)}>
          <option value="">全部状态</option>
          <option value="TO_READ">待阅读</option>
          <option value="INTENSIVE_READ">已精读</option>
        </select>
        <button className="secondary-button" type="submit">筛选</button>
      </form>
      <div className="paper-list">
        {papers.length === 0 ? (
          <EmptyState title="暂无文献" detail="上传第一篇 PDF 后，就可以在这里管理阅读状态和问答历史。" actionLabel="上传文献" onAction={onUpload} />
        ) : (
          papers.map((paper) => (
            <PaperCard
              key={paper.id}
              paper={paper}
              active={paper.id === selectedPaperId}
              onClick={() => onSelect(paper)}
              onToggleRead={() => onToggleRead(paper)}
              onDelete={() => onDelete(paper)}
            />
          ))
        )}
      </div>
    </section>
  );
}

function UploadView({
  form,
  file,
  metadataStatus,
  onFileChange,
  onFormChange,
  onSubmit
}: {
  form: PaperForm;
  file: File | null;
  metadataStatus: string;
  onFileChange: (file: File | null) => void;
  onFormChange: (key: keyof PaperForm, value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <section className="panel upload-layout">
      <div className="section-head">
        <div>
          <h2>上传文献</h2>
          <p>上传 PDF 后自动识别题录，并触发后端 PDFBox 解析。</p>
        </div>
      </div>
      <form className="upload-form" onSubmit={onSubmit}>
        <label className="file-drop">
          <UploadCloud size={30} />
          <strong>{file ? file.name : '选择或拖入 PDF 文件'}</strong>
          <span>{file ? formatBytes(file.size) : '支持 50MB 以内 PDF'}</span>
          <input type="file" accept="application/pdf,.pdf" onChange={(event) => onFileChange(event.target.files?.[0] ?? null)} />
        </label>
        {metadataStatus && <div className="metadata-status"><Loader2 className="spin" size={15} />{metadataStatus}</div>}
        <div className="form-grid">
          <Field label="标题" value={form.title} onChange={(value) => onFormChange('title', value)} required />
          <Field label="作者" value={form.authors} onChange={(value) => onFormChange('authors', value)} />
          <Field label="会议 / 期刊" value={form.venue} onChange={(value) => onFormChange('venue', value)} />
          <Field label="年份" value={form.year} onChange={(value) => onFormChange('year', value)} />
          <Field label="关键词" value={form.keywords} onChange={(value) => onFormChange('keywords', value)} />
        </div>
        <label className="textarea-field">
          <span>摘要</span>
          <textarea value={form.abstractText} onChange={(event) => onFormChange('abstractText', event.target.value)} />
        </label>
        <label className="textarea-field">
          <span>阅读备注</span>
          <textarea value={form.note} onChange={(event) => onFormChange('note', event.target.value)} />
        </label>
        <button className="primary-button submit-button" type="submit">
          <UploadCloud size={17} />
          上传并解析
        </button>
      </form>
    </section>
  );
}

function ReaderView({
  papers,
  scope,
  selectedPaper,
  sessions,
  selectedSessionId,
  chats,
  prompts,
  question,
  isStreaming,
  streamStatus,
  onQuestionChange,
  onSelectScope,
  onAsk,
  onCancelStream,
  onCreateSession,
  onSelectSession,
  onRenameSession,
  onArchiveSession,
  onToggleRead,
  onParse,
  onUnparse,
  onSourceClick,
  onFeedback,
  pdfJump
}: {
  papers: Paper[];
  scope: ReaderScope;
  selectedPaper: Paper | null;
  sessions: ChatSession[];
  selectedSessionId: number | null;
  chats: ChatRecord[];
  prompts: string[];
  question: string;
  isStreaming: boolean;
  streamStatus: string;
  onQuestionChange: (value: string) => void;
  onSelectScope: (scope: ReaderScope, id?: number) => void;
  onAsk: (prompt?: string) => void;
  onCancelStream: () => void;
  onCreateSession: () => void;
  onSelectSession: (session: ChatSession) => void;
  onRenameSession: (session: ChatSession) => void;
  onArchiveSession: (session: ChatSession) => void;
  onToggleRead: (paper: Paper) => void;
  onParse: (paper: Paper) => void;
  onUnparse: (paper: Paper) => void;
  onSourceClick: (source: SourceResponse) => void;
  onFeedback: (chat: ChatRecord, score: 1 | -1) => void;
  pdfJump: PdfJump | null;
}) {
  const indexedCount = papers.filter((paper) => paper.processStatus === 'INDEXED').length;

  if (scope === 'paper' && !selectedPaper) {
    return <EmptyState title="还没有可阅读的文献" detail="先上传一篇 PDF，再进入 Agent 阅读视图。" />;
  }

  return (
    <section className="reader-layout">
      <div className="reader-toolbar">
        <select
          value={String(selectedPaper?.id ?? '')}
          onChange={(event) => {
            onSelectScope('paper', Number(event.target.value));
          }}
        >
          {papers.map((paper) => (
            <option key={paper.id} value={paper.id}>{paper.title}</option>
          ))}
        </select>
        {scope === 'paper' && selectedPaper && (
          <>
            <button className={`secondary-button read-action ${selectedPaper.status === 'INTENSIVE_READ' ? 'is-read' : ''}`} type="button" onClick={() => onToggleRead(selectedPaper)}>
              <CheckCircle2 size={17} />
              {selectedPaper.status === 'INTENSIVE_READ' ? '已精读' : '标记精读'}
            </button>
            {selectedPaper.processStatus === 'INDEXED' ? (
              <button className="secondary-button danger-action" type="button" onClick={() => onUnparse(selectedPaper)}>
                <Trash2 size={17} />
                取消解析
              </button>
            ) : (
              <button className="secondary-button" type="button" onClick={() => onParse(selectedPaper)}>
                <Layers size={17} />
                解析 PDF
              </button>
            )}
          </>
        )}
      </div>
      <div className="pdf-panel">
        {scope === 'library' ? (
          <>
            <div className="paper-meta">
              <div>
                <h2>全库问答</h2>
                <p>{papers.length} 篇文献 · {indexedCount} 篇已解析</p>
              </div>
              <span className={`status-pill ${indexedCount > 0 ? 'is-indexed' : ''}`}>Library RAG</span>
            </div>
            <div className="library-scope-panel">
              <div className="library-scope-grid">
                <Metric icon={BookOpen} label="文献总数" value={papers.length} />
                <Metric icon={Database} label="向量索引" value={indexedCount} />
                <Metric icon={History} label="全库问答" value={chats.length} />
              </div>
              <div className="library-source-list">
                {papers.slice(0, 8).map((paper) => (
                  <button key={paper.id} type="button" onClick={() => onSelectScope('paper', paper.id)}>
                    <FileText size={17} />
                    <span>{paper.title}</span>
                    <em>{processLabel(paper.processStatus)}</em>
                  </button>
                ))}
              </div>
            </div>
          </>
        ) : selectedPaper && (
          <>
            <div className="paper-meta">
              <div>
                <h2>{selectedPaper.title}</h2>
                <p>{[selectedPaper.authors, selectedPaper.venue, selectedPaper.year].filter(Boolean).join(' · ') || '未填写题录信息'}</p>
              </div>
              <span className={`status-pill ${selectedPaper.processStatus === 'INDEXED' ? 'is-indexed' : ''}`}>{processLabel(selectedPaper.processStatus)}</span>
            </div>
            <PdfPreview
              paper={selectedPaper}
              targetPage={pdfJump?.paperId === selectedPaper.id ? pdfJump.page : undefined}
              jumpSignal={pdfJump?.paperId === selectedPaper.id ? pdfJump.signal : undefined}
            />
          </>
        )}
      </div>
      <aside className="agent-panel">
        <div className="agent-title">
          <Brain size={22} />
          <div>
            <h2>{scope === 'library' ? '全库 Agent' : '多 Agent Lite'}</h2>
            <p>{scope === 'library' ? '跨论文检索、综合回答和引用校验。' : '检索、回答、引用校验和格式化。'}</p>
          </div>
        </div>
        <div className="quick-prompts">
          {prompts.map((prompt) => (
            <button key={prompt} type="button" disabled={isStreaming} onClick={() => onAsk(prompt)}>{prompt}</button>
          ))}
        </div>
        <ChatSessionBar
          sessions={sessions}
          selectedSessionId={selectedSessionId}
          onCreate={onCreateSession}
          onSelect={onSelectSession}
          onRename={onRenameSession}
          onArchive={onArchiveSession}
        />
        <div className="chat-list">
          {chats.length === 0 ? (
            <EmptyState compact title="暂无问答" detail="提出一个问题，Agent 会保存回答和来源片段。" />
          ) : (
            chats.map((chat) => <ChatBubble key={chat.id} chat={chat} onSourceClick={onSourceClick} onFeedback={onFeedback} />)
          )}
        </div>
        <form
          className="ask-box"
          onSubmit={(event) => {
            event.preventDefault();
            onAsk();
          }}
        >
          <input value={question} disabled={isStreaming} placeholder={scope === 'library' ? '围绕全部文献提问...' : '围绕当前论文提问...'} onChange={(event) => onQuestionChange(event.target.value)} />
          {isStreaming ? (
            <button className="stream-stop icon-button" type="button" title="停止等待" onClick={onCancelStream}>
              <CircleStop size={18} />
            </button>
          ) : (
            <button className="send icon-button" type="submit" title="发送" disabled={!question.trim()}>
              <Send size={18} />
            </button>
          )}
          {isStreaming && (
            <small className="stream-status">
              <Loader2 className="spin" size={14} />
              {streamStatus || 'Agent 正在运行...'}
            </small>
          )}
        </form>
      </aside>
    </section>
  );
}

function LibraryChatView({
  papers,
  sessions,
  selectedSessionId,
  chats,
  prompts,
  question,
  isStreaming,
  streamStatus,
  onQuestionChange,
  onAsk,
  onCancelStream,
  onCreateSession,
  onSelectSession,
  onRenameSession,
  onArchiveSession,
  onSelectPaper,
  onUpload,
  onSourceClick,
  onFeedback
}: {
  papers: Paper[];
  sessions: ChatSession[];
  selectedSessionId: number | null;
  chats: ChatRecord[];
  prompts: string[];
  question: string;
  isStreaming: boolean;
  streamStatus: string;
  onQuestionChange: (value: string) => void;
  onAsk: (prompt?: string) => void;
  onCancelStream: () => void;
  onCreateSession: () => void;
  onSelectSession: (session: ChatSession) => void;
  onRenameSession: (session: ChatSession) => void;
  onArchiveSession: (session: ChatSession) => void;
  onSelectPaper: (paper: Paper) => void;
  onUpload: () => void;
  onSourceClick: (source: SourceResponse) => void;
  onFeedback: (chat: ChatRecord, score: 1 | -1) => void;
}) {
  const indexedPapers = papers.filter((paper) => paper.processStatus === 'INDEXED');
  const intensivePapers = papers.filter((paper) => paper.status === 'INTENSIVE_READ');

  return (
    <section className="library-chat-layout">
      <div className="library-chat-overview">
        <div className="section-head">
          <div>
            <h2>全库问答</h2>
            <p>跨论文检索来源片段，适合做综述、横向比较和研究路线梳理。</p>
          </div>
          <span className={`status-pill ${indexedPapers.length > 0 ? 'is-indexed' : ''}`}>Library RAG</span>
        </div>

        <div className="library-scope-grid">
          <Metric icon={BookOpen} label="文献总数" value={papers.length} />
          <Metric icon={Database} label="向量索引" value={indexedPapers.length} />
          <Metric icon={CheckCircle2} label="已精读" value={intensivePapers.length} />
        </div>

        <div className="library-chat-prompts">
          {prompts.map((prompt) => (
            <button key={prompt} type="button" disabled={isStreaming} onClick={() => onAsk(prompt)}>
              <Brain size={16} />
              <span>{prompt}</span>
            </button>
          ))}
        </div>

        <div className="library-source-list">
          {papers.length === 0 ? (
            <EmptyState compact title="还没有文献" detail="上传 PDF 后即可进行全库问答。" actionLabel="上传文献" onAction={onUpload} />
          ) : (
            papers.map((paper) => (
              <button key={paper.id} type="button" onClick={() => onSelectPaper(paper)}>
                <FileText size={17} />
                <span>{paper.title}</span>
                <em>{processLabel(paper.processStatus)}</em>
              </button>
            ))
          )}
        </div>
      </div>

      <aside className="agent-panel library-chat-agent">
        <div className="agent-title">
          <Brain size={22} />
          <div>
            <h2>全库 Agent</h2>
            <p>面向所有已解析文献检索、回答和引用校验。</p>
          </div>
        </div>
        <ChatSessionBar
          sessions={sessions}
          selectedSessionId={selectedSessionId}
          onCreate={onCreateSession}
          onSelect={onSelectSession}
          onRename={onRenameSession}
          onArchive={onArchiveSession}
        />
        <div className="chat-list">
          {chats.length === 0 ? (
            <EmptyState compact title="暂无全库问答" detail="提出一个跨论文问题，Agent 会保存回答和来源片段。" />
          ) : (
            chats.map((chat) => <ChatBubble key={chat.id} chat={chat} onSourceClick={onSourceClick} onFeedback={onFeedback} />)
          )}
        </div>
        <form
          className="ask-box"
          onSubmit={(event) => {
            event.preventDefault();
            onAsk();
          }}
        >
          <input value={question} disabled={isStreaming} placeholder="围绕全部文献提问..." onChange={(event) => onQuestionChange(event.target.value)} />
          {isStreaming ? (
            <button className="stream-stop icon-button" type="button" title="停止等待" onClick={onCancelStream}>
              <CircleStop size={18} />
            </button>
          ) : (
            <button className="send icon-button" type="submit" title="发送" disabled={!question.trim()}>
              <Send size={18} />
            </button>
          )}
          {isStreaming && (
            <small className="stream-status">
              <Loader2 className="spin" size={14} />
              {streamStatus || 'Agent 正在运行...'}
            </small>
          )}
        </form>
      </aside>
    </section>
  );
}

function HistoryView({
  papers,
  scope,
  selectedPaper,
  chats,
  onSelectScope,
  onSourceClick,
  onFeedback
}: {
  papers: Paper[];
  scope: ReaderScope;
  selectedPaper: Paper | null;
  chats: ChatRecord[];
  onSelectScope: (scope: ReaderScope, id?: number) => void;
  onSourceClick: (source: SourceResponse) => void;
  onFeedback: (chat: ChatRecord, score: 1 | -1) => void;
}) {
  return (
    <section className="panel history-layout">
      <div className="section-head">
        <div>
          <h2>问答历史</h2>
          <p>按文献查看已保存的 Agent 问答记录。</p>
        </div>
        <select
          value={scope === 'library' ? 'library' : String(selectedPaper?.id ?? '')}
          onChange={(event) => {
            if (event.target.value === 'library') {
              onSelectScope('library');
              return;
            }
            onSelectScope('paper', Number(event.target.value));
          }}
        >
          <option value="library">全库问答</option>
          {papers.map((paper) => (
            <option key={paper.id} value={paper.id}>{paper.title}</option>
          ))}
        </select>
      </div>
      <div className="history-list">
        {chats.length === 0 ? (
          <EmptyState title="暂无历史" detail="完成问答后会自动写入历史记录。" />
        ) : (
          chats.map((chat) => (
            <article className="history-item" key={chat.id}>
              <time><Clock3 size={14} /> {formatTime(chat.createdAt)} · {chat.modelName || 'agent'}</time>
              <h3>{chat.question}</h3>
              <MarkdownContent content={chat.answer} />
              <SourceCards sources={chat.sources} onSourceClick={onSourceClick} />
              <FeedbackBar chat={chat} onFeedback={onFeedback} />
              {chat.sources.length > 0 && <span>来源：{formatSources(chat.sources)}</span>}
            </article>
          ))
        )}
      </div>
    </section>
  );
}

function ChatSessionBar({
  sessions,
  selectedSessionId,
  onCreate,
  onSelect,
  onRename,
  onArchive
}: {
  sessions: ChatSession[];
  selectedSessionId: number | null;
  onCreate: () => void;
  onSelect: (session: ChatSession) => void;
  onRename: (session: ChatSession) => void;
  onArchive: (session: ChatSession) => void;
}) {
  const selected = sessions.find((session) => session.id === selectedSessionId) ?? sessions[0] ?? null;

  return (
    <div className="chat-session-bar">
      <div className="chat-session-head">
        <span>
          <MessageSquareText size={15} />
          会话
        </span>
        <button className="icon-button small" type="button" title="新建会话" onClick={onCreate}>
          <Plus size={15} />
        </button>
      </div>
      {sessions.length > 0 ? (
        <div className="chat-session-list">
          {sessions.slice(0, 8).map((session) => (
            <button
              className={`chat-session-item ${session.id === selected?.id ? 'active' : ''}`}
              key={session.id}
              type="button"
              onClick={() => onSelect(session)}
            >
              <span>{session.title || '新对话'}</span>
              <em>{session.messageCount} 轮</em>
            </button>
          ))}
        </div>
      ) : (
        <p className="chat-session-empty">还没有会话，发送问题时会自动创建。</p>
      )}
      {selected && (
        <div className="chat-session-actions">
          <button className="icon-button small" type="button" title="重命名会话" onClick={() => onRename(selected)}>
            <Pencil size={14} />
          </button>
          <button className="icon-button small danger" type="button" title="归档会话" onClick={() => onArchive(selected)}>
            <Trash2 size={14} />
          </button>
          <span>{selected.lastMessageAt ? formatTime(selected.lastMessageAt) : '等待第一轮问答'}</span>
        </div>
      )}
    </div>
  );
}

function AdminView({
  overview,
  tracePage,
  traceFilters,
  agentToolExecutionPage,
  agentToolExecutionFilters,
  chunkPage,
  chunkFilters,
  parseJobPage,
  parseJobFilters,
  users,
  agentPipelineNodes,
  ingestionPipelineNodes,
  retrievalChannels,
  retrievalProcessors,
  agentTools,
  queryMappings,
  intentRoutes,
  answerPromptTemplates,
  modelTargets,
  samplePrompts,
  ragSettings,
  loading,
  currentUserId,
  onRefresh,
  onTraceFilterChange,
  onTracePageChange,
  onAgentToolExecutionFilterChange,
  onAgentToolExecutionPageChange,
  onChunkFilterChange,
  onChunkPageChange,
  onParseJobFilterChange,
  onParseJobPageChange,
  onChunkEnabledChange,
  onAgentPipelineNodeEnabledChange,
  onAgentToolEnabledChange,
  onAgentToolMinimumRoleChange,
  onUserStatusChange,
  onCreateQueryMapping,
  onUpdateQueryMapping,
  onDeleteQueryMapping,
  onCreateIntentRoute,
  onUpdateIntentRoute,
  onDeleteIntentRoute,
  onCreateAnswerPromptTemplate,
  onUpdateAnswerPromptTemplate,
  onDeleteAnswerPromptTemplate,
  onCreateModelTarget,
  onUpdateModelTarget,
  onDeleteModelTarget,
  onCreateSamplePrompt,
  onUpdateSamplePrompt,
  onDeleteSamplePrompt,
  onUpdateRagSettings
}: {
  overview: AdminOverview | null;
  tracePage: PageResponse<AdminTrace> | null;
  traceFilters: AdminTraceFilters;
  agentToolExecutionPage: PageResponse<AdminAgentToolExecution> | null;
  agentToolExecutionFilters: AdminAgentToolExecutionFilters;
  chunkPage: PageResponse<AdminChunk> | null;
  chunkFilters: AdminChunkFilters;
  parseJobPage: PageResponse<AdminParseJob> | null;
  parseJobFilters: AdminParseJobFilters;
  users: AdminUser[];
  agentPipelineNodes: AdminAgentPipelineNode[];
  ingestionPipelineNodes: AdminIngestionPipelineNode[];
  retrievalChannels: AdminRetrievalChannelCatalog[];
  retrievalProcessors: AdminRetrievalProcessorCatalog[];
  agentTools: AdminAgentTool[];
  queryMappings: QueryTermMapping[];
  intentRoutes: IntentRoute[];
  answerPromptTemplates: AnswerPromptTemplate[];
  modelTargets: ModelTarget[];
  samplePrompts: SamplePrompt[];
  ragSettings: RagSettings | null;
  loading: boolean;
  currentUserId: number;
  onRefresh: () => void;
  onTraceFilterChange: (patch: Partial<AdminTraceFilters>) => void;
  onTracePageChange: (page: number) => void;
  onAgentToolExecutionFilterChange: (patch: Partial<AdminAgentToolExecutionFilters>) => void;
  onAgentToolExecutionPageChange: (page: number) => void;
  onChunkFilterChange: (patch: Partial<AdminChunkFilters>) => void;
  onChunkPageChange: (page: number) => void;
  onParseJobFilterChange: (patch: Partial<AdminParseJobFilters>) => void;
  onParseJobPageChange: (page: number) => void;
  onChunkEnabledChange: (chunk: AdminChunk, enabled: boolean) => void;
  onAgentPipelineNodeEnabledChange: (node: AdminAgentPipelineNode, enabled: boolean) => void;
  onAgentToolEnabledChange: (tool: AdminAgentTool, enabled: boolean) => void;
  onAgentToolMinimumRoleChange: (tool: AdminAgentTool, minimumRole: 'USER' | 'ADMIN') => void;
  onUserStatusChange: (user: AdminUser, status: 'NORMAL' | 'DISABLED') => void;
  onCreateQueryMapping: (input: { term: string; expansions: string }) => void;
  onUpdateQueryMapping: (mapping: QueryTermMapping, patch: Partial<Pick<QueryTermMapping, 'term' | 'expansions' | 'enabled'>>) => void;
  onDeleteQueryMapping: (mapping: QueryTermMapping) => void;
  onCreateIntentRoute: (input: IntentRouteInput) => void;
  onUpdateIntentRoute: (route: IntentRoute, patch: Partial<IntentRouteInput & { enabled: boolean }>) => void;
  onDeleteIntentRoute: (route: IntentRoute) => void;
  onCreateAnswerPromptTemplate: (input: AnswerPromptTemplateInput) => void;
  onUpdateAnswerPromptTemplate: (template: AnswerPromptTemplate, patch: Partial<AnswerPromptTemplateInput & { enabled: boolean }>) => void;
  onDeleteAnswerPromptTemplate: (template: AnswerPromptTemplate) => void;
  onCreateModelTarget: (input: ModelTargetInput) => void;
  onUpdateModelTarget: (target: ModelTarget, patch: Partial<ModelTargetInput & { enabled: boolean }>) => void;
  onDeleteModelTarget: (target: ModelTarget) => void;
  onCreateSamplePrompt: (input: SamplePromptInput) => void;
  onUpdateSamplePrompt: (prompt: SamplePrompt, patch: Partial<SamplePromptInput & { enabled: boolean }>) => void;
  onDeleteSamplePrompt: (prompt: SamplePrompt) => void;
  onUpdateRagSettings: (input: RagSettingsInput) => void;
}) {
  const embeddedRatio = overview?.totalChunks ? Math.round((overview.embeddedChunks / overview.totalChunks) * 100) : 0;
  const indexedRatio = overview?.totalPapers ? Math.round((overview.indexedPapers / overview.totalPapers) * 100) : 0;

  return (
    <section className="admin-console">
      <div className="admin-hero">
        <div>
          <span className="admin-eyebrow"><ServerCog size={15} /> Admin Console</span>
          <h2>系统控制台</h2>
          <p>用户、文献、解析任务、知识库索引和模型调用状态。</p>
        </div>
        <button className="secondary-button" type="button" onClick={onRefresh} disabled={loading}>
          <RefreshCw className={loading ? 'spin' : ''} size={17} />
          刷新
        </button>
      </div>

      <div className="admin-stat-grid">
        <AdminStat icon={Users} label="用户" value={overview?.totalUsers ?? 0} detail={`${overview?.normalUsers ?? 0} 正常 / ${overview?.disabledUsers ?? 0} 禁用`} />
        <AdminStat icon={FileText} label="文献" value={overview?.totalPapers ?? 0} detail={`${overview?.indexedPapers ?? 0} 已解析 · ${indexedRatio}%`} />
        <AdminStat icon={Database} label="知识片段" value={overview?.totalChunks ?? 0} detail={`${overview?.embeddedChunks ?? 0} 已向量化 · ${embeddedRatio}%`} />
        <AdminStat icon={HardDrive} label="PDF 存储" value={formatBytes(overview?.storageBytes ?? 0)} detail={`${overview?.totalFiles ?? 0} 个文件`} />
        <AdminStat icon={MessageSquareText} label="问答" value={overview?.totalChats ?? 0} detail={`${overview?.libraryChats ?? 0} 次全库问答`} />
        <AdminStat icon={ThumbsUp} label="反馈" value={overview?.totalFeedbacks ?? 0} detail={`${overview?.positiveFeedbacks ?? 0} 有用 / ${overview?.negativeFeedbacks ?? 0} 无用`} />
        <AdminStat icon={Search} label="术语映射" value={overview?.totalQueryMappings ?? 0} detail={`${overview?.enabledQueryMappings ?? 0} 条启用`} />
        <AdminStat icon={Layers} label="意图路由" value={overview?.totalIntentRoutes ?? 0} detail={`${overview?.enabledIntentRoutes ?? 0} 条启用`} />
        <AdminStat icon={Bot} label="回答模板" value={overview?.totalAnswerPromptTemplates ?? 0} detail={`${overview?.enabledAnswerPromptTemplates ?? 0} 条启用`} />
        <AdminStat icon={ServerCog} label="模型目标" value={overview?.totalModelTargets ?? 0} detail={`${overview?.enabledModelTargets ?? 0} 条启用`} />
        <AdminStat icon={Brain} label="示例问题" value={overview?.totalSamplePrompts ?? 0} detail={`${overview?.enabledSamplePrompts ?? 0} 条启用`} />
        <AdminStat icon={Activity} label="平均耗时" value={formatLatency(overview?.averageLatencyMs ?? 0)} detail={`检索 ${formatLatency(overview?.averageRetrievalMs ?? 0)} / 生成 ${formatLatency(overview?.averageGenerationMs ?? 0)}`} />
        <AdminStat icon={ShieldCheck} label="限流" value={overview?.chatRateLimit?.activeGlobal ?? 0} detail={overview?.chatRateLimit ? `${overview.chatRateLimit.enabled ? '已启用' : '已关闭'} · ${overview.chatRateLimit.recentRequests}/${overview.chatRateLimit.userPerMinuteLimit} 每分钟` : '等待加载'} />
        <AdminStat icon={CircleStop} label="流式任务" value={overview?.activeStreamTasks?.length ?? 0} detail={(overview?.activeStreamTasks?.length ?? 0) > 0 ? '运行中或排队中' : '暂无活跃任务'} />
        <AdminStat icon={CheckCircle2} label="质量均分" value={overview?.averageAnswerQualityScore ?? 0} detail="成功 Trace 的评估分" />
      </div>

      <div className="admin-grid">
        <div className="admin-panel">
          <div className="admin-panel-head">
            <div>
              <h3>解析状态</h3>
              <p>文献处理状态分布 · 解析任务 {overview?.totalParseJobs ?? 0} 次</p>
            </div>
            <BarChart3 size={18} />
          </div>
          <div className="admin-status-list">
            {(overview?.processStatuses ?? []).length === 0 ? (
              <EmptyState compact title="暂无状态" detail="文献入库后会显示解析分布。" />
            ) : (
              overview!.processStatuses.map((item) => (
                <div className="admin-status-row" key={item.status}>
                  <span>{processLabel(item.status)}</span>
                  <strong>{item.count}</strong>
                  <div>
                    <i style={{ width: `${overview?.totalPapers ? Math.max(6, (item.count / overview.totalPapers) * 100) : 0}%` }} />
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="admin-panel">
          <div className="admin-panel-head">
            <div>
              <h3>模型调用</h3>
              <p>最近问答记录聚合</p>
            </div>
            <Brain size={18} />
          </div>
          <div className="admin-model-list">
            {(overview?.modelUsage ?? []).length === 0 ? (
              <EmptyState compact title="暂无调用" detail="完成问答后会统计模型使用。" />
            ) : (
              overview!.modelUsage.map((model) => (
                <div className="admin-model-row" key={model.modelName}>
                  <span>{model.modelName}</span>
                  <strong>{model.count}</strong>
                  <em>{formatLatency(model.averageLatencyMs)}</em>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <div className="admin-panel admin-model-health-panel">
        <div className="admin-panel-head">
          <div>
          <h3>模型健康</h3>
          <p>模型路由任务、最近状态、成功率、fallback 和熔断跳过情况。</p>
          </div>
          <Bot size={18} />
        </div>
        <div className="admin-model-health-list">
          {(overview?.modelHealth ?? []).length === 0 ? (
            <EmptyState compact title="暂无模型记录" detail="完成一次问答后会记录模型路由状态。" />
          ) : (
            overview!.modelHealth.map((model) => (
              <div className={`admin-model-health-row ${modelHealthRowClass(model)}`} key={`${model.taskType}-${model.targetName}`}>
                <strong className={`admin-model-health-status ${modelHealthStatusClass(model.lastStatus)}`}>
                  {modelHealthStatusLabel(model.lastStatus)}
                </strong>
                <span>
                  <b>{modelTaskLabel(model.taskType)} · {model.targetName}</b>
                  <small>
                    {model.provider} · {model.modelName} · {model.lastSeenAt ? formatTime(model.lastSeenAt) : '暂无时间'} · 熔断 {modelCircuitStateLabel(model.circuitState)}
                    {model.consecutiveFailures ? ` · 连续失败 ${model.consecutiveFailures}` : ''}
                    {model.circuitOpenUntil ? ` · 冷却至 ${formatTime(model.circuitOpenUntil)}` : ''}
                  </small>
                </span>
                <em>{model.totalCalls} 次</em>
                <em>{model.successCalls} 成功</em>
                <em>{model.failedCalls} 失败</em>
                <em>{model.fallbackCalls} fallback</em>
                <em>{model.skippedCalls} 跳过</em>
                <strong>{formatLatency(model.averageLatencyMs)}</strong>
              </div>
            ))
          )}
        </div>
      </div>

      <ActiveStreamTaskPanel tasks={overview?.activeStreamTasks ?? []} />

      <AgentPipelinePanel nodes={agentPipelineNodes} onEnabledChange={onAgentPipelineNodeEnabledChange} loading={loading} />

      <RetrievalCatalogPanel channels={retrievalChannels} processors={retrievalProcessors} />

      <AgentToolPanel
        tools={agentTools}
        onEnabledChange={onAgentToolEnabledChange}
        onMinimumRoleChange={onAgentToolMinimumRoleChange}
        loading={loading}
      />

      <AgentToolExecutionPanel
        page={agentToolExecutionPage}
        filters={agentToolExecutionFilters}
        tools={agentTools}
        loading={loading}
        onFilterChange={onAgentToolExecutionFilterChange}
        onPageChange={onAgentToolExecutionPageChange}
      />

      <ModelTargetPanel
        targets={modelTargets}
        onCreate={onCreateModelTarget}
        onUpdate={onUpdateModelTarget}
        onDelete={onDeleteModelTarget}
      />

      <RagSettingsPanel settings={ragSettings} rateLimit={overview?.chatRateLimit} onUpdate={onUpdateRagSettings} />

      <IntentRoutePanel
        routes={intentRoutes}
        tools={agentTools}
        onCreate={onCreateIntentRoute}
        onUpdate={onUpdateIntentRoute}
        onDelete={onDeleteIntentRoute}
      />

      <AnswerPromptTemplatePanel
        templates={answerPromptTemplates}
        onCreate={onCreateAnswerPromptTemplate}
        onUpdate={onUpdateAnswerPromptTemplate}
        onDelete={onDeleteAnswerPromptTemplate}
      />

      <QueryTermMappingPanel
        mappings={queryMappings}
        onCreate={onCreateQueryMapping}
        onUpdate={onUpdateQueryMapping}
        onDelete={onDeleteQueryMapping}
      />

      <SamplePromptPanel
        prompts={samplePrompts}
        onCreate={onCreateSamplePrompt}
        onUpdate={onUpdateSamplePrompt}
        onDelete={onDeleteSamplePrompt}
      />

      <IngestionPipelinePanel nodes={ingestionPipelineNodes} />

      <KnowledgeChunkExplorerPanel
        page={chunkPage}
        filters={chunkFilters}
        loading={loading}
        onFilterChange={onChunkFilterChange}
        onPageChange={onChunkPageChange}
        onEnabledChange={onChunkEnabledChange}
      />

      <ParseJobExplorerPanel
        page={parseJobPage}
        filters={parseJobFilters}
        loading={loading}
        averageParseMs={overview?.averageParseMs ?? 0}
        failedParseJobs={overview?.failedParseJobs ?? 0}
        onFilterChange={onParseJobFilterChange}
        onPageChange={onParseJobPageChange}
      />

      <div className="admin-panel admin-trace-panel">
        <div className="admin-panel-head">
          <div>
            <h3>RAG Trace</h3>
            <p>最近问答的节点链路、检索、生成和总耗时。{overview?.failedTraces ? `失败 ${overview.failedTraces} 次。` : ''}</p>
          </div>
          <Layers size={18} />
        </div>
        <div className="admin-trace-list">
          {(overview?.recentTraces ?? []).length === 0 ? (
            <EmptyState compact title="暂无 Trace" detail="完成一次问答后会记录 RAG 调用链路。" />
          ) : (
            <>
              <div className="admin-trace-row head">
                <span>状态</span>
                <span>问题</span>
                <span>质量</span>
                <span>模型</span>
                <span>来源</span>
                <span>检索</span>
                <span>生成</span>
                <span>评估</span>
                <span>总耗时</span>
              </div>
              {overview!.recentTraces.map((trace) => (
                <div className={`admin-trace-row ${trace.status === 'FAILED' ? 'is-failed' : ''}`} key={trace.id}>
                  <strong className={`admin-trace-status ${trace.status === 'SUCCESS' ? 'is-success' : 'is-failed'}`}>
                    {traceStatusLabel(trace.status)}
                  </strong>
                  <span className="admin-trace-question">
                    <strong>{trace.question}</strong>
                    <small>
                      {trace.username} · {scopeLabel(trace.scope)} · {intentLabel(trace.queryIntent)} · {strategyLabel(trace.answerStrategy)}{trace.comparisonRequested ? ' · 比较' : ''} · {trace.sessionTitle ? `会话：${trace.sessionTitle} · ` : ''}{trace.pipelineName || 'agent-pipeline'} · {trace.scope === 'LIBRARY' ? '全库知识库' : trace.paperTitle || '单篇文献'} · {formatTime(trace.createdAt)}
                    </small>
                    {trace.searchQuery && trace.searchQuery.trim() !== trace.question.trim() && (
                      <small className="admin-trace-search-query">检索式：{trace.searchQuery}</small>
                    )}
                    {trace.queryRewriteEnabled && trace.rewrittenQuery && trace.rewrittenQuery.trim() !== trace.question.trim() && (
                      <small className="admin-trace-rewrite">改写：{trace.rewrittenQuery}{trace.queryRewriteModelName ? ` · ${trace.queryRewriteModelName}` : ''}</small>
                    )}
                    {trace.queryRewriteEnabled && (trace.querySubQuestions ?? []).length > 1 && (
                      <small className="admin-trace-rewrite">子问题：{trace.querySubQuestions.map((item) => compactText(item, 42)).join(' / ')}</small>
                    )}
                    {(trace.queryExpansions ?? []).length > 0 && (
                      <div className="admin-query-expansions">
                        {trace.queryExpansions.map((expansion) => (
                          <span key={`${trace.id}-${expansion.id}-${expansion.term}`} title={expansion.expansions.join('，')}>
                            <i>{expansion.term}</i>
                            <b>{expansion.expansions.slice(0, 4).join('，')}</b>
                          </span>
                        ))}
                      </div>
                    )}
                    {trace.answerContract && (
                      <small className="admin-trace-search-query">契约：{compactText(trace.answerContract, 96)}</small>
                    )}
                    <TraceGuidance trace={trace} />
                    {(trace.toolExecutions ?? []).length > 0 && (
                      <div className="admin-tool-executions">
                        {trace.toolExecutions.map((tool) => (
                          <span
                            className={`admin-tool-execution ${tool.status === 'FAILED' ? 'is-failed' : ''}`}
                            key={`${trace.id}-${tool.name}`}
                            title={tool.errorMessage || tool.details || tool.summary || tool.label || tool.name}
                          >
                            <i>{tool.label || tool.name}</i>
                            <b>{tool.status === 'SUCCESS' ? 'OK' : tool.status}</b>
                            <em>{formatLatency(tool.latencyMs)}</em>
                          </span>
                        ))}
                      </div>
                    )}
                    {trace.memoryTurnCount > 0 && (
                      <small className="admin-trace-memory">记忆：{trace.memoryTurnCount} 轮 / {trace.memoryChars} 字</small>
                    )}
                    {trace.memorySummaryUsed && (
                      <small className="admin-trace-memory">
                        摘要：{trace.memorySummaryTurnCount} 轮 / {trace.memorySummaryChars} 字 · {memorySummaryMethodLabel(trace.memorySummaryMethod)}{trace.memorySummaryModelName ? ` · ${trace.memorySummaryModelName}` : ''}
                      </small>
                    )}
                    {trace.answerQualityNotes && (
                      <small className="admin-trace-quality-note">质量：{trace.answerQualityNotes}</small>
                    )}
                    <small className="admin-trace-quality-note">
                      评估：{qualityMethodLabel(trace.answerQualityMethod)}{trace.answerQualityConfidence ? ` · 置信 ${trace.answerQualityConfidence}%` : ''}{trace.answerQualityJudgeModelName ? ` · ${trace.answerQualityJudgeModelName}` : ''}
                    </small>
                    {(trace.retrievalChannels ?? []).length > 0 && (
                      <div className="admin-retrieval-channels">
                        {trace.retrievalChannels.map((channel) => (
                          <span
                            className={`admin-retrieval-channel ${channel.status === 'FAILED' ? 'is-failed' : ''}`}
                            key={`${trace.id}-${channel.name}`}
                            title={channel.errorMessage || channel.label || channel.name}
                          >
                            <i>{channel.label || channel.name}</i>
                            <b>{channel.candidateCount}</b>
                            <em>{formatLatency(channel.latencyMs)}</em>
                          </span>
                        ))}
                      </div>
                    )}
                    {(trace.retrievalProcessors ?? []).length > 0 && (
                      <div className="admin-retrieval-processors">
                        {trace.retrievalProcessors.map((processor) => (
                          <span
                            className={`admin-retrieval-processor ${processor.status === 'FAILED' ? 'is-failed' : ''}`}
                            key={`${trace.id}-${processor.name}`}
                            title={processor.errorMessage || processor.label || processor.name}
                          >
                            <i>{processor.label || processor.name}</i>
                            <b>{`${processor.inputCount}->${processor.outputCount}`}</b>
                            <em>{formatLatency(processor.latencyMs)}</em>
                          </span>
                        ))}
                      </div>
                    )}
                    {(trace.nodeSpans ?? []).length > 0 && (
                      <div className="admin-node-spans">
                        {trace.nodeSpans.map((span) => (
                          <span
                            className={`admin-node-span ${span.status === 'FAILED' ? 'is-failed' : ''}`}
                            key={`${trace.id}-${span.order}-${span.name}`}
                            title={span.errorMessage || span.name}
                          >
                            <i>{nodeSpanLabel(span.name)}</i>
                            <b>{formatLatency(span.durationMs)}</b>
                          </span>
                        ))}
                      </div>
                    )}
                    {trace.status === 'FAILED' && trace.errorMessage && <small className="admin-trace-error">{trace.errorMessage}</small>}
                  </span>
                  <span className={`admin-quality-badge ${qualityBadgeClass(trace.answerQualityLabel)}`} title={`${qualityMethodLabel(trace.answerQualityMethod)} · ${trace.answerQualityNotes || qualityLabel(trace.answerQualityLabel)}`}>
                    <b>{trace.answerQualityScore}</b>
                    <small>{qualityLabel(trace.answerQualityLabel)}</small>
                  </span>
                  <em>{trace.modelName || 'fallback'}</em>
                  <span>{trace.sourceCount}</span>
                  <span>{formatLatency(trace.retrievalMs)}</span>
                  <span>{formatLatency(trace.generationMs)}</span>
                  <span>{formatLatency(trace.evaluationMs)}</span>
                  <strong>{formatLatency(trace.totalMs)}</strong>
                </div>
              ))}
            </>
          )}
        </div>
      </div>

      <TraceExplorerPanel
        page={tracePage}
        filters={traceFilters}
        loading={loading}
        onFilterChange={onTraceFilterChange}
        onPageChange={onTracePageChange}
      />

      <div className="admin-grid wide">
        <div className="admin-panel">
          <div className="admin-panel-head">
            <div>
              <h3>最近文献</h3>
              <p>按更新时间排序</p>
            </div>
            <Clock3 size={18} />
          </div>
          <div className="admin-recent-list">
            {(overview?.recentPapers ?? []).length === 0 ? (
              <EmptyState compact title="暂无文献" detail="上传 PDF 后会显示最近活动。" />
            ) : (
              overview!.recentPapers.map((paper) => (
                <div className="admin-recent-row" key={paper.id}>
                  <FileText size={16} />
                  <span>{paper.title}</span>
                  <em>{paper.owner}</em>
                  <strong>{processLabel(paper.processStatus)}</strong>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="admin-panel admin-users-panel">
          <div className="admin-panel-head">
            <div>
              <h3>用户管理</h3>
              <p>账号状态与资源使用</p>
            </div>
            <UserCog size={18} />
          </div>
          <div className="admin-user-table">
            <div className="admin-user-row head">
              <span>用户</span>
              <span>角色</span>
              <span>文献</span>
              <span>问答</span>
              <span>存储</span>
              <span>状态</span>
              <span>操作</span>
            </div>
            {users.length === 0 ? (
              <EmptyState compact title="暂无用户" detail="注册用户后会显示账号列表。" />
            ) : (
              users.map((userItem) => (
                <div className="admin-user-row" key={userItem.id}>
                  <span>
                    <strong>{userItem.username}</strong>
                    <small>{userItem.email}</small>
                  </span>
                  <em>{userItem.role}</em>
                  <span>{userItem.indexedPaperCount} / {userItem.paperCount}</span>
                  <span>{userItem.chatCount}</span>
                  <span>{formatBytes(userItem.storageBytes)}</span>
                  <strong className={`admin-user-status ${userItem.status === 'NORMAL' ? 'is-normal' : 'is-disabled'}`}>
                    {userItem.status === 'NORMAL' ? '正常' : '禁用'}
                  </strong>
                  <button
                    className={`secondary-button compact-action ${userItem.status === 'NORMAL' ? 'danger-action' : ''}`}
                    type="button"
                    disabled={userItem.id === currentUserId}
                    onClick={() => onUserStatusChange(userItem, userItem.status === 'NORMAL' ? 'DISABLED' : 'NORMAL')}
                  >
                    {userItem.status === 'NORMAL' ? '禁用' : '恢复'}
                  </button>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

function ActiveStreamTaskPanel({ tasks }: { tasks: ChatStreamTask[] }) {
  return (
    <div className="admin-panel admin-stream-task-panel">
      <div className="admin-panel-head">
        <div>
          <h3>活跃流式任务</h3>
          <p>仍在排队或运行的 SSE Agent 问答请求。</p>
        </div>
        <CircleStop size={18} />
      </div>
      <div className="admin-stream-task-list">
        {tasks.length === 0 ? (
          <EmptyState compact title="暂无活跃任务" detail="用户发起流式问答后会显示当前占用。" />
        ) : (
          <>
            <div className="admin-stream-task-row head">
              <span>阶段</span>
              <span>用户</span>
              <span>问题</span>
              <span>范围</span>
              <span>会话</span>
              <span>开始时间</span>
            </div>
            {tasks.map((task) => (
              <div className={`admin-stream-task-row ${task.cancelled ? 'is-cancelled' : ''}`} key={task.taskId}>
                <strong>{streamPhaseLabel(task.phase)}</strong>
                <span>{task.ownerUsername || '-'}</span>
                <span title={task.question}>{compactText(task.question, 96)}</span>
                <em>{task.paperId ? `单篇 #${task.paperId}` : '全库'}</em>
                <em>{task.sessionId ? `#${task.sessionId}` : '新会话'}</em>
                <small>{formatTime(task.startedAt)}</small>
              </div>
            ))}
          </>
        )}
      </div>
    </div>
  );
}

function KnowledgeChunkExplorerPanel({
  page,
  filters,
  loading,
  onFilterChange,
  onPageChange,
  onEnabledChange
}: {
  page: PageResponse<AdminChunk> | null;
  filters: AdminChunkFilters;
  loading: boolean;
  onFilterChange: (patch: Partial<AdminChunkFilters>) => void;
  onPageChange: (page: number) => void;
  onEnabledChange: (chunk: AdminChunk, enabled: boolean) => void;
}) {
  const [draft, setDraft] = useState<AdminChunkFilters>(filters);
  const chunks = page?.items ?? [];
  const currentPage = page?.page ?? 1;
  const totalPages = page?.totalPages ?? 0;
  const total = page?.total ?? 0;

  useEffect(() => {
    setDraft(filters);
  }, [filters.paperId, filters.keyword]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onFilterChange(draft);
  }

  return (
    <div className="admin-panel admin-chunk-panel">
      <div className="admin-panel-head">
        <div>
          <h3>知识片段治理</h3>
          <p>按文献或关键词检查入库后的 chunk 内容、页码和向量化状态。</p>
        </div>
        <Database size={18} />
      </div>
      <form className="admin-trace-filter admin-chunk-filter" onSubmit={submit}>
        <label>
          <span>文献 ID</span>
          <input type="number" min={1} value={draft.paperId} onChange={(event) => setDraft((current) => ({ ...current, paperId: event.target.value }))} />
        </label>
        <label className="trace-filter-keyword">
          <span>关键词</span>
          <input value={draft.keyword} placeholder="片段正文、标题、作者、用户" onChange={(event) => setDraft((current) => ({ ...current, keyword: event.target.value }))} />
        </label>
        <button className="secondary-button compact-action" type="submit" disabled={loading}>
          查询
        </button>
        <button
          className="secondary-button compact-action"
          type="button"
          disabled={loading}
          onClick={() => {
            setDraft(defaultChunkFilters);
            onFilterChange(defaultChunkFilters);
          }}
        >
          重置
        </button>
      </form>
      <div className="admin-trace-explorer-meta">
        <span>{total} 个知识片段</span>
        <span>第 {totalPages === 0 ? 0 : currentPage} / {totalPages} 页</span>
      </div>
      <div className="admin-chunk-list">
        {chunks.length === 0 ? (
          <EmptyState compact title="暂无匹配片段" detail="解析 PDF 后可按正文或文献 ID 检索 chunk。" />
        ) : (
          chunks.map((chunk) => (
            <article className="admin-chunk-row" key={chunk.id}>
              <div className="admin-chunk-row-head">
                <strong>#{chunk.id} · P{chunk.pageNumber} · C{chunk.chunkIndex}</strong>
                <span>
                  <i className={`admin-chunk-embed ${chunk.embedded ? 'is-embedded' : 'is-missing'}`}>
                    {chunk.embedded ? '已向量化' : '未向量化'}
                  </i>
                  <i className={`admin-chunk-enabled ${chunk.enabled ? 'is-enabled' : 'is-disabled'}`}>
                    {chunk.enabled ? '参与检索' : '已下线'}
                  </i>
                  <button className="secondary-button compact-action" type="button" disabled={loading} onClick={() => onEnabledChange(chunk, !chunk.enabled)}>
                    {chunk.enabled ? '禁用' : '启用'}
                  </button>
                </span>
              </div>
              <small>
                {chunk.username} · 文献 #{chunk.paperId} · {compactText(chunk.paperTitle, 96)} · {chunk.contentLength.toLocaleString('zh-CN')} 字 · {formatTime(chunk.createdAt)}
              </small>
              <p>{chunk.contentPreview}</p>
            </article>
          ))
        )}
      </div>
      <div className="admin-trace-pagination">
        <button className="secondary-button compact-action" type="button" disabled={loading || currentPage <= 1} onClick={() => onPageChange(Math.max(1, currentPage - 1))}>
          上一页
        </button>
        <button className="secondary-button compact-action" type="button" disabled={loading || totalPages === 0 || currentPage >= totalPages} onClick={() => onPageChange(currentPage + 1)}>
          下一页
        </button>
      </div>
    </div>
  );
}

function ParseJobExplorerPanel({
  page,
  filters,
  loading,
  averageParseMs,
  failedParseJobs,
  onFilterChange,
  onPageChange
}: {
  page: PageResponse<AdminParseJob> | null;
  filters: AdminParseJobFilters;
  loading: boolean;
  averageParseMs: number;
  failedParseJobs: number;
  onFilterChange: (patch: Partial<AdminParseJobFilters>) => void;
  onPageChange: (page: number) => void;
}) {
  const [draft, setDraft] = useState<AdminParseJobFilters>(filters);
  const jobs = page?.items ?? [];
  const currentPage = page?.page ?? 1;
  const totalPages = page?.totalPages ?? 0;
  const total = page?.total ?? 0;

  useEffect(() => {
    setDraft(filters);
  }, [filters.status, filters.keyword]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onFilterChange(draft);
  }

  return (
    <div className="admin-panel admin-job-panel admin-parse-job-explorer-panel">
      <div className="admin-panel-head">
        <div>
          <h3>解析任务 Explorer</h3>
          <p>按状态或关键词检索 PDF 入库任务。平均 {formatLatency(averageParseMs)}{failedParseJobs ? ` · 失败 ${failedParseJobs} 次` : ''}</p>
        </div>
        <Loader2 size={18} />
      </div>
      <form className="admin-trace-filter admin-parse-job-filter" onSubmit={submit}>
        <label>
          <span>状态</span>
          <select value={draft.status} onChange={(event) => setDraft((current) => ({ ...current, status: event.target.value }))}>
            <option value="">全部</option>
            <option value="RUNNING">进行中</option>
            <option value="SUCCESS">成功</option>
            <option value="FAILED">失败</option>
          </select>
        </label>
        <label className="trace-filter-keyword">
          <span>关键词</span>
          <input value={draft.keyword} placeholder="文献、文件、用户、节点或错误" onChange={(event) => setDraft((current) => ({ ...current, keyword: event.target.value }))} />
        </label>
        <button className="secondary-button compact-action" type="submit" disabled={loading}>
          查询
        </button>
        <button
          className="secondary-button compact-action"
          type="button"
          disabled={loading}
          onClick={() => {
            setDraft(defaultParseJobFilters);
            onFilterChange(defaultParseJobFilters);
          }}
        >
          重置
        </button>
      </form>
      <div className="admin-trace-explorer-meta">
        <span>{total} 个解析任务</span>
        <span>第 {totalPages === 0 ? 0 : currentPage} / {totalPages} 页</span>
      </div>
      <div className="admin-job-list">
        {jobs.length === 0 ? (
          <EmptyState compact title="暂无匹配任务" detail="触发 PDF 解析后可按状态、文件或错误信息检索入库任务。" />
        ) : (
          <>
            <div className="admin-job-row head">
              <span>状态</span>
              <span>文献</span>
              <span>文件</span>
              <span>页数</span>
              <span>片段</span>
              <span>耗时</span>
            </div>
            {jobs.map((job) => (
              <div className={`admin-job-row ${job.status === 'FAILED' ? 'is-failed' : ''}`} key={job.id}>
                <strong className={`admin-job-status ${job.status === 'SUCCESS' ? 'is-success' : job.status === 'RUNNING' ? 'is-running' : 'is-failed'}`}>
                  {parseJobStatusLabel(job.status)}
                </strong>
                <span className="admin-job-title">
                  <strong>#{job.id} · {job.paperTitle || '未关联文献'}</strong>
                  <small>
                    {job.username} · 开始 {formatTime(job.startedAt)}
                    {job.finishedAt ? ` · 结束 ${formatTime(job.finishedAt)}` : ''}
                    {job.paperId ? ` · 文献 #${job.paperId}` : ''}
                  </small>
                  {(job.nodeSpans ?? []).length > 0 && (
                    <div className="admin-ingestion-spans">
                      {job.nodeSpans.map((span) => (
                        <span
                          className={`admin-node-span ${span.status === 'FAILED' ? 'is-failed' : ''}`}
                          key={`${job.id}-${span.order}-${span.name}`}
                          title={span.errorMessage || span.label || span.name}
                        >
                          <i>{span.label || ingestionNodeLabel(span.name)}</i>
                          <b>{formatLatency(span.durationMs)}</b>
                        </span>
                      ))}
                    </div>
                  )}
                  {job.status === 'FAILED' && job.errorMessage && <small className="admin-job-error">{job.errorMessage}</small>}
                </span>
                <em>{job.fileName} · {formatBytes(job.fileSize)}</em>
                <span>{job.pageCount}</span>
                <span>{job.chunkCount}</span>
                <strong>{job.status === 'RUNNING' ? '进行中' : formatLatency(job.durationMs)}</strong>
              </div>
            ))}
          </>
        )}
      </div>
      <div className="admin-trace-pagination">
        <button className="secondary-button compact-action" type="button" disabled={loading || currentPage <= 1} onClick={() => onPageChange(Math.max(1, currentPage - 1))}>
          上一页
        </button>
        <button className="secondary-button compact-action" type="button" disabled={loading || totalPages === 0 || currentPage >= totalPages} onClick={() => onPageChange(currentPage + 1)}>
          下一页
        </button>
      </div>
    </div>
  );
}

function TraceExplorerPanel({
  page,
  filters,
  loading,
  onFilterChange,
  onPageChange
}: {
  page: PageResponse<AdminTrace> | null;
  filters: AdminTraceFilters;
  loading: boolean;
  onFilterChange: (patch: Partial<AdminTraceFilters>) => void;
  onPageChange: (page: number) => void;
}) {
  const [draft, setDraft] = useState<AdminTraceFilters>(filters);
  const traces = page?.items ?? [];
  const currentPage = page?.page ?? 1;
  const totalPages = page?.totalPages ?? 0;
  const total = page?.total ?? 0;

  useEffect(() => {
    setDraft(filters);
  }, [filters.status, filters.scope, filters.sessionId, filters.keyword]);

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onFilterChange(draft);
  }

  return (
    <div className="admin-panel admin-trace-explorer-panel">
      <div className="admin-panel-head">
        <div>
          <h3>Trace Explorer</h3>
          <p>按状态、范围、会话或关键词检索历史链路，展开查看节点诊断。</p>
        </div>
        <Search size={18} />
      </div>
      <form className="admin-trace-filter" onSubmit={submit}>
        <label>
          <span>状态</span>
          <select value={draft.status} onChange={(event) => setDraft((current) => ({ ...current, status: event.target.value }))}>
            <option value="">全部</option>
            <option value="SUCCESS">成功</option>
            <option value="FAILED">失败</option>
          </select>
        </label>
        <label>
          <span>范围</span>
          <select value={draft.scope} onChange={(event) => setDraft((current) => ({ ...current, scope: event.target.value }))}>
            <option value="">全部</option>
            <option value="LIBRARY">全库</option>
            <option value="PAPER">单篇</option>
          </select>
        </label>
        <label>
          <span>会话 ID</span>
          <input type="number" min={1} value={draft.sessionId} onChange={(event) => setDraft((current) => ({ ...current, sessionId: event.target.value }))} />
        </label>
        <label className="trace-filter-keyword">
          <span>关键词</span>
          <input value={draft.keyword} placeholder="问题、检索式、模型、会话标题" onChange={(event) => setDraft((current) => ({ ...current, keyword: event.target.value }))} />
        </label>
        <button className="secondary-button compact-action" type="submit" disabled={loading}>
          查询
        </button>
        <button
          className="secondary-button compact-action"
          type="button"
          disabled={loading}
          onClick={() => {
            setDraft(defaultTraceFilters);
            onFilterChange(defaultTraceFilters);
          }}
        >
          重置
        </button>
      </form>
      <div className="admin-trace-explorer-meta">
        <span>{total} 条 Trace</span>
        <span>第 {totalPages === 0 ? 0 : currentPage} / {totalPages} 页</span>
      </div>
      <div className="admin-trace-list">
        {traces.length === 0 ? (
          <EmptyState compact title="暂无匹配 Trace" detail="调整过滤条件后再试。" />
        ) : (
          traces.map((trace) => (
            <div className={`admin-trace-row explorer ${trace.status === 'FAILED' ? 'is-failed' : ''}`} key={trace.id}>
              <strong className={`admin-trace-status ${trace.status === 'SUCCESS' ? 'is-success' : 'is-failed'}`}>
                {traceStatusLabel(trace.status)}
              </strong>
              <span className="admin-trace-question">
                <strong>#{trace.id} · {trace.question}</strong>
                <small>
                  {trace.username} · {scopeLabel(trace.scope)} · {intentLabel(trace.queryIntent)} · {strategyLabel(trace.answerStrategy)} · {trace.sessionTitle ? `会话：${trace.sessionTitle} · ` : ''}{trace.paperTitle || '全库知识库'} · {formatTime(trace.createdAt)}
                </small>
                <details className="admin-trace-detail">
                  <summary>诊断详情</summary>
                  <div className="admin-trace-detail-grid">
                    <span>
                      <b>检索式</b>
                      <small>{trace.searchQuery || '-'}</small>
                    </span>
                    <span>
                      <b>改写</b>
                      <small>{trace.rewrittenQuery || '-'}{trace.queryRewriteModelName ? ` · ${trace.queryRewriteModelName}` : ''}</small>
                    </span>
                    <span>
                      <b>会话记忆</b>
                      <small>{trace.memoryTurnCount} 轮 / {trace.memoryChars} 字{trace.memorySummaryUsed ? ` · 摘要 ${trace.memorySummaryTurnCount} 轮 / ${trace.memorySummaryChars} 字` : ''}</small>
                    </span>
                    <span>
                      <b>质量评估</b>
                      <small>{trace.answerQualityScore} · {qualityLabel(trace.answerQualityLabel)} · {qualityMethodLabel(trace.answerQualityMethod)}{trace.answerQualityConfidence ? ` · 置信 ${trace.answerQualityConfidence}%` : ''}</small>
                    </span>
                  </div>
                  {trace.querySubQuestions.length > 0 && (
                    <small className="admin-trace-search-query">子问题：{trace.querySubQuestions.map((item) => compactText(item, 56)).join(' / ')}</small>
                  )}
                  {trace.answerContract && (
                    <small className="admin-trace-search-query">契约：{compactText(trace.answerContract, 180)}</small>
                  )}
                  <TraceGuidance trace={trace} />
                  {(trace.toolExecutions ?? []).length > 0 && (
                    <div className="admin-tool-executions">
                      {trace.toolExecutions.map((tool) => (
                        <span className={`admin-tool-execution ${tool.status === 'FAILED' ? 'is-failed' : ''}`} key={`${trace.id}-explorer-tool-${tool.name}`} title={tool.errorMessage || tool.details || tool.summary || tool.label || tool.name}>
                          <i>{tool.label || tool.name}</i>
                          <b>{tool.status === 'SUCCESS' ? compactText(tool.summary || '已执行', 48) : tool.status}</b>
                          <em>{formatLatency(tool.latencyMs)}</em>
                        </span>
                      ))}
                    </div>
                  )}
                  {trace.answerQualityNotes && (
                    <small className="admin-trace-quality-note">质量说明：{trace.answerQualityNotes}</small>
                  )}
                  {(trace.retrievalChannels ?? []).length > 0 && (
                    <div className="admin-retrieval-channels">
                      {trace.retrievalChannels.map((channel) => (
                        <span className={`admin-retrieval-channel ${channel.status === 'FAILED' ? 'is-failed' : ''}`} key={`${trace.id}-explorer-channel-${channel.name}`} title={channel.errorMessage || channel.label || channel.name}>
                          <i>{channel.label || channel.name}</i>
                          <b>{channel.candidateCount}</b>
                          <em>{formatLatency(channel.latencyMs)}</em>
                        </span>
                      ))}
                    </div>
                  )}
                  {(trace.retrievalProcessors ?? []).length > 0 && (
                    <div className="admin-retrieval-processors">
                      {trace.retrievalProcessors.map((processor) => (
                        <span className={`admin-retrieval-processor ${processor.status === 'FAILED' ? 'is-failed' : ''}`} key={`${trace.id}-explorer-processor-${processor.name}`} title={processor.errorMessage || processor.label || processor.name}>
                          <i>{processor.label || processor.name}</i>
                          <b>{`${processor.inputCount}->${processor.outputCount}`}</b>
                          <em>{formatLatency(processor.latencyMs)}</em>
                        </span>
                      ))}
                    </div>
                  )}
                  {(trace.nodeSpans ?? []).length > 0 && (
                    <div className="admin-node-spans">
                      {trace.nodeSpans.map((span) => (
                        <span className={`admin-node-span ${span.status === 'FAILED' ? 'is-failed' : ''}`} key={`${trace.id}-explorer-span-${span.order}-${span.name}`} title={span.errorMessage || span.name}>
                          <i>{nodeSpanLabel(span.name)}</i>
                          <b>{formatLatency(span.durationMs)}</b>
                        </span>
                      ))}
                    </div>
                  )}
                  {trace.errorMessage && <small className="admin-trace-error">{trace.errorMessage}</small>}
                </details>
              </span>
              <span className={`admin-quality-badge ${qualityBadgeClass(trace.answerQualityLabel)}`}>
                <b>{trace.answerQualityScore}</b>
                <small>{qualityLabel(trace.answerQualityLabel)}</small>
              </span>
              <em>{trace.modelName || 'fallback'}</em>
              <span>{trace.sourceCount}</span>
              <span>{formatLatency(trace.retrievalMs)}</span>
              <span>{formatLatency(trace.generationMs)}</span>
              <span>{formatLatency(trace.evaluationMs)}</span>
              <strong>{formatLatency(trace.totalMs)}</strong>
            </div>
          ))
        )}
      </div>
      <div className="admin-trace-pagination">
        <button className="secondary-button compact-action" type="button" disabled={loading || currentPage <= 1} onClick={() => onPageChange(Math.max(1, currentPage - 1))}>
          上一页
        </button>
        <button className="secondary-button compact-action" type="button" disabled={loading || totalPages === 0 || currentPage >= totalPages} onClick={() => onPageChange(currentPage + 1)}>
          下一页
        </button>
      </div>
    </div>
  );
}

function TraceGuidance({ trace }: { trace: AdminTrace }) {
  const guidance = trace.guidance;
  if (!guidance?.required) {
    return null;
  }
  const suggestions = guidance.suggestions ?? [];
  return (
    <div className="admin-guidance" title={guidance.reason || guidance.message || guidance.type}>
      <span>
        <i>引导</i>
        <b>{compactText(guidance.message || guidance.type, 96)}</b>
      </span>
      {suggestions.slice(0, 3).map((item, index) => (
        <em key={`${trace.id}-guidance-${index}`}>{compactText(item, 54)}</em>
      ))}
    </div>
  );
}

function AgentPipelinePanel({
  nodes,
  onEnabledChange,
  loading
}: {
  nodes: AdminAgentPipelineNode[];
  onEnabledChange: (node: AdminAgentPipelineNode, enabled: boolean) => void;
  loading: boolean;
}) {
  return (
    <div className="admin-panel admin-agent-pipeline-panel">
      <div className="admin-panel-head">
        <div>
          <h3>Agent Pipeline 节点</h3>
          <p>当前问答链路的节点顺序、职责和历史运行画像。</p>
        </div>
        <Layers size={18} />
      </div>
      <div className="agent-pipeline-list">
        {nodes.length === 0 ? (
          <EmptyState compact title="暂无节点" detail="注册 AgentNode Bean 后会显示在这里。" />
        ) : (
          nodes.map((node) => (
            <div className={`agent-pipeline-row ${node.enabled ? '' : 'is-disabled'}`} key={`${node.pipelineName}-${node.sortOrder}-${node.name}`}>
              <strong className="agent-pipeline-order">#{node.sortOrder}</strong>
              <span>
                <b>{node.label}</b>
                <small>{node.name} · {node.type} · {node.enabled ? '启用' : '停用'}{node.canDisable ? '' : ' · 主链路'}</small>
              </span>
              <p>{node.description}</p>
              <em>{node.totalRuns} 次</em>
              <em>{node.successRuns} 成功</em>
              <em>{node.failedRuns} 失败</em>
              <em>{node.skippedRuns} 跳过</em>
              <strong>{formatLatency(node.averageLatencyMs)}</strong>
              <small>{node.lastSeenAt ? formatTime(node.lastSeenAt) : '尚未运行'}</small>
              <button
                className={`secondary-button compact-action ${node.enabled ? 'danger-action' : ''}`}
                type="button"
                disabled={loading || !node.canDisable}
                title={node.canDisable ? undefined : '主链路节点不能停用'}
                onClick={() => onEnabledChange(node, !node.enabled)}
              >
                {node.canDisable ? (node.enabled ? '停用' : '启用') : '锁定'}
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function IngestionPipelinePanel({ nodes }: { nodes: AdminIngestionPipelineNode[] }) {
  return (
    <div className="admin-panel admin-ingestion-pipeline-panel">
      <div className="admin-panel-head">
        <div>
          <h3>入库 Pipeline 节点</h3>
          <p>PDF 解析入库链路的节点顺序、职责和历史运行画像。</p>
        </div>
        <Loader2 size={18} />
      </div>
      <div className="ingestion-pipeline-list">
        {nodes.length === 0 ? (
          <EmptyState compact title="暂无节点" detail="注册入库 Pipeline 节点后会显示在这里。" />
        ) : (
          nodes.map((node) => (
            <div className={`ingestion-pipeline-row ${node.enabled ? '' : 'is-disabled'}`} key={`${node.pipelineName}-${node.sortOrder}-${node.name}`}>
              <strong className="ingestion-pipeline-order">#{node.sortOrder}</strong>
              <span>
                <b>{node.label}</b>
                <small>{node.name} · {node.type}</small>
              </span>
              <p>{node.description}</p>
              <em>{node.totalRuns} 次</em>
              <em>{node.successRuns} 成功</em>
              <em>{node.failedRuns} 失败</em>
              <strong>{formatLatency(node.averageLatencyMs)}</strong>
              <small>{node.lastSeenAt ? formatTime(node.lastSeenAt) : '尚未运行'}</small>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function RetrievalCatalogPanel({
  channels,
  processors
}: {
  channels: AdminRetrievalChannelCatalog[];
  processors: AdminRetrievalProcessorCatalog[];
}) {
  return (
    <div className="admin-panel admin-retrieval-catalog-panel">
      <div className="admin-panel-head">
        <div>
          <h3>检索通道与后处理器</h3>
          <p>多路召回和后处理流水线的注册目录、候选规模和历史运行画像。</p>
        </div>
        <Search size={18} />
      </div>
      <div className="retrieval-catalog-section">
        <h4>检索通道</h4>
        <div className="retrieval-catalog-list">
          {channels.length === 0 ? (
            <EmptyState compact title="暂无检索通道" detail="注册 RetrievalChannel Bean 后会显示在这里。" />
          ) : (
            channels.map((channel) => (
              <div className={`retrieval-catalog-row ${channel.enabled ? '' : 'is-disabled'}`} key={`channel-${channel.name}`}>
                <strong className="retrieval-catalog-order">#{channel.priority}</strong>
                <span>
                  <b>{channel.label}</b>
                  <small>{channel.name}</small>
                </span>
                <p>{channel.description}</p>
                <em>{channel.totalRuns} 次</em>
                <em>{channel.successRuns} 成功</em>
                <em>{channel.failedRuns} 失败</em>
                <em>{channel.totalCandidates} 候选</em>
                <strong>{channel.averageCandidates} 均候选</strong>
                <strong>{formatLatency(channel.averageLatencyMs)}</strong>
                <small>{channel.lastSeenAt ? formatTime(channel.lastSeenAt) : '尚未运行'}</small>
              </div>
            ))
          )}
        </div>
      </div>
      <div className="retrieval-catalog-section">
        <h4>后处理器</h4>
        <div className="retrieval-catalog-list">
          {processors.length === 0 ? (
            <EmptyState compact title="暂无后处理器" detail="注册 RetrievalPostProcessor Bean 后会显示在这里。" />
          ) : (
            processors.map((processor) => (
              <div className={`retrieval-catalog-row ${processor.enabled ? '' : 'is-disabled'}`} key={`processor-${processor.name}`}>
                <strong className="retrieval-catalog-order">#{processor.sortOrder}</strong>
                <span>
                  <b>{processor.label}</b>
                  <small>{processor.name}</small>
                </span>
                <p>{processor.description}</p>
                <em>{processor.totalRuns} 次</em>
                <em>{processor.successRuns} 成功</em>
                <em>{processor.failedRuns} 失败</em>
                <em>{processor.averageInputCount} 入</em>
                <strong>{processor.averageOutputCount} 出</strong>
                <strong>{formatLatency(processor.averageLatencyMs)}</strong>
                <small>{processor.lastSeenAt ? formatTime(processor.lastSeenAt) : '尚未运行'}</small>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

function AgentToolPanel({
  tools,
  onEnabledChange,
  onMinimumRoleChange,
  loading
}: {
  tools: AdminAgentTool[];
  onEnabledChange: (tool: AdminAgentTool, enabled: boolean) => void;
  onMinimumRoleChange: (tool: AdminAgentTool, minimumRole: 'USER' | 'ADMIN') => void;
  loading: boolean;
}) {
  return (
    <div className="admin-panel admin-agent-tool-panel">
      <div className="admin-panel-head">
        <div>
          <h3>Agent 工具目录</h3>
          <p>当前 Pipeline 可调用的内部工具、触发规则和历史调用画像。</p>
        </div>
        <SlidersHorizontal size={18} />
      </div>
      <div className="agent-tool-list">
        {tools.length === 0 ? (
          <EmptyState compact title="暂无工具" detail="注册 AgentTool Bean 后会显示在这里。" />
        ) : (
          tools.map((tool) => (
            <div className={`agent-tool-row ${tool.enabled ? '' : 'is-disabled'}`} key={tool.name}>
              <strong className="agent-tool-status">{tool.enabled ? '启用' : '停用'}</strong>
              <span>
                <b>{tool.label}</b>
                <small>{tool.name} · {tool.source}</small>
              </span>
              <p>{tool.description}</p>
              <p>{tool.triggerDescription}</p>
              <em>{tool.totalCalls} 次</em>
              <em>{tool.successCalls} 成功</em>
              <em>{tool.failedCalls} 失败</em>
              <em>{tool.minimumRole === 'ADMIN' ? '仅管理员' : '全员'}</em>
              <strong>{formatLatency(tool.averageLatencyMs)}</strong>
              <small>{tool.lastSeenAt ? formatTime(tool.lastSeenAt) : '尚未调用'}</small>
              <button
                className="secondary-button compact-action"
                type="button"
                disabled={loading}
                title={tool.minimumRole === 'ADMIN' ? '开放给所有登录用户' : '限制为仅管理员调用'}
                onClick={() => onMinimumRoleChange(tool, tool.minimumRole === 'ADMIN' ? 'USER' : 'ADMIN')}
              >
                {tool.minimumRole === 'ADMIN' ? '开放全员' : '限管理员'}
              </button>
              <button
                className={`secondary-button compact-action ${tool.enabled ? 'danger-action' : ''}`}
                type="button"
                disabled={loading}
                onClick={() => onEnabledChange(tool, !tool.enabled)}
              >
                {tool.enabled ? '停用' : '启用'}
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function AgentToolExecutionPanel({
  page,
  filters,
  tools,
  loading,
  onFilterChange,
  onPageChange
}: {
  page: PageResponse<AdminAgentToolExecution> | null;
  filters: AdminAgentToolExecutionFilters;
  tools: AdminAgentTool[];
  loading: boolean;
  onFilterChange: (patch: Partial<AdminAgentToolExecutionFilters>) => void;
  onPageChange: (page: number) => void;
}) {
  const [draft, setDraft] = useState<AdminAgentToolExecutionFilters>(filters);
  const executions = page?.items ?? [];
  const currentPage = page?.page ?? 1;
  const totalPages = page?.totalPages ?? 0;
  const total = page?.total ?? 0;

  useEffect(() => {
    setDraft(filters);
  }, [filters.toolName, filters.status, filters.keyword]);

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onFilterChange(draft);
  }

  return (
    <div className="admin-panel admin-agent-tool-execution-panel">
      <div className="admin-panel-head">
        <div>
          <h3>Agent 工具调用审计</h3>
          <p>按工具、状态和关键词追踪最近工具调用，定位触发问题、失败原因和耗时。</p>
        </div>
        <History size={18} />
      </div>
      <form className="admin-trace-filter agent-tool-execution-filter" onSubmit={submit}>
        <label>
          <span>工具</span>
          <select value={draft.toolName} onChange={(event) => setDraft((current) => ({ ...current, toolName: event.target.value }))}>
            <option value="">全部</option>
            {tools.map((tool) => (
              <option value={tool.name} key={`audit-tool-${tool.name}`}>
                {tool.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>状态</span>
          <select value={draft.status} onChange={(event) => setDraft((current) => ({ ...current, status: event.target.value }))}>
            <option value="">全部</option>
            <option value="SUCCESS">成功</option>
            <option value="FAILED">失败</option>
          </select>
        </label>
        <label className="trace-filter-keyword">
          <span>关键词</span>
          <input value={draft.keyword} placeholder="问题、摘要、详情、错误、用户" onChange={(event) => setDraft((current) => ({ ...current, keyword: event.target.value }))} />
        </label>
        <button className="secondary-button compact-action" type="submit" disabled={loading}>
          查询
        </button>
        <button
          className="secondary-button compact-action"
          type="button"
          disabled={loading}
          onClick={() => {
            setDraft(defaultAgentToolExecutionFilters);
            onFilterChange(defaultAgentToolExecutionFilters);
          }}
        >
          重置
        </button>
      </form>
      <div className="admin-trace-explorer-meta agent-tool-execution-meta">
        <span>{total} 条工具调用</span>
        <span>第 {totalPages === 0 ? 0 : currentPage} / {totalPages} 页</span>
      </div>
      <div className="agent-tool-execution-list">
        {executions.length === 0 ? (
          <EmptyState compact title="暂无工具调用" detail="触发一次带工具结果的问答后会显示在这里。" />
        ) : (
          executions.map((execution) => (
            <div className={`agent-tool-execution-row ${execution.status === 'FAILED' ? 'is-failed' : ''}`} key={`${execution.traceId}-${execution.name}-${execution.createdAt}`}>
              <strong className={`admin-trace-status ${execution.status === 'SUCCESS' ? 'is-success' : 'is-failed'}`}>
                {execution.status === 'SUCCESS' ? '成功' : '失败'}
              </strong>
              <span className="agent-tool-execution-main">
                <b>{execution.label || execution.name}</b>
                <small>{execution.name} · Trace #{execution.traceId} · {traceStatusLabel(execution.traceStatus)}</small>
              </span>
              <span className="agent-tool-execution-question">
                <b>{compactText(execution.question, 96)}</b>
                <small>
                  {execution.username} · {scopeLabel(execution.scope)} · {execution.sessionTitle ? `会话：${execution.sessionTitle} · ` : ''}{execution.paperTitle || '全库知识库'} · {formatTime(execution.createdAt)}
                </small>
              </span>
              <span className="agent-tool-execution-summary" title={execution.errorMessage || execution.details || execution.summary || ''}>
                <b>{execution.status === 'SUCCESS' ? compactText(execution.summary || '已执行', 72) : compactText(execution.errorMessage || '执行失败', 72)}</b>
                <small>{execution.details ? compactText(execution.details, 96) : execution.errorMessage ? compactText(execution.errorMessage, 96) : '-'}</small>
              </span>
              <strong>{formatLatency(execution.latencyMs)}</strong>
            </div>
          ))
        )}
      </div>
      <div className="admin-trace-pagination agent-tool-execution-pagination">
        <button className="secondary-button compact-action" type="button" disabled={loading || currentPage <= 1} onClick={() => onPageChange(currentPage - 1)}>
          上一页
        </button>
        <button className="secondary-button compact-action" type="button" disabled={loading || totalPages === 0 || currentPage >= totalPages} onClick={() => onPageChange(currentPage + 1)}>
          下一页
        </button>
      </div>
    </div>
  );
}

function ModelTargetPanel({
  targets,
  onCreate,
  onUpdate,
  onDelete
}: {
  targets: ModelTarget[];
  onCreate: (input: ModelTargetInput) => void;
  onUpdate: (target: ModelTarget, patch: Partial<ModelTargetInput & { enabled: boolean }>) => void;
  onDelete: (target: ModelTarget) => void;
}) {
  const [editingId, setEditingId] = useState<number | null>(null);
  const [code, setCode] = useState('');
  const [provider, setProvider] = useState('OPENAI_COMPATIBLE');
  const [taskType, setTaskType] = useState('GENERAL');
  const [modelName, setModelName] = useState('');
  const [description, setDescription] = useState('');
  const [baseUrl, setBaseUrl] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [priority, setPriority] = useState('100');
  const [timeoutSeconds, setTimeoutSeconds] = useState('45');
  const editingTarget = targets.find((item) => item.id === editingId) ?? null;
  const providerRequiresUrl = provider === 'OPENAI_COMPATIBLE';

  function reset() {
    setEditingId(null);
    setCode('');
    setProvider('OPENAI_COMPATIBLE');
    setTaskType('GENERAL');
    setModelName('');
    setDescription('');
    setBaseUrl('');
    setApiKey('');
    setPriority('100');
    setTimeoutSeconds('45');
  }

  function startEdit(target: ModelTarget) {
    setEditingId(target.id);
    setCode(target.code);
    setProvider(target.provider);
    setTaskType(target.taskType ?? 'GENERAL');
    setModelName(target.modelName);
    setDescription(target.description ?? '');
    setBaseUrl(target.baseUrl ?? '');
    setApiKey('');
    setPriority(String(target.priority ?? 100));
    setTimeoutSeconds(String(target.timeoutSeconds ?? 45));
  }

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input = {
      code: code.trim(),
      provider,
      taskType,
      modelName: modelName.trim(),
      description: description.trim(),
      baseUrl: baseUrl.trim(),
      apiKey: apiKey.trim(),
      priority: Number.parseInt(priority, 10) || 100,
      timeoutSeconds: Number.parseInt(timeoutSeconds, 10) || 45
    };
    if (!input.code || !input.provider || !input.taskType || !input.modelName || (providerRequiresUrl && !input.baseUrl)) {
      return;
    }
    if (editingTarget) {
      onUpdate(editingTarget, input);
    } else {
      onCreate(input);
    }
    reset();
  }

  return (
    <div className="admin-panel admin-model-target-panel">
      <div className="admin-panel-head">
        <div>
          <h3>模型目标</h3>
          <p>按任务类型和优先级配置模型目标，失败后自动尝试下一个。</p>
        </div>
        <ServerCog size={18} />
      </div>
      <form className="model-target-form" onSubmit={submit}>
        <label>
          <span>标识</span>
          <input value={code} maxLength={64} placeholder="DEEPSEEK_PRIMARY" onChange={(event) => setCode(event.target.value)} />
        </label>
        <label>
          <span>供应商</span>
          <select value={provider} onChange={(event) => setProvider(event.target.value)}>
            <option value="OPENAI_COMPATIBLE">OPENAI_COMPATIBLE</option>
            <option value="ENV">ENV</option>
          </select>
        </label>
        <label>
          <span>任务类型</span>
          <select value={taskType} onChange={(event) => setTaskType(event.target.value)}>
            {modelTaskOptions.map((option) => (
              <option value={option.value} key={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
        <label>
          <span>模型</span>
          <input value={modelName} maxLength={160} placeholder={provider === 'ENV' ? 'ENV_CONFIGURED_CHAT_MODEL' : 'deepseek-v4-flash'} onChange={(event) => setModelName(event.target.value)} />
        </label>
        <label>
          <span>优先级</span>
          <input value={priority} inputMode="numeric" onChange={(event) => setPriority(event.target.value)} />
        </label>
        <label>
          <span>超时秒</span>
          <input value={timeoutSeconds} inputMode="numeric" onChange={(event) => setTimeoutSeconds(event.target.value)} />
        </label>
        <label className="wide">
          <span>Base URL</span>
          <input value={baseUrl} maxLength={500} placeholder="https://api.openai.com" disabled={provider === 'ENV'} onChange={(event) => setBaseUrl(event.target.value)} />
        </label>
        <label className="wide">
          <span>API Key</span>
          <input value={apiKey} maxLength={2000} placeholder={editingTarget?.apiKeyConfigured ? '留空表示保留原密钥' : '可选，本地兼容服务可留空'} disabled={provider === 'ENV'} onChange={(event) => setApiKey(event.target.value)} />
        </label>
        <label className="wide">
          <span>说明</span>
          <input value={description} maxLength={500} placeholder="主模型、备用模型或本地兼容网关" onChange={(event) => setDescription(event.target.value)} />
        </label>
        <div className="model-target-actions">
          {editingTarget && (
            <button className="secondary-button" type="button" onClick={reset}>
              取消
            </button>
          )}
          <button className="primary-button" type="submit" disabled={!code.trim() || !modelName.trim() || (providerRequiresUrl && !baseUrl.trim())}>
            <Plus size={17} />
            {editingTarget ? '更新' : '添加'}
          </button>
        </div>
      </form>
      <div className="model-target-list">
        {targets.length === 0 ? (
          <EmptyState compact title="暂无模型目标" detail="至少保留一个启用目标，模型路由才会尝试真实模型。" />
        ) : (
          [...targets].sort(compareModelTargets).map((target) => (
            <div className={`model-target-row ${target.enabled ? '' : 'is-disabled'}`} key={target.id}>
              <strong>{target.priority}</strong>
              <span>
                <b>{target.code}</b>
                <small>{modelTaskLabel(target.taskType)} · {target.provider} · {target.modelName} · {target.apiKeyConfigured ? '已配置密钥' : '无密钥'} · {target.timeoutSeconds}s</small>
              </span>
              <em>{target.baseUrl || target.description || '环境配置'}</em>
              <button className="secondary-button compact-action" type="button" onClick={() => startEdit(target)}>
                编辑
              </button>
              <button className="secondary-button compact-action" type="button" onClick={() => onUpdate(target, { enabled: !target.enabled })}>
                {target.enabled ? '停用' : '启用'}
              </button>
              <button className="icon-button small danger" type="button" title="删除模型目标" disabled={target.code === 'ENV_DEFAULT'} onClick={() => onDelete(target)}>
                <Trash2 size={15} />
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function RagSettingsPanel({ settings, rateLimit, onUpdate }: { settings: RagSettings | null; rateLimit?: AdminChatRateLimit; onUpdate: (input: RagSettingsInput) => void }) {
  const [form, setForm] = useState<RagSettingsFormState>(toRagSettingsForm(settings));

  useEffect(() => {
    setForm(toRagSettingsForm(settings));
  }, [settings]);

  function updateField(key: keyof RagSettingsInput, value: string) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onUpdate({
      candidateLimit: boundedInt(form.candidateLimit, 1, 50, defaultRagSettingsInput.candidateLimit),
      resultLimit: boundedInt(form.resultLimit, 1, 20, defaultRagSettingsInput.resultLimit),
      sourceExcerptChars: boundedInt(form.sourceExcerptChars, 120, 1200, defaultRagSettingsInput.sourceExcerptChars),
      vectorWeight: boundedFloat(form.vectorWeight, 0, 3, defaultRagSettingsInput.vectorWeight),
      keywordWeight: boundedFloat(form.keywordWeight, 0, 3, defaultRagSettingsInput.keywordWeight),
      memoryHistoryTurns: boundedInt(form.memoryHistoryTurns, 0, 12, defaultRagSettingsInput.memoryHistoryTurns),
      memoryMaxChars: boundedInt(form.memoryMaxChars, 0, 8000, defaultRagSettingsInput.memoryMaxChars),
      memorySummaryEnabled: form.memorySummaryEnabled,
      memorySummaryStartTurns: boundedInt(form.memorySummaryStartTurns, 2, 50, defaultRagSettingsInput.memorySummaryStartTurns),
      memorySummaryMaxChars: boundedInt(form.memorySummaryMaxChars, 300, 6000, defaultRagSettingsInput.memorySummaryMaxChars),
      queryRewriteEnabled: form.queryRewriteEnabled,
      queryRewriteMaxSubQuestions: boundedInt(form.queryRewriteMaxSubQuestions, 1, 6, defaultRagSettingsInput.queryRewriteMaxSubQuestions),
      answerQualityJudgeEnabled: form.answerQualityJudgeEnabled,
      rerankModelEnabled: form.rerankModelEnabled,
      rerankModelMaxCandidates: boundedInt(form.rerankModelMaxCandidates, 2, 20, defaultRagSettingsInput.rerankModelMaxCandidates),
      chatRateLimitEnabled: form.chatRateLimitEnabled,
      chatRateLimitGlobalConcurrency: boundedInt(form.chatRateLimitGlobalConcurrency, 1, 100, defaultRagSettingsInput.chatRateLimitGlobalConcurrency),
      chatRateLimitUserConcurrency: boundedInt(form.chatRateLimitUserConcurrency, 1, 20, defaultRagSettingsInput.chatRateLimitUserConcurrency),
      chatRateLimitUserPerMinute: boundedInt(form.chatRateLimitUserPerMinute, 1, 600, defaultRagSettingsInput.chatRateLimitUserPerMinute)
    });
  }

  return (
    <div className="admin-panel admin-rag-settings-panel">
      <div className="admin-panel-head">
        <div>
          <h3>RAG 检索参数</h3>
          <p>控制查询改写、候选召回、会话记忆、模型重排、模型评审、多通道融合权重和聊天入口限流。</p>
        </div>
        <SlidersHorizontal size={18} />
      </div>
      <div className="rate-limit-status">
        <span>{rateLimit ? (rateLimit.enabled ? '限流已启用' : '限流已关闭') : '等待加载'}</span>
        <strong>{rateLimit?.activeGlobal ?? 0} / {rateLimit?.globalConcurrencyLimit ?? defaultRagSettingsInput.chatRateLimitGlobalConcurrency}</strong>
        <em>当前运行中 · {rateLimit?.activeUsers ?? 0} 个活跃用户 · 最近一分钟 {rateLimit?.recentRequests ?? 0} 次</em>
      </div>
      <form className="rag-settings-form" onSubmit={submit}>
        <label>
          <span>候选数</span>
          <input type="number" min={1} max={50} value={form.candidateLimit} onChange={(event) => updateField('candidateLimit', event.target.value)} />
        </label>
        <label>
          <span>来源数</span>
          <input type="number" min={1} max={20} value={form.resultLimit} onChange={(event) => updateField('resultLimit', event.target.value)} />
        </label>
        <label>
          <span>摘录长度</span>
          <input type="number" min={120} max={1200} step={20} value={form.sourceExcerptChars} onChange={(event) => updateField('sourceExcerptChars', event.target.value)} />
        </label>
        <label>
          <span>向量权重</span>
          <input type="number" min={0} max={3} step={0.05} value={form.vectorWeight} onChange={(event) => updateField('vectorWeight', event.target.value)} />
        </label>
        <label>
          <span>关键词权重</span>
          <input type="number" min={0} max={3} step={0.05} value={form.keywordWeight} onChange={(event) => updateField('keywordWeight', event.target.value)} />
        </label>
        <label>
          <span>记忆轮数</span>
          <input type="number" min={0} max={12} value={form.memoryHistoryTurns} onChange={(event) => updateField('memoryHistoryTurns', event.target.value)} />
        </label>
        <label>
          <span>记忆字符</span>
          <input type="number" min={0} max={8000} step={200} value={form.memoryMaxChars} onChange={(event) => updateField('memoryMaxChars', event.target.value)} />
        </label>
        <label>
          <span>摘要触发</span>
          <input type="number" min={2} max={50} value={form.memorySummaryStartTurns} onChange={(event) => updateField('memorySummaryStartTurns', event.target.value)} />
        </label>
        <label>
          <span>摘要字符</span>
          <input type="number" min={300} max={6000} step={100} value={form.memorySummaryMaxChars} onChange={(event) => updateField('memorySummaryMaxChars', event.target.value)} />
        </label>
        <label>
          <span>改写子问</span>
          <input type="number" min={1} max={6} value={form.queryRewriteMaxSubQuestions} onChange={(event) => updateField('queryRewriteMaxSubQuestions', event.target.value)} />
        </label>
        <label>
          <span>重排候选</span>
          <input type="number" min={2} max={20} value={form.rerankModelMaxCandidates} onChange={(event) => updateField('rerankModelMaxCandidates', event.target.value)} />
        </label>
        <label>
          <span>全局并发</span>
          <input type="number" min={1} max={100} value={form.chatRateLimitGlobalConcurrency} onChange={(event) => updateField('chatRateLimitGlobalConcurrency', event.target.value)} />
        </label>
        <label>
          <span>用户并发</span>
          <input type="number" min={1} max={20} value={form.chatRateLimitUserConcurrency} onChange={(event) => updateField('chatRateLimitUserConcurrency', event.target.value)} />
        </label>
        <label>
          <span>每分钟次数</span>
          <input type="number" min={1} max={600} value={form.chatRateLimitUserPerMinute} onChange={(event) => updateField('chatRateLimitUserPerMinute', event.target.value)} />
        </label>
        <label className="rag-settings-check">
          <span>会话摘要</span>
          <input type="checkbox" checked={form.memorySummaryEnabled} onChange={(event) => setForm((current) => ({ ...current, memorySummaryEnabled: event.target.checked }))} />
        </label>
        <label className="rag-settings-check">
          <span>查询改写</span>
          <input type="checkbox" checked={form.queryRewriteEnabled} onChange={(event) => setForm((current) => ({ ...current, queryRewriteEnabled: event.target.checked }))} />
        </label>
        <label className="rag-settings-check">
          <span>模型评审</span>
          <input type="checkbox" checked={form.answerQualityJudgeEnabled} onChange={(event) => setForm((current) => ({ ...current, answerQualityJudgeEnabled: event.target.checked }))} />
        </label>
        <label className="rag-settings-check">
          <span>模型重排</span>
          <input type="checkbox" checked={form.rerankModelEnabled} onChange={(event) => setForm((current) => ({ ...current, rerankModelEnabled: event.target.checked }))} />
        </label>
        <label className="rag-settings-check">
          <span>聊天限流</span>
          <input type="checkbox" checked={form.chatRateLimitEnabled} onChange={(event) => setForm((current) => ({ ...current, chatRateLimitEnabled: event.target.checked }))} />
        </label>
        <div className="rag-settings-actions">
          <em>{settings?.updatedAt ? `更新于 ${formatTime(settings.updatedAt)}` : '使用默认参数'}</em>
          <button className="primary-button" type="submit">
            <CheckCircle2 size={17} />
            保存
          </button>
        </div>
      </form>
    </div>
  );
}

function IntentRoutePanel({
  routes,
  tools,
  onCreate,
  onUpdate,
  onDelete
}: {
  routes: IntentRoute[];
  tools: AdminAgentTool[];
  onCreate: (input: IntentRouteInput) => void;
  onUpdate: (route: IntentRoute, patch: Partial<IntentRouteInput & { enabled: boolean }>) => void;
  onDelete: (route: IntentRoute) => void;
}) {
  const [editingId, setEditingId] = useState<number | null>(null);
  const [intentCode, setIntentCode] = useState('');
  const [label, setLabel] = useState('');
  const [description, setDescription] = useState('');
  const [keywords, setKeywords] = useState('');
  const [searchHint, setSearchHint] = useState('');
  const [answerStrategy, setAnswerStrategy] = useState('EVIDENCE_GROUNDED_QA');
  const [answerContract, setAnswerContract] = useState('');
  const [boundToolName, setBoundToolName] = useState('');
  const [comparisonEnabled, setComparisonEnabled] = useState(false);
  const [sortOrder, setSortOrder] = useState('100');
  const editingRoute = routes.find((item) => item.id === editingId) ?? null;

  function reset() {
    setEditingId(null);
    setIntentCode('');
    setLabel('');
    setDescription('');
    setKeywords('');
    setSearchHint('');
    setAnswerStrategy('EVIDENCE_GROUNDED_QA');
    setAnswerContract('');
    setBoundToolName('');
    setComparisonEnabled(false);
    setSortOrder('100');
  }

  function startEdit(route: IntentRoute) {
    setEditingId(route.id);
    setIntentCode(route.intentCode);
    setLabel(route.label);
    setDescription(route.description ?? '');
    setKeywords(route.keywords);
    setSearchHint(route.searchHint ?? '');
    setAnswerStrategy(route.answerStrategy);
    setAnswerContract(route.answerContract ?? '');
    setBoundToolName(route.boundToolName ?? '');
    setComparisonEnabled(route.comparisonEnabled);
    setSortOrder(String(route.sortOrder ?? 100));
  }

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input = {
      intentCode: intentCode.trim(),
      label: label.trim(),
      description: description.trim(),
      keywords: keywords.trim(),
      searchHint: searchHint.trim(),
      answerStrategy: answerStrategy.trim(),
      answerContract: answerContract.trim(),
      boundToolName: boundToolName.trim(),
      comparisonEnabled,
      sortOrder: Number.parseInt(sortOrder, 10) || 100
    };
    if (!input.intentCode || !input.label || !input.keywords || !input.answerStrategy) {
      return;
    }
    if (editingRoute) {
      onUpdate(editingRoute, input);
    } else {
      onCreate(input);
    }
    reset();
  }

  return (
    <div className="admin-panel admin-intent-route-panel">
      <div className="admin-panel-head">
        <div>
          <h3>意图路由</h3>
          <p>把问题意图、检索提示和回答策略做成可运营规则。</p>
        </div>
        <Layers size={18} />
      </div>
      <form className="intent-route-form" onSubmit={submit}>
        <label>
          <span>标识</span>
          <input value={intentCode} maxLength={64} placeholder="METHOD_ANALYSIS" onChange={(event) => setIntentCode(event.target.value)} />
        </label>
        <label>
          <span>名称</span>
          <input value={label} maxLength={120} placeholder="方法分析" onChange={(event) => setLabel(event.target.value)} />
        </label>
        <label className="wide">
          <span>关键词</span>
          <input value={keywords} maxLength={2000} placeholder="方法,架构,method,architecture" onChange={(event) => setKeywords(event.target.value)} />
        </label>
        <label>
          <span>回答策略</span>
          <input value={answerStrategy} maxLength={64} placeholder="EVIDENCE_GROUNDED_QA" onChange={(event) => setAnswerStrategy(event.target.value)} />
        </label>
        <label>
          <span>检索提示</span>
          <input value={searchHint} maxLength={500} placeholder="method architecture module" onChange={(event) => setSearchHint(event.target.value)} />
        </label>
        <label>
          <span>绑定工具</span>
          <select value={boundToolName} onChange={(event) => setBoundToolName(event.target.value)}>
            <option value="">不绑定</option>
            {tools.map((tool) => (
              <option value={tool.name} key={`intent-tool-${tool.name}`}>
                {tool.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>排序</span>
          <input value={sortOrder} inputMode="numeric" onChange={(event) => setSortOrder(event.target.value)} />
        </label>
        <label className="wide">
          <span>输出契约</span>
          <input value={answerContract} maxLength={2000} placeholder="输出结构：..." onChange={(event) => setAnswerContract(event.target.value)} />
        </label>
        <label className="wide">
          <span>说明</span>
          <input value={description} maxLength={500} placeholder="用于识别特定阅读任务" onChange={(event) => setDescription(event.target.value)} />
        </label>
        <label className="intent-route-check">
          <input type="checkbox" checked={comparisonEnabled} onChange={(event) => setComparisonEnabled(event.target.checked)} />
          <span>比较类</span>
        </label>
        <div className="intent-route-actions">
          {editingRoute && (
            <button className="secondary-button" type="button" onClick={reset}>
              取消
            </button>
          )}
          <button className="primary-button" type="submit" disabled={!intentCode.trim() || !label.trim() || !keywords.trim() || !answerStrategy.trim()}>
            <Plus size={17} />
            {editingRoute ? '更新' : '添加'}
          </button>
        </div>
      </form>
      <div className="intent-route-list">
        {routes.length === 0 ? (
          <EmptyState compact title="暂无意图路由" detail="添加规则后，QueryPlanningNode 会优先读取这些配置。" />
        ) : (
          routes.map((route) => (
            <div className={`intent-route-row ${route.enabled ? '' : 'is-disabled'}`} key={route.id}>
              <strong>{route.intentCode}</strong>
              <span>
                <b>{route.label}</b>
                <small>{route.keywords}</small>
              </span>
              <em>{route.answerStrategy}</em>
              <em>{route.boundToolName ? `工具 ${route.boundToolName}` : '无工具'} · {route.comparisonEnabled ? '比较' : '普通'} · {route.enabled ? '启用' : '停用'} · {route.sortOrder}</em>
              <button className="secondary-button compact-action" type="button" onClick={() => startEdit(route)}>
                编辑
              </button>
              <button
                className="secondary-button compact-action"
                type="button"
                onClick={() => onUpdate(route, { enabled: !route.enabled })}
              >
                {route.enabled ? '停用' : '启用'}
              </button>
              <button className="icon-button small danger" type="button" title="删除意图路由" onClick={() => onDelete(route)}>
                <Trash2 size={15} />
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function AnswerPromptTemplatePanel({
  templates,
  onCreate,
  onUpdate,
  onDelete
}: {
  templates: AnswerPromptTemplate[];
  onCreate: (input: AnswerPromptTemplateInput) => void;
  onUpdate: (template: AnswerPromptTemplate, patch: Partial<AnswerPromptTemplateInput & { enabled: boolean }>) => void;
  onDelete: (template: AnswerPromptTemplate) => void;
}) {
  const [editingId, setEditingId] = useState<number | null>(null);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [systemPrompt, setSystemPrompt] = useState('');
  const [userPromptTemplate, setUserPromptTemplate] = useState('');
  const [defaultTemplate, setDefaultTemplate] = useState(false);
  const [sortOrder, setSortOrder] = useState('100');
  const editingTemplate = templates.find((item) => item.id === editingId) ?? null;

  function reset() {
    setEditingId(null);
    setCode('');
    setName('');
    setDescription('');
    setSystemPrompt('');
    setUserPromptTemplate('');
    setDefaultTemplate(false);
    setSortOrder('100');
  }

  function startEdit(template: AnswerPromptTemplate) {
    setEditingId(template.id);
    setCode(template.code);
    setName(template.name);
    setDescription(template.description ?? '');
    setSystemPrompt(template.systemPrompt);
    setUserPromptTemplate(template.userPromptTemplate);
    setDefaultTemplate(template.defaultTemplate);
    setSortOrder(String(template.sortOrder ?? 100));
  }

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input = {
      code: code.trim(),
      name: name.trim(),
      description: description.trim(),
      systemPrompt: systemPrompt.trim(),
      userPromptTemplate: userPromptTemplate.trim(),
      defaultTemplate,
      sortOrder: Number.parseInt(sortOrder, 10) || 100
    };
    if (!input.code || !input.name || !input.systemPrompt || !input.userPromptTemplate) {
      return;
    }
    if (editingTemplate) {
      onUpdate(editingTemplate, input);
    } else {
      onCreate(input);
    }
    reset();
  }

  return (
    <div className="admin-panel admin-answer-template-panel">
      <div className="admin-panel-head">
        <div>
          <h3>回答 Prompt 模板</h3>
          <p>运营 AnswerAgent 的 System Prompt 和用户消息模板。</p>
        </div>
        <Bot size={18} />
      </div>
      <form className="answer-template-form" onSubmit={submit}>
        <label>
          <span>标识</span>
          <input value={code} maxLength={64} placeholder="ACADEMIC_RAG" onChange={(event) => setCode(event.target.value)} />
        </label>
        <label>
          <span>名称</span>
          <input value={name} maxLength={120} placeholder="科研 RAG 模板" onChange={(event) => setName(event.target.value)} />
        </label>
        <label>
          <span>排序</span>
          <input value={sortOrder} inputMode="numeric" onChange={(event) => setSortOrder(event.target.value)} />
        </label>
        <label className="answer-template-check">
          <input type="checkbox" checked={defaultTemplate} onChange={(event) => setDefaultTemplate(event.target.checked)} />
          <span>默认</span>
        </label>
        <label className="wide">
          <span>说明</span>
          <input value={description} maxLength={500} placeholder="用于论文精读和全库综合" onChange={(event) => setDescription(event.target.value)} />
        </label>
        <label className="wide">
          <span>System Prompt</span>
          <textarea value={systemPrompt} maxLength={8000} rows={6} placeholder="你是 Research Paper Agent..." onChange={(event) => setSystemPrompt(event.target.value)} />
        </label>
        <label className="wide">
          <span>User Prompt 模板</span>
          <textarea
            value={userPromptTemplate}
            maxLength={12000}
            rows={7}
            placeholder="回答范围：{{scope}}\n{{paper_metadata}}\n回答策略：{{answer_strategy}}\n输出契约：{{answer_contract}}\n历史对话：\n{{conversation_history}}\n业务工具结果：\n{{tool_context}}\n意图引导：\n{{guidance_context}}\n用户问题：{{question}}\n检索片段：{{sources}}"
            onChange={(event) => setUserPromptTemplate(event.target.value)}
          />
        </label>
        <div className="answer-template-actions">
          {editingTemplate && (
            <button className="secondary-button" type="button" onClick={reset}>
              取消
            </button>
          )}
          <button className="primary-button" type="submit" disabled={!code.trim() || !name.trim() || !systemPrompt.trim() || !userPromptTemplate.trim()}>
            <Plus size={17} />
            {editingTemplate ? '更新' : '添加'}
          </button>
        </div>
      </form>
      <div className="answer-template-list">
        {templates.length === 0 ? (
          <EmptyState compact title="暂无回答模板" detail="添加后 AnswerAgent 会使用启用的默认模板渲染提示词。" />
        ) : (
          templates.map((template) => (
            <div className={`answer-template-row ${template.enabled ? '' : 'is-disabled'}`} key={template.id}>
              <strong>{template.code}</strong>
              <span>
                <b>{template.name}</b>
                <small>{template.description || compactText(template.systemPrompt, 88)}</small>
              </span>
              <em>{template.defaultTemplate ? '默认' : '候选'} · {template.enabled ? '启用' : '停用'} · {template.sortOrder}</em>
              <button className="secondary-button compact-action" type="button" onClick={() => startEdit(template)}>
                编辑
              </button>
              <button
                className="secondary-button compact-action"
                type="button"
                onClick={() => onUpdate(template, { enabled: !template.enabled })}
              >
                {template.enabled ? '停用' : '启用'}
              </button>
              <button
                className="secondary-button compact-action"
                type="button"
                onClick={() => onUpdate(template, { defaultTemplate: true, enabled: true })}
              >
                设默认
              </button>
              <button className="icon-button small danger" type="button" title="删除回答模板" onClick={() => onDelete(template)}>
                <Trash2 size={15} />
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function QueryTermMappingPanel({
  mappings,
  onCreate,
  onUpdate,
  onDelete
}: {
  mappings: QueryTermMapping[];
  onCreate: (input: { term: string; expansions: string }) => void;
  onUpdate: (mapping: QueryTermMapping, patch: Partial<Pick<QueryTermMapping, 'term' | 'expansions' | 'enabled'>>) => void;
  onDelete: (mapping: QueryTermMapping) => void;
}) {
  const [term, setTerm] = useState('');
  const [expansions, setExpansions] = useState('');

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextTerm = term.trim();
    const nextExpansions = expansions.trim();
    if (!nextTerm || !nextExpansions) {
      return;
    }
    onCreate({ term: nextTerm, expansions: nextExpansions });
    setTerm('');
    setExpansions('');
  }

  return (
    <div className="admin-panel admin-query-mapping-panel">
      <div className="admin-panel-head">
        <div>
          <h3>查询术语映射</h3>
          <p>把领域缩写、别名和同义词注入检索式，提升全库问答召回。</p>
        </div>
        <Search size={18} />
      </div>
      <form className="query-mapping-form" onSubmit={submit}>
        <label>
          <span>术语</span>
          <input value={term} maxLength={120} placeholder="例如 GNN" onChange={(event) => setTerm(event.target.value)} />
        </label>
        <label>
          <span>扩展词</span>
          <input value={expansions} maxLength={1000} placeholder="Graph Neural Network，图神经网络" onChange={(event) => setExpansions(event.target.value)} />
        </label>
        <button className="primary-button" type="submit" disabled={!term.trim() || !expansions.trim()}>
          <Plus size={17} />
          添加
        </button>
      </form>
      <div className="query-mapping-list">
        {mappings.length === 0 ? (
          <EmptyState compact title="暂无术语映射" detail="添加领域术语后，命中的问答会自动扩展检索式。" />
        ) : (
          mappings.map((mapping) => (
            <div className={`query-mapping-row ${mapping.enabled ? '' : 'is-disabled'}`} key={mapping.id}>
              <span>
                <strong>{mapping.term}</strong>
                <small>{mapping.expansions}</small>
              </span>
              <em>{mapping.enabled ? '启用' : '停用'} · {formatTime(mapping.updatedAt)}</em>
              <button
                className="secondary-button compact-action"
                type="button"
                onClick={() => onUpdate(mapping, { enabled: !mapping.enabled })}
              >
                {mapping.enabled ? '停用' : '启用'}
              </button>
              <button className="icon-button small danger" type="button" title="删除术语映射" onClick={() => onDelete(mapping)}>
                <Trash2 size={15} />
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function SamplePromptPanel({
  prompts,
  onCreate,
  onUpdate,
  onDelete
}: {
  prompts: SamplePrompt[];
  onCreate: (input: SamplePromptInput) => void;
  onUpdate: (prompt: SamplePrompt, patch: Partial<SamplePromptInput & { enabled: boolean }>) => void;
  onDelete: (prompt: SamplePrompt) => void;
}) {
  const [editingId, setEditingId] = useState<number | null>(null);
  const [scope, setScope] = useState<'PAPER' | 'LIBRARY'>('PAPER');
  const [title, setTitle] = useState('');
  const [prompt, setPrompt] = useState('');
  const [description, setDescription] = useState('');
  const [sortOrder, setSortOrder] = useState('100');
  const editingPrompt = prompts.find((item) => item.id === editingId) ?? null;

  function reset() {
    setEditingId(null);
    setScope('PAPER');
    setTitle('');
    setPrompt('');
    setDescription('');
    setSortOrder('100');
  }

  function startEdit(item: SamplePrompt) {
    setEditingId(item.id);
    setScope(normalizePromptScope(item.scope));
    setTitle(item.title);
    setPrompt(item.prompt);
    setDescription(item.description ?? '');
    setSortOrder(String(item.sortOrder ?? 100));
  }

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextTitle = title.trim();
    const nextPrompt = prompt.trim();
    if (!nextTitle || !nextPrompt) {
      return;
    }
    const input = {
      scope,
      title: nextTitle,
      prompt: nextPrompt,
      description: description.trim(),
      sortOrder: Number.parseInt(sortOrder, 10) || 100
    };
    if (editingPrompt) {
      onUpdate(editingPrompt, input);
    } else {
      onCreate(input);
    }
    reset();
  }

  return (
    <div className="admin-panel admin-sample-prompt-panel">
      <div className="admin-panel-head">
        <div>
          <h3>示例问题</h3>
          <p>运营阅读页和全库问答页的推荐问法。</p>
        </div>
        <Brain size={18} />
      </div>
      <form className="sample-prompt-form" onSubmit={submit}>
        <label>
          <span>范围</span>
          <select value={scope} onChange={(event) => setScope(normalizePromptScope(event.target.value))}>
            <option value="PAPER">单篇</option>
            <option value="LIBRARY">全库</option>
          </select>
        </label>
        <label>
          <span>标题</span>
          <input value={title} maxLength={120} placeholder="核心方法" onChange={(event) => setTitle(event.target.value)} />
        </label>
        <label className="wide">
          <span>问题</span>
          <input value={prompt} maxLength={2000} placeholder="请总结这篇论文的核心方法和贡献。" onChange={(event) => setPrompt(event.target.value)} />
        </label>
        <label>
          <span>排序</span>
          <input value={sortOrder} inputMode="numeric" onChange={(event) => setSortOrder(event.target.value)} />
        </label>
        <label className="wide">
          <span>说明</span>
          <input value={description} maxLength={255} placeholder="用于快速建立论文理解" onChange={(event) => setDescription(event.target.value)} />
        </label>
        <div className="sample-prompt-actions">
          {editingPrompt && (
            <button className="secondary-button" type="button" onClick={reset}>
              取消
            </button>
          )}
          <button className="primary-button" type="submit" disabled={!title.trim() || !prompt.trim()}>
            <Plus size={17} />
            {editingPrompt ? '更新' : '添加'}
          </button>
        </div>
      </form>
      <div className="sample-prompt-list">
        {prompts.length === 0 ? (
          <EmptyState compact title="暂无示例问题" detail="添加后会出现在对应问答入口。" />
        ) : (
          prompts.map((item) => (
            <div className={`sample-prompt-row ${item.enabled ? '' : 'is-disabled'}`} key={item.id}>
              <strong>{scopeLabel(item.scope)}</strong>
              <span>
                <b>{item.title}</b>
                <small>{item.prompt}</small>
              </span>
              <em>{item.sortOrder} · {item.enabled ? '启用' : '停用'}</em>
              <button className="secondary-button compact-action" type="button" onClick={() => startEdit(item)}>
                编辑
              </button>
              <button
                className="secondary-button compact-action"
                type="button"
                onClick={() => onUpdate(item, { enabled: !item.enabled })}
              >
                {item.enabled ? '停用' : '启用'}
              </button>
              <button className="icon-button small danger" type="button" title="删除示例问题" onClick={() => onDelete(item)}>
                <Trash2 size={15} />
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function PdfPreview({ paper, targetPage, jumpSignal }: { paper: Paper; targetPage?: number; jumpSignal?: number }) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const frameRef = useRef<HTMLDivElement | null>(null);
  const [pdfDoc, setPdfDoc] = useState<any>(null);
  const [pageNumber, setPageNumber] = useState(1);
  const [pageCount, setPageCount] = useState(0);
  const [zoom, setZoom] = useState(1);
  const [loading, setLoading] = useState(false);
  const [rendering, setRendering] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    let loadedPdf: any;
    let pdfWorker: PDFWorker | undefined;
    async function loadPdf() {
      setError('');
      setPdfDoc(null);
      setPageNumber(1);
      if (!paper.fileId) {
        return;
      }
      try {
        setLoading(true);
        const cacheKey = `pdf:${paper.fileId}:${paper.fileSize || 0}`;
        const cached = await readCachedPdf(cacheKey);
        const data = cached ?? (await fetchPdfPreview(paper.fileId));
        if (!cached) {
          void writeCachedPdf(cacheKey, data);
        }
        const { task, worker } = createPdfLoadingTask(data);
        pdfWorker = worker;
        loadedPdf = await task.promise;
        if (cancelled) {
          loadedPdf.destroy?.();
          pdfWorker?.destroy();
          return;
        }
        setPdfDoc(loadedPdf);
        setPageCount(loadedPdf.numPages);
      } catch (err) {
        pdfWorker?.destroy();
        pdfWorker = undefined;
        if (!cancelled) {
          setError(extractErrorMessage(err));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void loadPdf();
    return () => {
      cancelled = true;
      loadedPdf?.destroy?.();
      pdfWorker?.destroy();
    };
  }, [paper.fileId, paper.fileSize]);

  useEffect(() => {
    if (!pdfDoc) {
      return undefined;
    }
    let cancelled = false;
    let renderTask: any;
    async function renderPage() {
      try {
        if (pageCount && pageNumber > pageCount) {
          setPageNumber(pageCount);
          return;
        }
        setRendering(true);
        const page = await pdfDoc.getPage(pageNumber);
        if (cancelled) {
          return;
        }
        const canvas = canvasRef.current;
        const frame = frameRef.current;
        if (!canvas || !frame) {
          return;
        }
        const baseViewport = page.getViewport({ scale: 1 });
        const availableWidth = Math.max(frame.clientWidth - 32, 320);
        const fitScale = Math.min(availableWidth / baseViewport.width, 1.3);
        const viewport = page.getViewport({ scale: Math.max(0.55, fitScale * zoom) });
        const pixelRatio = window.devicePixelRatio || 1;
        const context = canvas.getContext('2d');
        if (!context) {
          return;
        }
        canvas.width = Math.floor(viewport.width * pixelRatio);
        canvas.height = Math.floor(viewport.height * pixelRatio);
        canvas.style.width = `${Math.floor(viewport.width)}px`;
        canvas.style.height = `${Math.floor(viewport.height)}px`;
        context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
        context.clearRect(0, 0, viewport.width, viewport.height);
        renderTask = page.render({ canvasContext: context, viewport });
        await renderTask.promise;
      } catch (err) {
        if (!cancelled && !isPdfCancellation(err)) {
          setError('PDF 页面渲染失败，请刷新后重试。');
        }
      } finally {
        if (!cancelled) {
          setRendering(false);
        }
      }
    }
    void renderPage();
    return () => {
      cancelled = true;
      renderTask?.cancel?.();
    };
  }, [pdfDoc, pageNumber, pageCount, zoom]);

  useEffect(() => {
    if (!targetPage || targetPage < 1) {
      return;
    }
    setPageNumber(pageCount ? Math.min(targetPage, pageCount) : targetPage);
  }, [targetPage, jumpSignal, pageCount]);

  if (!paper.fileId) {
    return <div className="pdf-placeholder">该文献未关联 PDF 文件。</div>;
  }

  return (
    <div className="pdf-viewer">
      <div className="pdf-toolbar">
        <button className="icon-button" type="button" title="上一页" disabled={pageNumber <= 1} onClick={() => setPageNumber((value) => Math.max(1, value - 1))}>
          <ChevronLeft size={17} />
        </button>
        <span>{pageCount ? `${pageNumber} / ${pageCount}` : '加载中'}</span>
        <button className="icon-button" type="button" title="下一页" disabled={!pageCount || pageNumber >= pageCount} onClick={() => setPageNumber((value) => Math.min(pageCount, value + 1))}>
          <ChevronRight size={17} />
        </button>
        <button className="icon-button" type="button" title="缩小" disabled={zoom <= 0.75} onClick={() => setZoom((value) => Math.max(0.75, Number((value - 0.15).toFixed(2))))}>
          <ZoomOut size={17} />
        </button>
        <button className="icon-button" type="button" title="放大" disabled={zoom >= 1.65} onClick={() => setZoom((value) => Math.min(1.65, Number((value + 0.15).toFixed(2))))}>
          <ZoomIn size={17} />
        </button>
      </div>
      <div className="pdf-canvas-frame" ref={frameRef}>
        {(loading || rendering) && (
          <div className="pdf-rendering">
            <Loader2 className="spin" size={16} />
            {loading ? '正在获取 PDF...' : '正在渲染页面...'}
          </div>
        )}
        {error ? <div className="pdf-placeholder">{error}</div> : <canvas ref={canvasRef} aria-label="PDF 页面预览" />}
      </div>
    </div>
  );
}

function PaperCard({
  paper,
  active,
  onClick,
  onToggleRead,
  onDelete
}: {
  paper: Paper;
  active: boolean;
  onClick: () => void;
  onToggleRead: () => void;
  onDelete: () => void;
}) {
  return (
    <article className={`paper-card ${active ? 'active' : ''}`}>
      <button type="button" className="paper-main" onClick={onClick}>
        <FileText size={22} />
        <div>
          <strong>{paper.title}</strong>
          <span>{[paper.authors, paper.year].filter(Boolean).join(' · ') || '未填写作者信息'}</span>
          <small>{paper.keywords || paper.fileName || '未填写关键词'}</small>
        </div>
      </button>
      <div className="paper-card-actions">
        <em className={paper.status === 'INTENSIVE_READ' ? 'is-read' : 'is-pending'}>{statusLabel(paper.status)}</em>
        <button className="icon-button small" type="button" title="切换阅读状态" onClick={onToggleRead}>
          <CheckCircle2 size={15} />
        </button>
        <button className="icon-button small danger" type="button" title="删除文献" onClick={onDelete}>
          <Trash2 size={15} />
        </button>
      </div>
    </article>
  );
}

function ChatBubble({
  chat,
  onSourceClick,
  onFeedback
}: {
  chat: ChatRecord;
  onSourceClick?: (source: SourceResponse) => void;
  onFeedback?: (chat: ChatRecord, score: 1 | -1) => void;
}) {
  const transient = chat.id < 0;
  return (
    <>
      <div className="chat user">
        <span>你</span>
        <p className="chat-body plain-text">{chat.question}</p>
      </div>
      <div className={`chat assistant ${transient ? 'streaming' : ''}`}>
        <span>Agent · {transient ? '流式运行中' : (chat.modelName || 'fallback')}</span>
        <MarkdownContent content={chat.answer} />
        <SourceCards sources={chat.sources} onSourceClick={onSourceClick} />
        {!transient && <FeedbackBar chat={chat} onFeedback={onFeedback} />}
      </div>
    </>
  );
}

function FeedbackBar({
  chat,
  onFeedback
}: {
  chat: ChatRecord;
  onFeedback?: (chat: ChatRecord, score: 1 | -1) => void;
}) {
  return (
    <div className="feedback-bar">
      <button
        className={chat.feedbackScore === 1 ? 'is-positive' : ''}
        type="button"
        title="标记有用"
        onClick={() => onFeedback?.(chat, 1)}
      >
        <ThumbsUp size={14} />
        <span>有用</span>
      </button>
      <button
        className={chat.feedbackScore === -1 ? 'is-negative' : ''}
        type="button"
        title="标记无用"
        onClick={() => onFeedback?.(chat, -1)}
      >
        <ThumbsDown size={14} />
        <span>无用</span>
      </button>
      {chat.feedbackAt && <em>已反馈 · {formatTime(chat.feedbackAt)}</em>}
    </div>
  );
}

function SourceCards({ sources, onSourceClick }: { sources: SourceResponse[]; onSourceClick?: (source: SourceResponse) => void }) {
  if (!sources.length) {
    return null;
  }
  return (
    <div className="source-cards">
      {sources.map((source, index) => {
        const page = Math.max(1, source.page || 1);
        return (
          <button
            className="source-card"
            key={`${source.paperId}-${page}-${index}`}
            type="button"
            onClick={() => onSourceClick?.({ ...source, page })}
          >
            <span>
              <FileText size={14} />
              《{source.title}》
            </span>
            <strong>第 {page} 页</strong>
            <p>{source.content}</p>
          </button>
        );
      })}
    </div>
  );
}

function MarkdownContent({ content }: { content: string }) {
  return (
    <div className="chat-body markdown-content">
      <ReactMarkdown remarkPlugins={markdownPlugins}>{content || ''}</ReactMarkdown>
    </div>
  );
}

function NavButton({ icon: Icon, label, active, onClick }: { icon: LucideIcon; label: string; active: boolean; onClick: () => void }) {
  return (
    <button className={`nav-button ${active ? 'active' : ''}`} type="button" onClick={onClick}>
      <Icon size={18} />
      <span>{label}</span>
    </button>
  );
}

function Metric({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: number }) {
  return (
    <div className="metric">
      <Icon size={21} />
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function AdminStat({ icon: Icon, label, value, detail }: { icon: LucideIcon; label: string; value: number | string; detail: string }) {
  return (
    <div className="admin-stat">
      <Icon size={20} />
      <span>{label}</span>
      <strong>{value}</strong>
      <em>{detail}</em>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  type = 'text',
  required = false
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  required?: boolean;
}) {
  return (
    <label className="input-field">
      <span>{label}</span>
      <input required={required} type={type} value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}

function EmptyState({
  title,
  detail,
  actionLabel,
  onAction,
  compact = false
}: {
  title: string;
  detail: string;
  actionLabel?: string;
  onAction?: () => void;
  compact?: boolean;
}) {
  return (
    <div className={`empty-state ${compact ? 'compact' : ''}`}>
      <FileText size={compact ? 22 : 30} />
      <strong>{title}</strong>
      <p>{detail}</p>
      {actionLabel && onAction && (
        <button className="primary-button" type="button" onClick={onAction}>
          <Plus size={17} />
          {actionLabel}
        </button>
      )}
    </div>
  );
}

async function extractPdfMetadata(file: File) {
  const arrayBuffer = await file.arrayBuffer();
  const { task, worker } = createPdfLoadingTask(arrayBuffer);
  const pdf = await task.promise;
  try {
    const metadata = await pdf.getMetadata().catch(() => ({}));
    const info = (metadata as { info?: Record<string, string> }).info || {};
    const lines = await extractFirstPageLines(pdf);
    const text = lines.map((line) => line.text).join('\n');
    const title = cleanTitle(info.Title) || inferTitleFromLines(lines) || titleFromFileName(file.name);
    const authors = cleanAuthors(info.Author) || inferAuthorsFromLines(lines, title);
    const keywords = inferKeywords(text);
    const year = inferYear(`${info.CreationDate || ''}\n${info.ModDate || ''}\n${text}`);
    return {
      title,
      authors,
      keywords,
      year,
      extractedFromPdf: Boolean(cleanTitle(info.Title) || authors || keywords || year)
    };
  } finally {
    (pdf as any).destroy?.();
    worker.destroy();
  }
}

async function extractFirstPageLines(pdf: any) {
  if (!pdf.numPages) {
    return [] as Array<{ text: string; size: number; y: number }>;
  }
  const page = await pdf.getPage(1);
  const textContent = await page.getTextContent();
  const rows: Array<{ y: number; size: number; items: Array<{ text: string; x: number }> }> = [];
  textContent.items.forEach((item: any) => {
    const text = cleanText(item.str);
    if (!text) {
      return;
    }
    const transform = item.transform || [];
    const y = Number(transform[5]) || 0;
    const x = Number(transform[4]) || 0;
    const size = Math.max(Math.abs(Number(transform[3]) || 0), Number(item.height) || 0);
    const row = rows.find((candidate) => Math.abs(candidate.y - y) <= Math.max(2.5, size * 0.45));
    if (row) {
      row.items.push({ text, x });
      row.size = Math.max(row.size, size);
    } else {
      rows.push({ y, size, items: [{ text, x }] });
    }
  });
  return rows
    .sort((left, right) => right.y - left.y)
    .map((row) => ({
      text: cleanText(row.items.sort((left, right) => left.x - right.x).map((item) => item.text).join(' ')),
      size: row.size,
      y: row.y
    }))
    .filter((line) => line.text);
}

function inferTitleFromLines(lines: Array<{ text: string; size: number }>) {
  const candidates = lines.slice(0, 24).filter((line) => isUsefulTitleLine(line.text));
  if (!candidates.length) {
    return '';
  }
  const maxSize = Math.max(...candidates.map((line) => line.size || 0));
  const largeLines = candidates.filter((line) => (line.size || 0) >= maxSize * 0.8).slice(0, 3);
  return cleanTitle(largeLines.map((line) => line.text).join(' ')) || cleanTitle(candidates[0].text);
}

function inferAuthorsFromLines(lines: Array<{ text: string }>, title: string) {
  const titleText = normalizeComparable(title);
  const startIndex = Math.max(0, lines.findIndex((line) => normalizeComparable(line.text).includes(titleText.slice(0, 32))) + 1);
  const parts: string[] = [];
  for (const line of lines.slice(startIndex, startIndex + 8)) {
    const text = cleanAuthors(line.text);
    if (!text || /^(abstract|keywords?|introduction)\b/i.test(text)) {
      break;
    }
    if (looksLikeAuthorLine(text)) {
      parts.push(text);
    } else if (parts.length) {
      break;
    }
  }
  return cleanAuthors(parts.join(', '));
}

function inferKeywords(text: string) {
  const line = text.split('\n').find((item) => /^key\s*words?\s*[:：]/i.test(item.trim()));
  return line ? cleanText(line.replace(/^key\s*words?\s*[:：]\s*/i, '')).slice(0, 180) : '';
}

function inferYear(text: string) {
  const years = text.match(/\b(?:19|20)\d{2}\b/g) || [];
  const currentYear = new Date().getFullYear() + 1;
  return years.map(Number).find((year) => year >= 1990 && year <= currentYear) || '';
}

function isUsefulTitleLine(text: string) {
  const value = cleanText(text);
  return value.length >= 8 && value.length <= 220 && !/(abstract|keywords?|doi|arxiv|copyright|journal|conference|www\.|https?:|@)/i.test(value);
}

function looksLikeAuthorLine(text: string) {
  const value = cleanText(text);
  if (value.length < 4 || value.length > 180 || /@|https?:|www\.|abstract|keywords?/i.test(value)) {
    return false;
  }
  const separators = (value.match(/,|;|\band\b|，|、/gi) || []).length;
  const capitalizedWords = (value.match(/\b[A-Z][a-zA-Z.'-]{1,}\b/g) || []).length;
  return separators >= 1 || capitalizedWords >= 2;
}

function isPdfFile(value: File) {
  return value.type === 'application/pdf' || /\.pdf$/i.test(value.name);
}

function cleanTitle(value = '') {
  const title = cleanText(value).replace(/\.pdf$/i, '').replace(/\s*[-_]\s*Microsoft Word$/i, '');
  return /^(untitled|unknown|microsoft word|document)$/i.test(title) ? '' : title;
}

function cleanAuthors(value = '') {
  return cleanText(value)
    .replace(/\b(orcid|corresponding author)\b/gi, '')
    .replace(/[†*‡§]/g, '')
    .replace(/\s*\d+\s*/g, ' ')
    .replace(/\s*,\s*/g, ', ')
    .replace(/\s{2,}/g, ' ')
    .replace(/^,|,$/g, '')
    .trim();
}

function cleanText(value = '') {
  return String(value).replace(/\s+/g, ' ').trim();
}

function normalizeComparable(value = '') {
  return cleanText(value).toLowerCase().replace(/[^a-z0-9\u4e00-\u9fa5]/g, '');
}

function titleFromFileName(fileName = '') {
  return cleanTitle(fileName.replace(/\.pdf$/i, '').replace(/[_-]+/g, ' ')) || '未命名文献';
}

function statusLabel(status: string) {
  return status === 'INTENSIVE_READ' ? '已精读' : '待阅读';
}

function processLabel(status: string) {
  const labels: Record<string, string> = {
    PENDING: '待解析',
    PARSING: '解析中',
    INDEXING: '索引中',
    INDEXED: '已解析',
    FAILED: '解析失败'
  };
  return labels[status] || status;
}

function traceStatusLabel(status: string) {
  const labels: Record<string, string> = {
    SUCCESS: '成功',
    FAILED: '失败'
  };
  return labels[status] || status;
}

function modelHealthStatusLabel(status: string) {
  const labels: Record<string, string> = {
    SUCCESS: '健康',
    FAILED: '失败',
    FALLBACK: '兜底',
    SKIPPED: '跳过'
  };
  return labels[status] || status;
}

function modelHealthStatusClass(status: string) {
  if (status === 'SUCCESS') {
    return 'is-success';
  }
  if (status === 'FAILED') {
    return 'is-failed';
  }
  if (status === 'SKIPPED') {
    return 'is-skipped';
  }
  return 'is-fallback';
}

function modelHealthRowClass(model: AdminModelHealth) {
  if (model.circuitState === 'OPEN' || model.lastStatus === 'SKIPPED') {
    return 'is-skipped';
  }
  if (model.lastStatus === 'FAILED') {
    return 'is-failed';
  }
  if (model.lastStatus === 'FALLBACK') {
    return 'is-fallback';
  }
  return '';
}

function modelCircuitStateLabel(state: string) {
  const labels: Record<string, string> = {
    CLOSED: '关闭',
    OPEN: '打开',
    HALF_OPEN: '半开'
  };
  return labels[state] || state || '关闭';
}

function parseJobStatusLabel(status: string) {
  const labels: Record<string, string> = {
    RUNNING: '进行中',
    SUCCESS: '成功',
    FAILED: '失败'
  };
  return labels[status] || status;
}

function nodeSpanLabel(name: string) {
  const labels: Record<string, string> = {
    'scope-resolution': '范围',
    'conversation-memory': '记忆',
    'query-rewrite-and-split': '改写',
    'query-planning': '规划',
    'tool-execution': '工具',
    retrieval: '检索',
    'intent-guidance': '引导',
    'answer-planning': '策略',
    'answer-generation': '生成',
    'citation-verification': '校验',
    'answer-quality-evaluation': '评估',
    'answer-formatting': '格式'
  };
  return labels[name] || name;
}

function ingestionNodeLabel(name: string) {
  const labels: Record<string, string> = {
    prepare: '准备',
    'fetch-pdf': '读取PDF',
    'parse-pdf': '抽取文本',
    'persist-chunks': '写入片段',
    'index-embeddings': '向量索引',
    finalize: '完成'
  };
  return labels[name] || name;
}

function intentLabel(intent = 'GENERAL_QA') {
  const labels: Record<string, string> = {
    GENERAL_QA: '问答',
    SUMMARY: '总结',
    CONTRIBUTION: '贡献',
    EXPERIMENT: '实验',
    LIMITATION: '局限',
    COMPARISON: '比较',
    REVIEW_SYNTHESIS: '综述'
  };
  return labels[intent] || intent;
}

function strategyLabel(strategy = 'EVIDENCE_GROUNDED_QA') {
  const labels: Record<string, string> = {
    EVIDENCE_GROUNDED_QA: '证据问答',
    CROSS_PAPER_COMPARISON: '对比矩阵',
    REVIEW_SYNTHESIS: '综述综合',
    CONTRIBUTION_ANALYSIS: '贡献分析',
    EXPERIMENT_READING: '实验解读',
    LIMITATION_REVIEW: '局限审查',
    STRUCTURED_SUMMARY: '结构摘要',
    EVIDENCE_GAP: '证据不足',
    GUIDED_CLARIFICATION: '引导澄清'
  };
  return labels[strategy] || strategy;
}

function qualityLabel(label = 'UNASSESSED') {
  const labels: Record<string, string> = {
    STRONG: '优秀',
    GOOD: '良好',
    NEEDS_REVIEW: '复核',
    LOW_EVIDENCE: '低证据',
    MATERIAL_LIMITED: '材料不足',
    EMPTY: '空回答',
    UNASSESSED: '未评估'
  };
  return labels[label] || label;
}

function qualityBadgeClass(label = 'UNASSESSED') {
  if (label === 'STRONG' || label === 'GOOD') {
    return 'is-good';
  }
  if (label === 'MATERIAL_LIMITED' || label === 'NEEDS_REVIEW') {
    return 'is-watch';
  }
  if (label === 'LOW_EVIDENCE' || label === 'EMPTY') {
    return 'is-risk';
  }
  return '';
}

function qualityMethodLabel(method = 'HEURISTIC') {
  const labels: Record<string, string> = {
    LLM_JUDGE: '模型评审',
    HEURISTIC: '启发式',
    HEURISTIC_FALLBACK: '启发式兜底'
  };
  return labels[method] || method;
}

function memorySummaryMethodLabel(method = 'NONE') {
  const labels: Record<string, string> = {
    LLM_SUMMARY: '模型摘要',
    HEURISTIC: '启发式',
    HEURISTIC_FALLBACK: '启发式兜底',
    NONE: '无摘要'
  };
  return labels[method] || method;
}

function scopeLabel(scope: string) {
  return scope === 'LIBRARY' ? '全库' : '单篇';
}

function compactText(value: string, maxLength = 120) {
  const normalized = value.replace(/\s+/g, ' ').trim();
  return normalized.length > maxLength ? `${normalized.slice(0, maxLength)}...` : normalized;
}

function toRagSettingsForm(settings: RagSettings | null): RagSettingsFormState {
  const source = settings ?? defaultRagSettingsInput;
  return {
    candidateLimit: String(source.candidateLimit),
    resultLimit: String(source.resultLimit),
    sourceExcerptChars: String(source.sourceExcerptChars),
    vectorWeight: String(source.vectorWeight),
    keywordWeight: String(source.keywordWeight),
    memoryHistoryTurns: String(source.memoryHistoryTurns),
    memoryMaxChars: String(source.memoryMaxChars),
    memorySummaryEnabled: source.memorySummaryEnabled ?? true,
    memorySummaryStartTurns: String(source.memorySummaryStartTurns ?? defaultRagSettingsInput.memorySummaryStartTurns),
    memorySummaryMaxChars: String(source.memorySummaryMaxChars ?? defaultRagSettingsInput.memorySummaryMaxChars),
    queryRewriteEnabled: source.queryRewriteEnabled,
    queryRewriteMaxSubQuestions: String(source.queryRewriteMaxSubQuestions),
    answerQualityJudgeEnabled: source.answerQualityJudgeEnabled ?? true,
    rerankModelEnabled: source.rerankModelEnabled ?? false,
    rerankModelMaxCandidates: String(source.rerankModelMaxCandidates ?? defaultRagSettingsInput.rerankModelMaxCandidates),
    chatRateLimitEnabled: source.chatRateLimitEnabled ?? true,
    chatRateLimitGlobalConcurrency: String(source.chatRateLimitGlobalConcurrency ?? defaultRagSettingsInput.chatRateLimitGlobalConcurrency),
    chatRateLimitUserConcurrency: String(source.chatRateLimitUserConcurrency ?? defaultRagSettingsInput.chatRateLimitUserConcurrency),
    chatRateLimitUserPerMinute: String(source.chatRateLimitUserPerMinute ?? defaultRagSettingsInput.chatRateLimitUserPerMinute)
  };
}

function boundedInt(value: string, min: number, max: number, fallback: number) {
  const parsed = Number.parseInt(value, 10);
  const candidate = Number.isFinite(parsed) ? parsed : fallback;
  return Math.max(min, Math.min(max, candidate));
}

function boundedFloat(value: string, min: number, max: number, fallback: number) {
  const parsed = Number.parseFloat(value);
  const candidate = Number.isFinite(parsed) ? parsed : fallback;
  return Math.max(min, Math.min(max, Number(candidate.toFixed(2))));
}

function normalizePromptScope(value: string): 'PAPER' | 'LIBRARY' {
  return value === 'PAPER' ? 'PAPER' : 'LIBRARY';
}

function promptTexts(prompts: SamplePrompt[], fallback: string[]) {
  const texts = prompts
    .filter((item) => item.enabled && item.prompt?.trim())
    .sort(compareSamplePrompts)
    .map((item) => item.prompt.trim());
  return texts.length > 0 ? texts : fallback;
}

function compareIntentRoutes(a: IntentRoute, b: IntentRoute) {
  if ((a.sortOrder ?? 100) !== (b.sortOrder ?? 100)) {
    return (a.sortOrder ?? 100) - (b.sortOrder ?? 100);
  }
  return a.id - b.id;
}

function compareAnswerPromptTemplates(a: AnswerPromptTemplate, b: AnswerPromptTemplate) {
  if (a.defaultTemplate !== b.defaultTemplate) {
    return a.defaultTemplate ? -1 : 1;
  }
  if ((a.sortOrder ?? 100) !== (b.sortOrder ?? 100)) {
    return (a.sortOrder ?? 100) - (b.sortOrder ?? 100);
  }
  return a.id - b.id;
}

function compareModelTargets(a: ModelTarget, b: ModelTarget) {
  if (modelTaskOrder(a.taskType) !== modelTaskOrder(b.taskType)) {
    return modelTaskOrder(a.taskType) - modelTaskOrder(b.taskType);
  }
  if ((a.priority ?? 100) !== (b.priority ?? 100)) {
    return (a.priority ?? 100) - (b.priority ?? 100);
  }
  return a.id - b.id;
}

const modelTaskOptions = [
  { value: 'GENERAL', label: '通用兜底' },
  { value: 'ANSWER_GENERATION', label: '回答生成' },
  { value: 'QUERY_REWRITE', label: '查询改写' },
  { value: 'QUALITY_EVALUATION', label: '质量评估' },
  { value: 'CONVERSATION_SUMMARY', label: '会话摘要' },
  { value: 'RETRIEVAL_RERANK', label: '检索重排' }
];

function modelTaskLabel(taskType = 'GENERAL') {
  return modelTaskOptions.find((option) => option.value === taskType)?.label ?? taskType;
}

function modelTaskOrder(taskType = 'GENERAL') {
  const index = modelTaskOptions.findIndex((option) => option.value === taskType);
  return index < 0 ? modelTaskOptions.length : index;
}

function compareSamplePrompts(a: SamplePrompt, b: SamplePrompt) {
  if ((a.sortOrder ?? 100) !== (b.sortOrder ?? 100)) {
    return (a.sortOrder ?? 100) - (b.sortOrder ?? 100);
  }
  return a.id - b.id;
}

function formatBytes(bytes = 0) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function formatLatency(value = 0) {
  if (!value) {
    return '-';
  }
  return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(1)} s`;
}

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN');
}

function formatSources(sources: Array<{ title: string; page: number }>) {
  return sources
    .slice(0, 5)
    .map((source) => `《${source.title}》P${source.page}`)
    .join(' · ');
}

function pendingChatRecord(
  id: number,
  paperId: number | null,
  sessionId: number | null,
  question: string,
  message: string
): ChatRecord {
  return {
    id,
    ...(paperId ? { paperId } : {}),
    ...(sessionId ? { sessionId } : {}),
    question,
    answer: streamChatContent(message),
    sources: [],
    modelName: 'streaming',
    latencyMs: 0,
    createdAt: new Date().toISOString()
  };
}

function streamEventMessage(eventName: string) {
  switch (eventName) {
    case 'started':
      return '已建立流式问答连接。';
    case 'running':
      return '多 Agent 正在检索、生成并校验引用...';
    case 'final':
      return '最终回答已生成，正在同步问答历史...';
    case 'done':
      return '流式问答已完成。';
    default:
      return 'Agent 正在运行...';
  }
}

function streamPhaseLabel(phase = 'queued') {
  const labels: Record<string, string> = {
    queued: '排队',
    started: '已连接',
    running: '运行中',
    final: '已生成',
    done: '完成',
    cancelled: '已取消',
    error: '失败'
  };
  return labels[phase] || phase;
}

function streamChatContent(message: string) {
  return `**流式运行中**\n\n${message}`;
}

function isAbortError(err: unknown) {
  const value = err as { name?: string; message?: string };
  return value?.name === 'AbortError' || String(value?.message || '').toLowerCase().includes('aborted');
}

function extractErrorMessage(err: unknown) {
  if (err instanceof Error) {
    return err.message;
  }
  return typeof err === 'string' ? err : '未知错误';
}

function cloneArrayBuffer(data: ArrayBuffer | ArrayBufferView): ArrayBuffer {
  if (data instanceof ArrayBuffer) {
    return data.slice(0);
  }
  const copy = new Uint8Array(data.byteLength);
  copy.set(new Uint8Array(data.buffer, data.byteOffset, data.byteLength));
  return copy.buffer;
}

function isPdfCancellation(err: unknown) {
  const value = err as { name?: string; message?: string };
  const message = String(value?.message || '');
  return value?.name === 'RenderingCancelledException' || value?.name === 'AbortException' || message.includes('cancelled');
}

let pdfCacheDbPromise: Promise<IDBDatabase | null> | null = null;

async function openPdfCacheDb() {
  if (!('indexedDB' in window)) {
    return null;
  }
  if (!pdfCacheDbPromise) {
    pdfCacheDbPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(PDF_CACHE_DB, PDF_CACHE_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(PDF_CACHE_STORE)) {
          db.createObjectStore(PDF_CACHE_STORE, { keyPath: 'id' });
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }
  return pdfCacheDbPromise;
}

async function readCachedPdf(cacheKey: string) {
  try {
    const db = await openPdfCacheDb();
    if (!db) {
      return null;
    }
    return await new Promise<ArrayBuffer | null>((resolve, reject) => {
      const transaction = db.transaction(PDF_CACHE_STORE, 'readonly');
      const store = transaction.objectStore(PDF_CACHE_STORE);
      const request = store.get(cacheKey);
      request.onsuccess = () => resolve(request.result?.data ? cloneArrayBuffer(request.result.data) : null);
      request.onerror = () => reject(request.error);
    });
  } catch {
    return null;
  }
}

async function writeCachedPdf(cacheKey: string, data: ArrayBuffer) {
  try {
    const db = await openPdfCacheDb();
    if (!db) {
      return;
    }
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction(PDF_CACHE_STORE, 'readwrite');
      const store = transaction.objectStore(PDF_CACHE_STORE);
      store.put({ id: cacheKey, data: cloneArrayBuffer(data), cachedAt: Date.now() });
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
    });
  } catch {
    // Cache is an optimization only.
  }
}
