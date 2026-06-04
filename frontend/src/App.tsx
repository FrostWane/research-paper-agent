import { useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { getDocument, PDFWorker } from 'pdfjs-dist';
import pdfWorkerSrc from 'pdfjs-dist/build/pdf.worker.mjs?url';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  BookOpen,
  Bot,
  Brain,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Database,
  FileText,
  History,
  Layers,
  Loader2,
  LogIn,
  LogOut,
  MessageSquareText,
  Plus,
  RefreshCw,
  Search,
  Send,
  ShieldCheck,
  Trash2,
  UploadCloud,
  ZoomIn,
  ZoomOut
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { askAgent, listChats } from './api/agent';
import { login, me, register } from './api/auth';
import { fetchPdfPreview, uploadPaperFile } from './api/files';
import { clearToken, getToken, setToken } from './api/request';
import { createPaper, deletePaper, listPapers, parsePaper, updatePaperStatus } from './api/papers';
import type { ChatRecord, Paper, PaperForm, User } from './types';

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

type ViewKey = 'library' | 'upload' | 'reader' | 'history';
type AuthMode = 'login' | 'register';

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [credentials, setCredentials] = useState({ username: '', email: '', account: '', password: '' });
  const [activeView, setActiveView] = useState<ViewKey>('library');
  const [papers, setPapers] = useState<Paper[]>([]);
  const [selectedPaperId, setSelectedPaperId] = useState<number | null>(null);
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [form, setForm] = useState<PaperForm>(emptyForm);
  const [file, setFile] = useState<File | null>(null);
  const [metadataStatus, setMetadataStatus] = useState('');
  const [chats, setChats] = useState<ChatRecord[]>([]);
  const [question, setQuestion] = useState('');
  const [loading, setLoading] = useState(true);
  const [busyText, setBusyText] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const noticeTimerRef = useRef<number | null>(null);

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
    };
  }, []);

  useEffect(() => {
    if (selectedPaper?.id) {
      void loadChatList(selectedPaper.id);
    } else {
      setChats([]);
    }
  }, [selectedPaper?.id]);

  async function bootstrap() {
    try {
      setLoading(true);
      setError('');
      if (getToken()) {
        const current = await me();
        setUser(current);
        await loadPaperList();
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
      showNotice('已进入 Research Paper Agent 工作台。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  function signOut() {
    clearToken();
    setUser(null);
    setPapers([]);
    setSelectedPaperId(null);
    setChats([]);
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
      setActiveView('reader');
      showNotice('文献已加入工作台。');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setBusyText('');
    }
  }

  async function handleAsk(prompt = question) {
    if (!selectedPaper || !prompt.trim()) {
      return;
    }
    try {
      setError('');
      setQuestion('');
      setBusyText('多 Agent 正在检索、生成并校验引用...');
      await askAgent(selectedPaper.id, prompt.trim(), true);
      await loadChatList(selectedPaper.id);
      showNotice('回答已保存到问答历史。');
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
          <NavButton icon={MessageSquareText} label="Agent 阅读" active={activeView === 'reader'} onClick={() => setActiveView('reader')} />
          <NavButton icon={History} label="问答历史" active={activeView === 'history'} onClick={() => setActiveView('history')} />
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

        <section className="metrics">
          <Metric icon={FileText} label="文献总数" value={stats.total} />
          <Metric icon={CheckCircle2} label="已精读" value={stats.intensive} />
          <Metric icon={Layers} label="已解析" value={stats.indexed} />
          <Metric icon={History} label="当前问答" value={stats.chats} />
        </section>

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
            selectedPaper={selectedPaper}
            chats={chats}
            question={question}
            onQuestionChange={setQuestion}
            onSelectPaper={(id) => setSelectedPaperId(id)}
            onAsk={(prompt) => void handleAsk(prompt)}
            onToggleRead={(paper) => void handleToggleRead(paper)}
            onParse={(paper) => void handleParse(paper)}
          />
        )}

        {activeView === 'history' && (
          <HistoryView
            papers={papers}
            selectedPaper={selectedPaper}
            chats={chats}
            onSelectPaper={(id) => setSelectedPaperId(id)}
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
  selectedPaper,
  chats,
  question,
  onQuestionChange,
  onSelectPaper,
  onAsk,
  onToggleRead,
  onParse
}: {
  papers: Paper[];
  selectedPaper: Paper | null;
  chats: ChatRecord[];
  question: string;
  onQuestionChange: (value: string) => void;
  onSelectPaper: (id: number) => void;
  onAsk: (prompt?: string) => void;
  onToggleRead: (paper: Paper) => void;
  onParse: (paper: Paper) => void;
}) {
  if (!selectedPaper) {
    return <EmptyState title="还没有可阅读的文献" detail="先上传一篇 PDF，再进入 Agent 阅读视图。" />;
  }

  return (
    <section className="reader-layout">
      <div className="reader-toolbar">
        <select value={selectedPaper.id} onChange={(event) => onSelectPaper(Number(event.target.value))}>
          {papers.map((paper) => (
            <option key={paper.id} value={paper.id}>{paper.title}</option>
          ))}
        </select>
        <button className={`secondary-button read-action ${selectedPaper.status === 'INTENSIVE_READ' ? 'is-read' : ''}`} type="button" onClick={() => onToggleRead(selectedPaper)}>
          <CheckCircle2 size={17} />
          {selectedPaper.status === 'INTENSIVE_READ' ? '已精读' : '标记精读'}
        </button>
        <button className="secondary-button" type="button" onClick={() => onParse(selectedPaper)}>
          <Layers size={17} />
          解析 PDF
        </button>
      </div>
      <div className="pdf-panel">
        <div className="paper-meta">
          <div>
            <h2>{selectedPaper.title}</h2>
            <p>{[selectedPaper.authors, selectedPaper.venue, selectedPaper.year].filter(Boolean).join(' · ') || '未填写题录信息'}</p>
          </div>
          <span className={`status-pill ${selectedPaper.processStatus === 'INDEXED' ? 'is-indexed' : ''}`}>{processLabel(selectedPaper.processStatus)}</span>
        </div>
        <PdfPreview paper={selectedPaper} />
      </div>
      <aside className="agent-panel">
        <div className="agent-title">
          <Brain size={22} />
          <div>
            <h2>多 Agent Lite</h2>
            <p>检索、回答、引用校验和格式化。</p>
          </div>
        </div>
        <div className="quick-prompts">
          {quickPrompts.map((prompt) => (
            <button key={prompt} type="button" onClick={() => onAsk(prompt)}>{prompt}</button>
          ))}
        </div>
        <div className="chat-list">
          {chats.length === 0 ? (
            <EmptyState compact title="暂无问答" detail="提出一个问题，Agent 会保存回答和来源片段。" />
          ) : (
            chats.map((chat) => <ChatBubble key={chat.id} chat={chat} />)
          )}
        </div>
        <form
          className="ask-box"
          onSubmit={(event) => {
            event.preventDefault();
            onAsk();
          }}
        >
          <input value={question} placeholder="围绕当前论文提问..." onChange={(event) => onQuestionChange(event.target.value)} />
          <button className="send icon-button" type="submit" title="发送">
            <Send size={18} />
          </button>
        </form>
      </aside>
    </section>
  );
}

function HistoryView({
  papers,
  selectedPaper,
  chats,
  onSelectPaper
}: {
  papers: Paper[];
  selectedPaper: Paper | null;
  chats: ChatRecord[];
  onSelectPaper: (id: number) => void;
}) {
  return (
    <section className="panel history-layout">
      <div className="section-head">
        <div>
          <h2>问答历史</h2>
          <p>按文献查看已保存的 Agent 问答记录。</p>
        </div>
        <select value={selectedPaper?.id ?? ''} onChange={(event) => onSelectPaper(Number(event.target.value))}>
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
              {chat.sources.length > 0 && <span>来源页码：{chat.sources.map((source) => source.page).join(', ')}</span>}
            </article>
          ))
        )}
      </div>
    </section>
  );
}

function PdfPreview({ paper }: { paper: Paper }) {
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
  }, [pdfDoc, pageNumber, zoom]);

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

function ChatBubble({ chat }: { chat: ChatRecord }) {
  return (
    <>
      <div className="chat user">
        <span>你</span>
        <p className="chat-body plain-text">{chat.question}</p>
      </div>
      <div className="chat assistant">
        <span>Agent · {chat.modelName || 'fallback'}</span>
        <MarkdownContent content={chat.answer} />
      </div>
    </>
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

function formatBytes(bytes = 0) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN');
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
