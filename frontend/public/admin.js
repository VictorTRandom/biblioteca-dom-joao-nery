import { api, element, showStatus } from "./api.js";

const $ = selector => document.querySelector(selector);
const status = $("#admin-status");
const editDialog = $("#edit-dialog");
const editForm = $("#edit-form");
const editStatus = $("#edit-status");
const copyDialog = $("#copy-dialog");
const copyForm = $("#copy-form");
const copyStatus = $("#copy-status");
const schoolDate = new Intl.DateTimeFormat("pt-BR", { timeZone: "America/Sao_Paulo", dateStyle: "short" });
const sections = {
  books: ["Livros e exemplares", "Cadastre títulos, gerencie o estoque e consulte os códigos gerados.", "Novo livro"],
  authors: ["Autores", "Gerencie os autores vinculados aos títulos.", "Novo autor"],
  subjects: ["Assuntos", "Organize os títulos por tema.", "Novo assunto"],
  students: ["Alunos", "Gerencie os cadastros usados nos empréstimos.", "Novo aluno"],
  loans: ["Empréstimos e relatórios", "Registre retiradas e devoluções e consulte o histórico.", "Novo empréstimo"],
};
const loanStatuses = { em_aberto: "Em aberto", atrasado: "Atrasado", devolvido: "Devolvido" };
let view = "books";
let renderedRequest = 0;
let editorRequest = 0;
let copyRequest = 0;
let copyBook;
let saveForm;
let loanRows = [];

function call(path, options) {
  return api(`/admin${path}`, options).catch(error => {
    if (error.status === 401) location.replace("/login.html");
    throw error;
  });
}

function formatDate(value) {
  return value ? value.split("-").reverse().join("/") : "—";
}

function action(label, run, danger = false) {
  const button = element("button", label, `text-button${danger ? " danger" : ""}`);
  button.type = "button";
  button.addEventListener("click", () => run(button));
  return button;
}

function actions(...buttons) {
  const group = element("div", null, "action-group");
  group.append(...buttons);
  return group;
}

function tag(label, color) {
  return element("span", label, `tag ${color || ""}`);
}

function table(container, headers, rows) {
  if (!rows.length) {
    container.replaceChildren(element("p", "Nenhum registro encontrado.", "status"));
    return;
  }
  const grid = element("table");
  const head = grid.createTHead().insertRow();
  for (const label of headers) {
    const th = element("th", label);
    th.scope = "col";
    head.append(th);
  }
  const body = grid.createTBody();
  for (const row of rows) {
    const tr = body.insertRow();
    for (const value of row) {
      const cell = tr.insertCell();
      if (value instanceof Node) cell.append(value);
      else cell.textContent = value ?? "—";
    }
  }
  container.replaceChildren(grid);
}

async function removeItem(kind, row, button) {
  if (!confirm(`Excluir ${row.title || row.name}?`)) return;
  button.disabled = true;
  try {
    await call(`/${kind}/${row.id}`, { method: "DELETE" });
    await refresh();
    showStatus(status, "Cadastro excluído.");
  } catch (error) {
    showStatus(status, error.message, true);
  } finally {
    button.disabled = false;
  }
}

function draw(kind, rows) {
  const container = $("#admin-table");
  const editRemove = row => [
    action("Editar", () => openEditor(kind, row)),
    action("Excluir", button => removeItem(kind, row, button), true),
  ];
  if (kind === "books") {
    table(container, ["Código", "Livro", "Autor", "Disponíveis", "Situação", "Ações"], rows.map(row => [
      row.code, row.title, row.author, `${row.available}/${row.total_copies}`,
      tag(row.active ? "Ativo" : "Inativo", row.active ? "" : "neutral"),
      actions(action("Exemplares", () => openCopies(row)), ...editRemove(row)),
    ]));
  } else if (kind === "students") {
    table(container, ["RA", "Nome", "E-mail", "Turma", "Situação", "Ações"], rows.map(row => [
      row.ra, row.name, row.email || "—", row.class_name,
      tag(row.active ? "Ativo" : "Inativo", row.active ? "" : "neutral"), actions(...editRemove(row)),
    ]));
  } else if (kind === "loans") {
    table(container, ["Aluno", "Livro", "Exemplar", "Retirada", "Prazo", "Devolução", "Situação", "Observações", "Ação"], rows.map(row => [
      `${row.student} · ${row.ra}`, row.title, row.copy_code, formatDate(row.borrowed_at), formatDate(row.due_date),
      formatDate(row.returned_at), tag(loanStatuses[row.status], row.status === "atrasado" ? "warn" : "neutral"),
      row.notes || "—", row.returned_at ? "—" : action("Devolver", button => returnLoan(row, button)),
    ]));
  } else {
    table(container, ["Nome", "Ações"], rows.map(row => [row.name, actions(...editRemove(row))]));
  }
}

