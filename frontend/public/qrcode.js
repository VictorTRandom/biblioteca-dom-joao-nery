import { api, showStatus } from "./api.js";

const form = document.querySelector("#qr-form");
const button = form.querySelector("button");
const status = document.querySelector("#qr-status");
const img = document.querySelector("#qr-image");
const download = document.querySelector("#qr-download");
const hint = document.querySelector("#qr-hint");
let objectUrl;

form.elements.url.value = new URL("/", location.origin).href;
form.addEventListener("submit", async event => {
  event.preventDefault();
  button.disabled = true;
  showStatus(status, "Gerando código…");
  try {
    const data = await api(`/catalog/qr?url=${encodeURIComponent(form.elements.url.value.trim())}`, { binary: true });
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = URL.createObjectURL(data);
    img.src = download.href = objectUrl;
    img.hidden = download.hidden = false;
    hint.hidden = true;
    showStatus(status, "QR Code pronto para baixar.");
  } catch (error) {
    showStatus(status, error.message, true);
    img.hidden = download.hidden = true;
    hint.hidden = false;
  } finally {
    button.disabled = false;
  }
});
