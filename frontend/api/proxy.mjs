const allowed = new Set(["GET", "HEAD", "POST", "PUT", "DELETE"]);
const routedParameter = "__biblioteca_path";

export default async function proxy(req, res) {
  res.setHeader("Cache-Control", "private, no-store");
  if (!allowed.has(req.method)) return res.status(405).json({ message: "Método inválido." });

  let backend;
  try {
    backend = new URL(process.env.BIBLIOTECA_API_ORIGIN);
    if (backend.protocol !== "https:" || backend.username || backend.password
        || backend.search || backend.hash || backend.pathname !== "/") throw new Error();
  } catch {
    return res.status(503).json({ message: "Configure a origem HTTPS da API Java no projeto Vercel." });
  }

  const inbound = new URL(req.url, "http://localhost");
  const route = req.query?.[routedParameter] ?? inbound.searchParams.get(routedParameter);
  const directPath = inbound.pathname.replace(/^\/api\//, "");
  const path = directPath !== "proxy" ? directPath : route;
  if (typeof path !== "string" || !/^[a-z0-9-]+(?:\/[a-z0-9-]+)*$/.test(path) || path === "proxy") {
    return res.status(404).json({ message: "Rota não encontrada." });
  }
  backend.pathname = `/api/${path}`;
  for (const [key, value] of inbound.searchParams) {
    if (key !== routedParameter) backend.searchParams.append(key, value);
  }

  const headers = new Headers();
  for (const key of ["accept", "content-type", "cookie", "x-biblioteca-request"]) {
    if (typeof req.headers[key] === "string") headers.set(key, req.headers[key]);
  }
  let body;
  if (req.method !== "GET" && req.method !== "HEAD" && req.body !== undefined) {
    if (typeof req.body === "string" || Buffer.isBuffer(req.body)) body = req.body;
    else if (headers.get("content-type")?.startsWith("application/x-www-form-urlencoded")) {
      body = new URLSearchParams(req.body).toString();
    } else body = JSON.stringify(req.body);
  }
  try {
    const upstream = await fetch(backend, { method: req.method, headers, body, redirect: "manual", signal: AbortSignal.timeout(55000) });
    for (const key of ["content-type", "www-authenticate"]) {
      if (upstream.headers.has(key)) res.setHeader(key, upstream.headers.get(key));
    }
    const cookies = upstream.headers.getSetCookie();
    if (cookies.length) res.setHeader("Set-Cookie", cookies);
    res.status(upstream.status);
    if (req.method === "HEAD" || upstream.status === 204) return res.end();
    return res.end(Buffer.from(await upstream.arrayBuffer()));
  } catch {
    return res.status(502).json({ message: "A API Java está indisponível. Aguarde e tente novamente." });
  }
}