async function refresh() {
  const request = ++renderedRequest;
  const requested = view;
  const suffix = requested === "loans" ? `?filter=${$("#loan-filter").value}` : "";
  $("#admin-table").replaceChildren(element("p", "Carregando…", "status"));
  $("#add-item").disabled = true;
  $("#download-report").disabled = true;
  try {
    const [rows, summary] = await Promise.all([call(`/${requested}${suffix}`), call("/summary")]);
    if (request !== renderedRequest) return;
    for (const [key, value] of Object.entries(summary)) $(`#metric-${key}`).textContent = value;
    if (requested === "loans") loanRows = rows;
    draw(requested, rows);
  } catch (error) {
    if (request !== renderedRequest) return;
    loanRows = [];
    $("#admin-table").replaceChildren(element("p", error.message, "status error"));
  } finally {
    if (request === renderedRequest) {
      $("#add-item").disabled = false;
      $("#download-report").disabled = !loanRows.length;
    }
  }
}

function selectView(next) {
  view = next;
  const [title, description, add] = sections[next];
  $("#view-title").textContent = title;
  $("#view-description").textContent = description;
  $("#add-item").textContent = add;
  $("#loan-tools").hidden = next !== "loans";
  for (const tab of document.querySelectorAll("[data-view]")) {
    if (tab.dataset.view === next) tab.setAttribute("aria-current", "true");
    else tab.removeAttribute("aria-current");
  }
  showStatus(status);
  refresh();
}

function field(name, title, value, options = {}) {
  const label = element("label", null, options.full ? "full" : "");
  const type = options.type || "text";
  const input = element(options.choices ? "select" : type === "textarea" ? "textarea" : "input");
  if (options.choices) {
    for (const [key, text] of options.choices) input.add(new Option(text, key));
  } else if (type !== "textarea") {
    input.type = type;
  }
  input.name = name;
  input.required = options.required || false;
  for (const key of ["min", "max", "maxLength"]) if (options[key] !== undefined) input[key] = options[key];
  if (type === "checkbox") {
    input.checked = value;
    label.classList.add("check-label");
    label.append(input, document.createTextNode(title));
  } else {
    if (value !== undefined && value !== null) input.value = value;
    label.append(document.createTextNode(title), input);
  }
  return label;
}

const required = maxLength => ({ required: true, maxLength });
const numeric = (min, max, isRequired = false) => ({ type: "number", min, max, required: isRequired });
const nameChoices = (rows, initial) => [["", initial], ...rows.map(row => [row.id, row.name])];

