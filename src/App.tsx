import { useEffect, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { AnimatePresence, motion } from "framer-motion";
import JSZip from "jszip";
import {
  ArrowDown,
  ArrowRight,
  Check,
  Copy,
  Download,
  FileCode2,
  FolderOpen,
  Play,
  RotateCcw,
  ScanEye,
  Search,
  Settings2,
  ShieldCheck,
  Square,
  X,
} from "lucide-react";

type NativeFile = { path: string; content: string; language: string };
type Mood = "HAPPY" | "ANGRY" | "EXCITED" | "SAD" | "THINKING";
type Phase = "idle" | "analyzing" | "complete";

const rawFiles = import.meta.glob("../android-app/**/*.{kt,kts,xml,properties}", {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

const projectFiles: NativeFile[] = Object.entries(rawFiles)
  .map(([relativePath, content]) => {
    const path = relativePath.replace(/^\.\.\//, "");
    return {
      path,
      content,
      language: path.endsWith(".xml") ? "xml" : path.endsWith(".properties") ? "properties" : "kotlin",
    };
  })
  .sort((a, b) => a.path.localeCompare(b.path));

const featurePaths = [
  "android-app/app/src/main/java/com/pixelbuddy/ai/capture/ScreenCaptureService.kt",
  "android-app/app/src/main/java/com/pixelbuddy/ai/capture/ScreenFrameProcessor.kt",
  "android-app/app/src/main/java/com/pixelbuddy/ai/capture/GeminiVisionClient.kt",
  "android-app/app/src/main/java/com/pixelbuddy/ai/capture/VisionCredentialStore.kt",
  "android-app/app/src/main/java/com/pixelbuddy/ai/MainActivity.kt",
  "android-app/app/src/main/java/com/pixelbuddy/ai/overlay/PixelOverlayService.kt",
  "android-app/app/src/main/java/com/pixelbuddy/ai/overlay/TaskOverlayController.kt",
  "android-app/app/src/main/res/layout/activity_main.xml",
  "android-app/app/src/main/res/layout/overlay_tasks_panel.xml",
  "android-app/app/src/main/AndroidManifest.xml",
  "android-app/app/build.gradle.kts",
];

const featureSet = new Set(featurePaths);
const fileMap = new Map(projectFiles.map((file) => [file.path, file]));

const facePixels = [
  "00011111111100",
  "01112222222110",
  "11222222222211",
  "12222222222221",
  "12222222222221",
  "12213122213121",
  "12213122213121",
  "12222222222221",
  "12222111122221",
  "12221122112221",
  "12211111111221",
  "11222222222211",
  "01112222222110",
  "00011111111100",
];

const moodStyles: Record<Mood, { fill: string; border: string; text: string; tail: string }> = {
  HAPPY: { fill: "#FFF2BB", border: "#B68016", text: "#443019", tail: "31%" },
  ANGRY: { fill: "#FFD8D3", border: "#C43532", text: "#511512", tail: "50%" },
  EXCITED: { fill: "#EADFFF", border: "#8254C4", text: "#392358", tail: "23%" },
  SAD: { fill: "#DCEBFF", border: "#6486AD", text: "#263C59", tail: "69%" },
  THINKING: { fill: "#F3F0EB", border: "#80838C", text: "#292C34", tail: "40%" },
};

function PixelFace({ size = 66 }: { size?: number }) {
  return (
    <svg
      aria-hidden="true"
      className="pixel-face block shrink-0"
      width={size}
      height={size}
      viewBox="0 0 14 14"
      shapeRendering="crispEdges"
    >
      {facePixels.flatMap((row, y) =>
        [...row].map((pixel, x) => {
          if (pixel === "0") return null;
          return (
            <rect
              key={`${x}-${y}`}
              x={x}
              y={y}
              width="1"
              height="1"
              fill={pixel === "1" ? "#5F1016" : pixel === "3" ? "#FFA993" : "#F14B49"}
            />
          );
        })
      )}
    </svg>
  );
}

function downloadText(filename: string, text: string) {
  const url = URL.createObjectURL(new Blob([text], { type: "text/plain;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export default function App() {
  const [selectedPath, setSelectedPath] = useState(featurePaths[0]);
  const [showAll, setShowAll] = useState(false);
  const [search, setSearch] = useState("");
  const [copied, setCopied] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState("");

  const [avatarVisible, setAvatarVisible] = useState(true);
  const [avatarPosition, setAvatarPosition] = useState({ x: 0.83, y: 0.74 });
  const [menuOpen, setMenuOpen] = useState(false);
  const [tasksOpen, setTasksOpen] = useState(false);
  const [consentOpen, setConsentOpen] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [phase, setPhase] = useState<Phase>("idle");
  const [bubbleText, setBubbleText] = useState("Tap me to open screen tools.");
  const [bubbleMood, setBubbleMood] = useState<Mood>("THINKING");
  const [bubbleVisible, setBubbleVisible] = useState(true);

  const heroRef = useRef<HTMLElement>(null);
  const sourceRef = useRef<HTMLElement>(null);
  const drag = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    x: number;
    y: number;
    moved: boolean;
  } | null>(null);
  const analysisTimer = useRef<number | null>(null);

  const visibleFiles = useMemo(() => {
    const files = showAll ? projectFiles : featurePaths.map((path) => fileMap.get(path)).filter((file): file is NativeFile => Boolean(file));
    const query = search.trim().toLowerCase();
    return query ? files.filter((file) => file.path.toLowerCase().includes(query)) : files;
  }, [showAll, search]);

  const selectedFile = fileMap.get(selectedPath) ?? projectFiles[0];
  const lineCount = selectedFile?.content.split("\n").length ?? 0;

  useEffect(() => {
    return () => {
      if (analysisTimer.current !== null) window.clearTimeout(analysisTimer.current);
    };
  }, []);

  const scrollToSource = (path?: string) => {
    if (path) {
      setSelectedPath(path);
      setSearch("");
      if (!featureSet.has(path)) setShowAll(true);
    }
    sourceRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const showDemoBubble = (text: string, mood: Mood) => {
    setBubbleText(text);
    setBubbleMood(mood);
    setBubbleVisible(true);
  };

  const analyzeDemo = () => {
    if (phase === "analyzing") return;
    setMenuOpen(false);
    setTasksOpen(false);
    if (!sharing) {
      setConsentOpen(true);
      return;
    }

    setPhase("analyzing");
    showDemoBubble("Looking at your screen now...", "THINKING");
    analysisTimer.current = window.setTimeout(() => {
      setPhase("complete");
      showDemoBubble(
        "Wait for the eye to fire, then jump right to the narrow ledge. Keep your dash for the next gap.",
        "EXCITED"
      );
      analysisTimer.current = null;
    }, 1700);
  };

  const allowDemo = () => {
    setConsentOpen(false);
    setSharing(true);
    setPhase("analyzing");
    setMenuOpen(false);
    setTasksOpen(false);
    showDemoBubble("Looking at your screen now...", "THINKING");
    analysisTimer.current = window.setTimeout(() => {
      setPhase("complete");
      showDemoBubble(
        "Wait for the eye to fire, then jump right to the narrow ledge. Keep your dash for the next gap.",
        "EXCITED"
      );
      analysisTimer.current = null;
    }, 1700);
  };

  const stopDemo = () => {
    if (analysisTimer.current !== null) window.clearTimeout(analysisTimer.current);
    analysisTimer.current = null;
    setSharing(false);
    setPhase("idle");
    showDemoBubble("Screen sharing stopped. Tap Tasks when you're ready.", "SAD");
  };

  const copyFile = async () => {
    if (!selectedFile) return;
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(selectedFile.content);
      } else {
        const field = document.createElement("textarea");
        field.value = selectedFile.content;
        document.body.appendChild(field);
        field.select();
        document.execCommand("copy");
        field.remove();
      }
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setNotice("Clipboard access is unavailable in this browser.");
    }
  };

  const downloadProject = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      const zip = new JSZip();
      projectFiles.forEach((file) => {
        zip.file(`PixelBuddy/${file.path.replace(/^android-app\//, "")}`, file.content);
      });
      const blob = await zip.generateAsync({ type: "blob", compression: "DEFLATE", compressionOptions: { level: 6 } });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "PixelBuddy-Android-source.zip";
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setNotice("Android source downloaded.");
    } catch {
      setNotice("Download failed. Please try again.");
    } finally {
      setDownloading(false);
      window.setTimeout(() => setNotice(""), 3200);
    }
  };

  const onAvatarDown = (event: ReactPointerEvent<HTMLButtonElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId);
    drag.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      x: avatarPosition.x,
      y: avatarPosition.y,
      moved: false,
    };
  };

  const onAvatarMove = (event: ReactPointerEvent<HTMLButtonElement>) => {
    const current = drag.current;
    const rect = heroRef.current?.getBoundingClientRect();
    if (!current || !rect || current.pointerId !== event.pointerId) return;
    const deltaX = event.clientX - current.startX;
    const deltaY = event.clientY - current.startY;
    if (Math.abs(deltaX) + Math.abs(deltaY) > 7) current.moved = true;
    if (!current.moved) return;

    const minimumX = window.innerWidth < 640 ? 0.79 : 0.56;
    setAvatarPosition({
      x: Math.max(minimumX, Math.min(0.91, current.x + deltaX / rect.width)),
      y: Math.max(0.30, Math.min(0.83, current.y + deltaY / rect.height)),
    });
    setMenuOpen(false);
    setTasksOpen(false);
  };

  const onAvatarUp = (event: ReactPointerEvent<HTMLButtonElement>) => {
    const current = drag.current;
    if (!current || current.pointerId !== event.pointerId) return;
    if (!current.moved) {
      setMenuOpen((open) => !open);
      setTasksOpen(false);
      setBubbleVisible(false);
    }
    drag.current = null;
  };

  return (
    <main className="min-h-screen overflow-hidden bg-[#0c1016] text-[#f6f4ef]">
      <header className="relative z-30 flex h-[76px] items-center justify-between border-b border-white/10 bg-[#0c1016] px-5 md:px-12">
        <button
          className="flex items-center gap-3 text-left"
          onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
          aria-label="Back to PixelBuddy preview"
        >
          <PixelFace size={33} />
          <span className="text-[19px] font-bold tracking-[-0.06em]">PixelBuddy<span className="text-[#f14b49]">.</span></span>
        </button>
        <span className="mono hidden text-[11px] uppercase tracking-[0.18em] text-white/45 md:block">
          Android build / Screen awareness / 08
        </span>
        <button
          className="inline-flex items-center gap-2 border border-[#f14b49] bg-[#f14b49] px-3 py-2.5 text-xs font-bold text-[#170d0d] transition-colors hover:bg-[#ff766f] md:px-5 md:text-sm"
          onClick={downloadProject}
          disabled={downloading}
        >
          <Download size={16} strokeWidth={2.3} />
          <span className="hidden sm:inline">{downloading ? "Preparing source..." : "Download Android source"}</span>
          <span className="sm:hidden">Source</span>
        </button>
      </header>

      <section ref={heroRef} className="demo-scene relative isolate min-h-[690px] overflow-hidden md:min-h-[calc(100svh-76px)]">
        <div className="relative z-10 mx-auto flex min-h-[690px] max-w-[1536px] items-start px-6 pt-20 md:min-h-[calc(100svh-76px)] md:items-center md:px-12 md:pt-0">
          <motion.div
            className="max-w-[620px] pointer-events-none"
            initial={{ opacity: 0, y: 22 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, ease: "easeOut" }}
          >
            <p className="mono mb-6 text-[11px] uppercase tracking-[0.22em] text-[#ff8d7e]">Screen awareness / Step 08</p>
            <h1 className="text-[clamp(46px,12vw,148px)] font-bold leading-[0.83] tracking-[-0.092em]">
              Pixel<span className="text-[#f14b49]">Buddy.</span>
            </h1>
            <h2 className="mt-9 max-w-[510px] text-3xl font-medium leading-[1.14] tracking-[-0.045em] md:text-[42px]">
              A second set of eyes for whatever is on screen.
            </h2>
            <p className="mt-5 max-w-[425px] text-[15px] leading-7 text-[#d2d0d2] md:text-base">
              Capture a frame on command. Get one clear next move from Gemini, right beside your floating buddy.
            </p>
            <div className="pointer-events-auto mt-8 flex flex-wrap items-center gap-5">
              <button
                onClick={() => scrollToSource()}
                className="group flex items-center gap-3 bg-[#f14b49] px-6 py-4 text-sm font-bold text-[#1c1011] transition-colors hover:bg-[#ff7b73]"
              >
                Explore the source <ArrowRight size={18} className="transition-transform group-hover:translate-x-1" />
              </button>
              <button
                onClick={() => {
                  setTasksOpen(true);
                  setMenuOpen(false);
                  setBubbleVisible(false);
                  if (!avatarVisible) setAvatarVisible(true);
                }}
                className="flex items-center gap-2 border-b border-white/65 pb-1 text-sm font-medium transition-colors hover:text-[#ff9a90]"
              >
                Try the preview <ArrowDown size={17} />
              </button>
            </div>
          </motion.div>
        </div>

        <AnimatePresence>
          {avatarVisible && (
            <motion.div
              className="absolute z-20"
              style={{ left: `${avatarPosition.x * 100}%`, top: `${avatarPosition.y * 100}%` }}
              initial={{ opacity: 0, scale: 0.5, x: "-50%", y: "-50%" }}
              animate={{ opacity: 1, scale: 1, x: "-50%", y: "-50%" }}
              exit={{ opacity: 0, scale: 0.5, x: "-50%", y: "-50%" }}
              transition={{ type: "spring", stiffness: 290, damping: 19, delay: 0.24 }}
            >
              <button
                aria-label="PixelBuddy: tap to open menu or drag to reposition"
                className="pixel-bob block touch-none cursor-grab select-none active:cursor-grabbing"
                onPointerDown={onAvatarDown}
                onPointerMove={onAvatarMove}
                onPointerUp={onAvatarUp}
                onPointerCancel={() => { drag.current = null; }}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    setMenuOpen((open) => !open);
                    setBubbleVisible(false);
                  }
                }}
              >
                <PixelFace size={76} />
              </button>

              <AnimatePresence mode="wait">
                {bubbleVisible && !menuOpen && !tasksOpen && (
                  <motion.div
                    key={`${bubbleMood}-${bubbleText}`}
                    className="absolute right-[calc(100%+18px)] top-[-24px] w-[min(244px,58vw)] border-2 px-[17px] py-[15px] text-[13px] font-semibold leading-[1.46] shadow-[8px_12px_0_rgba(9,8,26,0.24)] md:w-[268px] md:text-sm"
                    style={{
                      backgroundColor: moodStyles[bubbleMood].fill,
                      borderColor: moodStyles[bubbleMood].border,
                      color: moodStyles[bubbleMood].text,
                      borderRadius: bubbleMood === "ANGRY" ? "6px" : "19px",
                    }}
                    initial={{ opacity: 0, x: 12, scale: 0.92 }}
                    animate={{ opacity: 1, x: 0, scale: 1 }}
                    exit={{ opacity: 0, x: 10, scale: 0.94 }}
                    transition={{ duration: 0.25 }}
                  >
                    {bubbleText}
                    <span
                      className="absolute -right-[15px] h-[18px] w-[17px]"
                      style={{
                        top: moodStyles[bubbleMood].tail,
                        backgroundColor: moodStyles[bubbleMood].fill,
                        clipPath: "polygon(0 0,100% 50%,0 100%)",
                      }}
                    />
                  </motion.div>
                )}
              </AnimatePresence>

              <AnimatePresence>
                {menuOpen && (
                  <motion.div
                    className="absolute right-[calc(100%+16px)] top-1/2 flex -translate-y-1/2 flex-col gap-1 border border-white/25 bg-[#121720]/95 p-1.5 shadow-2xl backdrop-blur"
                    initial={{ opacity: 0, x: 12 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 8 }}
                    transition={{ duration: 0.18 }}
                  >
                    <button
                      aria-label="Close floating assistant"
                      title="Close overlay"
                      className="grid h-11 w-11 place-items-center text-[#ebe8e7] transition hover:bg-[#f14b49] hover:text-white"
                      onClick={() => { setAvatarVisible(false); setMenuOpen(false); setBubbleVisible(false); }}
                    >
                      <X size={21} />
                    </button>
                    <button
                      aria-label="Show settings source"
                      title="Settings"
                      className="grid h-11 w-11 place-items-center text-[#ebe8e7] transition hover:bg-[#f14b49] hover:text-white"
                      onClick={() => { setMenuOpen(false); scrollToSource("android-app/app/src/main/java/com/pixelbuddy/ai/MainActivity.kt"); }}
                    >
                      <Settings2 size={20} />
                    </button>
                    <button
                      aria-label="Open tasks"
                      title="Tasks"
                      className="grid h-11 w-11 place-items-center text-[#ebe8e7] transition hover:bg-[#f14b49] hover:text-white"
                      onClick={() => { setMenuOpen(false); setTasksOpen(true); }}
                    >
                      <ScanEye size={20} />
                    </button>
                  </motion.div>
                )}
              </AnimatePresence>

              <AnimatePresence>
                {tasksOpen && (
                  <motion.div
                    className="absolute right-[calc(100%+16px)] top-[-20px] w-[220px] border border-white/20 bg-[#121720]/95 p-4 shadow-2xl backdrop-blur"
                    initial={{ opacity: 0, x: 12 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 8 }}
                    transition={{ duration: 0.18 }}
                  >
                    <div className="flex items-center justify-between">
                      <span className="mono text-[11px] uppercase tracking-[0.13em] text-white">Screen tools</span>
                      <button aria-label="Close tasks" onClick={() => setTasksOpen(false)} className="text-white/55 hover:text-white">
                        <X size={16} />
                      </button>
                    </div>
                    <p className="mt-3 text-[12px] leading-5 text-white/60">Get a next move for the scene on screen.</p>
                    <button
                      onClick={analyzeDemo}
                      disabled={phase === "analyzing"}
                      className="mt-4 flex w-full items-center justify-center gap-2 bg-[#f14b49] py-2.5 text-xs font-bold text-[#1c1011] transition hover:bg-[#ff7b73] disabled:opacity-60"
                    >
                      <ScanEye size={15} /> Analyze screen
                    </button>
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.div>
          )}
        </AnimatePresence>

        <AnimatePresence>
          {consentOpen && (
            <motion.div
              className="absolute inset-0 z-40 flex items-center justify-center bg-[#060910]/80 px-5 backdrop-blur-sm"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
            >
              <motion.div
                className="w-full max-w-[390px] border border-white/20 bg-[#161b25] p-7 shadow-2xl"
                initial={{ y: 18, scale: 0.96 }}
                animate={{ y: 0, scale: 1 }}
                exit={{ y: 12, scale: 0.98 }}
              >
                <ScanEye size={29} className="text-[#f14b49]" />
                <h3 className="mt-5 text-2xl font-semibold tracking-[-0.05em]">Share this screen?</h3>
                <p className="mt-3 text-sm leading-6 text-white/65">
                  This is a browser simulation. On Android, the system asks for MediaProjection permission before capture begins.
                </p>
                <div className="mt-7 flex gap-3">
                  <button
                    className="flex-1 border border-white/20 px-4 py-3 text-sm hover:bg-white/10"
                    onClick={() => { setConsentOpen(false); showDemoBubble("Screen sharing was not allowed.", "SAD"); }}
                  >
                    Not now
                  </button>
                  <button className="flex-1 bg-[#f14b49] px-4 py-3 text-sm font-bold text-[#1c1011] hover:bg-[#ff7b73]" onClick={allowDemo}>
                    Allow in demo
                  </button>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>
      </section>

      <section className="border-b border-white/10 bg-[#11161f] px-6 py-14 md:px-12 md:py-16">
        <div className="mx-auto flex max-w-[1440px] flex-col gap-7 lg:flex-row lg:items-center lg:justify-between">
          <div className="max-w-[600px]">
            <p className="mono text-[11px] uppercase tracking-[0.18em] text-[#f14b49]">Preview controls</p>
            <h2 className="mt-3 text-3xl font-semibold tracking-[-0.055em] md:text-4xl">A frame, only when you ask.</h2>
            <p className="mt-3 text-sm leading-6 text-white/55">
              This interactive preview uses a sample game image. Live screen capture and Gemini requests run only in the Android app.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2.5">
            {!avatarVisible && (
              <button
                onClick={() => { setAvatarVisible(true); showDemoBubble("Tap me to open screen tools.", "THINKING"); }}
                className="inline-flex h-11 items-center gap-2 border border-white/20 px-4 text-sm hover:bg-white/10"
              >
                <RotateCcw size={16} /> Restore overlay
              </button>
            )}
            <button
              onClick={() => sharing ? analyzeDemo() : setConsentOpen(true)}
              className="inline-flex h-11 items-center gap-2 bg-[#f14b49] px-4 text-sm font-bold text-[#1c1011] hover:bg-[#ff7b73] disabled:opacity-50"
              disabled={phase === "analyzing"}
            >
              {phase === "analyzing" ? <Play size={16} className="animate-pulse" /> : <ScanEye size={16} />}
              {phase === "analyzing" ? "Analyzing..." : sharing ? "Analyze again" : "Try screen analysis"}
            </button>
            <button
              onClick={stopDemo}
              disabled={!sharing}
              className="inline-flex h-11 items-center gap-2 border border-white/20 px-4 text-sm hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-35"
            >
              <Square size={14} /> Stop sharing
            </button>
          </div>
        </div>
      </section>

      <section ref={sourceRef} id="source" className="scroll-mt-6 bg-[#0c1016] px-6 pb-24 pt-20 md:px-12 md:pt-28">
        <div className="mx-auto max-w-[1440px]">
          <div className="mb-9 flex flex-col gap-6 md:flex-row md:items-end md:justify-between">
            <div>
              <p className="mono text-[11px] uppercase tracking-[0.18em] text-[#f14b49]">Native Android / Kotlin</p>
              <h2 className="mt-3 text-[clamp(38px,5vw,68px)] font-semibold leading-[1] tracking-[-0.075em]">The actual source.</h2>
              <p className="mt-4 max-w-[580px] text-sm leading-6 text-white/55">
                These are real files in <span className="mono text-white/80">android-app/</span>, not snippets. Browse, copy, or download the project.
              </p>
            </div>
            <div className="flex items-center gap-2 border-b border-white/15 pb-1">
              <button
                onClick={() => { setShowAll(false); setSearch(""); setSelectedPath(featurePaths[0]); }}
                className={`px-3 py-2 text-sm transition ${!showAll ? "border-b-2 border-[#f14b49] text-white" : "text-white/45 hover:text-white"}`}
              >
                Step 8 files
              </button>
              <button
                onClick={() => { setShowAll(true); setSearch(""); }}
                className={`px-3 py-2 text-sm transition ${showAll ? "border-b-2 border-[#f14b49] text-white" : "text-white/45 hover:text-white"}`}
              >
                Full project
              </button>
            </div>
          </div>

          <div className="grid min-h-[660px] overflow-hidden border border-white/15 bg-[#111721] lg:grid-cols-[300px_minmax(0,1fr)]">
            <aside className="flex min-h-[300px] flex-col border-b border-white/15 lg:max-h-[760px] lg:border-b-0 lg:border-r">
              <div className="flex h-[64px] items-center gap-3 border-b border-white/10 px-5">
                <FolderOpen size={17} className="text-[#f14b49]" />
                <span className="mono text-[11px] uppercase tracking-[0.12em] text-white/70">{showAll ? "Project files" : "Screen awareness"}</span>
                <span className="mono ml-auto text-[11px] text-white/35">{visibleFiles.length}</span>
              </div>
              <label className="mx-4 my-3 flex items-center gap-2 border border-white/10 bg-[#0b1017] px-3 py-2 text-white/40 focus-within:border-[#f14b49]">
                <Search size={15} />
                <input
                  type="search"
                  aria-label="Search source files"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Find a file"
                  className="w-full bg-transparent text-[12px] text-white outline-none placeholder:text-white/35"
                />
              </label>
              <div className="source-scroll max-h-[420px] flex-1 overflow-y-auto px-2 pb-3 lg:max-h-none">
                {visibleFiles.length === 0 && <p className="px-4 py-5 text-sm text-white/45">No matching files.</p>}
                {visibleFiles.map((file) => {
                  const active = file.path === selectedFile?.path;
                  const filename = file.path.split("/").pop() ?? file.path;
                  const folder = file.path.replace(/^android-app\//, "").slice(0, -(filename.length + 1));
                  return (
                    <button
                      key={file.path}
                      onClick={() => setSelectedPath(file.path)}
                      className={`mb-0.5 flex w-full items-start gap-3 border-l-2 px-3 py-3 text-left transition ${
                        active ? "border-[#f14b49] bg-white/[0.07] text-white" : "border-transparent text-white/60 hover:bg-white/[0.04] hover:text-white"
                      }`}
                    >
                      <FileCode2 size={16} className={`mt-0.5 shrink-0 ${active ? "text-[#f14b49]" : "text-white/35"}`} />
                      <span className="min-w-0">
                        <span className="block break-all text-[13px] font-medium leading-5">{filename}</span>
                        <span className="mono mt-0.5 block truncate text-[10px] text-white/35" title={folder}>{folder}</span>
                      </span>
                    </button>
                  );
                })}
              </div>
            </aside>

            <div className="flex min-w-0 flex-col">
              <div className="flex min-h-[64px] flex-wrap items-center justify-between gap-3 border-b border-white/10 px-4 py-3 md:px-6">
                <div className="min-w-0">
                  <p className="truncate text-[13px] font-semibold md:text-sm" title={selectedFile?.path}>{selectedFile?.path.split("/").pop()}</p>
                  <p className="mono mt-1 truncate text-[10px] text-white/35" title={selectedFile?.path}>{selectedFile?.path.replace(/^android-app\//, "")}</p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={copyFile}
                    title="Copy file"
                    aria-label="Copy file"
                    className="flex h-9 items-center gap-2 border border-white/15 px-3 text-xs hover:bg-white/10"
                  >
                    {copied ? <Check size={15} /> : <Copy size={15} />}
                    <span className="hidden sm:inline">{copied ? "Copied" : "Copy"}</span>
                  </button>
                  <button
                    onClick={() => selectedFile && downloadText(selectedFile.path.split("/").pop() ?? "source.txt", selectedFile.content)}
                    title="Download file"
                    aria-label="Download file"
                    className="grid h-9 w-9 place-items-center border border-white/15 hover:bg-white/10"
                  >
                    <Download size={15} />
                  </button>
                </div>
              </div>

              <div className="source-scroll h-[570px] min-w-0 overflow-auto bg-[#0b1119] md:h-[660px]">
                <AnimatePresence mode="wait">
                  <motion.div
                    key={selectedFile?.path}
                    initial={{ opacity: 0, y: 5 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.17 }}
                    className="w-max min-w-full py-5"
                  >
                    {selectedFile?.content.split("\n").map((line, index) => {
                      const trimmed = line.trimStart();
                      const lineColor = trimmed.startsWith("//") || trimmed.startsWith("<!--")
                        ? "text-[#85919f]"
                        : /^(class |data class |object |enum class |fun |override fun |private fun |package |import |<\?xml|<manifest|<service|<activity)/.test(trimmed)
                          ? "text-[#eebf9a]"
                          : "text-[#d3dbe5]";
                      return (
                        <div key={index} className="mono flex min-h-[21px] whitespace-pre pr-7 text-[11px] leading-[21px] md:text-[12px]">
                          <span className="sticky left-0 mr-5 w-12 shrink-0 border-r border-white/[0.06] bg-[#0b1119] pr-3 text-right text-white/25 select-none">{index + 1}</span>
                          <code className={lineColor}>{line || " "}</code>
                        </div>
                      );
                    })}
                  </motion.div>
                </AnimatePresence>
              </div>

              <div className="mono flex min-h-[38px] items-center justify-between gap-3 border-t border-white/10 px-5 text-[10px] uppercase tracking-[0.1em] text-white/35">
                <span>{selectedFile?.language ?? "source"}</span>
                <span>{lineCount} lines</span>
              </div>
            </div>
          </div>

          <div className="mt-7 flex flex-col gap-4 border-t border-white/10 pt-6 text-sm text-white/45 md:flex-row md:items-center md:justify-between">
            <p>Overlay, bubble and screen awareness are implemented here. Steps 4-7 have not been added.</p>
            <div className="flex items-center gap-2 text-white/65"><ShieldCheck size={17} className="text-[#f14b49]" /> Screenshots are not saved to disk.</div>
          </div>
        </div>
      </section>

      <footer className="border-t border-white/10 bg-[#090c11] px-6 py-8 md:px-12">
        <div className="mx-auto flex max-w-[1440px] flex-wrap items-center justify-between gap-4">
          <span className="flex items-center gap-3 text-sm font-bold"><PixelFace size={24} /> PixelBuddy.</span>
          <span className="mono text-[10px] uppercase tracking-[0.16em] text-white/35">Native code / Browser simulation</span>
        </div>
      </footer>

      <AnimatePresence>
        {notice && (
          <motion.div
            role="status"
            className="fixed bottom-6 right-6 z-50 border border-white/20 bg-[#202630] px-5 py-3 text-sm text-white shadow-xl"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 10 }}
          >
            {notice}
          </motion.div>
        )}
      </AnimatePresence>
    </main>
  );
}