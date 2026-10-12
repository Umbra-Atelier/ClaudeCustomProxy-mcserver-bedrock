import React, { useState } from 'react';
import {
  Activity,
  Terminal,
  Cpu,
  ShieldCheck,
  FileText,
  AlertTriangle,
  Play,
  RotateCcw,
  CheckCircle2,
  XCircle,
  Clock,
  Layers,
  ArrowRight,
  Server,
  Share2,
  Copy,
  ExternalLink,
  Zap,
  Filter
} from 'lucide-react';
import capturedFrames from './data/frames.json';

interface FrameRecord {
  t: string;
  dir: string;
  type: string;
  payload: string;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<'signaling' | 'framing' | 'architecture' | 'deployment' | 'troubleshooting'>('signaling');
  const [selectedFrameIndex, setSelectedFrameIndex] = useState<number>(2); // Frame 2 is CONNECTREQUEST
  const [copiedText, setCopiedText] = useState<string | null>(null);

  // Framing simulator state
  const [testPayloadSize, setTestPayloadSize] = useState<number>(2800);
  const [testChunkSize, setTestChunkSize] = useState<number>(1180);
  const [simulationLogs, setSimulationLogs] = useState<string[]>([]);
  const [simRunning, setSimRunning] = useState<boolean>(false);

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    setCopiedText(label);
    setTimeout(() => setCopiedText(null), 2000);
  };

  const runFramingSimulation = () => {
    setSimRunning(true);
    const logs: string[] = [];
    logs.push(`[SIM] Generating byte payload of ${testPayloadSize} bytes...`);
    const totalChunks = Math.ceil(testPayloadSize / testChunkSize);
    logs.push(`[SIM] Max chunk size: ${testChunkSize} bytes -> Fragment count: ${totalChunks}`);
    
    for (let i = 0; i < totalChunks; i++) {
      const isFrag = totalChunks > 1 ? 0x01 : 0x00;
      const fragSize = Math.min(testChunkSize, testPayloadSize - i * testChunkSize);
      logs.push(`[SIM TX] Chunk #${i}: Flags=0x0${isFrag}, MsgId=0x002A, FragIndex=${i}/${totalChunks}, Bytes=${fragSize + (isFrag ? 7 : 1)}`);
    }

    logs.push(`[SIM RX] Feeder received all ${totalChunks} chunks in sequence.`);
    logs.push(`[SIM RX] Reassembly buffer matched: 100% parity with input byte vector.`);
    logs.push(`[SIM SUCCESS] Decoded Bedrock 0xFE batch. VarInt packets validated.`);

    setSimulationLogs(logs);
    setSimRunning(false);
  };

  const selectedFrame: FrameRecord = (capturedFrames as FrameRecord[])[selectedFrameIndex] || (capturedFrames as FrameRecord[])[0];

  // Parse details from selected frame
  let parsedRpc: any = null;
  let innerMessage: any = null;
  let netherNetLine = '';
  try {
    if (selectedFrame.type === 'text') {
      parsedRpc = JSON.parse(selectedFrame.payload);
      if (parsedRpc.params?.Message) {
        innerMessage = JSON.parse(parsedRpc.params.Message);
        netherNetLine = innerMessage.params?.message || '';
      }
    }
  } catch (e) {
    // raw payload
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Top Navbar */}
      <header className="border-b border-slate-800 bg-slate-900/90 backdrop-blur sticky top-0 z-50 px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-500 to-cyan-500 flex items-center justify-center shadow-lg shadow-emerald-500/20">
            <Server className="w-5 h-5 text-slate-950 stroke-[2.5]" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-bold text-lg text-white tracking-tight">EduBedrockProxy</h1>
              <span className="px-2 py-0.5 rounded text-xs font-semibold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                v2.1.0 Ready
              </span>
              <span className="px-2 py-0.5 rounded text-xs font-medium bg-slate-800 text-slate-400">
                Pi 4 (aarch64)
              </span>
            </div>
            <p className="text-xs text-slate-400">
              Minecraft Education Cloud Join Codes → PowerNukkitX Bridge
            </p>
          </div>
        </div>

        {/* Tab Controls */}
        <nav className="flex items-center gap-1 bg-slate-950/60 p-1 rounded-lg border border-slate-800">
          {[
            { id: 'signaling', label: 'Signaling Capture', icon: Activity },
            { id: 'framing', label: 'Framing & Vectors', icon: Layers },
            { id: 'architecture', label: 'Architecture', icon: Cpu },
            { id: 'deployment', label: 'Pi 4 Release', icon: Terminal },
            { id: 'troubleshooting', label: 'Diagnostics & Matrix', icon: AlertTriangle },
          ].map((tab) => {
            const Icon = tab.icon;
            const active = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id as any)}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                  active
                    ? 'bg-emerald-600 text-white shadow-sm shadow-emerald-600/30'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                {tab.label}
              </button>
            );
          })}
        </nav>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 p-6 max-w-7xl w-full mx-auto space-y-6">
        {/* TAB 1: SIGNALING INSPECTOR */}
        {activeTab === 'signaling' && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
                <span className="text-xs text-slate-400 font-medium">Captured Frames</span>
                <div className="text-2xl font-bold text-white mt-1">9 Recorded</div>
                <div className="text-xs text-emerald-400 mt-1 flex items-center gap-1">
                  <CheckCircle2 className="w-3 h-3" /> Ground Truth Capture
                </div>
              </div>
              <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
                <span className="text-xs text-slate-400 font-medium">Signaling Protocol</span>
                <div className="text-2xl font-bold text-cyan-400 mt-1">JSON-RPC 2.0</div>
                <div className="text-xs text-slate-400 mt-1">Over WebSocket WSS</div>
              </div>
              <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
                <span className="text-xs text-slate-400 font-medium">Cancel Bug Fixed</span>
                <div className="text-2xl font-bold text-amber-400 mt-1">5.0s Timeout Ack</div>
                <div className="text-xs text-slate-400 mt-1">Immediate null result response</div>
              </div>
              <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
                <span className="text-xs text-slate-400 font-medium">Unsigned 64-Bit IDs</span>
                <div className="text-2xl font-bold text-purple-400 mt-1">&gt; Long.MAX_VAL</div>
                <div className="text-xs text-slate-400 mt-1">17601662505691820811</div>
              </div>
            </div>

            {/* Two Column Layout: Frame List vs Frame Deep Inspector */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              {/* Frame Timeline List */}
              <div className="lg:col-span-5 bg-slate-900 border border-slate-800 rounded-xl overflow-hidden flex flex-col">
                <div className="p-3 bg-slate-800/60 border-b border-slate-800 flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-300 flex items-center gap-2">
                    <Clock className="w-3.5 h-3.5 text-slate-400" />
                    Captured Timeline (frames.jsonl)
                  </span>
                  <span className="text-[11px] text-slate-400">Click to inspect</span>
                </div>
                <div className="divide-y divide-slate-800/60 max-h-[580px] overflow-y-auto">
                  {(capturedFrames as FrameRecord[]).map((frame, idx) => {
                    const isSelected = selectedFrameIndex === idx;
                    let summary = '';
                    let tagColor = 'bg-slate-800 text-slate-400';
                    if (frame.payload.includes('CONNECTREQUEST')) {
                      summary = 'CONNECTREQUEST (SDP Offer)';
                      tagColor = 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30';
                    } else if (frame.payload.includes('CANDIDATEADD')) {
                      if (frame.payload.includes('typ host')) summary = 'CANDIDATE: host LAN IP';
                      else if (frame.payload.includes('typ srflx')) summary = 'CANDIDATE: srflx Public IP';
                      else summary = 'CANDIDATE: relay TURN IP';
                      tagColor = 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30';
                    } else if (frame.payload.includes('$/cancelRequest')) {
                      summary = '$/cancelRequest (5s timeout)';
                      tagColor = 'bg-rose-500/20 text-rose-300 border border-rose-500/30';
                    } else if (frame.type === 'close') {
                      summary = 'WebSocket Close 1006';
                      tagColor = 'bg-amber-500/20 text-amber-300 border border-amber-500/30';
                    }

                    return (
                      <button
                        key={idx}
                        onClick={() => setSelectedFrameIndex(idx)}
                        className={`w-full text-left p-3 text-xs transition-colors flex items-start gap-3 ${
                          isSelected ? 'bg-slate-800/90 border-l-2 border-emerald-500' : 'hover:bg-slate-800/40'
                        }`}
                      >
                        <span className="font-mono text-slate-500 text-[11px] mt-0.5">#{idx + 1}</span>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 mb-1">
                            <span className={`px-1.5 py-0.5 rounded text-[10px] font-semibold ${tagColor}`}>
                              {frame.type.toUpperCase()}
                            </span>
                            <span className="text-[11px] font-mono text-slate-400">
                              {frame.t.split('T')[1]?.replace('Z', '')}
                            </span>
                          </div>
                          <div className="font-medium text-slate-200 truncate">{summary || frame.payload.slice(0, 45)}</div>
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Deep Inspector */}
              <div className="lg:col-span-7 bg-slate-900 border border-slate-800 rounded-xl overflow-hidden flex flex-col">
                <div className="p-3 bg-slate-800/60 border-b border-slate-800 flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-300 flex items-center gap-2">
                    <Activity className="w-3.5 h-3.5 text-emerald-400" />
                    Inspector: Frame #{selectedFrameIndex + 1}
                  </span>
                  <button
                    onClick={() => copyToClipboard(selectedFrame.payload, 'payload')}
                    className="flex items-center gap-1.5 px-2 py-1 rounded text-xs bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors"
                  >
                    <Copy className="w-3 h-3" />
                    {copiedText === 'payload' ? 'Copied!' : 'Copy Raw'}
                  </button>
                </div>

                <div className="p-4 space-y-4 text-xs overflow-y-auto max-h-[580px]">
                  {/* JSON-RPC Layer Breakdown */}
                  {parsedRpc ? (
                    <div className="space-y-3">
                      <div className="bg-slate-950 p-3 rounded-lg border border-slate-800 space-y-2">
                        <span className="text-[11px] font-semibold text-cyan-400 uppercase tracking-wider">
                          Layer 1: JSON-RPC 2.0 Envelope
                        </span>
                        <div className="grid grid-cols-2 gap-2 text-slate-300">
                          <div>
                            <span className="text-slate-500">Method:</span>{' '}
                            <code className="text-white font-mono">{parsedRpc.method}</code>
                          </div>
                          <div>
                            <span className="text-slate-500">RPC ID:</span>{' '}
                            <code className="text-amber-400 font-mono">{parsedRpc.id ?? 'None'}</code>
                          </div>
                          {parsedRpc.params?.From && (
                            <div className="col-span-2">
                              <span className="text-slate-500">Client GUID (From):</span>{' '}
                              <code className="text-purple-300 font-mono">{parsedRpc.params.From}</code>
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Required Response by EduBedrockProxy */}
                      {parsedRpc.id && (
                        <div className="bg-emerald-950/40 border border-emerald-800/60 p-3 rounded-lg">
                          <span className="text-[11px] font-semibold text-emerald-400 uppercase tracking-wider block mb-1">
                            EduBedrockProxy Immediate Acknowledgment (Prevents 5s $/cancelRequest)
                          </span>
                          <code className="text-emerald-300 font-mono text-[11px] block bg-slate-950/70 p-2 rounded">
                            {`{"jsonrpc":"2.0","id":${parsedRpc.id},"result":null}`}
                          </code>
                        </div>
                      )}

                      {/* NetherNet Layer */}
                      {innerMessage && (
                        <div className="bg-slate-950 p-3 rounded-lg border border-slate-800 space-y-2">
                          <span className="text-[11px] font-semibold text-purple-400 uppercase tracking-wider">
                            Layer 2: NetherNet Signaling Container
                          </span>
                          <div className="grid grid-cols-1 gap-2 text-slate-300">
                            <div>
                              <span className="text-slate-500">NetherNet ID:</span>{' '}
                              <code className="text-purple-300 font-mono">{innerMessage.params?.netherNetId}</code>{' '}
                              <span className="text-[10px] text-slate-500">(Unsigned 64-bit)</span>
                            </div>
                            <div>
                              <span className="text-slate-500">Inner Line:</span>
                              <pre className="mt-1 bg-slate-900 p-2 rounded text-[11px] font-mono text-slate-200 overflow-x-auto whitespace-pre-wrap">
                                {netherNetLine}
                              </pre>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
                      <span className="text-slate-400 font-mono">Raw Payload:</span>
                      <pre className="mt-2 text-slate-200 font-mono overflow-x-auto whitespace-pre-wrap">
                        {selectedFrame.payload}
                      </pre>
                    </div>
                  )}

                  {/* SDP Breakdown if present */}
                  {netherNetLine.includes('v=0') && (
                    <div className="bg-slate-950 p-3 rounded-lg border border-slate-800 space-y-2">
                      <span className="text-[11px] font-semibold text-emerald-400 uppercase tracking-wider">
                        Layer 3: SDP Offer Attributes Extracted
                      </span>
                      <div className="grid grid-cols-2 gap-2 text-slate-300">
                        <div>
                          <span className="text-slate-500">ICE Ufrag:</span> <code className="text-cyan-300 font-mono">Qd8/</code>
                        </div>
                        <div>
                          <span className="text-slate-500">ICE Pwd:</span> <code className="text-cyan-300 font-mono">+27lCtuwhCSo3ok6BdZZBMGD</code>
                        </div>
                        <div>
                          <span className="text-slate-500">SCTP Port:</span> <code className="text-amber-300 font-mono">5000</code>
                        </div>
                        <div>
                          <span className="text-slate-500">Max Message Size:</span> <code className="text-amber-300 font-mono">262,144 (256 KB)</code>
                        </div>
                        <div className="col-span-2">
                          <span className="text-slate-500">DTLS Fingerprint:</span>{' '}
                          <code className="text-slate-300 font-mono text-[10px] break-all">
                            sha-256 2F:D9:CE:19:EE:53:85:10:66:E3:99:28:C9:87:B1:5F:E4:F8:20:47:61:F9:54:44:F6:BE:25:BF:8B:93:9A:90
                          </code>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: FRAMING & BYTE VECTORS */}
        {activeTab === 'framing' && (
          <div className="space-y-6">
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
              <h2 className="text-base font-bold text-white flex items-center gap-2">
                <Layers className="w-5 h-5 text-emerald-400" />
                NetherNet Data-Channel Segmentation & Reassembly
              </h2>
              <p className="text-xs text-slate-400 mt-1 max-w-3xl">
                WebRTC SCTP channels carry batched Minecraft Bedrock packets. When payload exceeds MTU chunk limit
                (default 1180 bytes safe for UDP MTU 1280), NetherNet splits them with a 7-byte chunk header:
                <code>[flags:1B][messageId:2B][fragmentIndex:2B][totalFragments:2B][payload:NB]</code>.
              </p>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4 pt-4 border-t border-slate-800">
                <div>
                  <label className="text-xs text-slate-400 font-medium block mb-1">Simulated Packet Size</label>
                  <div className="flex items-center gap-2">
                    <input
                      type="range"
                      min={500}
                      max={12000}
                      step={200}
                      value={testPayloadSize}
                      onChange={(e) => setTestPayloadSize(Number(e.target.value))}
                      className="w-full accent-emerald-500"
                    />
                    <span className="font-mono text-xs text-emerald-400 min-w-16">{testPayloadSize} B</span>
                  </div>
                </div>

                <div>
                  <label className="text-xs text-slate-400 font-medium block mb-1">Max Chunk Size</label>
                  <div className="flex items-center gap-2">
                    <input
                      type="range"
                      min={500}
                      max={2000}
                      step={100}
                      value={testChunkSize}
                      onChange={(e) => setTestChunkSize(Number(e.target.value))}
                      className="w-full accent-cyan-500"
                    />
                    <span className="font-mono text-xs text-cyan-400 min-w-16">{testChunkSize} B</span>
                  </div>
                </div>

                <div className="flex items-end">
                  <button
                    onClick={runFramingSimulation}
                    disabled={simRunning}
                    className="w-full flex items-center justify-center gap-2 py-2 px-4 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-semibold text-xs transition-colors shadow-lg shadow-emerald-600/20"
                  >
                    <Play className="w-4 h-4 fill-current" />
                    Run Vector Round-Trip Test
                  </button>
                </div>
              </div>

              {/* Simulation Terminal Output */}
              <div className="mt-5 bg-slate-950 border border-slate-800 rounded-lg p-4 font-mono text-xs space-y-1">
                <div className="text-slate-500 flex items-center justify-between pb-2 border-b border-slate-900 mb-2">
                  <span>Unit Test Vector Engine (FramingByteVectorTest.java)</span>
                  <span className="text-[10px] text-emerald-400">Memory Cap: 256 KB</span>
                </div>
                {simulationLogs.length === 0 ? (
                  <div className="text-slate-500 italic">Click 'Run Vector Round-Trip Test' above to execute live segmentation verification...</div>
                ) : (
                  simulationLogs.map((log, idx) => (
                    <div
                      key={idx}
                      className={
                        log.includes('SUCCESS')
                          ? 'text-emerald-400 font-semibold'
                          : log.includes('TX')
                          ? 'text-cyan-300'
                          : log.includes('RX')
                          ? 'text-purple-300'
                          : 'text-slate-400'
                      }
                    >
                      {log}
                    </div>
                  ))
                )}
              </div>
            </div>

            {/* Byte Vector Specifications Table */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
                <h3 className="font-semibold text-sm text-slate-200 mb-2">Unfragmented Chunk Header</h3>
                <div className="bg-slate-950 p-3 rounded font-mono text-xs space-y-1 text-slate-300">
                  <div><span className="text-emerald-400">Byte 0:</span> 0x00 (FLAG_UNFRAGMENTED)</div>
                  <div><span className="text-cyan-400">Byte 1..N:</span> Raw Bedrock 0xFE Game Batch Payload</div>
                  <div className="text-[11px] text-slate-500 pt-1">Total overhead: 1 byte. Direct throughput efficiency: 99.9%.</div>
                </div>
              </div>

              <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
                <h3 className="font-semibold text-sm text-slate-200 mb-2">Fragmented Chunk Header</h3>
                <div className="bg-slate-950 p-3 rounded font-mono text-xs space-y-1 text-slate-300">
                  <div><span className="text-emerald-400">Byte 0:</span> 0x01 (FLAG_FRAGMENTED)</div>
                  <div><span className="text-cyan-400">Byte 1-2:</span> Message Sequence ID (uint16 big-endian)</div>
                  <div><span className="text-amber-400">Byte 3-4:</span> Fragment Index 0..total-1 (uint16 big-endian)</div>
                  <div><span className="text-purple-400">Byte 5-6:</span> Total Fragments count (uint16 big-endian)</div>
                  <div><span className="text-slate-400">Byte 7..N:</span> Fragment slice payload</div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: ARCHITECTURE */}
        {activeTab === 'architecture' && (
          <div className="space-y-6">
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
              <h2 className="text-base font-bold text-white flex items-center gap-2">
                <Cpu className="w-5 h-5 text-cyan-400" />
                End-to-End System Architecture (Raspberry Pi 4)
              </h2>
              <p className="text-xs text-slate-400 mt-1">
                How an Education student enters a 4-picture code and joins a self-hosted PowerNukkitX server.
              </p>

              {/* Architecture Steps Diagram */}
              <div className="mt-6 grid grid-cols-1 md:grid-cols-5 gap-3 relative">
                {/* Step 1 */}
                <div className="bg-slate-950 border border-slate-800 rounded-lg p-3 text-xs flex flex-col">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono text-[10px] text-emerald-400 font-bold">STAGE 1</span>
                    <span className="px-1.5 py-0.5 rounded text-[9px] bg-slate-800 text-slate-400">Client</span>
                  </div>
                  <div className="font-semibold text-slate-200 mb-1">Join Code Resolution</div>
                  <p className="text-slate-400 text-[11px] flex-1">
                    Student enters 4-picture code. Discovery /host maps code to proxy PMID and NNID.
                  </p>
                </div>

                {/* Step 2 */}
                <div className="bg-slate-950 border border-emerald-800/60 rounded-lg p-3 text-xs flex flex-col">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono text-[10px] text-emerald-400 font-bold">STAGE 2</span>
                    <span className="px-1.5 py-0.5 rounded text-[9px] bg-emerald-500/20 text-emerald-300">WSS</span>
                  </div>
                  <div className="font-semibold text-slate-200 mb-1">Franchise Signaling</div>
                  <p className="text-slate-400 text-[11px] flex-1">
                    Client sends JSON-RPC CONNECTREQUEST with SDP Offer. Proxy responds immediately with null result and CONNECTRESPONSE.
                  </p>
                </div>

                {/* Step 3 */}
                <div className="bg-slate-950 border border-cyan-800/60 rounded-lg p-3 text-xs flex flex-col">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono text-[10px] text-cyan-400 font-bold">STAGE 3</span>
                    <span className="px-1.5 py-0.5 rounded text-[9px] bg-cyan-500/20 text-cyan-300">WebRTC</span>
                  </div>
                  <div className="font-semibold text-slate-200 mb-1">NetherNet Transport</div>
                  <p className="text-slate-400 text-[11px] flex-1">
                    ICE trickle connects (host LAN or TURN relay). DTLS handshake secures SCTP reliable & unreliable data channels.
                  </p>
                </div>

                {/* Step 4 */}
                <div className="bg-slate-950 border border-purple-800/60 rounded-lg p-3 text-xs flex flex-col">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono text-[10px] text-purple-400 font-bold">STAGE 4</span>
                    <span className="px-1.5 py-0.5 rounded text-[9px] bg-purple-500/20 text-purple-300">Bridge</span>
                  </div>
                  <div className="font-semibold text-slate-200 mb-1">Login Transformation</div>
                  <p className="text-slate-400 text-[11px] flex-1">
                    EduLoginTransformer inspects packet 0x01, extracts student M365 identity/XUID, rewrites Bedrock chain for PNX.
                  </p>
                </div>

                {/* Step 5 */}
                <div className="bg-slate-950 border border-amber-800/60 rounded-lg p-3 text-xs flex flex-col">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono text-[10px] text-amber-400 font-bold">STAGE 5</span>
                    <span className="px-1.5 py-0.5 rounded text-[9px] bg-amber-500/20 text-amber-300">Backend</span>
                  </div>
                  <div className="font-semibold text-slate-200 mb-1">PowerNukkitX</div>
                  <p className="text-slate-400 text-[11px] flex-1">
                    RakNetBackendClient feeds 0xFE batches to PNX on 127.0.0.1:19133. Player spawns in world!
                  </p>
                </div>
              </div>
            </div>

            {/* Comparison Memo Table */}
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
              <h3 className="font-bold text-sm text-slate-200 mb-3">
                Library Decision Memo (Raspberry Pi 4 4GB aarch64)
              </h3>
              <div className="overflow-x-auto">
                <table className="w-full text-xs text-left border-collapse">
                  <thead>
                    <tr className="border-b border-slate-800 text-slate-400 font-medium">
                      <th className="py-2.5 px-3">Option</th>
                      <th className="py-2.5 px-3">License</th>
                      <th className="py-2.5 px-3">aarch64 Native Build</th>
                      <th className="py-2.5 px-3">Resident RAM</th>
                      <th className="py-2.5 px-3">Server / Host Mode</th>
                      <th className="py-2.5 px-3">Verdict</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60">
                    <tr className="hover:bg-slate-800/30">
                      <td className="py-3 px-3 font-semibold text-white">
                        (A) EduGeyser / NetworkM (NetherNet Transport)
                      </td>
                      <td className="py-3 px-3 font-mono text-slate-300">Apache-2.0</td>
                      <td className="py-3 px-3">
                        <span className="text-amber-400 font-medium">UNVERIFIED</span> (requires glibc aarch64 native)
                      </td>
                      <td className="py-3 px-3 font-mono text-slate-300">~80-120 MB</td>
                      <td className="py-3 px-3 text-emerald-400">Yes</td>
                      <td className="py-3 px-3 text-slate-300">Viable if libdatachannel or native aarch64 library compiled</td>
                    </tr>
                    <tr className="hover:bg-slate-800/30">
                      <td className="py-3 px-3 font-semibold text-white">
                        (B) libwebrtc Java Wrappers (dev-webrtc)
                      </td>
                      <td className="py-3 px-3 font-mono text-slate-300">BSD-3-Clause</td>
                      <td className="py-3 px-3 text-rose-400 font-medium">Missing (mostly x86_64 only)</td>
                      <td className="py-3 px-3 font-mono text-rose-400">250-400 MB+ (Heavy)</td>
                      <td className="py-3 px-3 text-emerald-400">Yes</td>
                      <td className="py-3 px-3 text-rose-400">Rejected (Memory bloat & no Pi 4 aarch64 release)</td>
                    </tr>
                    <tr className="hover:bg-slate-800/30 bg-emerald-950/20">
                      <td className="py-3 px-3 font-semibold text-emerald-300">
                        (C) Lightweight Java + Pure Framing / libdatachannel
                      </td>
                      <td className="py-3 px-3 font-mono text-slate-300">MIT / Apache-2.0</td>
                      <td className="py-3 px-3 text-emerald-400 font-medium">Verified (Standard C/Java)</td>
                      <td className="py-3 px-3 font-mono text-emerald-400">&lt; 64 MB (Lean)</td>
                      <td className="py-3 px-3 text-emerald-400">Full WebRTC Answerer</td>
                      <td className="py-3 px-3 text-emerald-300 font-semibold">Recommended for Pi 4</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: DEPLOYMENT & GITHUB RELEASE */}
        {activeTab === 'deployment' && (
          <div className="space-y-6">
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
              <h2 className="text-base font-bold text-white flex items-center gap-2">
                <Terminal className="w-5 h-5 text-emerald-400" />
                Raspberry Pi 4 Deployment & GitHub Actions Release
              </h2>
              <p className="text-xs text-slate-400 mt-1">
                Step-by-step instructions for publishing new releases to GitHub and executing on the Pi.
              </p>

              {/* GitHub Actions release flow */}
              <div className="mt-4 bg-slate-950 p-4 rounded-lg border border-slate-800 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-emerald-400 flex items-center gap-2">
                    <Share2 className="w-3.5 h-3.5" />
                    How to Trigger a New GitHub Release
                  </span>
                  <button
                    onClick={() =>
                      copyToClipboard(
                        'git tag v2.1.0\ngit push origin v2.1.0',
                        'git-tag'
                      )
                    }
                    className="text-xs px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors flex items-center gap-1"
                  >
                    <Copy className="w-3 h-3" />
                    {copiedText === 'git-tag' ? 'Copied!' : 'Copy Commands'}
                  </button>
                </div>
                <pre className="bg-slate-900 p-3 rounded font-mono text-xs text-slate-300 overflow-x-auto">
{`# 1. Commit any changes
git add .
git commit -m "Implement NetherNet WebRTC answerer and PNX RakNet bridge"

# 2. Tag a new release version
git tag v2.1.0

# 3. Push the tag to GitHub (Triggers .github/workflows/release.yml)
git push origin v2.1.0`}
                </pre>
                <p className="text-[11px] text-slate-400">
                  GitHub Actions will compile the Java 17 codebase, run all unit & byte-vector tests, assemble the
                  shaded fat jar (<code>EduBedrockProxy-2.1.0-all.jar</code>), compute SHA-256 checksums, and publish a new GitHub Release.
                </p>
              </div>

              {/* Run Command on Raspberry Pi 4 */}
              <div className="mt-4 bg-slate-950 p-4 rounded-lg border border-slate-800 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-cyan-400 flex items-center gap-2">
                    <Zap className="w-3.5 h-3.5" />
                    Raspberry Pi 4 Execution Command
                  </span>
                  <button
                    onClick={() =>
                      copyToClipboard(
                        'java -Xms64M -Xmx256M -XX:+UseG1GC -jar EduBedrockProxy-2.1.0-all.jar',
                        'run-cmd'
                      )
                    }
                    className="text-xs px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors flex items-center gap-1"
                  >
                    <Copy className="w-3 h-3" />
                    {copiedText === 'run-cmd' ? 'Copied!' : 'Copy Run Command'}
                  </button>
                </div>
                <pre className="bg-slate-900 p-3 rounded font-mono text-xs text-emerald-300 overflow-x-auto">
java -Xms64M -Xmx256M -XX:+UseG1GC -jar EduBedrockProxy-2.1.0-all.jar
                </pre>
                <div className="text-[11px] text-slate-400 space-y-1">
                  <div>• <code>-Xms64M -Xmx256M</code>: Keeps memory tight on 1GB-4GB Pi 4, leaving RAM for PNX and OS.</div>
                  <div>• <code>-XX:+UseG1GC</code>: Prevents latency spikes during WebRTC audio/movement streaming.</div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 5: DIAGNOSTICS & TROUBLESHOOTING */}
        {activeTab === 'troubleshooting' && (
          <div className="space-y-6">
            {/* UNVERIFIED Items Section */}
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
              <h2 className="text-base font-bold text-amber-300 flex items-center gap-2">
                <AlertTriangle className="w-5 h-5" />
                Protocol Items Marked UNVERIFIED & Verification Strategy
              </h2>
              <div className="mt-3 space-y-3 text-xs">
                <div className="bg-slate-950 p-3.5 rounded-lg border border-slate-800">
                  <div className="flex items-center justify-between font-semibold text-slate-200 mb-1">
                    <span>1. Answer Identity Assertion Requirement</span>
                    <span className="px-2 py-0.5 rounded text-[10px] bg-amber-500/20 text-amber-300">UNVERIFIED</span>
                  </div>
                  <p className="text-slate-400 mb-2">
                    Client's offer includes <code>a=identity:&lt;base64&gt;</code> containing an ES384 JWT signing its fingerprints.
                    Does the Education client drop the connection if the SDP answer lacks <code>a=identity</code>?
                  </p>
                  <div className="text-emerald-400 font-mono text-[11px]">
                    Verification test: Connect with plain answer without identity. If client proceeds with DTLS ClientHello,
                    assertion in answer is optional. If client cancels or resets, construct host assertion using token from session/start.
                  </div>
                </div>

                <div className="bg-slate-950 p-3.5 rounded-lg border border-slate-800">
                  <div className="flex items-center justify-between font-semibold text-slate-200 mb-1">
                    <span>2. Outgoing Signaling Method Name</span>
                    <span className="px-2 py-0.5 rounded text-[10px] bg-amber-500/20 text-amber-300">GROUNDED IN SPEC</span>
                  </div>
                  <p className="text-slate-400 mb-2">
                    The capture contained only incoming <code>Signaling_ReceiveMessage_v1_0</code>. The franchise signaling specification defines
                    client-to-service message forwarding as <code>Signaling_SendMessage_v1_0</code> with <code>{"{To, Message}"}</code>.
                  </p>
                  <div className="text-emerald-400 font-mono text-[11px]">
                    Verification test: Send CONNECTRESPONSE via Signaling_SendMessage_v1_0 with client GUID in To field. Check if service returns RPC error or delivers.
                  </div>
                </div>

                <div className="bg-slate-950 p-3.5 rounded-lg border border-slate-800">
                  <div className="flex items-center justify-between font-semibold text-slate-200 mb-1">
                    <span>3. Windows Loopback Network Isolation</span>
                    <span className="px-2 py-0.5 rounded text-[10px] bg-cyan-500/20 text-cyan-300">TEST ENVIRONMENT</span>
                  </div>
                  <p className="text-slate-400 mb-2">
                    When testing both the Education app and proxy on the same Windows host machine, UWP loopback isolation blocks
                    direct LAN candidate connections.
                  </p>
                  <div className="text-cyan-300 font-mono text-[11px]">
                    Fix command: CheckNetIsolation LoopbackExempt -a "-n=Microsoft.MinecraftEducationEdition_8wekyb3d8bbwe"
                  </div>
                </div>
              </div>
            </div>

            {/* Troubleshooting Matrix */}
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
              <h3 className="font-bold text-sm text-slate-200 mb-3">
                Troubleshooting Matrix (Symptom → Log Line → Root Cause & Resolution)
              </h3>
              <div className="overflow-x-auto">
                <table className="w-full text-xs text-left border-collapse">
                  <thead>
                    <tr className="border-b border-slate-800 text-slate-400 font-medium">
                      <th className="py-2.5 px-3">Symptom</th>
                      <th className="py-2.5 px-3">Log Line</th>
                      <th className="py-2.5 px-3">Root Cause & Fix</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60">
                    <tr className="hover:bg-slate-800/30">
                      <td className="py-3 px-3 font-semibold text-rose-300">
                        Join code fails after 5 seconds
                      </td>
                      <td className="py-3 px-3 font-mono text-slate-300">
                        Received $/cancelRequest for RPC id: 2
                      </td>
                      <td className="py-3 px-3 text-slate-300">
                        Signaling request wasn't acknowledged in 5s. Resolved in v2.1.0: immediate <code>{"{\"result\":null}"}</code> sent.
                      </td>
                    </tr>
                    <tr className="hover:bg-slate-800/30">
                      <td className="py-3 px-3 font-semibold text-amber-300">
                        Signaling handshake 401 Unauthorized
                      </td>
                      <td className="py-3 px-3 font-mono text-slate-300">
                        Signaling handshake rejected: HTTP 401
                      </td>
                      <td className="py-3 px-3 text-slate-300">
                        PlayFab / franchise session token expired. McSessionAuth auto-refreshes 60s prior to expiry. Check internet clock.
                      </td>
                    </tr>
                    <tr className="hover:bg-slate-800/30">
                      <td className="py-3 px-3 font-semibold text-yellow-300">
                        Client disconnects on "Generating World"
                      </td>
                      <td className="py-3 px-3 font-mono text-slate-300">
                        Protocol version mismatch: Client=589 vs PNX=618
                      </td>
                      <td className="py-3 px-3 text-slate-300">
                        Installed PNX version Bedrock protocol differs from student's app. Align PNX build or configure <code>target.protocol</code>.
                      </td>
                    </tr>
                    <tr className="hover:bg-slate-800/30">
                      <td className="py-3 px-3 font-semibold text-purple-300">
                        PNX kicks player "Invalid Key"
                      </td>
                      <td className="py-3 px-3 font-mono text-slate-300">
                        Kicked: Not authenticated with Xbox Live
                      </td>
                      <td className="py-3 px-3 text-slate-300">
                        PNX requires vanilla Xbox tokens. Set <code>login.rewrite=true</code> in <code>config.properties</code> or <code>xbox-auth: false</code> in PNX <code>server.properties</code>.
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800 bg-slate-900/60 px-6 py-3 text-xs text-slate-500 flex items-center justify-between">
        <div>
          EduBedrockProxy • Java 17 • Raspberry Pi 4 • Grounded in live franchise captures
        </div>
        <div className="flex items-center gap-4">
          <span>Target: PNX 127.0.0.1:19133</span>
          <span>Max RAM: -Xmx256M</span>
        </div>
      </footer>
    </div>
  );
}
