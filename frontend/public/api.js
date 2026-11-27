export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

export async function api(path, { method = "GET", body, binary = false } = {}) {
  const headers = new Headers({ Accept: binary ? "image/png" : "application/json" });
  if (method !== "GET") headers.set("X-Biblioteca-Request", "1");
  if (body !== undefined && !(body instanceof URLSearchParams)) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(body);
  }
  let response;
  try {
    response = await fetch(`/api${path}`, { method, headers, body, credentials: "same-origin", cache: "no-store" });
  } catch {
    throw new ApiError("A conexão falhou. Verifique a rede e tente novamente.", 0);
  }
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new ApiError(error?.message || "O servidor não conseguiu concluir a solicitação.", response.status);
  }
  if (response.status === 204) return;
  return binary ? response.blob() : response.json();
}

export function showStatus(element, message = "", error = false) {
  element.textContent = message;
  element.classList.toggle("error", error);
}

export function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined && text !== null) node.textContent = text;
  if (className) node.className = className;
  return node;
}
