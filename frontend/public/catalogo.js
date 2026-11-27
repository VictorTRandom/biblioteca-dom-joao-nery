import { api, element, showStatus } from "./api.js";

const form = document.querySelector("#filter");
const search = document.querySelector("#q");
const subjects = document.querySelector("#subjects");
const books = document.querySelector("#books");
const status = document.querySelector("#catalog-status");
const heading = document.querySelector("#results-title");
const template = document.querySelector("#book-card");
const dialog = document.querySelector("#book-dialog");
const pager = document.querySelector("#pager");
const previous = document.querySelector("#previous");
const next = document.querySelector("#next");
const pageNumber = document.querySelector("#page-number");
const requestDialog = document.querySelector("#request-dialog");
const requestForm = document.querySelector("#request-form");
const requestStatus = document.querySelector("#request-status");
const requestResult = document.querySelector("#request-result");
const requestCode = document.querySelector("#request-code");
const trackDialog = document.querySelector("#track-dialog");
const trackForm = document.querySelector("#track-form");
const trackStatus = document.querySelector("#track-status");
const trackResult = document.querySelector("#track-result");
let requestNumber = 0;
let requestDialogNumber = 0;
let trackingNumber = 0;
let requestedBook;
let page = 1;

function showDetails(book) {
  const fields = {
    subject: book.subject,
    title: book.title,
    author: book.author,
    description: book.description || "Sinopse ainda não cadastrada.",
    code: book.code,
    year: book.publication_year ?? "Não informado",
    stock: `${book.available} de ${book.total_copies}`,
  };
  for (const [key, value] of Object.entries(fields)) document.querySelector(`#detail-${key}`).textContent = value;
  const button = document.querySelector("#detail-request");
  button.hidden = book.available < 1;
  button.onclick = () => {
    dialog.close();
    openRequest(book);
  };
  dialog.showModal();
}

function openRequest(book) {
  requestDialogNumber++;
  requestedBook = book;
  requestForm.reset();
  requestCode.value = "";
  requestForm.hidden = false;
  requestForm.querySelector("button").disabled = false;
  requestResult.hidden = true;
  showStatus(requestStatus);
  document.querySelector("#request-book").textContent = `${book.title} · ${book.code}`;
  requestDialog.showModal();
}

function openTrack(code = "") {
  trackingNumber++;
  trackForm.reset();
  trackForm.querySelector("button").disabled = false;
  trackForm.elements.tracking.value = code;
  trackResult.replaceChildren();
  trackResult.hidden = true;
  showStatus(trackStatus);
  trackDialog.showModal();
  if (code) trackForm.requestSubmit();
}

requestForm.addEventListener("submit", async event => {
  event.preventDefault();
  const request = requestDialogNumber;
  const button = requestForm.querySelector("button");
  const ra = requestForm.elements.ra.value.trim();
  button.disabled = true;
  showStatus(requestStatus, "Enviando pedido…");
  try {
    const result = await api("/catalog/requests", { method: "POST", body: { bookId: requestedBook.id, ra } });
    if (request !== requestDialogNumber || !requestDialog.open) return;
    requestForm.hidden = true;
    requestCode.value = result.tracking_code;
    requestResult.hidden = false;
    showStatus(requestStatus, "Pedido enviado.");
    requestCode.focus();
    requestCode.select();
  } catch (error) {
    if (request === requestDialogNumber && requestDialog.open) showStatus(requestStatus, error.message, true);
  } finally {
    if (request === requestDialogNumber) button.disabled = false;
  }
});

document.querySelector("#copy-code").addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(requestCode.value);
    showStatus(requestStatus, "Código copiado.");
  } catch {
    requestCode.focus();
    requestCode.select();
    showStatus(requestStatus, "Selecione e copie o código de acompanhamento.");
  }
});

document.querySelector("#track-request").addEventListener("click", () => {
  requestDialog.close();
  openTrack(requestCode.value);
});
document.querySelector("#track-open").addEventListener("click", () => openTrack());

