(() => {
  "use strict";

  const API_BASE = "/api/jobs";
  const POLL_INTERVAL_MS = 4000;

  const STATUS_META = {
    PENDING: { label: "Chờ xử lý", cls: "neutral", icon: "clock", progress: false },
    SCRIPTING: { label: "Đang sinh kịch bản", cls: "info", icon: "spinner", progress: true, step: "sinh kịch bản" },
    SCRIPT_READY: { label: "Chờ duyệt", cls: "warning", icon: "alert", progress: false },
    SHOT_PLAN_READY: { label: "Duyệt Shot Plan", cls: "warning", icon: "alert", progress: false },
    GENERATING_KEYFRAMES: { label: "Đang sinh keyframe", cls: "progress", icon: "spinner", progress: true, step: "sinh keyframe" },
    KEYFRAMES_REVIEW: { label: "Duyệt keyframe", cls: "warning", icon: "alert", progress: false },
    APPROVED: { label: "Đã duyệt", cls: "info", icon: "check", progress: false },
    GENERATING_AUDIO: { label: "Đang tạo giọng đọc", cls: "progress", icon: "spinner", progress: true, step: "tạo giọng đọc (TTS)" },
    GENERATING_IMAGES: { label: "Đang tạo ảnh AI", cls: "progress", icon: "spinner", progress: true, step: "tạo ảnh minh hoạ" },
    RENDERING: { label: "Đang render video", cls: "progress", icon: "spinner", progress: true, step: "render video (FFmpeg)" },
    UPLOADING: { label: "Đang upload YouTube", cls: "progress", icon: "spinner", progress: true, step: "upload YouTube" },
    COMPLETED: { label: "Hoàn thành", cls: "success", icon: "check", progress: false },
    FAILED: { label: "Thất bại", cls: "danger", icon: "alert", progress: false },
  };

  const ICONS = {
    clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>',
    check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>',
    alert: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>',
    spinner: '<span class="spinner" aria-hidden="true"></span>',
  };

  // ---------- Particle System ----------
  function initParticles() {
    const canvas = document.getElementById("particleCanvas");
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    let particles = [];
    let animFrameId;

    function resize() {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    }

    function createParticle() {
      return {
        x: Math.random() * canvas.width,
        y: Math.random() * canvas.height,
        vx: (Math.random() - 0.5) * 0.3,
        vy: (Math.random() - 0.5) * 0.3,
        size: Math.random() * 2 + 0.5,
        opacity: Math.random() * 0.5 + 0.1,
        hue: Math.random() > 0.7 ? 35 : (Math.random() > 0.5 ? 190 : 270), // gold, cyan, purple
        pulse: Math.random() * Math.PI * 2,
        pulseSpeed: Math.random() * 0.02 + 0.005,
      };
    }

    function init() {
      resize();
      const count = Math.min(Math.floor((canvas.width * canvas.height) / 18000), 80);
      particles = Array.from({ length: count }, createParticle);
    }

    function draw() {
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      for (const p of particles) {
        p.x += p.vx;
        p.y += p.vy;
        p.pulse += p.pulseSpeed;

        // Wrap around
        if (p.x < -10) p.x = canvas.width + 10;
        if (p.x > canvas.width + 10) p.x = -10;
        if (p.y < -10) p.y = canvas.height + 10;
        if (p.y > canvas.height + 10) p.y = -10;

        const pulseOpacity = p.opacity * (0.6 + 0.4 * Math.sin(p.pulse));

        // Glow
        ctx.beginPath();
        const gradient = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.size * 4);
        gradient.addColorStop(0, `hsla(${p.hue}, 80%, 70%, ${pulseOpacity * 0.4})`);
        gradient.addColorStop(1, `hsla(${p.hue}, 80%, 70%, 0)`);
        ctx.fillStyle = gradient;
        ctx.arc(p.x, p.y, p.size * 4, 0, Math.PI * 2);
        ctx.fill();

        // Core
        ctx.beginPath();
        ctx.fillStyle = `hsla(${p.hue}, 80%, 80%, ${pulseOpacity})`;
        ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
        ctx.fill();
      }

      // Draw subtle connection lines between nearby particles
      for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
          const dx = particles[i].x - particles[j].x;
          const dy = particles[i].y - particles[j].y;
          const dist = Math.sqrt(dx * dx + dy * dy);
          if (dist < 120) {
            ctx.beginPath();
            ctx.strokeStyle = `rgba(255, 183, 77, ${0.03 * (1 - dist / 120)})`;
            ctx.lineWidth = 0.5;
            ctx.moveTo(particles[i].x, particles[i].y);
            ctx.lineTo(particles[j].x, particles[j].y);
            ctx.stroke();
          }
        }
      }

      animFrameId = requestAnimationFrame(draw);
    }

    // Check for reduced motion preference
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (!prefersReducedMotion) {
      init();
      draw();
      window.addEventListener("resize", () => {
        resize();
        // Recreate particles on resize
        const count = Math.min(Math.floor((canvas.width * canvas.height) / 18000), 80);
        while (particles.length < count) particles.push(createParticle());
        while (particles.length > count) particles.pop();
      });
    }
  }

  // ---------- State ----------
  let jobs = [];
  let characters = [];
  let jobsSignature = "";
  let selectedJobId = null;
  let scriptDirty = false;
  let pollTimer = null;
  let pendingAction = false;
  let currentPage = 0;
  const PAGE_SIZE = 10;
  let totalPages = 1;
  let totalElements = 0;

  // ---------- DOM refs ----------
  const jobListEl = document.getElementById("jobList");
  const jobCountEl = document.getElementById("jobCount");
  const jobPaginationEl = document.getElementById("jobPagination");
  const emptyStateEl = document.getElementById("emptyState");
  const createForm = document.getElementById("createJobForm");
  const topicInput = document.getElementById("topicInput");
  const createJobBtn = document.getElementById("createJobBtn");
  const createJobError = document.getElementById("createJobError");
  const advancedToggle = document.getElementById("advancedToggle");
  const advancedOptions = document.getElementById("advancedOptions");
  const sourceContentInput = document.getElementById("sourceContentInput");
  const creationModeInput = document.getElementById("creationModeInput");
  const directorPromptField = document.getElementById("directorPromptField");
  const directorPromptInput = document.getElementById("directorPromptInput");
  const initialScriptInput = document.getElementById("initialScriptInput");
  const durationInput = document.getElementById("durationInput");
  const languageInput = document.getElementById("languageInput");
  const voiceInput = document.getElementById("voiceInput");
  const speechRateInput = document.getElementById("speechRateInput");
  const subtitlesInput = document.getElementById("subtitlesInput");
  const aspectRatioInput = document.getElementById("aspectRatioInput");
  const sceneMotionInput = document.getElementById("sceneMotionInput");
  const initialImagesInput = document.getElementById("initialImagesInput");
  const initialImagePreview = document.getElementById("initialImagePreview");
  const imageAgentInput = document.getElementById("imageAgentInput");
  const characterInput = document.getElementById("characterInput");
  const supportingCastList = document.getElementById("supportingCastList");
  const addSupportingCharacterBtn = document.getElementById("addSupportingCharacterBtn");
  const imageCountInput = document.getElementById("imageCountInput");
  const imageStyleInput = document.getElementById("imageStyleInput");
  const imageMoodInput = document.getElementById("imageMoodInput");
  const musicInput = document.getElementById("musicInput");
  const musicVolumeInput = document.getElementById("musicVolumeInput");
  const connectionStatus = document.getElementById("connectionStatus");

  const drawer = document.getElementById("drawer");
  const drawerBackdrop = document.getElementById("drawerBackdrop");
  const drawerClose = document.getElementById("drawerClose");
  const drawerJobId = document.getElementById("drawerJobId");
  const drawerTitle = document.getElementById("drawerTitle");
  const drawerTimestamp = document.getElementById("drawerTimestamp");
  const drawerConfigSummary = document.getElementById("drawerConfigSummary");
  const drawerErrorBox = document.getElementById("drawerErrorBox");
  const drawerErrorText = document.getElementById("drawerErrorText");
  const drawerCompletedBox = document.getElementById("drawerCompletedBox");
  const drawerYoutubeId = document.getElementById("drawerYoutubeId");
  const drawerVideoPlayer = document.getElementById("drawerVideoPlayer");
  const cinemaFrame = document.getElementById("cinemaFrame");
  const drawerProgressBox = document.getElementById("drawerProgressBox");
  const drawerProgressStep = document.getElementById("drawerProgressStep");
  const scriptTextarea = document.getElementById("scriptTextarea");
  const scriptHint = document.getElementById("scriptHint");
  const drawerActions = document.getElementById("drawerActions");
  const toastRegion = document.getElementById("toastRegion");
  const backgroundImageInput = document.getElementById("backgroundImageInput");
  const uploadBackgroundBtn = document.getElementById("uploadBackgroundBtn");
  const backgroundPreviewGrid = document.getElementById("backgroundPreviewGrid");
  const shotPlanSection = document.getElementById("shotPlanSection");
  const shotPlanGrid = document.getElementById("shotPlanGrid");
  const shotApprovalSummary = document.getElementById("shotApprovalSummary");
  const shotPlanActions = document.getElementById("shotPlanActions");

  const reproduceOptionsSection = document.getElementById("reproduceOptionsSection");
  const reproLanguageInput = document.getElementById("reproLanguageInput");
  const reproVoiceInput = document.getElementById("reproVoiceInput");
  const reproSpeechRateInput = document.getElementById("reproSpeechRateInput");
  const reproSubtitlesInput = document.getElementById("reproSubtitlesInput");
  const reproAspectRatioInput = document.getElementById("reproAspectRatioInput");
  const reproSceneMotionInput = document.getElementById("reproSceneMotionInput");
  const reproImageAgentInput = document.getElementById("reproImageAgentInput");
  const reproCharacterInput = document.getElementById("reproCharacterInput");
  const reproImageCountInput = document.getElementById("reproImageCountInput");
  const reproImageStyleInput = document.getElementById("reproImageStyleInput");
  const reproImageMoodInput = document.getElementById("reproImageMoodInput");
  const reproMusicVolumeInput = document.getElementById("reproMusicVolumeInput");
  let reproInitializedJobId = null;

  const VOICES = {
    vi: [["vi-VN-HoaiMyNeural", "Hoài My · Nữ"], ["vi-VN-NamMinhNeural", "Nam Minh · Nam"]],
    en: [["en-US-JennyNeural", "Jenny · Female"], ["en-US-GuyNeural", "Guy · Male"]],
    ja: [["ja-JP-NanamiNeural", "Nanami · Nữ"], ["ja-JP-KeitaNeural", "Keita · Nam"]],
    ko: [["ko-KR-SunHiNeural", "Sun Hi · Nữ"], ["ko-KR-InJoonNeural", "In Joon · Nam"]],
    "zh-CN": [["zh-CN-XiaoxiaoNeural", "Xiaoxiao · Nữ"], ["zh-CN-YunxiNeural", "Yunxi · Nam"]],
  };

  // Chất liệu (media) và thể loại/tâm trạng (mood) là 2 chiều độc lập, ghép lại
  // thành 1 chuỗi prompt duy nhất khi gửi lên - cho phép kết hợp bất kỳ (VD:
  // "Chân thực" + "Kinh dị", hoặc "Minh hoạ 2D" + "Kinh dị").
  const MEDIA_STYLES = ["cinematic", "photorealistic", "anime sakuga animatic", "anime", "3D animation", "2D illustration", "documentary"];
  const MOOD_STYLES = [
    "horror, dark eerie atmosphere, dramatic low-key lighting, deep shadows, unsettling grainy mood",
    "comedic, bright vibrant colors, playful exaggerated cartoon expressions, lighthearted whimsical mood",
    "epic fantasy, majestic dramatic cinematic lighting, grand scale, richly detailed world",
    "romantic, warm soft lighting, dreamy pastel tones, tender emotional mood",
    "film noir mystery/thriller, moody low-key lighting, deep shadows, suspenseful tense atmosphere",
  ];

  function composeImageStyle(mediaEl, moodEl) {
    return [mediaEl.value, moodEl.value].filter(Boolean).join(", ");
  }

  function parseImageStyle(raw) {
    const value = raw || "cinematic";
    for (const media of MEDIA_STYLES) {
      if (value === media) return { media, mood: "" };
      if (value.startsWith(media + ", ")) {
        const rest = value.slice(media.length + 2);
        return { media, mood: MOOD_STYLES.includes(rest) ? rest : "" };
      }
    }
    return { media: "cinematic", mood: MOOD_STYLES.includes(value) ? value : "" };
  }

  // ---------- Character library ----------
  async function loadCharacters() {
    try {
      const res = await fetch("/api/characters");
      characters = await res.json();
      const options = `<option value="">Không dùng nhân vật</option>` +
        characters.map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join("");
      const prevCreate = characterInput.value;
      const prevRepro = reproCharacterInput.value;
      characterInput.innerHTML = options;
      reproCharacterInput.innerHTML = options;
      const optionalCast = `<option value="">AI tự tạo theo prompt</option>` +
        characters.map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join("");
      supportingCastList.querySelectorAll("select").forEach((select) => fillSupportingCharacterSelect(select));
      if (characters.some((c) => String(c.id) === prevCreate)) characterInput.value = prevCreate;
      if (characters.some((c) => String(c.id) === prevRepro)) reproCharacterInput.value = prevRepro;
    } catch (err) {
      // thư viện nhân vật không tải được không nên chặn phần còn lại của dashboard
    }
  }

  function refreshVoiceOptions() {
    voiceInput.innerHTML = VOICES[languageInput.value].map(([value, label]) =>
      `<option value="${value}">${label}</option>`).join("");
  }
  languageInput.addEventListener("change", refreshVoiceOptions);

  function refreshReproVoiceOptions() {
    reproVoiceInput.innerHTML = VOICES[reproLanguageInput.value].map(([value, label]) =>
      `<option value="${value}">${label}</option>`).join("");
  }
  reproLanguageInput.addEventListener("change", refreshReproVoiceOptions);

  function refreshCreationMode() {
    const storyboard = creationModeInput.value === "storyboard_animatic";
    directorPromptField.hidden = !storyboard;
    document.querySelectorAll(".storyboard-cast-field").forEach((field) => { field.hidden = !storyboard; });
    if (storyboard) {
      imageAgentInput.value = "mcp";
      if (!Number(imageCountInput.value)) imageCountInput.value = "12";
      imageStyleInput.value = "anime sakuga animatic";
      sceneMotionInput.value = "anime_sakuga";
    }
  }
  creationModeInput.addEventListener("change", refreshCreationMode);

  function fillSupportingCharacterSelect(select) {
    const current = select.value;
    select.innerHTML = `<option value="">Chọn từ thư viện</option>` +
      characters.map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join("");
    if ([...select.options].some((option) => option.value === current)) select.value = current;
  }

  function addSupportingCharacter() {
    const row = document.createElement("div");
    row.className = "supporting-cast-row";
    row.innerHTML = `<select aria-label="Nhân vật phụ"></select><button type="button" class="btn btn--secondary">Xoá</button>`;
    fillSupportingCharacterSelect(row.querySelector("select"));
    row.querySelector("button").addEventListener("click", () => row.remove());
    supportingCastList.append(row);
  }
  addSupportingCharacterBtn.addEventListener("click", addSupportingCharacter);

  imageStyleInput.addEventListener("change", () => {
    if (imageStyleInput.value === "anime sakuga animatic") {
      sceneMotionInput.value = "anime_sakuga";
    }
  });

  reproImageStyleInput.addEventListener("change", () => {
    if (reproImageStyleInput.value === "anime sakuga animatic") {
      reproSceneMotionInput.value = "anime_sakuga";
    }
  });

  function populateReproOptions(job) {
    reproLanguageInput.value = VOICES[job.language] ? job.language : "vi";
    refreshReproVoiceOptions();
    if (job.voice && [...reproVoiceInput.options].some((o) => o.value === job.voice)) {
      reproVoiceInput.value = job.voice;
    }
    reproSpeechRateInput.value = String(job.speechRatePercent ?? 0);
    reproSubtitlesInput.checked = job.subtitlesEnabled !== false;
    reproAspectRatioInput.value = job.aspectRatio || "16:9";
    reproSceneMotionInput.value = [...reproSceneMotionInput.options].some((option) => option.value === job.sceneMotion)
      ? job.sceneMotion : "none";
    reproImageAgentInput.value = job.imageAgent || "mcp";
    reproCharacterInput.value = job.characterId ? String(job.characterId) : "";
    reproImageCountInput.value = job.imageCount || 6;
    const parsedStyle = parseImageStyle(job.imageStyle);
    reproImageStyleInput.value = parsedStyle.media;
    reproImageMoodInput.value = parsedStyle.mood;
    reproMusicVolumeInput.value = String(job.musicVolumePercent ?? 18);
  }

  // ---------- Utils ----------
  function badgeHtml(status) {
    const meta = STATUS_META[status] || STATUS_META.PENDING;
    const iconHtml = meta.icon === "spinner" ? ICONS.spinner : ICONS[meta.icon];
    return `<span class="badge badge--${meta.cls}">${iconHtml}${meta.label}</span>`;
  }

  function relativeTime(isoString) {
    const then = new Date(isoString).getTime();
    const diffSec = Math.round((Date.now() - then) / 1000);
    if (diffSec < 10) return "vừa xong";
    if (diffSec < 60) return `${diffSec} giây trước`;
    const diffMin = Math.round(diffSec / 60);
    if (diffMin < 60) return `${diffMin} phút trước`;
    const diffHour = Math.round(diffMin / 60);
    if (diffHour < 24) return `${diffHour} giờ trước`;
    const diffDay = Math.round(diffHour / 24);
    return `${diffDay} ngày trước`;
  }

  function escapeHtml(str) {
    const div = document.createElement("div");
    div.textContent = str ?? "";
    return div.innerHTML;
  }

  function showToast(message, type = "default") {
    const toast = document.createElement("div");
    toast.className = `toast${type !== "default" ? ` toast--${type}` : ""}`;
    toast.textContent = message;
    toastRegion.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = "0";
      toast.style.transform = "translateY(10px)";
      toast.style.transition = "opacity 300ms ease, transform 300ms ease";
      setTimeout(() => toast.remove(), 300);
    }, 3500);
  }

  async function apiFetch(path, options) {
    const isMultipart = options && options.body instanceof FormData;
    const res = await fetch(API_BASE + path, {
      ...options,
      headers: isMultipart ? undefined : { "Content-Type": "application/json", ...(options && options.headers) },
    });
    let body = null;
    try { body = await res.json(); } catch (_) { /* no body */ }
    if (!res.ok) {
      const message = (body && body.message) || `Lỗi HTTP ${res.status}`;
      throw new Error(message);
    }
    return body;
  }

  // ---------- Rendering ----------
  function renderJobList() {
    jobCountEl.textContent = String(totalElements);
    emptyStateEl.hidden = jobs.length > 0;
    jobListEl.innerHTML = jobs
      .map((job, index) => {
        return `
        <li>
          <button type="button" class="job-card" data-job-id="${job.id}" style="animation-delay: ${index * 60}ms">
            <div class="job-card__main">
              <p class="job-card__topic">${escapeHtml(job.topic)}</p>
              <div class="job-card__meta">
                <span>#${job.id}</span>
                <span>·</span>
                <span>${relativeTime(job.updatedAt)}</span>
              </div>
            </div>
            <div class="job-card__side">
              ${badgeHtml(job.status)}
            </div>
          </button>
        </li>`;
      })
      .join("");

    jobListEl.querySelectorAll(".job-card").forEach((card) => {
      card.addEventListener("click", () => openDrawer(Number(card.dataset.jobId)));
    });

    renderPagination();
  }

  function renderPagination() {
    if (!jobPaginationEl) return;
    if (totalPages <= 1) {
      jobPaginationEl.innerHTML = "";
      jobPaginationEl.hidden = true;
      return;
    }
    jobPaginationEl.hidden = false;
    jobPaginationEl.innerHTML = `
      <button type="button" class="btn-icon" id="pagePrevBtn" ${currentPage <= 0 ? "disabled" : ""} aria-label="Trang trước">‹</button>
      <span class="pagination__label">Trang ${currentPage + 1} / ${totalPages}</span>
      <button type="button" class="btn-icon" id="pageNextBtn" ${currentPage >= totalPages - 1 ? "disabled" : ""} aria-label="Trang sau">›</button>
    `;
    const prevBtn = document.getElementById("pagePrevBtn");
    const nextBtn = document.getElementById("pageNextBtn");
    if (prevBtn) prevBtn.addEventListener("click", () => goToPage(currentPage - 1));
    if (nextBtn) nextBtn.addEventListener("click", () => goToPage(currentPage + 1));
  }

  function goToPage(page) {
    if (page < 0 || page >= totalPages || page === currentPage) return;
    currentPage = page;
    fetchJobs(true);
  }

  function refreshDrawerFrame(job) {
    const meta = STATUS_META[job.status] || STATUS_META.PENDING;

    drawerJobId.textContent = job.id;
    drawerTitle.textContent = job.topic;
    drawerTimestamp.textContent = `Cập nhật ${relativeTime(job.updatedAt)}`;
    const languageLabels = { vi: "Tiếng Việt", en: "English", ja: "日本語", ko: "한국어", "zh-CN": "中文" };
    const character = characters.find((c) => c.id === job.characterId);
    drawerConfigSummary.innerHTML = [
      languageLabels[job.language] || "Tiếng Việt",
      `${job.speechRatePercent >= 0 ? "+" : ""}${job.speechRatePercent || 0}% tốc độ`,
      job.subtitlesEnabled === false ? "Không phụ đề" : "Có phụ đề",
      job.aspectRatio || "16:9",
      ({ kenburns: "Ken Burns", anime_sakuga: "Anime Sakuga", anime_tracking: "Anime Tracking", anime_impact: "Anime Impact" })[job.sceneMotion] || "Cảnh tĩnh",
      ...(character ? [`🎭 ${character.name}`] : []),
    ].map((item) => `<span>${escapeHtml(item)}</span>`).join("");

    const badgeContainer = drawer.querySelector(".drawer__meta");
    const oldBadge = badgeContainer.querySelector(".badge");
    if (oldBadge) oldBadge.outerHTML = badgeHtml(job.status);

    drawerErrorBox.hidden = job.status !== "FAILED";
    if (job.status === "FAILED") drawerErrorText.textContent = job.errorMessage || "Không rõ lỗi";

    drawerCompletedBox.hidden = job.status !== "COMPLETED";
    if (job.status === "COMPLETED") {
      drawerYoutubeId.textContent = job.youtubeVideoId
        ? `YouTube ID: ${job.youtubeVideoId}`
        : "(chưa có video ID — đang dùng stub upload)";
      const videoSrc = `/media/job-${job.id}-video.mp4`;
      if (drawerVideoPlayer.dataset.jobId !== String(job.id)) {
        drawerVideoPlayer.src = videoSrc;
        drawerVideoPlayer.dataset.jobId = String(job.id);
      }
      cinemaFrame.hidden = false;
      drawerVideoPlayer.hidden = false;
      reproduceOptionsSection.hidden = false;
      if (reproInitializedJobId !== job.id) {
        populateReproOptions(job);
        reproInitializedJobId = job.id;
      }
    } else {
      cinemaFrame.hidden = true;
      drawerVideoPlayer.hidden = true;
      drawerVideoPlayer.removeAttribute("src");
      drawerVideoPlayer.dataset.jobId = "";
      reproduceOptionsSection.hidden = true;
    }

    drawerProgressBox.hidden = !meta.progress;
    if (meta.progress) {
      drawerProgressStep.textContent = meta.step;
    }

    const storyboardJob = job.creationMode === "storyboard_animatic";
    shotPlanSection.hidden = !storyboardJob || !["SHOT_PLAN_READY", "GENERATING_KEYFRAMES", "KEYFRAMES_REVIEW"].includes(job.status);
    if (!shotPlanSection.hidden) loadShotPlan(job);

    if (!scriptDirty) {
      scriptTextarea.value = job.scriptContent || "";
    }
    const editable = job.status === "SCRIPT_READY";
    scriptTextarea.readOnly = !editable;
    scriptHint.textContent = editable
      ? "Bạn có thể sửa kịch bản trước khi duyệt."
      : job.scriptContent
        ? "Kịch bản chỉ chỉnh sửa được khi job đang ở trạng thái Chờ duyệt."
        : "Kịch bản sẽ xuất hiện sau khi bước sinh kịch bản hoàn tất.";

    renderDrawerActions(job, editable);
  }

  function renderDrawerActions(job, editable) {
    drawerActions.innerHTML = "";

    if (editable) {
      const saveBtn = makeButton("secondary", ICONS.check, "Lưu kịch bản", () => saveScript(job.id));
      const approveBtn = makeButton("primary", ICONS.check, "Duyệt & sản xuất", () => approveJob(job.id));
      drawerActions.append(saveBtn, approveBtn);
    } else if (job.status === "FAILED") {
      const retryBtn = makeButton("danger", ICONS.clock, "Thử lại", () => retryJob(job.id));
      drawerActions.append(retryBtn);
    } else if (job.status === "COMPLETED") {
      const reproduceBtn = makeButton("secondary", ICONS.clock, "Sản xuất lại", () => reproduceJob(job.id));
      drawerActions.append(reproduceBtn);
    }
  }

  function makeButton(variant, iconHtml, label, onClick) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = `btn btn--${variant} btn--3d`;
    btn.innerHTML = `${iconHtml}<span>${label}</span>`;
    btn.addEventListener("click", onClick);
    return btn;
  }

  // ---------- Drawer open/close ----------
  function openDrawer(jobId) {
    selectedJobId = jobId;
    scriptDirty = false;
    backgroundImageInput.value = "";
    backgroundPreviewGrid.innerHTML = "";
    const job = jobs.find((j) => j.id === jobId);
    if (!job) return;
    refreshDrawerFrame(job);
    drawer.hidden = false;
    drawerBackdrop.hidden = false;
    document.body.style.overflow = "hidden";
  }

  function closeDrawer() {
    selectedJobId = null;
    scriptDirty = false;
    reproInitializedJobId = null;
    drawer.hidden = true;
    drawerBackdrop.hidden = true;
    document.body.style.overflow = "";
    drawerVideoPlayer.pause();
    drawerVideoPlayer.removeAttribute("src");
    drawerVideoPlayer.dataset.jobId = "";
  }

  drawerClose.addEventListener("click", closeDrawer);
  drawerBackdrop.addEventListener("click", closeDrawer);
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !drawer.hidden) closeDrawer();
  });
  scriptTextarea.addEventListener("input", () => { scriptDirty = true; });

  // ---------- Actions ----------
  async function createJob() {
    createJobError.hidden = true;
    createJobBtn.disabled = true;
    try {
      const hadScript = Boolean(initialScriptInput.value.trim());
      const formData = new FormData();
      formData.append("topic", topicInput.value.trim());
      formData.append("sourceContent", sourceContentInput.value.trim());
      formData.append("creationMode", creationModeInput.value);
      formData.append("directorPrompt", directorPromptInput.value.trim());
      formData.append("scriptContent", initialScriptInput.value.trim());
      if (durationInput.value) formData.append("targetDurationSeconds", durationInput.value);
      formData.append("voice", voiceInput.value);
      formData.append("language", languageInput.value);
      formData.append("speechRatePercent", speechRateInput.value);
      formData.append("subtitlesEnabled", String(subtitlesInput.checked));
      formData.append("aspectRatio", aspectRatioInput.value);
      formData.append("sceneMotion", sceneMotionInput.value);
      formData.append("imageAgent", imageAgentInput.value);
      if (characterInput.value) formData.append("characterId", characterInput.value);
      supportingCastList.querySelectorAll("select").forEach((select) => {
        if (select.value) formData.append("castCharacterIds", select.value);
      });
      formData.append("imageCount", imageCountInput.value);
      formData.append("imageStyle", composeImageStyle(imageStyleInput, imageMoodInput));
      formData.append("musicVolumePercent", musicVolumeInput.value);
      Array.from(initialImagesInput.files).forEach((file) => formData.append("files", file));
      if (musicInput.files[0]) formData.append("musicFile", musicInput.files[0]);
      const characterWasChosen = Boolean(characterInput.value);
      const created = await apiFetch("", { method: "POST", headers: {}, body: formData });
      createForm.reset();
      imageAgentInput.value = "mcp";
      refreshCreationMode();
      refreshVoiceOptions();
      initialImagePreview.innerHTML = "";
      showToast(hadScript ? "Đã tạo job, kịch bản sẵn sàng duyệt" : "Đã tạo job, đang sinh kịch bản…", "success");
      if (!characterWasChosen && created && created.characterId) {
        const detected = characters.find((c) => c.id === created.characterId);
        if (detected) showToast(`🎭 Tự nhận diện nhân vật "${detected.name}" từ nội dung`, "success");
      }
      currentPage = 0;
      await fetchJobs(true);
    } catch (err) {
      createJobError.textContent = err.message;
      createJobError.hidden = false;
    } finally {
      createJobBtn.disabled = false;
    }
  }

  advancedToggle.addEventListener("click", () => {
    const opening = advancedOptions.hidden;
    advancedOptions.hidden = !opening;
    advancedToggle.setAttribute("aria-expanded", String(opening));
  });

  initialImagesInput.addEventListener("change", () => {
    const files = Array.from(initialImagesInput.files);
    initialImagePreview.innerHTML = files.map((file, i) =>
      `<div><img src="${URL.createObjectURL(file)}" alt="Ảnh ${i + 1}"><span>${i + 1}</span></div>`).join("");
    if (files.length) imageAgentInput.value = "none";
  });

  async function saveScript(jobId) {
    if (pendingAction) return;
    pendingAction = true;
    try {
      await apiFetch(`/${jobId}/script`, {
        method: "PUT",
        body: JSON.stringify({ scriptContent: scriptTextarea.value }),
      });
      scriptDirty = false;
      showToast("Đã lưu kịch bản", "success");
      await fetchJobs(true);
    } catch (err) {
      showToast(err.message, "error");
    } finally {
      pendingAction = false;
    }
  }

  async function approveJob(jobId) {
    if (pendingAction) return;
    pendingAction = true;
    try {
      await apiFetch(`/${jobId}/approve`, { method: "POST" });
      showToast("Đã duyệt, đang sản xuất video…", "success");
      await fetchJobs(true);
    } catch (err) {
      showToast(err.message, "error");
    } finally {
      pendingAction = false;
    }
  }

  async function retryJob(jobId) {
    if (pendingAction) return;
    pendingAction = true;
    try {
      await apiFetch(`/${jobId}/retry`, { method: "POST" });
      showToast("Đang chạy lại job…", "success");
      await fetchJobs(true);
    } catch (err) {
      showToast(err.message, "error");
    } finally {
      pendingAction = false;
    }
  }

  async function reproduceJob(jobId) {
    if (pendingAction) return;
    pendingAction = true;
    try {
      const body = {
        language: reproLanguageInput.value,
        voice: reproVoiceInput.value,
        speechRatePercent: Number(reproSpeechRateInput.value),
        subtitlesEnabled: reproSubtitlesInput.checked,
        aspectRatio: reproAspectRatioInput.value,
        sceneMotion: reproSceneMotionInput.value,
        imageAgent: reproImageAgentInput.value,
        characterId: reproCharacterInput.value ? Number(reproCharacterInput.value) : null,
        imageCount: Number(reproImageCountInput.value),
        imageStyle: composeImageStyle(reproImageStyleInput, reproImageMoodInput),
        musicVolumePercent: Number(reproMusicVolumeInput.value),
      };
      await apiFetch(`/${jobId}/reproduce`, { method: "POST", body: JSON.stringify(body) });
      showToast("Đang sản xuất lại video với cấu hình mới…", "success");
      await fetchJobs(true);
    } catch (err) {
      showToast(err.message, "error");
    } finally {
      pendingAction = false;
    }
  }

  async function uploadImages(jobId, files) {
    if (pendingAction) return;
    pendingAction = true;
    uploadBackgroundBtn.disabled = true;
    try {
      const formData = new FormData();
      files.forEach((file) => formData.append("files", file));
      await apiFetch(`/${jobId}/images`, {
        method: "POST",
        headers: {}, // để fetch tự set Content-Type multipart kèm boundary
        body: formData,
      });
      showToast(`Đã lưu ${files.length} ảnh, sẽ dùng ở lần render tiếp theo`, "success");
    } catch (err) {
      showToast(err.message, "error");
    } finally {
      pendingAction = false;
      uploadBackgroundBtn.disabled = false;
    }
  }

  async function loadShotPlan(job) {
    try {
      const shots = await apiFetch(`/${job.id}/shots`, { method: "GET" });
      const approved = shots.filter((shot) => shot.approved).length;
      shotApprovalSummary.textContent = `${approved}/${shots.length} keyframe đã duyệt`;
      shotPlanGrid.innerHTML = shots.map((shot) => `
        <article class="shot-card" data-shot="${shot.shotNumber}">
          <div class="shot-card__preview">${shot.imageUrl
            ? `<img src="${shot.imageUrl}?v=${encodeURIComponent(job.updatedAt)}" alt="${shot.title}">`
            : `<span>${shot.title}</span>`}</div>
          <div class="shot-card__body">
            <strong>${shot.title} · ${escapeHtml(shot.camera || "")}</strong>
            <textarea class="shot-narration" rows="2">${escapeHtml(shot.narration || "")}</textarea>
            <textarea class="shot-prompt" rows="4">${escapeHtml(shot.visualPrompt || "")}</textarea>
            <div class="shot-card__actions">
              <button type="button" class="btn btn--secondary shot-save">Lưu cảnh</button>
              ${job.status === "KEYFRAMES_REVIEW" ? `<button type="button" class="btn btn--secondary shot-regenerate-seed">Tạo lại · seed mới</button>
              <button type="button" class="btn btn--secondary shot-regenerate-prompt">Lưu prompt & tạo lại</button>` : ""}
              <label class="btn btn--secondary">Thay ảnh<input class="shot-upload" type="file" accept="image/png,image/jpeg,image/webp" hidden></label>
              ${shot.imageUrl ? `<button type="button" class="btn ${shot.approved ? "btn--primary" : "btn--secondary"} shot-approve">${shot.approved ? "Đã duyệt" : "Duyệt ảnh"}</button>` : ""}
            </div>
          </div>
        </article>`).join("");
      bindShotActions(job, shots);
      shotPlanActions.innerHTML = job.status === "SHOT_PLAN_READY"
        ? `<button type="button" class="btn btn--primary" id="generateKeyframesBtn">Duyệt Shot Plan & sinh ${shots.length} ảnh</button>`
        : job.status === "KEYFRAMES_REVIEW"
          ? `<button type="button" class="btn btn--primary" id="renderApprovedShotsBtn" ${approved !== shots.length ? "disabled" : ""}>Render ${shots.length} cảnh đã duyệt</button>` : "";
      document.getElementById("generateKeyframesBtn")?.addEventListener("click", () => approveShotPlan(job.id));
      document.getElementById("renderApprovedShotsBtn")?.addEventListener("click", () => renderApprovedShots(job.id));
    } catch (error) {
      shotApprovalSummary.textContent = error.message;
    }
  }

  function bindShotActions(job, shots) {
    shotPlanGrid.querySelectorAll(".shot-card").forEach((card) => {
      const number = Number(card.dataset.shot);
      const shot = shots.find((item) => item.shotNumber === number);
      card.querySelector(".shot-save")?.addEventListener("click", async () => {
        await apiFetch(`/${job.id}/shots/${number}`, { method: "PUT", body: JSON.stringify({
          narration: card.querySelector(".shot-narration").value,
          visualPrompt: card.querySelector(".shot-prompt").value,
          camera: shot.camera,
          durationSeconds: shot.durationSeconds,
        }) });
        showToast(`Đã lưu P${String(number).padStart(2, "0")}`, "success");
      });
      card.querySelector(".shot-approve")?.addEventListener("click", async () => {
        await apiFetch(`/${job.id}/shots/${number}/approval?approved=${!shot.approved}`, { method: "POST" });
        await loadShotPlan(job);
      });
      const regenerate = async (newSeed) => {
        const buttons = card.querySelectorAll("button"); buttons.forEach((button) => { button.disabled = true; });
        try {
          await apiFetch(`/${job.id}/shots/${number}/regenerate?newSeed=${newSeed}`, {
            method: "POST", body: JSON.stringify({
              narration: card.querySelector(".shot-narration").value,
              visualPrompt: card.querySelector(".shot-prompt").value,
              camera: shot.camera,
              durationSeconds: shot.durationSeconds,
            })
          });
          showToast(`Đã tạo lại P${String(number).padStart(2, "0")}`, "success");
          await loadShotPlan(job);
        } catch (error) { showToast(error.message, "error"); }
        finally { buttons.forEach((button) => { button.disabled = false; }); }
      };
      card.querySelector(".shot-regenerate-seed")?.addEventListener("click", () => regenerate(true));
      card.querySelector(".shot-regenerate-prompt")?.addEventListener("click", () => regenerate(false));
      card.querySelector(".shot-upload")?.addEventListener("change", async (event) => {
        const file = event.target.files[0]; if (!file) return;
        const data = new FormData(); data.append("file", file);
        await apiFetch(`/${job.id}/shots/${number}/image`, { method: "POST", headers: {}, body: data });
        await loadShotPlan(job);
      });
    });
  }

  async function approveShotPlan(jobId) {
    await apiFetch(`/${jobId}/shots/approve-plan`, { method: "POST" });
    showToast("Đang sinh các keyframe…", "success");
    await fetchJobs(true);
  }

  async function renderApprovedShots(jobId) {
    await apiFetch(`/${jobId}/shots/render`, { method: "POST" });
    showToast("Đang tạo lời đọc và render animatic…", "success");
    await fetchJobs(true);
  }

  backgroundImageInput.addEventListener("change", () => {
    const files = Array.from(backgroundImageInput.files);
    backgroundPreviewGrid.innerHTML = files
      .map(
        (file, i) => `
        <div class="bg-upload__thumb">
          <img src="${URL.createObjectURL(file)}" alt="Ảnh ${i + 1}" />
          <span class="bg-upload__thumb-index">${i + 1}</span>
        </div>`
      )
      .join("");
  });

  uploadBackgroundBtn.addEventListener("click", () => {
    const files = Array.from(backgroundImageInput.files);
    if (files.length === 0 || selectedJobId == null) {
      showToast("Chọn ít nhất 1 ảnh trước đã", "error");
      return;
    }
    uploadImages(selectedJobId, files);
  });

  createForm.addEventListener("submit", (e) => {
    e.preventDefault();
    const hasInput = topicInput.value.trim() || sourceContentInput.value.trim() || directorPromptInput.value.trim() ||
      initialScriptInput.value.trim() || initialImagesInput.files.length;
    if (!hasInput) {
      createJobError.textContent = "Nhập chủ đề, nội dung, kịch bản hoặc chọn ít nhất một ảnh";
      createJobError.hidden = false;
      topicInput.focus();
      return;
    }
    createJobError.hidden = true;
    createJob();
  });

  // ---------- Polling ----------
  async function fetchJobs(force = false) {
    try {
      const data = await apiFetch(`?page=${currentPage}&size=${PAGE_SIZE}`, { method: "GET" });
      const signature = JSON.stringify(data);
      const newJobs = data.content;
      if (!force && signature === jobsSignature) return;
      
      jobsSignature = signature;
      jobs = newJobs;
      totalPages = Math.max(1, data.totalPages);
      totalElements = data.totalElements;
      if (currentPage > 0 && jobs.length === 0 && currentPage >= totalPages) {
        currentPage = totalPages - 1;
        return fetchJobs(true);
      }
      renderJobList();

      if (selectedJobId != null) {
        const job = jobs.find((j) => j.id === selectedJobId);
        if (job) refreshDrawerFrame(job);
        else closeDrawer();
      }
      setConnectionState(true);
    } catch (err) {
      setConnectionState(false);
    }
  }

  function setConnectionState(ok) {
    const dot = connectionStatus.querySelector(".dot");
    const label = connectionStatus.querySelector("span:last-child");
    dot.className = `dot ${ok ? "dot--ok" : "dot--error"}`;
    label.textContent = ok ? "Đang kết nối" : "Mất kết nối, đang thử lại…";
  }

  function startPolling() {
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(() => {
      if (!document.hidden) fetchJobs();
    }, POLL_INTERVAL_MS);
  }

  // ---------- Init ----------
  initParticles();
  loadCharacters();
  fetchJobs(true);
  startPolling();
})();
