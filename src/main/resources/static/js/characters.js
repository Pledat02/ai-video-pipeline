(() => {
  "use strict";

  const API_BASE = "/api/characters";

  const characterGridEl = document.getElementById("characterGrid");
  const characterCountEl = document.getElementById("characterCount");
  const emptyStateEl = document.getElementById("emptyCharacterState");
  const createForm = document.getElementById("createCharacterForm");
  const nameInput = document.getElementById("characterNameInput");
  const descriptionInput = document.getElementById("characterDescriptionInput");
  const imageInput = document.getElementById("characterImageInput");
  const createBtn = document.getElementById("createCharacterBtn");
  const createError = document.getElementById("createCharacterError");
  const toastRegion = document.getElementById("toastRegion");

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
    try { body = await res.json(); } catch (_) { /* no body (e.g. 204) */ }
    if (!res.ok) {
      const message = (body && body.message) || `Lỗi HTTP ${res.status}`;
      throw new Error(message);
    }
    return body;
  }

  function characterCardHtml(character) {
    const thumb = character.imageUrl
      ? `<img src="${character.imageUrl}" alt="${escapeHtml(character.name)}" />`
      : `<div class="character-card__placeholder" aria-hidden="true">🎭</div>`;
    const description = character.description
      ? escapeHtml(character.description)
      : "<em>Chưa có mô tả</em>";
    return `
      <article class="character-card" data-character-id="${character.id}">
        <div class="character-card__thumb">${thumb}</div>
        <div class="character-card__body">
          <h3 class="character-card__name">${escapeHtml(character.name)}</h3>
          <p class="character-card__description">${description}</p>
        </div>
        <button type="button" class="btn-icon btn-icon--close character-card__delete" data-character-id="${character.id}" aria-label="Xoá nhân vật">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
        </button>
      </article>`;
  }

  async function loadCharacters() {
    try {
      const characters = await apiFetch("", { method: "GET" });
      characterCountEl.textContent = String(characters.length);
      emptyStateEl.hidden = characters.length > 0;
      characterGridEl.innerHTML = characters.map(characterCardHtml).join("");
      characterGridEl.querySelectorAll(".character-card__delete").forEach((btn) => {
        btn.addEventListener("click", (e) => {
          e.stopPropagation();
          deleteCharacter(Number(btn.dataset.characterId));
        });
      });
    } catch (err) {
      showToast(err.message, "error");
    }
  }

  async function deleteCharacter(id) {
    if (!confirm("Xoá nhân vật này? Các video đã tạo trước đó vẫn giữ nguyên mô tả đã dùng.")) return;
    try {
      await apiFetch(`/${id}`, { method: "DELETE" });
      showToast("Đã xoá nhân vật", "success");
      await loadCharacters();
    } catch (err) {
      showToast(err.message, "error");
    }
  }

  createForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const name = nameInput.value.trim();
    if (!name) {
      createError.textContent = "Nhập tên nhân vật trước đã";
      createError.hidden = false;
      nameInput.focus();
      return;
    }
    createError.hidden = true;
    createBtn.disabled = true;
    try {
      const formData = new FormData();
      formData.append("name", name);
      formData.append("description", descriptionInput.value.trim());
      if (imageInput.files[0]) formData.append("image", imageInput.files[0]);
      await apiFetch("", { method: "POST", body: formData });
      createForm.reset();
      showToast("Đã tạo nhân vật", "success");
      await loadCharacters();
    } catch (err) {
      createError.textContent = err.message;
      createError.hidden = false;
    } finally {
      createBtn.disabled = false;
    }
  });

  loadCharacters();
})();
