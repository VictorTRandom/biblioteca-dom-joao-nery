"""Exporta relatórios, gera o QR Code e verifica a API da biblioteca."""

import argparse
import csv
import getpass
from http.cookiejar import CookieJar
import json
import os
from pathlib import Path
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import HTTPCookieProcessor, HTTPRedirectHandler, Request, build_opener


class ApiError(RuntimeError):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Biblioteca:
    def __init__(self, url):
        address = urlsplit(url)
        local = address.scheme == "http" and address.hostname in {"localhost", "127.0.0.1"}
        if (not address.hostname or not (address.scheme == "https" or local)
                or address.username is not None or address.password is not None
                or address.query or address.fragment or address.path not in {"", "/"}):
            raise ValueError("Informe a URL HTTPS do catálogo ou o endereço HTTP local.")
        self.url = url.rstrip("/")
        self.opener = build_opener(NoRedirect(), HTTPCookieProcessor(CookieJar()))

    def request(self, path, method="GET", data=None, content_type="application/json", binary=False):
        headers = {"Accept": "image/png" if binary else "application/json"}
        if method != "GET":
            headers["X-Biblioteca-Request"] = "1"
        if data is not None:
            headers["Content-Type"] = content_type
            data = json.dumps(data).encode() if content_type == "application/json" else urlencode(data).encode()
        request = Request(self.url + "/api" + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=65) as response:
                body = response.read()
                return body if binary else json.loads(body) if body else None
        except HTTPError as error:
            try:
                message = json.load(error).get("message")
            except (ValueError, AttributeError):
                message = None
            raise ApiError(error.code, message or f"A requisição retornou HTTP {error.code}.") from None
        except (URLError, TimeoutError) as error:
            raise RuntimeError(f"Falha ao conectar com a biblioteca: {error}") from None

    def login(self, email, password):
        self.request("/login", "POST", {"email": email, "password": password},
                     content_type="application/x-www-form-urlencoded")


def csv_safe(value):
    text = str(value if value is not None else "")
    if text.startswith(("\t", "\r", "\n")) or text.lstrip("\ufeff \t\r\n").startswith(("=", "+", "-", "@")):
        return "'" + text
    return text


def export_report(client, mode, file):
    rows = client.request("/admin/loans?" + urlencode({"filter": mode}))
    keys = ("ra", "student", "title", "copy_code", "borrowed_at", "due_date", "returned_at", "status", "notes")
    labels = ("RA", "Aluno", "Livro", "Exemplar", "Retirada", "Prazo", "Devolução", "Situação", "Observações")
    statuses = {"em_aberto": "Em aberto", "atrasado": "Atrasado", "devolvido": "Devolvido"}
    with file.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream, delimiter=";")
        writer.writerow(labels)
        for row in rows:
            row["status"] = statuses[row["status"]]
            writer.writerow([csv_safe(row[key]) for key in keys])
    print(f"{len(rows)} registros exportados para {file}.")


def public_check(client):
    result = client.request("/catalog")
    if not isinstance(result["items"], list):
        raise RuntimeError("A API não retornou o catálogo esperado.")
    try:
        client.request("/admin/students")
    except ApiError as error:
        if error.status in {401, 403}:
            print("Catálogo público respondeu e a API de alunos recusou acesso sem sessão.")
            return
        raise
    raise RuntimeError("A API de alunos permitiu acesso sem uma sessão.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default=os.getenv("BIBLIOTECA_URL", "http://127.0.0.1:8096"))
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("verificar", help="Testa o catálogo e a proteção dos dados de alunos.")
    qr = commands.add_parser("qrcode", help="Salva o QR Code da página pública em PNG.")
    qr.add_argument("--saida", type=Path, default=Path("qrcode-acervo.png"))
    report = commands.add_parser("relatorio", help="Exporta os empréstimos em CSV.")
    report.add_argument("--tipo", choices=["todos", "ativos", "atrasados"], default="atrasados")
    report.add_argument("--saida", type=Path, default=Path("emprestimos.csv"))
    options = parser.parse_args()
    try:
        client = Biblioteca(options.url)
        if options.command == "verificar":
            public_check(client)
        elif options.command == "qrcode":
            data = client.request("/catalog/qr?" + urlencode({"url": client.url + "/"}), binary=True)
            if not data.startswith(b"\x89PNG\r\n\x1a\n"):
                raise RuntimeError("A API não retornou o PNG esperado.")
            options.saida.write_bytes(data)
            print(f"QR Code salvo em {options.saida}.")
        else:
            email = os.getenv("BIBLIOTECA_ADMIN_EMAIL") or input("E-mail administrativo: ")
            password = os.getenv("BIBLIOTECA_ADMIN_PASSWORD") or getpass.getpass("Senha administrativa: ")
            client.login(email, password)
            export_report(client, options.tipo, options.saida)
    except (ApiError, OSError, RuntimeError, ValueError) as error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
