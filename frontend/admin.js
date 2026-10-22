const tokenKey = "tam-solar-admin-token";
const loginPanel = document.querySelector("#login-panel");
const loginForm = document.querySelector("#login-form");
const workspace = document.querySelector("#workspace");
const loginMessage = document.querySelector("#login-message");
const workspaceMessage = document.querySelector("#workspace-message");
const inquiryList = document.querySelector("#inquiry-list");
const detailPanel = document.querySelector("#detail-panel");
const filterForm = document.querySelector("#filter-form");
const pageLabel = document.querySelector("#page-label");
const pageSize = 20;
let page = 0;

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined && text !== null) node.textContent = String(text);
  if (className) node.className = className;
  return node;
}

function setMessage(node, message) {
  node.textContent = message || "";
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set("X-Admin-Token", sessionStorage.getItem(tokenKey) || "");
  if (options.body) headers.set("Content-Type", "application/json");
  const response = await fetch(path, { ...options, headers });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401 || response.status === 503) {
      signOut(payload.error || "Admin authorization failed.");
    }
    throw new Error(payload.error || `Request failed (${response.status}).`);
  }
  return payload;
}

function signOut(message = "") {
  sessionStorage.removeItem(tokenKey);
  loginPanel.hidden = false;
  workspace.hidden = true;
  detailPanel.replaceChildren(element("p", "Select an enquiry to view its details and conversation."));
  setMessage(loginMessage, message);
}

function filters() {
  const values = new FormData(filterForm);
  const params = new URLSearchParams({ page: String(page), size: String(pageSize) });
  for (const key of ["search", "status", "fromDate", "toDate"]) {
    const value = values.get(key);
    if (value) params.set(key, String(value));
  }
  return params;
}

async function loadList() {
  setMessage(workspaceMessage, "Loading enquiries…");
  const result = await api(`/api/admin/inquiries?${filters()}`);
  inquiryList.replaceChildren();
  for (const inquiry of result.items) {
    const row = document.createElement("tr");
    const referenceCell = document.createElement("td");
    const selectButton = element("button", `TS-${String(inquiry.id).padStart(6, "0")}`);
    selectButton.type = "button";
    selectButton.addEventListener("click", () => loadDetail(inquiry.id));
    referenceCell.append(selectButton);
    row.append(referenceCell);
    for (const key of ["name", "enquiry_type", "status", "created_at"]) {
      row.append(element("td", inquiry[key]));
    }
    inquiryList.append(row);
  }
  pageLabel.textContent = `Page ${result.page + 1} · ${result.total} enquiries`;
  document.querySelector("#previous-page").disabled = result.page <= 0;
  document.querySelector("#next-page").disabled = (result.page + 1) * result.size >= result.total;
  setMessage(workspaceMessage, result.items.length ? "" : "No enquiries match these filters.");
}

function appendField(list, label, value) {
  list.append(element("dt", label), element("dd", value || "—"));
}

