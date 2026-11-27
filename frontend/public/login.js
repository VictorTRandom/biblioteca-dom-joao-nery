import { api, showStatus } from "./api.js";

const form = document.querySelector("#login-form");
const button = form.querySelector("button");
const status = document.querySelector("#login-status");

form.addEventListener("submit", async event => {
  event.preventDefault();
  button.disabled = true;
  showStatus(status, "Verificando credenciais…");
  try {
    const body = new URLSearchParams(new FormData(form));
    await api("/login", { method: "POST", body });
    location.replace("/admin.html");
  } catch (error) {
    showStatus(status, error.message, true);
    button.disabled = false;
  }
});