async function openEditor(kind, row) {
  const request = ++editorRequest;
  const editing = Boolean(row);
  const title = editing ? `Editar ${row.title || row.name}` : sections[kind][2];
  $("#edit-title").textContent = title;
  const fields = $("#edit-fields");
  fields.replaceChildren();
  editForm.hidden = true;
  showStatus(editStatus, "Carregando formulário…");
  editDialog.showModal();
  try {
    if (kind === "books") {
      const [authors, subjects] = await Promise.all([call("/authors"), call("/subjects")]);
      if (request !== editorRequest || !editDialog.open) return;
      if (!authors.length || !subjects.length) throw new Error("Cadastre pelo menos um autor e um assunto primeiro.");
      fields.append(
        field("title", "Título", row?.title, { ...required(200), full: true }),
        field("authorId", "Autor", row?.author_id, { choices: nameChoices(authors, "Selecione um autor"), required: true }),
        field("subjectId", "Assunto", row?.subject_id, { choices: nameChoices(subjects, "Selecione um assunto"), required: true }),
        field("year", "Ano de publicação", row?.publication_year, numeric(1, 3000)),
        field("description", "Sinopse", row?.description, { type: "textarea", maxLength: 4000, full: true }),
        field("active", "Visível no catálogo", row?.active ?? true, { type: "checkbox", full: true }),
      );
      if (!editing) fields.insertBefore(field("initialCopies", "Exemplares iniciais", 1, numeric(0, 100, true)), fields.children[4]);
    } else if (kind === "students") {
      fields.append(
        field("ra", "RA", row?.ra, required(40)),
        field("className", "Turma", row?.class_name, required(60)),
        field("name", "Nome", row?.name, { ...required(160), full: true }),
        field("email", "E-mail", row?.email, { type: "email", maxLength: 254, full: true }),
        field("active", "Aluno ativo", row?.active ?? true, { type: "checkbox", full: true }),
      );
    } else if (kind === "loans") {
      await loanFields(fields, request);
      if (request !== editorRequest || !editDialog.open) return;
    } else {
      fields.append(field("name", "Nome", row?.name, { ...required(kind === "subjects" ? 100 : 160), full: true }));
    }
    saveForm = formBody => saveItem(kind, row, formBody);
    editForm.hidden = false;
    showStatus(editStatus);
    editForm.querySelector("input, select")?.focus();
  } catch (error) {
    if (request === editorRequest && editDialog.open) showStatus(editStatus, error.message, true);
  }
}

async function loanFields(fields, editorNumber) {
  const [students, books] = await Promise.all([call("/students"), call("/books")]);
  if (editorNumber !== editorRequest || !editDialog.open) return;
  const studentChoices = nameChoices(students.filter(row => row.active).map(row => ({ ...row, name: `${row.name} · ${row.ra}` })), "Selecione um aluno");
  const bookChoices = [["", "Selecione um título"], ...books.filter(row => row.active && row.available > 0).map(row => [row.id, `${row.title} · ${row.code}`])];
  if (studentChoices.length < 2 || bookChoices.length < 2) throw new Error("É necessário um aluno ativo e um exemplar disponível.");
  const minDate = schoolDate.format(new Date()).split("/").reverse().join("-");
  const book = field("bookId", "Livro", "", { choices: bookChoices, required: true, full: true });
  const copy = field("copyId", "Exemplar", "", { choices: [["", "Escolher automaticamente"]] });
  const select = copy.querySelector("select");
  let choiceRequest = 0;
  book.querySelector("select").addEventListener("change", async event => {
    const request = ++choiceRequest;
    select.replaceChildren(new Option("Escolher automaticamente", ""));
    if (!event.target.value) return;
    try {
      const rows = await call(`/books/${event.target.value}/copies`);
      if (request !== choiceRequest || editorNumber !== editorRequest || !editDialog.open) return;
      for (const row of rows) if (row.active && !row.on_loan) select.add(new Option(row.code, row.id));
    } catch (error) {
      if (request === choiceRequest && editorNumber === editorRequest && editDialog.open) showStatus(editStatus, error.message, true);
    }
  });
  fields.append(
    field("studentId", "Aluno", "", { choices: studentChoices, required: true, full: true }),
    book, copy, field("dueDate", "Devolução prevista", minDate, { type: "date", min: minDate, required: true }),
    field("notes", "Observações", "", { type: "textarea", maxLength: 1000, full: true }),
  );
}

async function saveItem(kind, row, data) {
  const body = Object.fromEntries(data.entries());
  for (const key of ["year", "authorId", "subjectId", "initialCopies", "studentId", "bookId", "copyId"]) {
    if (key in body) body[key] = body[key] === "" ? null : Number(body[key]);
  }
  if (kind === "books" || kind === "students") body.active = data.has("active");
  const result = await call(`/${kind}${row ? `/${row.id}` : ""}`, { method: row ? "PUT" : "POST", body });
  editDialog.close();
  await refresh();
  if (kind === "books" && !row) {
    showStatus(status, `Livro cadastrado com o código ${result.code}.`);
    await openCopies(result);
  } else if (kind === "loans") {
    showStatus(status, `Empréstimo registrado para o exemplar ${result.copy_code}.`);
  } else {
    showStatus(status, "Cadastro salvo.");
  }
}

function drawCopies(rows) {
  table($("#copy-table"), ["Código", "Situação", "Ação"], rows.map(row => [
    row.code,
    tag(row.on_loan ? "Emprestado" : row.active ? "Disponível" : "Inativo", row.active && !row.on_loan ? "" : "neutral"),
    row.on_loan ? "—" : action(row.active ? "Inativar" : "Reativar", button => toggleCopy(row, button)),
  ]));
}

