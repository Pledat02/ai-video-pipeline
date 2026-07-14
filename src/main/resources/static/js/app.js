(() => {
  "use strict";

  const API_BASE = "/api/jobs";
  const POLL_INTERVAL_MS = 4000;

  const STATUS_META = {
    PENDING: { label: "Chờ xử lý", cls: "neutral", icon: "clock", progress: false },
    SCRIPTING: { label: "Đang sinh kịch bản", cls: "info", icon: "spinner", progress: true, step: "sinh kịch bản" },
    SCRIPT_READY: { label: "Chờ duyệt", cls: "warning", icon: "alert", progress: false },
    APPROVED: { label: "Đã duyệt", cls: "info", icon: "check", progress: false },
    GENERATING_AUDIO: { label: "Đang tạo giọng đọc", cls: "progress", icon: "spinner", progress: true, step: "tạo giọng đọc (TTS)" },
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

  // ---------- State ----------
  let jobs = [];
  let jobsSignature = "";
  let selectedJobId = null;
  let scriptDirty = false;
  let pollTimer = null;
  let pendingAction = false;

  // ---------- DOM refs ----------
  const jobListEl = document.getElementById("jobList");
  const jobCountEl = document.getElementById("jobCount");
  const emptyStateEl = document.getElementById("emptyState");
  const createForm = document.getElementById("createJobForm");
  const topicInput = document.getElementById("topicInput");
  const createJobBtn = document.getElementById("createJobBtn");
  const createJobError = document.getElementById("createJobError");
  const connectionStatus = document.getElementById("connectionStatus");

  const drawer = document.getElementById("drawer");
  const drawerBackdrop = document.getElementById("drawerBackdrop");
  const drawerClose = document.getElementById("drawerClose");
  const drawerJobId = document.getElementById("drawerJobId");
  const drawerTitle = document.getElementById("drawerTitle");
  const drawerTimestamp = document.getElementById("drawerTimestamp");
  const drawerErrorBox = document.getElementById("drawerErrorBox");
  const drawerErrorText = document.getElementById("drawerErrorText");
  const drawerCompletedBox = document.getElementById("drawerCompletedBox");
  const drawerYoutubeId = document.getElementById("drawerYoutubeId");
  const drawerVideoPlayer = document.getElementById("drawerVideoPlayer");
  const drawerProgressBox = document.getElementById("drawerProgressBox");
  const drawerProgressStep = document.getElementById("drawerProgressStep");
  const scriptTextarea = document.getElementById("scriptTextarea");
  const scriptHint = document.getElementById("scriptHint");
  const drawerActions = document.getElementById("drawerActions");
  const toastRegion = document.getElementById("toastRegion");
  const backgroundImageInput = document.getElementById("backgroundImageInput");
  const uploadBackgroundBtn = document.getElementById("uploadBackgroundBtn");
  const backgroundPreviewGrid = document.getElementById("backgroundPreviewGrid");

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
    setTimeout(() => toast.remove(), 4000);
  }

  async function apiFetch(path, options) {
    const res = await fetch(API_BASE + path, {
      headers: { "Content-Type": "application/json" },
      ...options,
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
    jobCountEl.textContent = String(jobs.length);
    emptyStateEl.hidden = jobs.length > 0;
    jobListEl.innerHTML = jobs
      .map((job) => {
        const preview = job.scriptContent
          ? escapeHtml(job.scriptContent).slice(0, 90) + (job.scriptContent.length > 90 ? "…" : "")
          : "";
        return `
        <li>
          <button type="button" class="job-card" data-job-id="${job.id}">
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
  }

  function refreshDrawerFrame(job) {
    const meta = STATUS_META[job.status] || STATUS_META.PENDING;

    drawerJobId.textContent = job.id;
    drawerTitle.textContent = job.topic;
    drawerTimestamp.textContent = `Cập nhật ${relativeTime(job.updatedAt)}`;

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
      drawerVideoPlayer.hidden = false;
    } else {
      drawerVideoPlayer.hidden = true;
      drawerVideoPlayer.removeAttribute("src");
      drawerVideoPlayer.dataset.jobId = "";
    }

    drawerProgressBox.hidden = !meta.progress;
    if (meta.progress) drawerProgressStep.textContent = meta.step;

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
    }
  }

  function makeButton(variant, iconHtml, label, onClick) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = `btn btn--${variant}`;
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
  async function createJob(topic) {
    createJobError.hidden = true;
    createJobBtn.disabled = true;
    try {
      await apiFetch("", { method: "POST", body: JSON.stringify({ topic }) });
      topicInput.value = "";
      showToast("Đã tạo job, đang sinh kịch bản…", "success");
      await fetchJobs(true);
    } catch (err) {
      createJobError.textContent = err.message;
      createJobError.hidden = false;
    } finally {
      createJobBtn.disabled = false;
    }
  }

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
    const topic = topicInput.value.trim();
    if (!topic) {
      createJobError.textContent = "Nhập chủ đề video trước đã";
      createJobError.hidden = false;
      topicInput.focus();
      return;
    }
    createJobError.hidden = true;
    createJob(topic);
  });

  // ---------- Polling ----------
  async function fetchJobs(force = false) {
    try {
      const data = await apiFetch("", { method: "GET" });
      const signature = JSON.stringify(data);
      if (!force && signature === jobsSignature) return;
      jobsSignature = signature;
      jobs = data;
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
  fetchJobs(true);
  startPolling();
})();
