package escola.biblioteca;

import java.sql.SQLException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> invalid() {
        return problem(HttpStatus.BAD_REQUEST, "Confira os dados informados.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException error) {
        var field = error.getBindingResult().getFieldError();
        String label = field == null ? "" : switch (field.getField()) {
            case "title" -> "título";
            case "description" -> "descrição";
            case "year" -> "ano";
            case "authorId" -> "autor";
            case "subjectId" -> "assunto";
            case "active" -> "situação";
            case "name" -> "nome";
            case "ra" -> "RA";
            case "email" -> "e-mail";
            case "className" -> "turma";
            case "studentId" -> "aluno";
            case "bookId" -> "livro";
            case "copyId" -> "exemplar";
            case "dueDate" -> "data prevista";
            case "notes" -> "observações";
            case "quantity", "initialCopies" -> "quantidade";
            default -> "";
        };
        return problem(HttpStatus.BAD_REQUEST, label.isEmpty() ? "Confira os campos do formulário." : "Confira o campo " + label + ".");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> constraint(DataIntegrityViolationException error) {
        if (error.getMostSpecificCause() instanceof SQLException sql) {
            return switch (sql.getSQLState()) {
                case "23505" -> problem(HttpStatus.CONFLICT, "Já existe um cadastro com esse valor ou o exemplar foi emprestado.");
                case "23503" -> problem(HttpStatus.CONFLICT, "O registro tem vínculos com o acervo ou o histórico. Confira os cadastros relacionados.");
                default -> problem(HttpStatus.BAD_REQUEST, "O banco recusou os dados informados.");
            };
        }
        return problem(HttpStatus.BAD_REQUEST, "O banco recusou os dados informados.");
    }

    private ResponseEntity<Map<String, String>> problem(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