async function loadDetail(id) {
  setMessage(workspaceMessage, "Loading enquiry…");
  let inquiry;
  try {
    inquiry = await api(`/api/admin/inquiries/${id}`);
  } catch (error) {
    setMessage(workspaceMessage, error.message);
    return;
  }
  detailPanel.replaceChildren();
  detailPanel.append(element("h2", `TS-${String(inquiry.id).padStart(6, "0")}`));
  const details = document.createElement("dl");
  for (const [label, key] of [
    ["Customer", "name"], ["Email", "email"], ["Customer type", "customer_type"],
    ["Enquiry topic", "enquiry_type"], ["Submitted", "created_at"],
    ["Last updated", "updated_at"], ["Message", "message"],
  ]) appendField(details, label, inquiry[key]);
  detailPanel.append(details);

  const statusHeading = element("h3", "Workflow");
  const statusForm = document.createElement("form");
  statusForm.className = "admin-form";
  const statusLabel = element("label", "Status");
  const statusSelect = document.createElement("select");
  for (const value of ["NEW", "IN_PROGRESS", "REPLIED", "CONVERTED", "CLOSED"]) {
    const option = element("option", value);
    option.value = value;
    option.selected = inquiry.status === value;
    statusSelect.append(option);
  }
  statusLabel.append(statusSelect);
  const saveStatus = element("button", "Update status", "button button-dark");
  saveStatus.type = "submit";
  statusForm.append(statusLabel, saveStatus);
  statusForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    saveStatus.disabled = true;
    try {
      await api(`/api/admin/inquiries/${id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status: statusSelect.value }),
      });
      await loadList();
      await loadDetail(id);
    } catch (error) {
      setMessage(workspaceMessage, error.message);
    } finally {
      saveStatus.disabled = false;
    }
  });
  detailPanel.append(statusHeading, statusForm);

  const notesHeading = element("h3", "Internal notes (not sent to the customer)");
  const notesForm = document.createElement("form");
  notesForm.className = "admin-form";
  const notesLabel = element("label", "Notes");
  const notes = document.createElement("textarea");
  notes.maxLength = 10000;
  notes.value = inquiry.internal_notes || "";
  notesLabel.append(notes);
  const saveNotes = element("button", "Save notes", "button button-dark");
  saveNotes.type = "submit";
  notesForm.append(notesLabel, saveNotes);
  notesForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    saveNotes.disabled = true;
    try {
      await api(`/api/admin/inquiries/${id}/notes`, {
        method: "PUT",
        body: JSON.stringify({ notes: notes.value }),
      });
      setMessage(workspaceMessage, "Internal notes saved.");
    } catch (error) {
      setMessage(workspaceMessage, error.message);
    } finally {
      saveNotes.disabled = false;
    }
  });
  detailPanel.append(notesHeading, notesForm);

  detailPanel.append(element("h3", "Conversation"));
  for (const item of inquiry.conversation) {
    const message = document.createElement("article");
    message.className = "conversation-message";
    message.append(
      element("strong", item.kind === "ADMIN_REPLY" ? "TAM Solar reply" : "Customer confirmation"),
      element("small", `${item.created_at} · ${item.status}${item.sent_at ? ` · sent ${item.sent_at}` : ""}`),
      element("p", item.text_body),
    );
    detailPanel.append(message);
  }
  detailPanel.append(element("h3", "Email delivery"));
  for (const delivery of inquiry.emailDeliveries) {
    detailPanel.append(element(
      "p",
      `${delivery.kind}: ${delivery.status} · attempts ${delivery.attempts}${delivery.last_error ? ` · ${delivery.last_error}` : ""}`,
      "delivery-status",
    ));
  }

  const replyHeading = element("h3", "Reply to customer");
  const replyForm = document.createElement("form");
  replyForm.className = "reply-form";
  const recipientLabel = element("label", "Customer email (saved enquiry address)");
  const recipient = document.createElement("input");
  recipient.type = "email";
  recipient.value = inquiry.email;
  recipient.readOnly = true;
  recipientLabel.append(recipient);
  const subjectLabel = element("label", "Subject");
  const subject = document.createElement("input");
  subject.required = true;
  subject.maxLength = 300;
  subject.value = "Regarding your Tam Solar enquiry";
  subjectLabel.append(subject);
  const bodyLabel = element("label", "Reply message");
  const body = document.createElement("textarea");
  body.required = true;
  body.maxLength = 10000;
  bodyLabel.append(body);
  const send = element("button", "Send reply", "button button-dark");
  send.type = "submit";
  let pendingReplyKey = null;
  subject.addEventListener("input", () => {
    if (!send.disabled) pendingReplyKey = null;
  });
  body.addEventListener("input", () => {
    if (!send.disabled) pendingReplyKey = null;
  });
  replyForm.append(recipientLabel, subjectLabel, bodyLabel, send);
  replyForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!replyForm.reportValidity()) return;
    send.disabled = true;
    send.textContent = "Queueing reply…";
    try {
      pendingReplyKey ||= globalThis.crypto?.randomUUID?.()
        || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
      await api(`/api/admin/inquiries/${id}/replies`, {
        method: "POST",
        headers: { "Idempotency-Key": pendingReplyKey },
        body: JSON.stringify({ subject: subject.value, message: body.value }),
      });
      pendingReplyKey = null;
      body.value = "";
      setMessage(workspaceMessage, "Reply saved and queued for delivery. Delivery status is shown below.");
      await loadDetail(id);
    } catch (error) {
      setMessage(workspaceMessage, error.message);
    } finally {
      send.disabled = false;
      send.textContent = "Send reply";
    }
  });
  detailPanel.append(replyHeading, replyForm);
  setMessage(workspaceMessage, "");
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const token = document.querySelector("#admin-token").value.trim();
  sessionStorage.setItem(tokenKey, token);
  loginMessage.textContent = "Checking access…";
  try {
    await loadList();
    loginPanel.hidden = true;
    workspace.hidden = false;
    setMessage(loginMessage, "");
  } catch (error) {
    signOut(error.message);
  }
});

filterForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  page = 0;
  try {
    await loadList();
  } catch (error) {
    setMessage(workspaceMessage, error.message);
  }
});

document.querySelector("#previous-page").addEventListener("click", async () => {
  page = Math.max(0, page - 1);
  try { await loadList(); } catch (error) { setMessage(workspaceMessage, error.message); }
});
document.querySelector("#next-page").addEventListener("click", async () => {
  page += 1;
  try { await loadList(); } catch (error) { setMessage(workspaceMessage, error.message); }
});
document.querySelector("#sign-out").addEventListener("click", () => signOut());

if (sessionStorage.getItem(tokenKey)) {
  document.querySelector("#admin-token").value = sessionStorage.getItem(tokenKey);
  loginForm.requestSubmit();
}
