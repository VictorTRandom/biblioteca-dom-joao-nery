CREATE TABLE administrators (
    email varchar(254) PRIMARY KEY,
    password_hash varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE app_metadata (key text PRIMARY KEY);

CREATE TABLE authors (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(160) NOT NULL CHECK (btrim(name) <> '')
);
CREATE UNIQUE INDEX authors_name_unique ON authors (lower(name));

CREATE TABLE subjects (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(100) NOT NULL CHECK (btrim(name) <> '')
);
CREATE UNIQUE INDEX subjects_name_unique ON subjects (lower(name));

CREATE TABLE students (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ra varchar(40) NOT NULL,
    name varchar(160) NOT NULL,
    email varchar(254) NOT NULL DEFAULT '',
    class_name varchar(60) NOT NULL,
    active boolean NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX students_ra_unique ON students (lower(ra));

CREATE TABLE books (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title varchar(200) NOT NULL,
    description varchar(4000) NOT NULL DEFAULT '',
    publication_year int CHECK (publication_year BETWEEN 1 AND 3000),
    author_id bigint NOT NULL REFERENCES authors(id) ON DELETE RESTRICT,
    subject_id bigint NOT NULL REFERENCES subjects(id) ON DELETE RESTRICT,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE copies (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    book_id bigint NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    active boolean NOT NULL DEFAULT true
);
CREATE INDEX copies_book_idx ON copies(book_id);

CREATE TABLE loans (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id bigint NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    copy_id bigint NOT NULL REFERENCES copies(id) ON DELETE RESTRICT,
    borrowed_at date NOT NULL,
    due_date date NOT NULL CHECK (due_date >= borrowed_at),
    returned_at date CHECK (returned_at >= borrowed_at),
    notes varchar(1000) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX loans_one_active_per_copy ON loans(copy_id) WHERE returned_at IS NULL;
CREATE INDEX loans_copy_history_idx ON loans(copy_id);
CREATE INDEX loans_student_idx ON loans(student_id);
CREATE INDEX loans_active_due_idx ON loans(due_date) WHERE returned_at IS NULL;

CREATE FUNCTION inventory_code(prefix text, id bigint) RETURNS text
LANGUAGE sql IMMUTABLE STRICT
RETURN prefix || '-' || lpad(id::text, greatest(6, length(id::text)), '0');

CREATE FUNCTION search_text(value text) RETURNS text
LANGUAGE sql IMMUTABLE STRICT
RETURN translate(lower(value), 'áàâãäéèêëíìîïóòôõöúùûüçñ', 'aaaaaeeeeiiiiooooouuuucn');

CREATE VIEW inventory AS
SELECT b.id, inventory_code('LIV', b.id) AS code, b.title, b.description, b.publication_year,
       b.author_id, a.name AS author, b.subject_id, s.name AS subject, b.active,
       count(c.id) FILTER (WHERE c.active) AS total_copies,
       count(c.id) FILTER (WHERE c.active AND l.id IS NULL) AS available
FROM books b
JOIN authors a ON a.id = b.author_id
JOIN subjects s ON s.id = b.subject_id
LEFT JOIN copies c ON c.book_id = b.id
LEFT JOIN loans l ON l.copy_id = c.id AND l.returned_at IS NULL
GROUP BY b.id, a.name, s.name;
