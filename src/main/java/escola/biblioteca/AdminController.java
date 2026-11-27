package escola.biblioteca;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
class AdminController {
    private final Library library;

    AdminController(Library library) {
        this.library = library;
    }

    @GetMapping("/session")
    Map<String, String> session(Principal user) {
        return Map.of("email", user.getName());
    }

    @GetMapping("/{kind:authors|subjects}")
    List<Map<String, Object>> entries(@PathVariable String kind) {
        return library.names(entriesOf(kind));
    }

    @PostMapping("/{kind:authors|subjects}")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> addEntry(@PathVariable String kind, @Valid @RequestBody Inputs.Name input) {
        return Map.of("id", library.saveName(entriesOf(kind), null, input));
    }

    @PutMapping("/{kind:authors|subjects}/{id}")
    Map<String, Long> editEntry(@PathVariable String kind, @PathVariable long id, @Valid @RequestBody Inputs.Name input) {
        return Map.of("id", library.saveName(entriesOf(kind), id, input));
    }

    @DeleteMapping("/{kind:authors|subjects}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteEntry(@PathVariable String kind, @PathVariable long id) {
        library.deleteName(entriesOf(kind), id);
    }

    @GetMapping("/books")
    List<Map<String, Object>> books() {
        return library.books();
    }

    @PostMapping("/books")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> addBook(@Valid @RequestBody Inputs.Book input) {
        return library.saveBook(null, input);
    }

    @PutMapping("/books/{id}")
    Map<String, Object> editBook(@PathVariable long id, @Valid @RequestBody Inputs.Book input) {
        return library.saveBook(id, input);
    }

    @DeleteMapping("/books/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteBook(@PathVariable long id) {
        library.deleteBook(id);
    }

    @GetMapping("/books/{id}/copies")
    List<Map<String, Object>> copies(@PathVariable long id) {
        return library.copies(id);
    }

    @PostMapping("/books/{id}/copies")
    @ResponseStatus(HttpStatus.CREATED)
    List<Map<String, Object>> addCopies(@PathVariable long id, @Valid @RequestBody Inputs.Quantity input) {
        return library.addCopies(id, input.quantity());
    }

    @PutMapping("/books/{id}/copies/{copyId}")
    List<Map<String, Object>> changeCopy(@PathVariable long id, @PathVariable long copyId,
                                       @Valid @RequestBody Inputs.Status input) {
        return library.changeCopy(id, copyId, input.active());
    }

    @GetMapping("/students")
    List<Map<String, Object>> students() {
        return library.students();
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> addStudent(@Valid @RequestBody Inputs.Student input) {
        return Map.of("id", library.saveStudent(null, input));
    }

    @PutMapping("/students/{id}")
    Map<String, Long> editStudent(@PathVariable long id, @Valid @RequestBody Inputs.Student input) {
        return Map.of("id", library.saveStudent(id, input));
    }

    @DeleteMapping("/students/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteStudent(@PathVariable long id) {
        library.deleteStudent(id);
    }

    @GetMapping("/loans")
    List<Map<String, Object>> loans(@RequestParam(defaultValue = "todos") String filter) {
        return library.loans(filter);
    }

    @PostMapping("/loans")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> borrow(@Valid @RequestBody Inputs.Loan input) {
        return library.borrow(input);
    }

    @PostMapping("/loans/{id}/return")
    Map<String, Object> returnLoan(@PathVariable long id) {
        return library.returnLoan(id);
    }

    @GetMapping("/requests")
    List<Map<String, Object>> requests(@RequestParam(defaultValue = "pendentes") String filter) {
        return library.requests(filter);
    }

    @PostMapping("/requests/{id}/approve")
    Map<String, Object> approveRequest(@PathVariable long id, @Valid @RequestBody Inputs.ApproveRequest input) {
        return library.approveRequest(id, input.dueDate());
    }

    @PostMapping("/requests/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rejectRequest(@PathVariable long id) {
        library.rejectRequest(id);
    }

    @GetMapping("/summary")
    Map<String, Object> summary() {
        return library.totals();
    }

    private static Library.Entries entriesOf(String kind) {
        return kind.equals("authors") ? Library.Entries.AUTHORS : Library.Entries.SUBJECTS;
    }
}
