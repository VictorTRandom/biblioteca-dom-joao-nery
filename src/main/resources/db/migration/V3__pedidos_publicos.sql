CREATE TABLE loan_requests (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tracking_code uuid NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    book_id bigint NOT NULL REFERENCES books(id) ON DELETE RESTRICT,
    student_ra varchar(40) NOT NULL CHECK (btrim(student_ra) <> ''),
    requested_at timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz,
    status varchar(14) NOT NULL DEFAULT 'pendente' CHECK (status IN ('pendente', 'aprovado', 'recusado')),
    loan_id bigint UNIQUE REFERENCES loans(id) ON DELETE RESTRICT,
    CHECK ((status = 'aprovado') = (loan_id IS NOT NULL)),
    CHECK ((status = 'pendente') = (resolved_at IS NULL))
);

CREATE UNIQUE INDEX loan_requests_one_pending
    ON loan_requests(book_id, lower(student_ra)) WHERE status = 'pendente';
CREATE INDEX loan_requests_book_idx ON loan_requests(book_id);
CREATE INDEX loan_requests_status_idx ON loan_requests(status, requested_at DESC);
