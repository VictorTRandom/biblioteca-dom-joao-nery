"""Executa o roteiro completo em um banco de teste descartável."""

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
import os
from uuid import uuid4
from urllib.parse import urlencode
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from biblioteca import Biblioteca, ApiError, public_check


def expect(status, action):
    try:
        action()
    except ApiError as error:
        assert error.status == status, (error.status, str(error))
        return
    raise AssertionError(f"Esperava HTTP {status}.")


def school_today():
    try:
        return datetime.now(ZoneInfo("America/Sao_Paulo")).date().isoformat()
    except ZoneInfoNotFoundError:
        # No Windows, o relógio da demonstração está configurado no fuso da escola.
        return datetime.now().date().isoformat()


def run(url, email, password):
    public = Biblioteca(url)
    public_check(public)
    expect(401, lambda: public.login(email, "senha-invalida"))
    admin = Biblioteca(url)
    admin.login(email, password)
    assert admin.request("/admin/session")["email"] == email.lower()
    token = uuid4().hex[:9]

    def create(path, data):
        return admin.request(f"/admin/{path}", "POST", data)

    author = create("authors", {"name": f"Autora fictícia {token}"})["id"]
    subject = create("subjects", {"name": f"Assunto fictício {token}"})["id"]
    student = create("students", {
        "name": "Estudante fictício", "ra": f"TESTE-{token}", "email": "", "className": "Demo", "active": True,
    })["id"]
    model = {"title": f"Árvore {token}", "description": "Obra usada para testes automatizados.",
             "year": 2020, "authorId": author, "subjectId": subject, "initialCopies": 1, "active": True}
    book = create("books", model)
    other = create("books", {**model, "title": f"Título extra {token}", "initialCopies": 0})
    assert book["code"].startswith("LIV-") and book["code"] != other["code"] and book["available"] == 1
    old_copy = admin.request(f"/admin/books/{book['id']}/copies")[0]
    assert old_copy["code"].startswith("EX-")
    updates = {k: v for k, v in model.items() if k != "initialCopies"}
    edited = admin.request(f"/admin/books/{book['id']}", "PUT", {**updates, "year": 2021})
    assert edited["code"] == book["code"] and edited["publication_year"] == 2021
    for field in (f"Arvore {token}", f"Autora ficticia {token}", f"Assunto ficticio {token}"):
        result = public.request("/catalog?" + urlencode({"q": field}))
        assert any(entry["id"] == book["id"] for entry in result["items"])
    assert public.request(f"/catalog?subject={subject}")["items"]
    print("Login restrito, CRUD de acervo, busca e códigos persistentes verificados.")

    payload = {"studentId": student, "bookId": book["id"], "copyId": old_copy["id"],
               "dueDate": school_today(), "notes": "Teste de concorrência"}

    def parallel_loan():
        session = Biblioteca(url)
        session.login(email, password)
        try:
            return session.request("/admin/loans", "POST", payload)
        except ApiError as error:
            assert error.status == 409
            return None

    with ThreadPoolExecutor(max_workers=2) as pool:
        loans = list(pool.map(lambda _: parallel_loan(), range(2)))
    success = [item for item in loans if item is not None]
    assert len(success) == 1
    loan = success[0]
    assert public.request(f"/catalog/{book['id']}")["available"] == 0
    expect(409, lambda: create("loans", payload))
    expect(409, lambda: admin.request(f"/admin/books/{book['id']}/copies/{old_copy['id']}", "PUT", {"active": False}))
    for _ in range(2):
        result = admin.request(f"/admin/loans/{loan['id']}/return", "POST")
        assert result["status"] == "devolvido"
    assert public.request(f"/catalog/{book['id']}")["available"] == 1
    expect(409, lambda: admin.request(f"/admin/books/{book['id']}", "DELETE"))
    expect(409, lambda: admin.request(f"/admin/students/{student}", "DELETE"))
    expect(409, lambda: admin.request(f"/admin/authors/{author}", "DELETE"))
    print("Concorrência, falta de estoque, devolução e preservação do histórico verificados.")

    copies = create(f"books/{book['id']}/copies", {"quantity": 2})
    assert len({item["code"] for item in copies}) == 3 and old_copy["code"] in {item["code"] for item in copies}
    population = admin.request(f"/admin/books/{book['id']}/copies/{old_copy['id']}", "PUT", {"active": False})
    assert sum(item["active"] for item in population) == 2
    admin.request(f"/admin/books/{book['id']}", "PUT", {**updates, "active": False})
    expect(404, lambda: public.request(f"/catalog/{book['id']}"))
    admin.request(f"/admin/books/{other['id']}", "DELETE")
    request_book = create("books", {**model, "title": f"Pedido pelo catálogo {token}"})
    request_data = {"bookId": request_book["id"], "ra": f"TESTE-{token}"}
    pending = public.request("/catalog/requests", "POST", request_data)
    assert pending["status"] == "pendente" and "student_ra" not in pending and "student" not in pending
    tracking = "/catalog/requests/" + pending["tracking_code"]
    assert public.request(tracking)["status"] == "pendente"
    assert public.request(f"/catalog/{request_book['id']}")["available"] == 1
    expect(409, lambda: public.request("/catalog/requests", "POST", request_data))
    expect(401, lambda: public.request("/admin/requests"))
    queue = admin.request("/admin/requests")
    queued = next(entry for entry in queue if entry["title"] == request_book["title"])
    assert queued["student"] == "Estudante fictício"
    approval = f"/admin/requests/{queued['id']}/approve"
    booked = admin.request(approval, "POST", {"dueDate": school_today()})
    assert admin.request(approval, "POST", {"dueDate": school_today()})["id"] == booked["id"]
    assert public.request(tracking)["status"] == "aprovado"
    assert public.request(f"/catalog/{request_book['id']}")["available"] == 0
    admin.request(f"/admin/loans/{booked['id']}/return", "POST")
    assert public.request(tracking)["returned_at"] and public.request(f"/catalog/{request_book['id']}")["available"] == 1
    rejected = public.request("/catalog/requests", "POST", {"bookId": request_book["id"], "ra": f"INEXISTENTE-{token}"})
    invalid = next(entry for entry in admin.request("/admin/requests") if entry["student_ra"] == f"INEXISTENTE-{token}")
    expect(409, lambda: admin.request(f"/admin/requests/{invalid['id']}/approve", "POST", {"dueDate": school_today()}))
    admin.request(f"/admin/requests/{invalid['id']}/reject", "POST")
    assert public.request("/catalog/requests/" + rejected["tracking_code"])["status"] == "recusado"
    print("Pedidos públicos, acompanhamento, aprovação, recusa e estoque verificados.")
    # A instalação de demonstração contém um empréstimo fictício atrasado.
    overdue = admin.request("/admin/loans?filter=atrasados")
    assert overdue and all(item["status"] == "atrasado" for item in overdue)
    expect(400, lambda: public.request("/catalog/qr?url=https%3A%2F%2Fexample.com%2Fadmin.html"))
    qr = public.request("/catalog/qr?url=https%3A%2F%2Fexample.com%2F", binary=True)
    assert qr.startswith(b"\x89PNG\r\n\x1a\n")
    admin.request("/logout", "POST")
    expect(401, lambda: admin.request("/admin/session"))
    print("Exemplares, inativação, relatórios, QR Code e encerramento de sessão verificados.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:8097")
    parser.add_argument("--banco-de-teste", action="store_true", required=True)
    args = parser.parse_args()
    run(args.url, os.environ["BIBLIOTECA_ADMIN_EMAIL"], os.environ["BIBLIOTECA_ADMIN_PASSWORD"])
