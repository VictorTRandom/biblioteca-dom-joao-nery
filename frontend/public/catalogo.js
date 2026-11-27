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
let requestNumber = 0;
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
  dialog.showModal();
}

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