async function openCopies(row) {
  const request = ++copyRequest;
  copyBook = row;
  $("#copy-book").textContent = `${row.title} · ${row.code}`;
  $("#copy-table").replaceChildren();
  showStatus(copyStatus, "Carregando exemplares…");
  copyForm.reset();
  copyDialog.showModal();
  try {
    const rows = await call(`/books/${row.id}/copies`);
    if (request !== copyRequest || !copyDialog.open) return;
    drawCopies(rows);
    showStatus(copyStatus);
  } catch (error) {
    if (request === copyRequest && copyDialog.open) showStatus(copyStatus, error.message, true);
  }
}

async function toggleCopy(row, button) {
  button.disabled = true;
  try {
    drawCopies(await call(`/books/${copyBook.id}/copies/${row.id}`, { method: "PUT", body: { active: !row.active } }));
    await refresh();
    showStatus(copyStatus, "Situação do exemplar atualizada.");
  } catch (error) {
    showStatus(copyStatus, error.message, true);
  } finally {
    button.disabled = false;
  }
}

async function returnLoan(row, button) {
  if (!confirm(`Registrar a devolução de ${row.copy_code}?`)) return;
  button.disabled = true;
  try {
    await call(`/loans/${row.id}/return`, { method: "POST" });
    await refresh();
    showStatus(status, "Devolução registrada.");
  } catch (error) {
    showStatus(status, error.message, true);
  } finally {
    button.disabled = false;
  }
}

function csvValue(value) {
  let text = String(value ?? "");
  if (/^[\s\uFEFF]*[=+\-@]|^[\t\r\n]/.test(text)) text = "'" + text;
  return `"${text.replaceAll('"', '""')}"`;
}

function downloadReport() {
  const headers = ["RA", "Aluno", "Livro", "Exemplar", "Retirada", "Prazo", "Devolução", "Situação", "Observações"];
  const rows = loanRows.map(row => [row.ra, row.student, row.title, row.copy_code, row.borrowed_at, row.due_date,
    row.returned_at, loanStatuses[row.status], row.notes]);
  const csv = [headers, ...rows].map(row => row.map(csvValue).join(";")).join("\r\n");
  const url = URL.createObjectURL(new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" }));
  const link = element("a");
  link.href = url;
  link.download = `emprestimos-${$("#loan-filter").value}.csv`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

editForm.addEventListener("submit", async event => {
  event.preventDefault();
  const button = editForm.querySelector("button");
  button.disabled = true;
  showStatus(editStatus, "Salvando…");
  try {
    await saveForm(new FormData(editForm));
  } catch (error) {
    showStatus(editStatus, error.message, true);
  } finally {
    button.disabled = false;
  }
});

copyForm.addEventListener("submit", async event => {
  event.preventDefault();
  const button = copyForm.querySelector("button");
  button.disabled = true;
  try {
    const quantity = Number(copyForm.elements.quantity.value);
    drawCopies(await call(`/books/${copyBook.id}/copies`, { method: "POST", body: { quantity } }));
    await refresh();
    showStatus(copyStatus, `${quantity} ${quantity === 1 ? "código gerado" : "códigos gerados"}.`);
    copyForm.reset();
  } catch (error) {
    showStatus(copyStatus, error.message, true);
  } finally {
    button.disabled = false;
  }
});

$("#add-item").addEventListener("click", () => openEditor(view));
$("#loan-filter").addEventListener("change", () => refresh());
$("#download-report").addEventListener("click", downloadReport);
document.querySelectorAll("[data-view]").forEach(button => button.addEventListener("click", () => selectView(button.dataset.view)));
$("#logout").addEventListener("click", async () => {
  try {
    await api("/logout", { method: "POST" });
    location.replace("/login.html");
  } catch (error) {
    showStatus(status, error.message, true);
  }
});

try {
  const session = await call("/session");
  $("#session-email").textContent = session.email;
  $("#initial-status").hidden = true;
  $("#workspace").hidden = false;
  $("#logout").hidden = false;
  selectView(view);
} catch (error) {
  showStatus($("#initial-status"), error.message, true);
}