trackForm.addEventListener("submit", async event => {
  event.preventDefault();
  const request = ++trackingNumber;
  const code = trackForm.elements.tracking.value.trim().toLowerCase();
  const button = trackForm.querySelector("button");
  trackResult.hidden = true;
  if (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(code)) {
    showStatus(trackStatus, "Confira o código de acompanhamento recebido ao enviar o pedido.", true);
    return;
  }
  button.disabled = true;
  showStatus(trackStatus, "Consultando…");
  try {
    const result = await api(`/catalog/requests/${code}`);
    if (request !== trackingNumber || !trackDialog.open) return;
    const descriptions = {
      pendente: "Aguardando confirmação na biblioteca.",
      aprovado: result.returned_at ? "Livro devolvido." : "Retirada confirmada.",
      recusado: "Pedido recusado. Procure a biblioteca para mais informações.",
    };
    trackResult.replaceChildren(element("strong", result.title), element("p", descriptions[result.status]));
    if (result.due_date && !result.returned_at) {
      trackResult.append(element("p", `Prazo para devolução: ${result.due_date.split("-").reverse().join("/")}.`));
    }
    trackResult.hidden = false;
    showStatus(trackStatus);
  } catch (error) {
    if (request === trackingNumber && trackDialog.open) showStatus(trackStatus, error.message, true);
  } finally {
    if (request === trackingNumber) button.disabled = false;
  }
});

function bookCard(book) {
  const card = template.content.cloneNode(true);
  const availability = card.querySelector(".availability");
  card.querySelector(".book-visual").dataset.color = book.id % 5;
  card.querySelector(".mock-title").textContent = book.title;
  card.querySelector(".book-category").textContent = book.subject;
  card.querySelector(".book-title").textContent = book.title;
  card.querySelector(".book-author").textContent = book.author;
  availability.textContent = book.available === 0 ? "Indisponível" : `${book.available} ${book.available === 1 ? "disponível" : "disponíveis"}`;
  availability.classList.toggle("unavailable", book.available === 0);
  const details = card.querySelector(".details");
  details.setAttribute("aria-label", `Detalhes de ${book.title}`);
  details.addEventListener("click", () => showDetails(book));
  const request = card.querySelector(".book-request");
  request.hidden = book.available < 1;
  request.setAttribute("aria-label", `Solicitar empréstimo de ${book.title}`);
  request.addEventListener("click", () => openRequest(book));
  return card;
}

async function load(target = 1) {
  const request = ++requestNumber;
  const query = search.value.trim();
  const params = new URLSearchParams({ q: query, subject: subjects.value, page: target });
  showStatus(status, "Carregando acervo…");
  try {
    const result = await api(`/catalog?${params}`);
    if (request !== requestNumber) return;
    page = result.page;
    books.replaceChildren(...result.items.map(bookCard));
    heading.textContent = query || subjects.value !== "0" ? "Resultados da busca" : "Livros do acervo";
    const count = result.items.length;
    showStatus(status, count === 0 ? "Nenhum livro encontrado." : `${count} ${count === 1 ? "título nesta página" : "títulos nesta página"}`);
    pager.hidden = page === 1 && !result.hasNext;
    previous.disabled = page === 1;
    next.disabled = !result.hasNext;
    pageNumber.textContent = `Página ${page}`;
  } catch (error) {
    if (request !== requestNumber) return;
    books.replaceChildren();
    pager.hidden = true;
    showStatus(status, error.message, true);
  }
}

form.addEventListener("submit", event => { event.preventDefault(); load(); });
subjects.addEventListener("change", () => load());
previous.addEventListener("click", () => load(page - 1));
next.addEventListener("click", () => load(page + 1));
api("/catalog/subjects").then(items => {
  for (const entry of items) {
    const option = element("option", entry.name);
    option.value = entry.id;
    subjects.append(option);
  }
}).catch(() => { subjects.disabled = true; });
load();
