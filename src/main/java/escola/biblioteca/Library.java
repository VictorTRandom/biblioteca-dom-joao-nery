package escola.biblioteca;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class Library {
    private static final int PAGE_SIZE = 9;
    private static final String PUBLIC_FIELDS = "id, code, title, description, publication_year, author, subject, total_copies, available";
    private static final String CATALOG_CONDITIONS = """
            active AND (? = 0 OR subject_id = ?)
              AND position(search_text(?) IN search_text(concat_ws(' ', title, author, subject, code))) > 0
            """;
    private static final String ADMIN_LOANS = """
            SELECT l.id, l.borrowed_at, l.due_date, l.returned_at, l.notes,
                   s.name AS student, s.ra, b.title, inventory_code('EX', c.id) AS copy_code,
                   CASE WHEN l.returned_at IS NOT NULL THEN 'devolvido'
                        WHEN l.due_date < ? THEN 'atrasado' ELSE 'em_aberto' END AS status
            FROM loans l JOIN students s ON s.id = l.student_id
                         JOIN copies c ON c.id = l.copy_id
                         JOIN books b ON b.id = c.book_id
            """;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    Library(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    Map<String, Object> catalog(String query, long subject, int page) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + PUBLIC_FIELDS + " FROM inventory WHERE " + CATALOG_CONDITIONS
                        + " ORDER BY search_text(title), id LIMIT ? OFFSET ?",
                subject, subject, query.strip(), PAGE_SIZE + 1, (page - 1) * PAGE_SIZE);
        return Map.of("items", rows.subList(0, Math.min(rows.size(), PAGE_SIZE)), "page", page,
                "hasNext", rows.size() > PAGE_SIZE);
    }

    Map<String, Object> publicBook(long id) {
        return one(jdbc.queryForList("SELECT " + PUBLIC_FIELDS + " FROM inventory WHERE id=? AND active", id),
                "Livro não encontrado.");
    }

    List<Map<String, Object>> publicSubjects() {
        return jdbc.queryForList("""
                SELECT s.id,s.name FROM subjects s
                WHERE EXISTS (SELECT 1 FROM books b WHERE b.subject_id=s.id AND b.active)
                ORDER BY search_text(s.name)
                """);
    }

    enum Entries {
        AUTHORS("authors"), SUBJECTS("subjects");
        final String table;
        Entries(String table) { this.table = table; }
    }

    List<Map<String, Object>> names(Entries entries) {
        return jdbc.queryForList("SELECT id,name FROM " + entries.table + " ORDER BY search_text(name),id");
    }

    long saveName(Entries entries, Long id, Inputs.Name input) {
        String name = input.name().strip();
        if (entries == Entries.SUBJECTS && name.length() > 100) {
            throw bad("O assunto pode ter até 100 caracteres.");
        }
        if (id == null) {
            return jdbc.queryForObject("INSERT INTO " + entries.table + "(name) VALUES (?) RETURNING id", Long.class, name);
        }
        updated(jdbc.update("UPDATE " + entries.table + " SET name=? WHERE id=?", name, id), "Cadastro não encontrado.");
        return id;
    }

    void deleteName(Entries entries, long id) {
        updated(jdbc.update("DELETE FROM " + entries.table + " WHERE id=?", id), "Cadastro não encontrado.");
    }

    List<Map<String, Object>> books() {
        return jdbc.queryForList("SELECT * FROM inventory ORDER BY search_text(title),id");
    }

    @Transactional
    Map<String, Object> saveBook(Long id, Inputs.Book input) {
        String title = input.title().strip();
        String description = Inputs.optional(input.description());
        if (id == null) {
            id = jdbc.queryForObject("""
                    INSERT INTO books(title,description,publication_year,author_id,subject_id,active)
                    VALUES (?,?,?,?,?,?) RETURNING id
                    """, Long.class, title, description, input.year(), input.authorId(), input.subjectId(), input.active());
            addCopyRows(id, input.initialCopies() == null ? 1 : input.initialCopies());
        } else {
            if (input.initialCopies() != null) {
                throw bad("Use o painel de exemplares para alterar o estoque.");
            }
            updated(jdbc.update("""
                    UPDATE books SET title=?,description=?,publication_year=?,author_id=?,subject_id=?,active=? WHERE id=?
                    """, title, description, input.year(), input.authorId(), input.subjectId(), input.active(), id),
                    "Livro não encontrado.");
        }
        return jdbc.queryForMap("SELECT * FROM inventory WHERE id=?", id);
    }

    void deleteBook(long id) {
        updated(jdbc.update("DELETE FROM books WHERE id=?", id), "Livro não encontrado.");
    }

    List<Map<String, Object>> copies(long bookId) {
        return jdbc.queryForList("""
                SELECT c.id, inventory_code('EX',c.id) AS code, c.active,
                       EXISTS (SELECT 1 FROM loans l WHERE l.copy_id=c.id AND l.returned_at IS NULL) AS on_loan
                FROM copies c WHERE c.book_id=? ORDER BY c.id
                """, bookId);
    }

    @Transactional
    List<Map<String, Object>> addCopies(long bookId, int quantity) {
        lockBook(bookId);
        addCopyRows(bookId, quantity);
        return copies(bookId);
    }

    @Transactional
    List<Map<String, Object>> changeCopy(long bookId, long copyId, boolean active) {
        lockBook(bookId);
        one(jdbc.queryForList("SELECT id FROM copies WHERE id=? AND book_id=?", copyId, bookId),
                "Exemplar não encontrado.");
        if (!active && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM loans WHERE copy_id=? AND returned_at IS NULL)", Boolean.class, copyId))) {
            throw conflict("Registre a devolução antes de desativar o exemplar.");
        }
        jdbc.update("UPDATE copies SET active=? WHERE id=? AND book_id=?", active, copyId, bookId);
        return copies(bookId);
    }

    List<Map<String, Object>> students() {
        return jdbc.queryForList("SELECT * FROM students ORDER BY search_text(name),id");
    }

    long saveStudent(Long id, Inputs.Student input) {
        String ra = input.ra().strip();
        String name = input.name().strip();
        String email = Inputs.optional(input.email());
        String className = input.className().strip();
        if (id == null) {
            return jdbc.queryForObject("""
                    INSERT INTO students(ra,name,email,class_name,active) VALUES (?,?,?,?,?) RETURNING id
                    """, Long.class, ra, name, email, className, input.active());
        }
        updated(jdbc.update("UPDATE students SET ra=?,name=?,email=?,class_name=?,active=? WHERE id=?",
                ra, name, email, className, input.active(), id), "Aluno não encontrado.");
        return id;
    }

    void deleteStudent(long id) {
        updated(jdbc.update("DELETE FROM students WHERE id=?", id), "Aluno não encontrado.");
    }

    List<Map<String, Object>> loans(String filter) {
        LocalDate today = LocalDate.now(clock);
        String order = " ORDER BY l.borrowed_at DESC,l.id DESC";
        return switch (filter) {
            case "todos" -> jdbc.queryForList(ADMIN_LOANS + order, today);
            case "ativos" -> jdbc.queryForList(ADMIN_LOANS + " WHERE l.returned_at IS NULL" + order, today);
            case "atrasados" -> jdbc.queryForList(ADMIN_LOANS + " WHERE l.returned_at IS NULL AND l.due_date < ?" + order, today, today);
            default -> throw bad("Filtro de empréstimos inválido.");
        };
    }

    Map<String, Object> totals() {
        return jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM books WHERE active) AS titles,
                       (SELECT count(*) FROM copies c JOIN books b ON b.id=c.book_id WHERE b.active AND c.active) AS copies,
                       count(*) FILTER (WHERE returned_at IS NULL) AS active_loans,
                       count(*) FILTER (WHERE returned_at IS NULL AND due_date < ?) AS overdue
                FROM loans
                """, LocalDate.now(clock));
    }

    @Transactional
    Map<String, Object> borrow(Inputs.Loan input) {
        LocalDate today = LocalDate.now(clock);
        if (input.dueDate().isBefore(today) || input.dueDate().isAfter(today.plusYears(1))) {
            throw bad("A data prevista deve estar entre hoje e um ano a partir de hoje.");
        }
        if (!Boolean.TRUE.equals(lockBook(input.bookId()).get("active"))) {
            throw conflict("O livro está inativo.");
        }
        Map<String, Object> student = one(
                jdbc.queryForList("SELECT active FROM students WHERE id=? FOR UPDATE", input.studentId()),
                "Aluno não encontrado.");
        if (!Boolean.TRUE.equals(student.get("active"))) {
            throw conflict("O aluno está inativo.");
        }
        // O bloqueio do título serializa empréstimos e alterações de exemplares desse título.
        String available = """
                SELECT c.id FROM copies c WHERE c.book_id=? AND c.active
                AND NOT EXISTS (SELECT 1 FROM loans l WHERE l.copy_id=c.id AND l.returned_at IS NULL)
                """;
        List<Long> candidates = input.copyId() == null
                ? jdbc.queryForList(available + " ORDER BY c.id LIMIT 1", Long.class, input.bookId())
                : jdbc.queryForList(available + " AND c.id=?", Long.class, input.bookId(), input.copyId());
        if (candidates.isEmpty()) throw conflict("Não há exemplar disponível para este empréstimo.");
        Long copyId = candidates.getFirst();
        Long id = jdbc.queryForObject("""
                INSERT INTO loans(student_id,copy_id,borrowed_at,due_date,notes) VALUES (?,?,?,?,?) RETURNING id
                """, Long.class, input.studentId(), copyId, today, input.dueDate(), Inputs.optional(input.notes()));
        return jdbc.queryForMap(ADMIN_LOANS + " WHERE l.id=?", today, id);
    }

    @Transactional
    Map<String, Object> returnLoan(long id) {
        LocalDate today = LocalDate.now(clock);
        jdbc.update("UPDATE loans SET returned_at=? WHERE id=? AND returned_at IS NULL", today, id);
        return one(jdbc.queryForList(ADMIN_LOANS + " WHERE l.id=?", today, id), "Empréstimo não encontrado.");
    }

    @Transactional
    Map<String, Object> requestLoan(Inputs.PublicRequest input) {
        lockBook(input.bookId());
        Map<String, Object> book = publicBook(input.bookId());
        if (((Number) book.get("available")).longValue() == 0) {
            throw conflict("Este título está indisponível no momento.");
        }
        List<UUID> codes = jdbc.queryForList("""
                INSERT INTO loan_requests(book_id,student_ra) VALUES (?,?)
                ON CONFLICT (book_id, lower(student_ra)) WHERE status='pendente' DO NOTHING
                RETURNING tracking_code
                """, UUID.class, input.bookId(), input.ra().strip());
        if (codes.isEmpty()) {
            throw conflict("Já existe um pedido pendente com esses dados. Consulte o código do pedido anterior ou fale com a biblioteca.");
        }
        return track(codes.getFirst());
    }

    Map<String, Object> track(UUID code) {
        return one(jdbc.queryForList("""
                SELECT r.tracking_code, b.title, r.status, r.requested_at,
                       l.due_date, l.returned_at
                FROM loan_requests r JOIN books b ON b.id=r.book_id
                LEFT JOIN loans l ON l.id=r.loan_id
                WHERE r.tracking_code=?
                """, code), "Pedido não encontrado.");
    }

    List<Map<String, Object>> requests(String filter) {
        String selectRows = """
                SELECT r.id, r.student_ra, r.status, r.requested_at, s.name AS student,
                       coalesce(s.active, false) AS student_active, b.title, l.due_date
                FROM loan_requests r JOIN books b ON b.id=r.book_id
                LEFT JOIN students s ON lower(s.ra)=lower(r.student_ra)
                LEFT JOIN loans l ON l.id=r.loan_id
                """;
        return switch (filter) {
            case "pendentes" -> jdbc.queryForList(selectRows + " WHERE r.status='pendente' ORDER BY r.requested_at,r.id");
            case "todos" -> jdbc.queryForList(selectRows + " ORDER BY r.requested_at DESC,r.id DESC");
            default -> throw bad("Filtro de pedidos inválido.");
        };
    }

    @Transactional
    Map<String, Object> approveRequest(long id, LocalDate dueDate) {
        Map<String, Object> request = one(jdbc.queryForList("""
                SELECT id,book_id,student_ra,status,loan_id FROM loan_requests WHERE id=? FOR UPDATE
                """, id), "Pedido não encontrado.");
        if ("aprovado".equals(request.get("status"))) {
            long loanId = ((Number) request.get("loan_id")).longValue();
            return jdbc.queryForMap(ADMIN_LOANS + " WHERE l.id=?", LocalDate.now(clock), loanId);
        }
        if (!"pendente".equals(request.get("status"))) throw conflict("Este pedido já foi encerrado.");
        List<Long> students = jdbc.queryForList(
                "SELECT id FROM students WHERE lower(ra)=lower(?) AND active", Long.class, request.get("student_ra"));
        if (students.isEmpty()) throw conflict("Confira o RA e o cadastro ativo do aluno antes de confirmar.");
        long bookId = ((Number) request.get("book_id")).longValue();
        Map<String, Object> result = borrow(new Inputs.Loan(students.getFirst(), bookId, null, dueDate,
                "Pedido feito pelo catálogo público."));
        jdbc.update("""
                UPDATE loan_requests SET status='aprovado', loan_id=?, resolved_at=now() WHERE id=?
                """, result.get("id"), id);
        return result;
    }

    void rejectRequest(long id) {
        if (jdbc.update("UPDATE loan_requests SET status='recusado',resolved_at=now() WHERE id=? AND status='pendente'", id) > 0) {
            return;
        }
        Map<String, Object> request = one(jdbc.queryForList("SELECT status FROM loan_requests WHERE id=?", id),
                "Pedido não encontrado.");
        if (!"recusado".equals(request.get("status"))) throw conflict("Este pedido já foi confirmado.");
    }

    private void addCopyRows(long bookId, int quantity) {
        jdbc.update("INSERT INTO copies(book_id) SELECT ? FROM generate_series(1,?)", bookId, quantity);
    }

    private Map<String, Object> lockBook(long id) {
        return one(jdbc.queryForList("SELECT active FROM books WHERE id=? FOR UPDATE", id), "Livro não encontrado.");
    }

    private static Map<String, Object> one(List<Map<String, Object>> rows, String message) {
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        return rows.getFirst();
    }

    private static void updated(int rows, String message) {
        if (rows == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
